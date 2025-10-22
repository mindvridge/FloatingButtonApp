package com.mv.toki.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Toki Auth 서비스 API 인터페이스
 * 
 * 구글/카카오 소셜 로그인 및 토큰 관리를 담당합니다.
 */
interface AuthApi {
    
    /**
     * 구글 로그인 콜백
     * @param request 구글 인증 요청 (ID 토큰 포함)
     * @return JWT 액세스/리프레시 토큰
     */
    @POST("api/v1/auth/google/callback")
    suspend fun loginWithGoogle(
        @Body request: GoogleAuthRequest
    ): Response<TokenResponse>
    
    /**
     * 카카오 로그인 콜백
     * @param request 카카오 인증 요청 (액세스 토큰 포함)
     * @return JWT 액세스/리프레시 토큰
     */
    @POST("api/v1/auth/kakao/callback")
    suspend fun loginWithKakao(
        @Body request: KakaoAuthRequest
    ): Response<TokenResponse>
    
    /**
     * 토큰 갱신
     * @param request 리프레시 토큰
     * @return 새로운 액세스/리프레시 토큰
     */
    @POST("api/v1/auth/refresh")
    suspend fun refreshToken(
        @Body request: RefreshTokenRequest
    ): Response<TokenResponse>
    
    /**
     * 사용자 프로필 조회
     * @param token Bearer {accessToken}
     * @return 사용자 정보
     */
    @GET("api/v1/auth/me")
    suspend fun getUserProfile(
        @Header("Authorization") token: String
    ): Response<UserProfileResponse>
    
    /**
     * 아이디/비밀번호 회원가입
     * @param request 회원가입 요청 (username, email, password, name)
     * @return JWT 액세스/리프레시 토큰
     */
    @POST("api/v1/auth/register")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<TokenResponse>
    
    /**
     * 아이디/비밀번호 로그인
     * @param request 로그인 요청 (login, password)
     * @return JWT 액세스/리프레시 토큰
     */
    @POST("api/v1/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<TokenResponse>
    
    /**
     * 아이디 찾기
     * @param request 아이디 찾기 요청 (email)
     * @return 아이디 찾기 결과
     */
    @POST("api/v1/auth/find-username")
    suspend fun findUsername(
        @Body request: FindUsernameRequest
    ): Response<FindUsernameResponse>
    
    /**
     * 비밀번호 재설정 요청
     * @param request 비밀번호 재설정 요청
     * @return 비밀번호 재설정 요청 결과
     */
    @POST("api/v1/auth/request-password-reset")
    suspend fun requestPasswordReset(
        @Body request: PasswordResetRequest
    ): Response<PasswordResetResponse>
    
    /**
     * 비밀번호 재설정 실행
     * @param request 비밀번호 재설정 실행 요청
     * @return 비밀번호 재설정 결과
     */
    @POST("api/v1/auth/reset-password")
    suspend fun resetPassword(
        @Body request: ResetPasswordRequest
    ): Response<ResetPasswordResponse>
    
    /**
     * 앱 업데이트 체크
     * @param request 앱 업데이트 체크 요청
     * @return 앱 업데이트 정보
     */
    @POST("api/v1/auth/app/check-update")
    suspend fun checkAppUpdate(
        @Body request: AppUpdateCheckRequest
    ): Response<AppUpdateCheckResponse>
}

// ==================== 요청 데이터 클래스 ====================

/**
 * 구글 인증 요청
 */
data class GoogleAuthRequest(
    @SerializedName("id_token")
    val idToken: String,            // 구글 ID 토큰
    
    @SerializedName("email")
    val email: String?,
    
    @SerializedName("name")
    val name: String?,
    
    @SerializedName("picture")
    val picture: String?
)

/**
 * 카카오 인증 요청
 */
data class KakaoAuthRequest(
    @SerializedName("access_token")
    val accessToken: String,        // 카카오 액세스 토큰
    
    @SerializedName("user_id")
    val userId: Long,               // 카카오 사용자 ID
    
    @SerializedName("email")
    val email: String?,
    
    @SerializedName("nickname")
    val nickname: String?,
    
    @SerializedName("profile_image_url")
    val profileImageUrl: String?
)

/**
 * 토큰 갱신 요청
 */
data class RefreshTokenRequest(
    @SerializedName("refresh_token")
    val refreshToken: String
)

/**
 * 아이디/비밀번호 회원가입 요청
 */
data class RegisterRequest(
    @SerializedName("username")
    val username: String,            // 사용자 아이디
    
    @SerializedName("email")
    val email: String,               // 이메일 주소
    
    @SerializedName("password")
    val password: String,            // 비밀번호
    
    @SerializedName("name")
    val name: String                 // 사용자 이름
)

/**
 * 아이디/비밀번호 로그인 요청
 */
data class LoginRequest(
    @SerializedName("login")
    val login: String,               // 사용자 아이디 또는 이메일
    
    @SerializedName("password")
    val password: String             // 비밀번호
)

/**
 * 아이디 찾기 요청
 */
data class FindUsernameRequest(
    @SerializedName("email")
    val email: String                // 이메일 주소
)

/**
 * 비밀번호 재설정 요청
 */
data class PasswordResetRequest(
    @SerializedName("email") val email: String
)

/**
 * 앱 업데이트 체크 요청
 */
data class AppUpdateCheckRequest(
    @SerializedName("current_version") val currentVersion: String,
    @SerializedName("platform") val platform: String,
    @SerializedName("package_name") val packageName: String
)

// ==================== 응답 데이터 클래스 ====================

/**
 * 토큰 응답 (로그인/토큰 갱신 시)
 */
data class TokenResponse(
    @SerializedName("access_token")
    val accessToken: String,        // JWT 액세스 토큰
    
    @SerializedName("refresh_token")
    val refreshToken: String,       // JWT 리프레시 토큰
    
    @SerializedName("expires_in")
    val expiresIn: Long,            // 만료 시간 (초)
    
    @SerializedName("token_type")
    val tokenType: String = "Bearer"
)

/**
 * 사용자 프로필 응답
 */
data class UserProfileResponse(
    @SerializedName("id")
    val id: String,                 // 서버에서 생성한 사용자 ID
    
    @SerializedName("provider")
    val provider: String,           // "google" or "kakao"
    
    @SerializedName("provider_id")
    val providerId: String,         // 소셜 로그인 제공자의 사용자 ID
    
    @SerializedName("email")
    val email: String?,
    
    @SerializedName("name")
    val name: String?,
    
    @SerializedName("nickname")
    val nickname: String?,
    
    @SerializedName("picture")
    val picture: String?,
    
    @SerializedName("created_at")
    val createdAt: String?,         // ISO 8601 형식
    
    @SerializedName("updated_at")
    val updatedAt: String?          // ISO 8601 형식
)

/**
 * 아이디 찾기 응답
 */
data class FindUsernameResponse(
    @SerializedName("message")
    val message: String,              // 결과 메시지
    
    @SerializedName("email_sent")
    val emailSent: Boolean            // 이메일 발송 여부
)

/**
 * 비밀번호 재설정 요청 응답
 */
data class PasswordResetResponse(
    @SerializedName("message") val message: String,
    @SerializedName("detail") val detail: String? = null
)

/**
 * 비밀번호 재설정 실행 요청
 */
data class ResetPasswordRequest(
    @SerializedName("token") val token: String,
    @SerializedName("new_password") val newPassword: String
)

/**
 * 비밀번호 재설정 실행 응답
 */
data class ResetPasswordResponse(
    @SerializedName("message") val message: String,
    @SerializedName("success") val success: Boolean
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

/**
 * 서버 오류 응답
 */
data class ErrorResponse(
    @SerializedName("detail")
    val detail: String?,
    
    @SerializedName("message")
    val message: String?,
    
    @SerializedName("code")
    val code: String?
)


