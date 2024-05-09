package com.tobe.healthy.schedule.application;

import com.tobe.healthy.config.error.CustomException;
import com.tobe.healthy.course.domain.dto.CourseDto;
import com.tobe.healthy.course.domain.entity.Course;
import com.tobe.healthy.course.repository.CourseRepository;
import com.tobe.healthy.member.domain.entity.Member;
import com.tobe.healthy.schedule.domain.dto.out.MyReservation;
import com.tobe.healthy.schedule.domain.dto.out.MyReservationResponse;
import com.tobe.healthy.schedule.domain.dto.out.ScheduleCommandResponse;
import com.tobe.healthy.schedule.domain.dto.out.ScheduleCommandResult;
import com.tobe.healthy.schedule.entity.in.ScheduleSearchCond;
import com.tobe.healthy.schedule.repository.student.StudentScheduleRepository;
import com.tobe.healthy.trainer.domain.entity.TrainerMemberMapping;
import com.tobe.healthy.trainer.respository.TrainerMemberMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.tobe.healthy.config.error.ErrorCode.TRAINER_NOT_MAPPED;
import static com.tobe.healthy.schedule.domain.entity.ReservationStatus.SOLD_OUT;
import static java.time.LocalTime.NOON;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class StudentScheduleService {

	private final StudentScheduleRepository studentScheduleRepository;
	private final CourseRepository courseRepository;
	private final TrainerMemberMappingRepository mappingRepository;

	public List<ScheduleCommandResult> findAllByApplicantId(Long memberId) {
		List<ScheduleCommandResult> result = studentScheduleRepository.findAllByApplicantId(memberId);
		return result.isEmpty() ? null : result;
	}

	public ScheduleCommandResponse findAllScheduleOfTrainer(ScheduleSearchCond searchCond, Member member) {

		TrainerMemberMapping mapping = mappingRepository.findTop1ByMemberIdOrderByCreatedAtDesc(member.getId())
				.orElseThrow(() -> new CustomException(TRAINER_NOT_MAPPED));
		Long trainerId = mapping.getTrainer().getId();
		return settingMorningAndAfternoon(studentScheduleRepository.findAllSchedule(searchCond, trainerId, member));
	}

	private ScheduleCommandResponse settingMorningAndAfternoon(List<ScheduleCommandResult> schedule) {
		List<ScheduleCommandResult> morning = schedule.stream()
				.filter(s -> NOON.isAfter(s.getLessonStartTime()))
				.peek(s -> {
					if(isExistsWaitingOrLessonDtEqToday(s) || isBeforeLessonDateTimeThenNow(s)){
						s.setReservationStatus(SOLD_OUT);
					}
				})
				.collect(Collectors.toList());

		List<ScheduleCommandResult> afternoon = schedule.stream()
				.filter(s -> NOON.isBefore(s.getLessonStartTime()))
				.peek(s -> {
					if(isExistsWaitingOrLessonDtEqToday(s) || isBeforeLessonDateTimeThenNow(s)){
						s.setReservationStatus(SOLD_OUT);
					}
				})
				.collect(Collectors.toList());

		return ScheduleCommandResponse.create(morning, afternoon);
	}

	private boolean isBeforeLessonDateTimeThenNow(ScheduleCommandResult schedule){
		LocalDateTime lessonDateTime = LocalDateTime.of(schedule.getLessonDt(), schedule.getLessonStartTime());
		return lessonDateTime.isBefore(LocalDateTime.now());
	}

	private boolean isExistsWaitingOrLessonDtEqToday(ScheduleCommandResult schedule){
		return schedule.getWaitingByName()!=null || schedule.getLessonDt().equals(LocalDate.now());
	}

	public MyReservationResponse findAllMyReservation(Long memberId, ScheduleSearchCond searchCond) {
		Optional<Course> optCourse = courseRepository.findTop1ByMemberIdAndRemainLessonCntGreaterThanOrderByCreatedAtDesc(memberId, -1);
		CourseDto course = optCourse.map(CourseDto::from).orElse(null);
		List<MyReservation> result = studentScheduleRepository.findAllMyReservation(memberId, searchCond);
		return MyReservationResponse.create(course, result);
	}
}
