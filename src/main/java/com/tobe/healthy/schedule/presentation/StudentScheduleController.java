package com.tobe.healthy.schedule.presentation;

import com.tobe.healthy.common.ResponseHandler;
import com.tobe.healthy.config.security.CustomMemberDetails;
import com.tobe.healthy.schedule.application.StudentScheduleService;
import com.tobe.healthy.schedule.domain.dto.out.MyReservationResponse;
import com.tobe.healthy.schedule.domain.dto.out.ScheduleCommandResponse;
import com.tobe.healthy.schedule.domain.dto.out.ScheduleCommandResult;
import com.tobe.healthy.schedule.entity.in.ScheduleSearchCond;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/schedule/v1/student")
@Slf4j
@Valid
@Tag(name = "03-02.수업 API", description = "수업 일정 API")
public class StudentScheduleController {

    private final StudentScheduleService studentScheduleService;

    @Operation(summary = "학생이 트레이너의 전체 일정을 조회한다.", description = "전체 일정을 조회한다. 특정 일자나 기간으로 조회하고 싶으면 DTO를 활용한다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "전체 일정 조회 완료")
            })
    @GetMapping("/all")
    public ResponseHandler<ScheduleCommandResponse> findAllScheduleOfTrainer(@ParameterObject ScheduleSearchCond searchCond,
                                                                             @AuthenticationPrincipal CustomMemberDetails customMemberDetails) {
        return ResponseHandler.<ScheduleCommandResponse>builder()
                .data(studentScheduleService.findAllScheduleOfTrainer(searchCond, customMemberDetails.getMember()))
                .message("전체 일정을 조회했습니다.")
                .build();
    }

    @Operation(summary = "학생이 내 수업을 조회한다.", description = "회원이 등록된 수업 전체를 조회한다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "내 수업 조회 완료")
            })
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_STUDENT')")
    public ResponseHandler<List<ScheduleCommandResult>> findMySchedule(@AuthenticationPrincipal CustomMemberDetails customMemberDetails) {
        return ResponseHandler.<List<ScheduleCommandResult>>builder()
                .data(studentScheduleService.findAllByApplicantId(customMemberDetails.getMemberId()))
                .message("내 수업을 조회하였습니다.")
                .build();
    }

    @Operation(summary = "학생이 내 예약을 조회한다.", description = "학생이 내 예약을 조회한다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "학생이 내 예약을 조회하였습니다.")
            })
    @GetMapping("/my-reservation")
    @PreAuthorize("hasAuthority('ROLE_STUDENT')")
    public ResponseHandler<MyReservationResponse> findAllMyReservation(@AuthenticationPrincipal CustomMemberDetails customMemberDetails,
                                                                       @ParameterObject ScheduleSearchCond searchCond) {
        return ResponseHandler.<MyReservationResponse>builder()
                .data(studentScheduleService.findAllMyReservation(customMemberDetails.getMemberId(), searchCond))
                .message("학생이 내 예약을 조회하였습니다.")
                .build();
    }
}