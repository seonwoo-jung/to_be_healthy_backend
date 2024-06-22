package com.tobe.healthy.member.application;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.tobe.healthy.common.Utils;
import com.tobe.healthy.common.redis.RedisService;
import com.tobe.healthy.config.error.CustomException;
import com.tobe.healthy.member.domain.dto.in.*;
import com.tobe.healthy.member.domain.dto.out.*;
import com.tobe.healthy.member.domain.entity.AlarmStatus;
import com.tobe.healthy.member.domain.entity.AlarmType;
import com.tobe.healthy.member.domain.entity.Member;
import com.tobe.healthy.member.repository.MemberRepository;
import com.tobe.healthy.trainer.domain.entity.TrainerMemberMapping;
import com.tobe.healthy.trainer.respository.TrainerMemberMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

import static com.tobe.healthy.common.Utils.*;
import static com.tobe.healthy.config.error.ErrorCode.*;
import static io.micrometer.common.util.StringUtils.isEmpty;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class MemberCommandService {

    private final PasswordEncoder passwordEncoder;
    private final MemberRepository memberRepository;
    private final RedisService redisService;
    private final TrainerMemberMappingRepository mappingRepository;
    private final AmazonS3 amazonS3;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    public String deleteMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(MEMBER_NOT_FOUND));
        member.deleteMember();
        mappingRepository.deleteByMemberId(memberId);
        return member.getUserId();
    }

    public boolean changePassword(CommandChangeMemberPassword request, Long memberId) {
        if (!request.getChangePassword1().equals(request.getChangePassword2())) {
            throw new CustomException(NOT_MATCH_PASSWORD);
        }

        if (Utils.validatePassword(request.getChangePassword1())) {
            throw new CustomException(PASSWORD_POLICY_VIOLATION);
        }

        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new CustomException(MEMBER_NOT_FOUND));

        if (passwordEncoder.matches(request.getChangePassword1(), member.getPassword())) {
            throw new IllegalArgumentException("이전 비밀번호와 동일합니다.");
        }

        String password = passwordEncoder.encode(request.getChangePassword1());

        member.changePassword(password);

        return true;
    }

    public RegisterMemberProfileResult registerProfile(MultipartFile uploadFile, Long memberId) {
        Member findMember = memberRepository.findMemberById(memberId)
                .orElseThrow(() -> new CustomException(MEMBER_NOT_FOUND));

        if (uploadFile.isEmpty()) {
            throw new IllegalArgumentException("프로필 사진을 등록해 주세요.");
        }

        ObjectMetadata objectMetadata = createObjectMetadata(uploadFile.getSize(), uploadFile.getContentType());
        String savedFileName = createFileName("origin/profile/") + uploadFile.getOriginalFilename().substring(uploadFile.getOriginalFilename().lastIndexOf("."));;

        try (InputStream inputStream = uploadFile.getInputStream()) {
            amazonS3.putObject(
                bucketName,
                savedFileName,
                inputStream,
                objectMetadata
            );

            String fileUrl = amazonS3.getUrl(bucketName, savedFileName).toString().replaceAll(S3_DOMAIN, CDN_DOMAIN);

            findMember.registerProfile(savedFileName, fileUrl);

            return RegisterMemberProfileResult.from(fileUrl, savedFileName);
        } catch (IOException e) {
            log.error("error => {}", e.getStackTrace()[0]);
            throw new CustomException(FILE_UPLOAD_ERROR);
        }
    }

    public DeleteMemberProfileResult deleteProfile(Long memberId) {
        Member findMember = memberRepository.findMemberById(memberId)
                .orElseThrow(() -> new CustomException(MEMBER_NOT_FOUND));

        if (ObjectUtils.isEmpty(findMember.getMemberProfile())) {
            throw new IllegalArgumentException("프로필 사진이 없습니다.");
        }

        String fileUrl = findMember.getMemberProfile().getFileUrl();
        String fileName = findMember.getMemberProfile().getFileName();

        amazonS3.deleteObject(bucketName, fileName);

        findMember.deleteProfile();

        return DeleteMemberProfileResult.from(fileUrl, fileName);
    }

    public CommandChangeNameResult changeName(CommandChangeName request, Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(MEMBER_NOT_FOUND));
        validateName(request.getName());
        member.changeName(request.getName());
        return CommandChangeNameResult.from(member);
    }

    public MemberChangeAlarmResult changeAlarm(AlarmType alarmType, AlarmStatus alarmStatus, Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(MEMBER_NOT_FOUND));
        member.changeAlarm(alarmType, alarmStatus);
        return MemberChangeAlarmResult.from(alarmType, alarmStatus);
    }

    public void updateMemo(Long trainerId, Long mmeberId, CommandUpdateMemo command) {
        memberRepository.findById(mmeberId)
                .orElseThrow(() -> new CustomException(MEMBER_NOT_FOUND));
        TrainerMemberMapping mapping = mappingRepository.findByTrainerIdAndMemberId(trainerId, mmeberId)
                .orElseThrow(() -> new CustomException(MEMBER_NOT_MAPPED));
        mapping.changeMemo(command.getMemo());
    }

    public CommandAssignNicknameResult assignNickname(CommandAssignNickname request, Long studentId) {
        Member member = memberRepository.findById(studentId)
                .orElseThrow(() -> new CustomException(MEMBER_NOT_FOUND));
        member.assignNickname(request.getNickname());
        return CommandAssignNicknameResult.from(member);
    }

    public Boolean changeEmail(CommandChangeEmail request, Long memberId) {
        memberRepository.findByEmail(request.getEmail()).ifPresent(m -> {
            throw new CustomException(MEMBER_EMAIL_DUPLICATION);
        });

        Member findMember = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(MEMBER_NOT_FOUND));

        String value = redisService.getValues(request.getEmail());

        if (isEmpty(value) || !value.equals(request.getEmailKey())) {
            throw new CustomException(MAIL_AUTH_CODE_NOT_VALID);
        }

        findMember.changeEmail(request.getEmail());
        redisService.deleteValues(request.getEmail());

        return true;
    }

    public Boolean changeScheduleNotice(AlarmStatus alarmStatus, Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(MEMBER_NOT_FOUND));
        member.changeScheduleNotice(alarmStatus);
        return true;
    }

    public Boolean changeTrainerFeedback(AlarmStatus alarmStatus, Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(MEMBER_NOT_FOUND));
        member.changeTrainerFeedback(alarmStatus);
        return true;
    }

    private void validateName(String name) {
        if (Utils.validateNameLength(name)) {
            throw new CustomException(MEMBER_NAME_LENGTH_NOT_VALID);
        }

        if (Utils.validateNameFormat(name)) {
            throw new CustomException(MEMBER_NAME_NOT_VALID);
        }
    }

    public Boolean changeDietNotice(AlarmStatus alarmStatus, Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(MEMBER_NOT_FOUND));
        member.changeDietNotice(alarmStatus);
        return true;
    }
}
