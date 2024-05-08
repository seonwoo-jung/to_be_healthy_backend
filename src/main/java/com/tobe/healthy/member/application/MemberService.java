package com.tobe.healthy.member.application;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tobe.healthy.common.redis.RedisKeyPrefix;
import com.tobe.healthy.common.redis.RedisService;
import com.tobe.healthy.config.OAuthProperties;
import com.tobe.healthy.config.error.CustomException;
import com.tobe.healthy.config.error.OAuthError.GoogleError;
import com.tobe.healthy.config.error.OAuthError.KakaoError;
import com.tobe.healthy.config.error.OAuthError.NaverError;
import com.tobe.healthy.config.error.OAuthException;
import com.tobe.healthy.config.security.JwtTokenGenerator;
import com.tobe.healthy.course.application.CourseService;
import com.tobe.healthy.course.domain.dto.CourseDto;
import com.tobe.healthy.course.domain.dto.in.CourseAddCommand;
import com.tobe.healthy.course.domain.entity.Course;
import com.tobe.healthy.course.repository.CourseRepository;
import com.tobe.healthy.diet.application.DietService;
import com.tobe.healthy.diet.domain.dto.DietDto;
import com.tobe.healthy.lesson_history.domain.dto.out.LessonHistoryResponse;
import com.tobe.healthy.lesson_history.repository.LessonHistoryRepository;
import com.tobe.healthy.member.domain.dto.in.*;
import com.tobe.healthy.member.domain.dto.in.MemberFindIdCommand.MemberFindIdCommandResult;
import com.tobe.healthy.member.domain.dto.in.OAuthInfo.NaverUserInfo;
import com.tobe.healthy.member.domain.dto.out.InvitationMappingResult;
import com.tobe.healthy.member.domain.dto.out.MemberInfoResult;
import com.tobe.healthy.member.domain.dto.out.MemberJoinCommandResult;
import com.tobe.healthy.member.domain.dto.out.StudentHomeResult;
import com.tobe.healthy.member.domain.entity.AlarmStatus;
import com.tobe.healthy.member.domain.entity.Member;
import com.tobe.healthy.member.domain.entity.MemberProfile;
import com.tobe.healthy.member.domain.entity.Tokens;
import com.tobe.healthy.member.repository.MemberProfileRepository;
import com.tobe.healthy.member.repository.MemberRepository;
import com.tobe.healthy.point.domain.dto.out.PointDto;
import com.tobe.healthy.point.domain.dto.out.RankDto;
import com.tobe.healthy.point.repository.PointRepository;
import com.tobe.healthy.schedule.domain.dto.out.MyReservation;
import com.tobe.healthy.schedule.repository.student.StudentScheduleRepository;
import com.tobe.healthy.trainer.application.TrainerService;
import com.tobe.healthy.trainer.domain.entity.TrainerMemberMapping;
import com.tobe.healthy.trainer.respository.TrainerMemberMappingRepository;
import io.jsonwebtoken.impl.Base64UrlCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

import static com.tobe.healthy.config.error.ErrorCode.*;
import static com.tobe.healthy.member.domain.entity.SocialType.*;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.http.MediaType.IMAGE_PNG_VALUE;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class MemberService {

    private final WebClient webClient;
    private final PasswordEncoder passwordEncoder;
    private final MemberRepository memberRepository;
    private final JwtTokenGenerator tokenGenerator;
    private final RedisService redisService;
    private final TrainerService trainerService;
    private final ObjectMapper objectMapper;
    private final OAuthProperties oAuthProperties;
    private final TrainerMemberMappingRepository mappingRepository;
    private final AmazonS3 amazonS3;
    private final MailService mailService;
    private final CourseService courseService;
    private final MemberProfileRepository memberProfileRepository;
    private final CourseRepository courseRepository;
    private final PointRepository pointRepository;
    private final StudentScheduleRepository studentScheduleRepository;
    private final LessonHistoryRepository lessonHistoryRepository;
    private final DietService dietService;

    private static final Integer EMAIL_AUTH_TIMEOUT = 3 * 60 * 1000;
    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    public boolean validateUserIdDuplication(String userId) {
        if (userId.length() < 4) {
            throw new CustomException(MEMBER_ID_NOT_VALID);
        }
        memberRepository.findByUserId(userId).ifPresent(m -> {
            throw new CustomException(MEMBER_ID_DUPLICATION);
        });
        return true;
    }

    public Boolean validateEmailDuplication(String email) {
        memberRepository.findByEmail(email).ifPresent(m -> {
            throw new CustomException(MEMBER_EMAIL_DUPLICATION);
        });
        return true;
    }

    public String sendEmailVerification(String email) {
        memberRepository.findByEmail(email).ifPresent(e -> {
            throw new CustomException(MEMBER_EMAIL_DUPLICATION);
        });

        String authKey = getAuthCode();
        redisService.setValuesWithTimeout(email, authKey, EMAIL_AUTH_TIMEOUT); // 3분

        // 3. 이메일에 인증번호 전송한다.
        mailService.sendAuthMail(email, authKey);

        return email;
    }

    public Boolean verifyEmailAuthNumber(String authNumber, String email) {
        String value = redisService.getValues(email);

        if (isEmpty(value) || !value.equals(authNumber)) {
            throw new CustomException(MAIL_AUTH_CODE_NOT_VALID);
        }

        return true;
    }

    public MemberJoinCommandResult joinMember(MemberJoinCommand request) {
        validateName(request.getName());
        validatePassword(request);
        validateDuplicationUserId(request.getUserId());
        validateDuplicationEmail(request.getEmail());

        String password = passwordEncoder.encode(request.getPassword());
        Member member = Member.join(request, password);
        memberRepository.save(member);

        return MemberJoinCommandResult.from(member);
    }

    private void validatePassword(MemberJoinCommand request) {
        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            throw new CustomException(CONFIRM_PASSWORD_NOT_MATCHED);
        }
        String regexp = "^[A-Za-z0-9]+$";
        if (request.getPassword().length() < 8 || !Pattern.matches(regexp, request.getPassword())) {
            throw new CustomException(PASSWORD_POLICY_VIOLATION);
        }
    }

    private void validateName(String name) {
        if (name.length() < 2) {
            throw new CustomException(MEMBER_NAME_LENGTH_NOT_VALID);
        }

        String regexp = "^[가-힣A-Za-z]+$";
        if (!Pattern.matches(regexp, name)) {
            throw new CustomException(MEMBER_NAME_NOT_VALID);
        }
    }

    public Tokens login(MemberLoginCommand request) {
        return memberRepository.findByUserId(request.getUserId(), request.getMemberType())
                .filter(member -> passwordEncoder.matches(request.getPassword(), member.getPassword()))
                .map(tokenGenerator::create)
                .orElseThrow(() -> new CustomException(MEMBER_NOT_FOUND));
    }

    public Tokens refreshToken(String userId, String refreshToken) {
        String result = redisService.getValues(userId);

        if (isEmpty(result)) {
            throw new CustomException(REFRESH_TOKEN_NOT_FOUND);
        }

        if (!result.equals(refreshToken)) {
            throw new CustomException(REFRESH_TOKEN_NOT_VALID);
        }

        Member member = memberRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(MEMBER_NOT_FOUND));

        return tokenGenerator.exchangeAccessToken(member.getId(),
                                                  member.getUserId(),
                                                  member.getMemberType(),
                                                  refreshToken,
                                                  member.getGym());
    }

    public MemberFindIdCommandResult findUserId(MemberFindIdCommand request) {
        Member member = memberRepository.findByEmailAndName(request.getEmail(), request.getName())
                .orElseThrow(() -> new CustomException(MEMBER_NOT_FOUND));
        return new MemberFindIdCommandResult(
                member.getUserId().substring(member.getUserId().length() - 3) + "**",
                member.getCreatedAt()
                );
    }

    public String findMemberPW(MemberFindPWCommand request) {
        Member member = memberRepository.findByUserIdAndName(request.getUserId(), request.getName())
                .orElseThrow(() -> new CustomException(MEMBER_NOT_FOUND));
        sendResetPassword(member.getEmail(), member);
        return member.getEmail();
    }

    public String deleteMember(String password, Long memberId) {
        Member member = memberRepository.findById(memberId)
                .filter(m -> passwordEncoder.matches(password, m.getPassword()))
                .orElseThrow(() -> new CustomException(MEMBER_NOT_FOUND));
        member.deleteMember();
        mappingRepository.deleteByMemberId(memberId);
        return member.getUserId();
    }

    public boolean changePassword(MemberPasswordChangeCommand request, Long memberId) {
        if (!request.getCurrPassword1().equals(request.getCurrPassword2())) {
            throw new CustomException(NOT_MATCH_PASSWORD);
        }

        Member member = memberRepository.findById(memberId)
                .filter(m -> passwordEncoder.matches(request.getCurrPassword1(), m.getPassword()))
                .orElseThrow(() -> new CustomException(MEMBER_NOT_FOUND));

        String password = passwordEncoder.encode(request.getChangePassword());

        member.changePassword(password);

        return true;
    }

    public Boolean changeProfile(MultipartFile uploadFile, Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(MEMBER_NOT_FOUND));

        if (!uploadFile.isEmpty()) {
            ObjectMetadata objectMetadata = getObjectMetadata(uploadFile.getSize(), uploadFile.getContentType());
            String savedFileName = "profile/" + createFileUUID();

            try (InputStream inputStream = uploadFile.getInputStream()) {
                amazonS3.putObject(
                        bucketName,
                        savedFileName,
                        inputStream,
                        objectMetadata
                );
                String fileUrl = amazonS3.getUrl(bucketName, savedFileName).toString();
                MemberProfile memberProfile = MemberProfile.create(fileUrl, member);
                memberProfileRepository.save(memberProfile);
            } catch (IOException e) {
                log.error("error => {}", e);
                throw new CustomException(FILE_UPLOAD_ERROR);
            }
        }
        return true;
    }

    public Boolean changeName(String name, Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(MEMBER_NOT_FOUND));
        member.changeName(name);
        return true;
    }

    public Boolean changeAlarm(AlarmStatus alarmStatus, Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(MEMBER_NOT_FOUND));
        member.changeAlarm(alarmStatus);
        return true;
    }

    public Boolean changeTrainerFeedback(AlarmStatus alarmStatus, Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(MEMBER_NOT_FOUND));
        member.changeTrainerFeedback(alarmStatus);
        return true;
    }

    public Tokens getKakaoAccessToken(SocialLoginCommand request) {
        IdToken response = getKakaoOAuthAccessToken(request.getCode(), request.getRedirectUrl());

        Optional<Member> findMember = memberRepository.findByEmailAndSocialType(response.getEmail(), KAKAO);

        if (findMember.isPresent()) {
            if (findMember.get().getMemberType().equals(request.getMemberType())) {
                return tokenGenerator.create(findMember.get());
            }
            throw new CustomException(MEMBER_NOT_FOUND);
        }

        Member member = Member.join(response.getEmail(), response.getNickname(), request.getMemberType(), KAKAO);
        MemberProfile profile = getProfile(response.getPicture(), member);
        member.setMemberProfile(profile);
        memberRepository.save(member);

        //초대가입인 경우
        if (StringUtils.isNotEmpty(request.getUuid())) {
            mappingTrainerAndStudent(member, request.getUuid(), response.getNickname(), true);
        }
        return tokenGenerator.create(member);
    }

    private byte[] getProfileImage(String imageName) {
        return webClient.get().uri(imageName)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class).flatMap(error -> {
                            log.error("error => {}", error);
                            return Mono.error(new CustomException(PROFILE_ACCESS_FAILED));
                        }))
                .bodyToMono(byte[].class)
                .share()
                .block();
    }

    private IdToken getKakaoOAuthAccessToken(String code, String redirectUrl) {
        MultiValueMap<String, String> request = new LinkedMultiValueMap<>();
        request.add("grant_type", oAuthProperties.getKakao().getGrantType());
        request.add("client_id", oAuthProperties.getKakao().getClientId());
        request.add("redirect_uri", redirectUrl);
        request.add("code", code);
        request.add("client_secret", oAuthProperties.getKakao().getClientSecret());
        OAuthInfo result = webClient.post()
                .uri(oAuthProperties.getKakao().getTokenUri())
                .bodyValue(request)
                .headers(header -> header.setContentType(APPLICATION_FORM_URLENCODED))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(KakaoError.class).flatMap(e -> {
                            log.error("error => {}", e);
                            return Mono.error(new OAuthException(e.getErrorDescription()));
                        }))
                .bodyToMono(OAuthInfo.class)
                .share().block();
        try {
            String token = decordToken(result);
            return new ObjectMapper().readValue(token, IdToken.class);
        } catch (JsonProcessingException e) {
            log.error("error => {}", e);
            throw new CustomException(JSON_PARSING_ERROR);
        }
    }

    private static String decordToken(OAuthInfo result) {
        byte[] decode = new Base64UrlCodec().decode(result.getIdToken().split("\\.")[1]);
        return new String(decode, StandardCharsets.UTF_8);
    }

    public Tokens getNaverAccessToken(SocialLoginCommand request) {
        OAuthInfo response = getNaverOAuthAccessToken(request.getCode(), request.getState());

        NaverUserInfo authorization = getNaverUserInfo(response);

        Optional<Member> findMember =
                memberRepository.findByEmailAndSocialType(authorization.getResponse().getEmail(), NAVER);

        if (findMember.isPresent()) {
            if (findMember.get().getMemberType().equals(request.getMemberType())) {
                return tokenGenerator.create(findMember.get());
            }
            throw new CustomException(MEMBER_NOT_FOUND);
        }

        Member member = Member.join(authorization.getResponse().getEmail(), authorization.getResponse().getName(), request.getMemberType(), NAVER);
        MemberProfile profile = getProfile(authorization.getResponse().getProfileImage(), member);
        member.setMemberProfile(profile);
        memberRepository.save(member);

        //초대가입인 경우
        if (StringUtils.isNotEmpty(request.getUuid())) {
            mappingTrainerAndStudent(member, request.getUuid(), authorization.getResponse().getName(), true);
        }
        return tokenGenerator.create(member);
    }

    private MemberProfile getProfile(String profileImage, Member member) {
        byte[] image = getProfileImage(profileImage);
        String savedFileName = "profile/" + createFileUUID();
        ObjectMetadata objectMetadata = getObjectMetadata((long) image.length, IMAGE_PNG_VALUE);
        try (InputStream inputStream = new ByteArrayInputStream(image)) {
            amazonS3.putObject(
                    bucketName,
                    savedFileName,
                    inputStream,
                    objectMetadata
            );
            String fileUrl = amazonS3.getUrl(bucketName, savedFileName).toString();
            return MemberProfile.create(fileUrl, member);
        } catch (IOException e) {
            log.error("error => {}", e);
            throw new CustomException(FILE_UPLOAD_ERROR);
        }
    }

    private MemberProfile getGoogleProfile(String profileImage, Member member) {
        byte[] image = getProfileImage(profileImage);
        String extension = ".jpg";
        String savedFileName = "profile/" + createFileUUID() + extension;
        ObjectMetadata objectMetadata = getObjectMetadata((long) image.length, IMAGE_PNG_VALUE);
        return qwe(member, image, savedFileName, objectMetadata);
    }

    private MemberProfile qwe(Member member, byte[] image, String savedFileName, ObjectMetadata objectMetadata) {
        try (InputStream inputStream = new ByteArrayInputStream(image)) {
            amazonS3.putObject(bucketName, savedFileName, inputStream, objectMetadata);
            String fileUrl = amazonS3.getUrl(bucketName, savedFileName).toString();
            return MemberProfile.create(fileUrl, member);
        } catch (IOException e) {
            log.error("error => {}", e);
            throw new CustomException(FILE_UPLOAD_ERROR);
        }
    }

    private NaverUserInfo getNaverUserInfo(OAuthInfo oAuthInfo) {
        return webClient.get()
                .uri(oAuthProperties.getNaver().getUserInfoUri())
                .headers(
                        header -> {
                            header.setContentType(APPLICATION_FORM_URLENCODED);
                            header.set("Authorization", "Bearer " + oAuthInfo.getAccessToken());
                        }
                )
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(NaverError.class).flatMap(e -> {
                            log.error("error => {}", e);
                            return Mono.error(new OAuthException(e.getMessage()));
                        }))
                .bodyToMono(NaverUserInfo.class)
                .share()
                .block();
    }

    private OAuthInfo getNaverOAuthAccessToken(String code, String state) {
        MultiValueMap<String, String> request = new LinkedMultiValueMap<>();
        request.add("grant_type", oAuthProperties.getNaver().getGrantType());
        request.add("client_id", oAuthProperties.getNaver().getClientId());
        request.add("client_secret", oAuthProperties.getNaver().getClientSecret());
        request.add("code", code);
        request.add("state", state);
        return webClient.post()
                .uri(oAuthProperties.getNaver().getTokenUri())
                .bodyValue(request)
                .headers(header -> header.setContentType(APPLICATION_FORM_URLENCODED))
                .retrieve()
                .bodyToMono(OAuthInfo.class)
                .share()
                .block();
    }

    @Transactional
    public Tokens getGoogleOAuth(SocialLoginCommand request) {
        OAuthInfo googleToken = getGoogleAccessToken(request.getCode(), request.getRedirectUrl());
        String[] check = googleToken.getIdToken().split("\\.");
        Base64.Decoder decoder = Base64.getDecoder();
        String payload = new String(decoder.decode(check[1]));
        Map<String, String> idToken = new HashMap<>();
        try {
            idToken = objectMapper.readValue(payload, Map.class);
        } catch (Exception e) {
            log.error("error => {}", e.getStackTrace()[0]);
        }
        String email = idToken.get("email");
        String name = idToken.get("name");
        String picture = idToken.get("picture");

        Optional<Member> findMember = memberRepository.findByEmailAndSocialType(email, GOOGLE);
        if (findMember.isPresent()) {
            if (findMember.get().getMemberType().equals(request.getMemberType())) {
                return tokenGenerator.create(findMember.get());
            }
            throw new CustomException(MEMBER_NOT_FOUND);
        }

        Member member = Member.join(email, name, request.getMemberType(), GOOGLE);
        MemberProfile profile = getGoogleProfile(picture, member);
        member.setMemberProfile(profile);
        memberRepository.save(member);

        //초대가입인 경우
        if (StringUtils.isNotEmpty(request.getUuid())) {
            mappingTrainerAndStudent(member, request.getUuid(), name, true);
        }
        return tokenGenerator.create(member);
    }

    private OAuthInfo getGoogleAccessToken(String code, String redirectUri) {
        String decode = URLDecoder.decode(code, StandardCharsets.UTF_8);
        MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
        requestBody.add("client_id", oAuthProperties.getGoogle().getClientId());
        requestBody.add("client_secret", oAuthProperties.getGoogle().getClientSecret());
        requestBody.add("grant_type", oAuthProperties.getGoogle().getGrantType());
        requestBody.add("redirect_uri", redirectUri);
        requestBody.add("code", decode);
        Mono<OAuthInfo> responseMono = null;
        try {
            responseMono = webClient.post()
                    .uri(oAuthProperties.getGoogle().getTokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response ->
                            response.bodyToMono(GoogleError.class).flatMap(e -> {
                                log.error("error => {}", e);
                                return Mono.error(new OAuthException(e.getErrorDescription()));
                            }))
                    .bodyToMono(OAuthInfo.class);
        } catch (Exception e) {
            log.error("error => {}", e.getStackTrace()[0]);
        }
        return responseMono.share().block();
    }

    private String createFileUUID() {
        return System.currentTimeMillis() + "-" + UUID.randomUUID();
    }

    private void validateDuplicationUserId(String userId) {
        if (Pattern.matches("^[가-힣]+$", userId)) {
            throw new CustomException(USERID_POLICY_VIOLATION);
        }
        memberRepository.findByUserId(userId).ifPresent(m -> {
            throw new CustomException(MEMBER_ID_DUPLICATION);
        });
    }

    private void validateDuplicationEmail(String email) {
        memberRepository.findByEmail(email).ifPresent(m -> {
            throw new CustomException(MEMBER_EMAIL_DUPLICATION);
        });
    }

    private void sendResetPassword(String email, Member member) {
        String resetPW = RandomStringUtils.random(12, true, true);
        member.resetPassword(passwordEncoder.encode(resetPW));
        mailService.sendResetPassword(email, resetPW);
    }

    private String getAuthCode() {
        Random random = new Random();
        StringBuilder buffer = new StringBuilder();

        while (buffer.length() < 6) {
            int num = random.nextInt(10);
            buffer.append(num);
        }

        return buffer.toString();
    }

    public MemberJoinCommandResult joinWithInvitation(MemberJoinCommand request) {
        MemberJoinCommandResult result = joinMember(request);
        Member member = memberRepository.findById(result.getId())
                .orElseThrow(() -> new CustomException(MEMBER_NOT_FOUND));

        mappingTrainerAndStudent(member, request.getUuid(), request.getName(), false);
        return result;
    }

    public void mappingTrainerAndStudent(Member member, String uuid, String reqName, boolean isSocial) {
        Map<String, String> map = getInviteMappingData(uuid);
        String name = map.get("name");
        if (!isSocial && !name.equals(reqName)) throw new CustomException(INVITE_NAME_NOT_VALID);
        member.changeName(name);

        //트레이너 학생 매핑
        Long trainerId = Long.valueOf(map.get("trainerId"));
        trainerService.mappingMemberAndTrainer(trainerId, member.getId());

        //수강권 등록
        int lessonCnt = Integer.parseInt(map.get("lessonCnt"));
        courseService.addCourse(trainerId, CourseAddCommand.create(member.getId(), lessonCnt));

        String invitationKey = RedisKeyPrefix.INVITATION.getDescription() + uuid;
        redisService.deleteValues(invitationKey);
    }

    public InvitationMappingResult getInvitationMapping(String uuid) {
        Map<String, String> map = getInviteMappingData(uuid);
        Long trainerId = Long.valueOf(map.get("trainerId"));
        String name = map.get("name");
        int lessonCnt = Integer.parseInt(map.get("lessonCnt"));
        Member member = memberRepository.findByMemberIdWithGym(trainerId);
        return InvitationMappingResult.create(member, name, lessonCnt);
    }

    private Map<String, String> getInviteMappingData(String uuid) {
        String invitationKey = RedisKeyPrefix.INVITATION.getDescription() + uuid;
        String mappedData = redisService.getValues(invitationKey);
        if (isEmpty(mappedData)) {
            throw new CustomException(INVITE_LINK_NOT_FOUND);
        }
        HashMap<String, String> map = new HashMap<>();
        try {
            map = objectMapper.readValue(mappedData, HashMap.class);
        } catch (JsonProcessingException e) {
            log.error("error => {}", e.getStackTrace()[0]);
        }
        return map;
    }

    public MemberInfoResult getMemberInfo(Long memberId) {
        Member member = memberRepository.findByIdAndDelYnFalse(memberId)
                .orElseThrow(() -> new CustomException(MEMBER_NOT_FOUND));
        return MemberInfoResult.create(member);
    }

    private ObjectMetadata getObjectMetadata(Long fileSize, String contentType) {
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentLength(fileSize);
        objectMetadata.setContentType(contentType);
        return objectMetadata;
    }

    public Boolean assignNickname(String nickname, Long studentId) {
        Member member = memberRepository.findById(studentId)
                .orElseThrow(() -> new CustomException(MEMBER_NOT_FOUND));
        member.assignNickname(nickname);
        return true;
    }

    public void updateMemo(Long trainerId, Long mmeberId, MemoCommand command) {
        memberRepository.findById(mmeberId)
                .orElseThrow(() -> new CustomException(MEMBER_NOT_FOUND));
        TrainerMemberMapping mapping = mappingRepository.findByTrainerIdAndMemberId(trainerId, mmeberId)
                .orElseThrow(() -> new CustomException(MEMBER_NOT_MAPPED));
        mapping.changeMemo(command.getMemo());
    }

    public StudentHomeResult getStudentHome(Long memberId) {
        //트레이너 매핑 여부
        TrainerMemberMapping mapping = mappingRepository.findTop1ByMemberIdOrderByCreatedAtDesc(memberId).orElse(null);
        boolean isMapped = mapping != null;

        //수강권
        Optional<Course> optCourse = courseRepository.findTop1ByMemberIdAndRemainLessonCntGreaterThanOrderByCreatedAtDesc(memberId, -1);
        CourseDto usingCourse = optCourse.map(CourseDto::from).orElse(null);

        //포인트
        int monthPoint = pointRepository.getPointOfSearchMonth(memberId, getNowMonth());
        int totalPoint = pointRepository.getTotalPoint(memberId);
        PointDto point = PointDto.create(monthPoint, totalPoint );

        //랭킹
        RankDto rank = new RankDto();
        if(isMapped){
            long totalMemberCnt = mappingRepository.countByTrainerId(mapping.getTrainer().getId());
            rank.setRanking(mapping.getRanking());
            rank.setTotalMemberCnt((int) totalMemberCnt);
        }

        //다음 PT 예정일
        MyReservation myReservation = studentScheduleRepository.findTop1MyReservation(memberId);

        //수업일지
        LessonHistoryResponse lessonHistory = lessonHistoryRepository.findTop1LessonHistoryByMemberId(memberId);

        //식단
        DietDto diet = dietService.getDietCreatedAtToday(memberId);

        return StudentHomeResult.create(isMapped, usingCourse, point, rank, myReservation, lessonHistory, diet);
    }

    private String getNowMonth() {
        return LocalDate.now().toString().substring(0, 7);
    }

    public Boolean changeScheduleNotice(AlarmStatus alarmStatus, Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(MEMBER_NOT_FOUND));
        member.changeScheduleNotice(alarmStatus);
        return true;
    }
}
