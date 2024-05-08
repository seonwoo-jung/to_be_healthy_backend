package com.tobe.healthy.lesson_history.presentation

import com.tobe.healthy.ApiResultResponse
import com.tobe.healthy.common.CustomPagingResponse
import com.tobe.healthy.config.error.ErrorResponse
import com.tobe.healthy.config.security.CustomMemberDetails
import com.tobe.healthy.lesson_history.application.LessonHistoryService
import com.tobe.healthy.lesson_history.domain.dto.`in`.*
import com.tobe.healthy.lesson_history.domain.dto.out.LessonHistoryDetailResponse
import com.tobe.healthy.lesson_history.domain.dto.out.LessonHistoryResponse
import com.tobe.healthy.lesson_history.domain.dto.out.UploadFileResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Pageable
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/lessonhistory/v1")
@Tag(name = "07. 수업 일지")
class LessonHistoryController(
    private val lessonHistoryService: LessonHistoryService
) {

    @PostMapping("/register")
    @PreAuthorize("hasAuthority('ROLE_TRAINER')")
    @Operation(summary = "수업 일지를 등록한다.",
        responses = [
            ApiResponse(responseCode = "200", description = "수업 일지를 등록하였습니다."),
            ApiResponse(
                responseCode = "404(1)", description = "학생을 찾을 수 없습니다.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "404(2)", description = "트레이너를 찾을 수 없습니다.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "404(3)", description = "일정을 찾을 수 없습니다.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
            ),
        ])
    fun registerLessonHistory(@Parameter(content = [Content(schema = Schema(implementation = RegisterLessonHistoryCommand::class))])
                              @RequestBody @Valid request: RegisterLessonHistoryCommand,
                              @AuthenticationPrincipal member: CustomMemberDetails): ApiResultResponse<Boolean> {
        return ApiResultResponse(
            message = "수업 일지를 등록하였습니다.",
            data = lessonHistoryService.registerLessonHistory(request, member.memberId)
        )
    }

    @Operation(summary = "게시글/댓글 작성 전에 파일을 첨부한다.",
        responses = [
            ApiResponse(responseCode = "200", description = "게시글/댓글 작성 전에 파일을 첨부한다.")
        ])
    @PostMapping("/file")
    @PreAuthorize("hasAuthority('ROLE_TRAINER')")
    fun registerFilesOfLessonHistory(uploadFiles: MutableList<MultipartFile>,
                                     @AuthenticationPrincipal member: CustomMemberDetails): ApiResultResponse<List<UploadFileResponse>> {
        return ApiResultResponse(
            message = "파일을 등록하였습니다.",
            data = lessonHistoryService.registerFilesOfLessonHistory(uploadFiles, member.memberId)
        )
    }

    @Operation(summary = "전체 수업 일지를 조회한다.",
        responses = [
            ApiResponse(responseCode = "200", description = "전체 수업 일지를 조회하였습니다.")
        ])
    @GetMapping
    fun findAllLessonHistory(@Parameter(content = [Content(schema = Schema(implementation = SearchCondRequest::class))]) request: SearchCondRequest,
                             @ParameterObject pageable: Pageable,
                             @AuthenticationPrincipal member: CustomMemberDetails): ApiResultResponse<CustomPagingResponse<LessonHistoryResponse>> {
        return ApiResultResponse(
            message = "전체 수업 일지를 조회하였습니다.",
            data = lessonHistoryService.findAllLessonHistory(request, pageable, member.memberId, member.memberType)
        )
    }

    @Operation(summary = "학생의 수업일지 전체를 조회한다.",
        responses = [
            ApiResponse(responseCode = "200", description = "학생의 전체 수업 일지를 조회하였습니다.")
        ])
    @GetMapping("/detail/{studentId}")
    @PreAuthorize("hasAuthority('ROLE_TRAINER')")
    fun findAllLessonHistoryByMemberId(@Parameter(description = "학생 ID", example = "1") @PathVariable studentId: Long,
                                       @Parameter(content = [Content(schema = Schema(implementation = SearchCondRequest::class))]) request: SearchCondRequest,
                                       @ParameterObject pageable: Pageable): ApiResultResponse<CustomPagingResponse<LessonHistoryResponse>> {
        return ApiResultResponse(
            message = "학생의 수업 일지 전체를 조회하였습니다.",
            data = lessonHistoryService.findAllLessonHistoryByMemberId(studentId, request, pageable)
        )
    }

    @Operation(summary = "수업일지 단건을 조회한다.",
        responses = [
            ApiResponse(responseCode = "200", description = "학생의 전체 수업 일지를 조회하였습니다."),
            ApiResponse(responseCode = "404", description = "수업 일지를 찾을 수 없습니다."),
        ])
    @GetMapping("/{lessonHistoryId}")
    fun findOneLessonHistory(@Parameter(description = "수업일지 ID", example = "1") @PathVariable lessonHistoryId: Long,
                             @AuthenticationPrincipal member: CustomMemberDetails): ApiResultResponse<LessonHistoryDetailResponse?> {
        return ApiResultResponse(
            message = "수업 일지 단건을 조회하였습니다.",
            data = lessonHistoryService.findOneLessonHistory(lessonHistoryId, member.memberId, member.memberType)
        )
    }

    @Operation(summary = "수업일지를 수정한다.",
        responses = [
            ApiResponse(responseCode = "200", description = "수업 일지를 수정하였습니다."),
            ApiResponse(responseCode = "404", description = "수업 일지를 찾을 수 없습니다."),
        ])
    @PatchMapping("/{lessonHistoryId}")
    @PreAuthorize("hasAuthority('ROLE_TRAINER')")
    fun updateLessonHistory(@Parameter(description = "수업일지 ID", example = "1") @PathVariable lessonHistoryId: Long,
                            @RequestBody @Valid lessonHistoryCommand: LessonHistoryCommand,
                            @AuthenticationPrincipal member: CustomMemberDetails
    ): ApiResultResponse<Boolean> {
        return ApiResultResponse(
            message = "수업 일지가 수정되었습니다.",
            data = lessonHistoryService.updateLessonHistory(lessonHistoryId, lessonHistoryCommand)
        )
    }

    @Operation(summary = "수업일지를 삭제한다.",
        responses = [
            ApiResponse(responseCode = "200", description = "수업 일지를 삭제하였습니다."),
            ApiResponse(responseCode = "404", description = "수업 일지를 찾을 수 없습니다."),
        ])
    @PreAuthorize("hasAuthority('ROLE_TRAINER')")
    @DeleteMapping("/{lessonHistoryId}")
    fun deleteLessonHistory(@Parameter(description = "수업일지 ID", example = "1") @PathVariable lessonHistoryId: Long): ApiResultResponse<Boolean> {
        return ApiResultResponse(
            message = "수업 일지가 삭제되었습니다.",
            data = lessonHistoryService.deleteLessonHistory(lessonHistoryId)
        )
    }

    @Operation(summary = "수업일지에 댓글을 등록한다.",
        responses = [
            ApiResponse(responseCode = "200", description = "수업 일지에 댓글이 등록되었습니다."),
            ApiResponse(responseCode = "404(1)", description = "회원을 찾을 수 없습니다."),
            ApiResponse(responseCode = "404(2)", description = "수업 일지를 찾을 수 없습니다."),
        ])
    @PostMapping("/{lessonHistoryId}/comment")
    fun registerLessonHistoryComment(@Parameter(description = "수업일지 ID", example = "1") @PathVariable lessonHistoryId: Long,
                                     @Parameter(content = [Content(schema = Schema(implementation = CommentRegisterCommand::class))])
                                     @RequestBody @Valid request: CommentRegisterCommand,
                                     @AuthenticationPrincipal member: CustomMemberDetails): ApiResultResponse<Boolean> {
        return ApiResultResponse(
            message = "댓글이 등록되었습니다.",
            data = lessonHistoryService.registerLessonHistoryComment(lessonHistoryId, request, member.memberId)
        )
    }

    @Operation(summary = "수업일지에 답글을 등록한다.",
        responses = [
            ApiResponse(responseCode = "200", description = "수업 일지에 대댓글이 등록되었습니다."),
            ApiResponse(responseCode = "404(1)", description = "회원을 찾을 수 없습니다."),
            ApiResponse(responseCode = "404(2)", description = "수업 일지를 찾을 수 없습니다."),
        ])
    @PostMapping("/{lessonHistoryId}/comment/{lessonHistoryCommentId}")
    fun registerLessonHistoryComment(@Parameter(description = "수업일지 ID", example = "1") @PathVariable lessonHistoryId: Long,
                                     @Parameter(description = "수업일지 댓글 ID", example = "1") @PathVariable lessonHistoryCommentId: Long,
                                     @Parameter(content = [Content(schema = Schema(implementation = CommentRegisterCommand::class))])
                                     @RequestBody @Valid request: CommentRegisterCommand,
                                     @AuthenticationPrincipal member: CustomMemberDetails): ApiResultResponse<Boolean> {
        return ApiResultResponse(
            message = "대댓글이 등록되었습니다.",
            data = lessonHistoryService.registerLessonHistoryReply(lessonHistoryId, lessonHistoryCommentId, request, member.memberId)
        )
    }

    @Operation(summary = "수업일지에 댓글/답글을 수정한다.",
        responses = [
            ApiResponse(responseCode = "200", description = "수업 일지에 댓글이 수정되었습니다."),
            ApiResponse(responseCode = "404", description = "수업 일지에 댓글을 찾을 수 없습니다.")
        ])
    @PatchMapping("/comment/{lessonHistoryCommentId}")
    fun updateLessonHistoryComment(@Parameter(description = "수업일지 댓글 ID", example = "1") @PathVariable lessonHistoryCommentId: Long,
                                   @RequestBody @Valid request: LessonHistoryCommentCommand
    ): ApiResultResponse<Boolean> {
        return ApiResultResponse(
            message = "댓글이 수정되었습니다.",
            data = lessonHistoryService.updateLessonHistoryComment(lessonHistoryCommentId, request)
        )
    }

    @Operation(summary = "수업일지에 댓글/답글을 삭제한다.",
        responses = [
            ApiResponse(responseCode = "200", description = "수업 일지에 댓글이 삭제되었습니다."),
            ApiResponse(responseCode = "404", description = "수업 일지에 댓글을 찾을 수 없습니다.")
        ])
    @DeleteMapping("/comment/{lessonHistoryCommentId}")
    fun deleteLessonHistoryComment(@Parameter(description = "수업일지 댓글 ID", example = "1") @PathVariable lessonHistoryCommentId: Long): ApiResultResponse<Boolean> {
        return ApiResultResponse(
            message = "댓글 1개가 삭제되었습니다.",
            data = lessonHistoryService.deleteLessonHistoryComment(lessonHistoryCommentId)
        )
    }
}