package com.mv.floatingbuttonapp.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

// API 요청 데이터 클래스 (실제 API 문서에 맞게 수정)
data class ReplyRequest(
    val 대상자: String,
    val 답변모드: String,
    val 답변길이: String,
    val 대화내용: String,
    val 추가지침: String
)

// API 응답 데이터 클래스 (실제 API 응답에 맞게 수정)
data class ReplyResponse(
    val model: String? = null,
    val count: Int? = null,
    val answers: List<String>? = null,
    val message: String? = null,
    val error: String? = null
)

// API 서비스 인터페이스
interface ApiService {
    @POST("v1/reply")
    suspend fun getReplies(@Body request: ReplyRequest): Response<ReplyResponse>
}
