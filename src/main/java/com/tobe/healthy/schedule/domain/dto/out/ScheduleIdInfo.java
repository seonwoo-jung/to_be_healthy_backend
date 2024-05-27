package com.tobe.healthy.schedule.domain.dto.out;

import com.tobe.healthy.schedule.domain.entity.Schedule;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@Builder
public class ScheduleIdInfo {
	private Long studentId;
	private Long trainerId;
	private Long scheduleId;
	private Long waitingStudentId;
	private String scheduleTime;

	public static ScheduleIdInfo from(Schedule schedule) {
		return ScheduleIdInfo.builder()
			.studentId(schedule.getApplicant().getId())
			.trainerId(schedule.getTrainer().getId())
			.scheduleId(schedule.getId())
			.build();
	}

	public static ScheduleIdInfo create(Schedule schedule, String scheduleTime) {
		return ScheduleIdInfo.builder()
				.studentId(schedule.getApplicant().getId())
				.trainerId(schedule.getTrainer().getId())
				.scheduleId(schedule.getId())
				.scheduleTime(scheduleTime)
				.build();
	}

	public static ScheduleIdInfo create(Long memberId, Schedule schedule, Long waitingStudentId, String scheduleTime) {
		return ScheduleIdInfo.builder()
				.studentId(memberId)
				.trainerId(schedule.getTrainer().getId())
				.scheduleId(schedule.getId())
				.waitingStudentId(waitingStudentId)
				.scheduleTime(scheduleTime)
				.build();
	}
}
