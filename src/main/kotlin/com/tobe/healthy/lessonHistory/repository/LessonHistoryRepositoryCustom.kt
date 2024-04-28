package com.tobe.healthy.lessonHistory.repository

import com.tobe.healthy.lessonHistory.domain.dto.out.LessonHistoryResponse
import com.tobe.healthy.lessonHistory.domain.dto.`in`.SearchCondRequest
import com.tobe.healthy.lessonHistory.domain.dto.out.LessonHistoryDetailResponse
import com.tobe.healthy.member.domain.entity.MemberType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface LessonHistoryRepositoryCustom {
    fun findAllLessonHistory(request: SearchCondRequest, pageable: Pageable, memberId: Long, memberType: MemberType): Page<LessonHistoryResponse>
    fun findOneLessonHistory(lessonHistoryId: Long?, memberId: Long, memberType: MemberType): LessonHistoryDetailResponse
    fun findAllLessonHistoryByMemberId(studentId: Long, request: SearchCondRequest, pageable: Pageable): Page<LessonHistoryResponse>
}
