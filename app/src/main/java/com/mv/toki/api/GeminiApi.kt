package com.mv.toki.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Gemini API 서비스 인터페이스
 * 
 * AI 답변 생성 및 분석 기능을 제공합니다.
 * Authorization 헤더는 AuthInterceptor에서 자동으로 추가됩니다.
 */
interface GeminiApi {
    
    /**
     * AI 답변 생성 (기존 getReplies를 대체)
     * @param request 답변 생성 요청
     * @return AI가 생성한 답변 목록
     */
    @POST("v1/reply")
    suspend fun getReplies(
        @Body request: ReplyRequest
    ): Response<ReplyResponse>
    
    /**
     * 채팅 메시지 전송
     * @param request 채팅 요청
     * @return 채팅 응답
     */
    @POST("api/chat")
    suspend fun sendMessage(
        @Body request: ChatRequest
    ): Response<ChatResponse>
    
    /**
     * 채팅 히스토리 조회
     * @return 채팅 메시지 목록
     */
    @GET("api/history")
    suspend fun getChatHistory(): Response<List<ChatMessage>>
    
    /**
     * 이미지 분석
     * @param request 이미지 분석 요청
     * @return 분석 결과
     */
    @POST("api/analyze")
    suspend fun analyzeImage(
        @Body request: ImageAnalysisRequest
    ): Response<AnalysisResponse>
    
    /**
     * 약관 동의 저장
     * @param request 약관 동의 요청
     * @return 약관 동의 결과
     */
    @POST("api/v1/terms/agree")
    suspend fun agreeToTerms(
        @Body request: TermsAgreeRequest
    ): Response<TermsAgreeResponse>
    
    /**
     * 약관 동의 상태 조회
     * @return 약관 동의 상태
     */
    @GET("api/v1/terms/status")
    suspend fun getTermsStatus(): Response<TermsStatusResponse>
    
    // ==================== 앱 버전 체크 관련 API ====================
    
    @POST("api/v1/app/check-update")
    suspend fun checkAppUpdate(
        @Body request: AppUpdateCheckRequest
    ): Response<AppUpdateCheckResponse>
}

// ==================== 채팅 관련 데이터 클래스 ====================

/**
 * 채팅 요청
 */
data class ChatRequest(
    val message: String,
    val conversationId: String? = null
)

/**
 * 채팅 응답
 */
data class ChatResponse(
    val reply: String,
    val conversationId: String,
    val timestamp: String
)

/**
 * 채팅 메시지
 */
data class ChatMessage(
    val id: String,
    val role: String,               // "user" or "assistant"
    val content: String,
    val timestamp: String
)

// ==================== 이미지 분석 관련 데이터 클래스 ====================

/**
 * 이미지 분석 요청
 */
data class ImageAnalysisRequest(
    val imageBase64: String,        // Base64로 인코딩된 이미지
    val analysisType: String        // "ocr", "sentiment", "object_detection" 등
)

/**
 * 분석 응답
 */
data class AnalysisResponse(
    val result: String,
    val confidence: Double? = null,
    val details: Map<String, Any>? = null
)

// ==================== 약관 동의 관련 데이터 클래스 ====================

/**
 * 약관 동의 요청
 */
data class TermsAgreeRequest(
    @SerializedName("service_terms") val serviceTerms: Boolean,
    @SerializedName("privacy_policy") val privacyPolicy: Boolean,
    @SerializedName("terms_version") val termsVersion: String,
    @SerializedName("user_id") val userId: Int? = null  // 서버에서 사용자 ID를 찾을 수 없는 문제 해결을 위해 명시적으로 전달
)

/**
 * 약관 동의 응답
 */
data class TermsAgreeResponse(
    @SerializedName("user_id") val userId: Int,
    @SerializedName("terms_version") val termsVersion: String,
    @SerializedName("service_terms") val serviceTerms: Boolean,
    @SerializedName("privacy_policy") val privacyPolicy: Boolean,
    @SerializedName("agreed_at") val agreedAt: String,
    @SerializedName("success") val success: Boolean
)

/**
 * 약관 동의 상태 응답
 */
data class TermsStatusResponse(
    @SerializedName("user_id") val userId: Int,
    @SerializedName("service_terms") val serviceTerms: Boolean,
    @SerializedName("privacy_policy") val privacyPolicy: Boolean,
    @SerializedName("terms_version") val termsVersion: String?,
    @SerializedName("agreed_at") val agreedAt: String?,
    @SerializedName("success") val success: Boolean
)

// ==================== 앱 버전 체크 관련 데이터 클래스 ====================

/**
 * 앱 업데이트 체크 요청
 */
data class AppUpdateCheckRequest(
    @SerializedName("current_version") val currentVersion: String,
    @SerializedName("platform") val platform: String,
    @SerializedName("package_name") val packageName: String
)

/**
 * 앱 업데이트 체크 응답
 */
data class AppUpdateCheckResponse(
    @SerializedName("needs_update") val needsUpdate: Boolean,
    @SerializedName("force_update") val forceUpdate: Boolean,
    @SerializedName("update_available") val updateAvailable: Boolean,
    @SerializedName("update_message") val updateMessage: String,
    @SerializedName("store_url") val storeUrl: String,
    @SerializedName("latest_version") val latestVersion: String,
    @SerializedName("current_version") val currentVersion: String,
    @SerializedName("release_notes") val releaseNotes: List<String>,
    @SerializedName("update_priority") val updatePriority: String,
    @SerializedName("update_type") val updateType: String,
    @SerializedName("last_checked") val lastChecked: String,
    @SerializedName("full_app_info") val fullAppInfo: String?
)

