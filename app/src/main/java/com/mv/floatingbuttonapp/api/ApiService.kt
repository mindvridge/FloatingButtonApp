package com.mv.floatingbuttonapp.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * AI 답변 생성 API 요청 데이터 클래스
 * 
 * 사용자가 선택한 옵션과 대화 내용을 바탕으로 AI에게 답변 생성을 요청할 때 사용됩니다.
 * 
 * @param 대상자 답변을 생성할 대상 (썸, 연인, 친구, 가족, 동료)
 * @param 답변모드 답변의 톤앤매너 (질문형, 공감형, 호응형)
 * @param 답변길이 답변의 길이 (짧게, 중간, 길게)
 * @param 대화내용 OCR로 추출된 원본 대화 내용
 * @param 추가지침 특별한 요구사항이나 추가 지침
 * @param 모델 사용할 AI 모델 (기본값: gemini-2.5-flash)
 */
data class ReplyRequest(
    val 대상자: String,
    val 답변모드: String,
    val 답변길이: String,
    val 대화내용: String,
    val 추가지침: String,
    val 모델: String = "gemini-2.5-flash" // gemini-2.5-flash 모델로 설정
)

/**
 * AI 답변 생성 API 응답 데이터 클래스
 * 
 * AI API로부터 받은 응답을 파싱하기 위한 데이터 클래스입니다.
 * 
 * @param model 사용된 AI 모델 이름
 * @param count 생성된 답변의 개수
 * @param answers 생성된 답변 목록
 * @param message 응답 메시지
 * @param error 에러 메시지 (실패 시)
 */
data class ReplyResponse(
    val model: String? = null,
    val count: Int? = null,
    val answers: List<String>? = null,
    val message: String? = null,
    val error: String? = null
)

/**
 * AI 답변 생성 API 서비스 인터페이스
 * 
 * Retrofit을 사용하여 외부 AI API와 통신하기 위한 인터페이스입니다.
 * Gemini AI API를 사용하여 대화 내용에 대한 맞춤형 답변을 생성합니다.
 */
interface ApiService {
    /**
     * AI 답변 생성 API 호출
     * 
     * @param request 답변 생성을 위한 요청 데이터
     * @return AI가 생성한 답변 목록이 포함된 응답
     */
    @POST("v1/reply")
    suspend fun getReplies(@Body request: ReplyRequest): Response<ReplyResponse>
}