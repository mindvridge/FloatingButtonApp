package com.mv.toki.auth

import android.content.Context
import android.util.Log
import com.mv.toki.api.RefreshTokenRequest
import com.mv.toki.api.TokenResponse
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

/**
 * 인증 인터셉터
 * 
 * 모든 API 요청에 자동으로 Authorization 헤더를 추가하고,
 * 토큰이 만료된 경우 자동으로 갱신합니다.
 */
class AuthInterceptor(context: Context) : Interceptor {
    
    companion object {
        private const val TAG = "AuthInterceptor"
        private const val HEADER_AUTHORIZATION = "Authorization"
    }
    
    private val tokenManager = TokenManager.getInstance(context)
    
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        // Authorization 헤더가 이미 있으면 그대로 진행
        if (originalRequest.header(HEADER_AUTHORIZATION) != null) {
            return chain.proceed(originalRequest)
        }
        
        // 인증이 필요 없는 엔드포인트는 건너뜀
        val url = originalRequest.url.toString()
        if (shouldSkipAuth(url)) {
            return chain.proceed(originalRequest)
        }
        
        // 토큰 확인 및 갱신
        val token = getValidToken()
        if (token == null) {
            Log.e(TAG, "=== 토큰 없음으로 인한 401 예상 ===")
            Log.e(TAG, "URL: ${originalRequest.url}")
            Log.e(TAG, "액세스 토큰 존재: ${!tokenManager.getAccessToken().isNullOrEmpty()}")
            Log.e(TAG, "리프레시 토큰 존재: ${!tokenManager.getRefreshToken().isNullOrEmpty()}")
            Log.e(TAG, "토큰 만료 여부: ${tokenManager.isTokenExpired()}")
            Log.e(TAG, "유효한 토큰 여부: ${tokenManager.hasValidToken()}")
            
            // 토큰이 없으면 401 에러 반환
            return createUnauthorizedResponse(originalRequest, "토큰이 없습니다")
        }
        
        // Authorization 헤더 추가
        val authenticatedRequest = originalRequest.newBuilder()
            .header(HEADER_AUTHORIZATION, token)
            .build()
        
        Log.d(TAG, "=== Authorization 헤더 추가 ===")
        Log.d(TAG, "URL: ${authenticatedRequest.url}")
        Log.d(TAG, "Authorization: ${token.take(50)}...")
        
        // 요청 실행
        val response = chain.proceed(authenticatedRequest)
        
        // 서버 오류 로깅 (500 오류 등)
        if (response.code >= 500) {
            Log.e(TAG, "=== 서버 오류 발생 ===")
            Log.e(TAG, "HTTP 코드: ${response.code}")
            Log.e(TAG, "URL: ${originalRequest.url}")
            Log.e(TAG, "응답 메시지: ${response.message}")
            
            // JWT 토큰 정보 로깅 (서버 디버깅용)
            tokenManager.logTokenInfo()
            
            // 오류 응답 본문 로깅 (읽기 전에 복사)
            try {
                val responseBody = response.peekBody(Long.MAX_VALUE)
                val errorBodyString = responseBody.string()
                Log.e(TAG, "서버 오류 응답 본문: $errorBodyString")
                
                // 데이터베이스 오류 특별 로깅
                if (errorBodyString.contains("psycopg2.errors") || 
                    errorBodyString.contains("column") || 
                    errorBodyString.contains("users.user_id")) {
                    Log.e(TAG, "=== 데이터베이스 스키마 오류 감지 ===")
                    Log.e(TAG, "서버에서 users.user_id 컬럼을 찾을 수 없음")
                    Log.e(TAG, "서버 개발팀에게 데이터베이스 스키마 확인 요청 필요")
                }
            } catch (e: Exception) {
                Log.e(TAG, "응답 본문 읽기 실패", e)
            }
        }
        
        // 401 Unauthorized 처리 (토큰 만료)
        if (response.code == 401) {
            Log.e(TAG, "=== 401 Unauthorized - 토큰 갱신 시도 ===")
            Log.e(TAG, "URL: ${originalRequest.url}")
            Log.e(TAG, "응답 메시지: ${response.message}")
            Log.e(TAG, "응답 헤더: ${response.headers}")
            
            val errorBody = response.body?.string()
            Log.e(TAG, "에러 본문: $errorBody")
            
            response.close()
            
            // 토큰 갱신 시도
            val newToken = refreshAccessToken()
            if (newToken != null) {
                // 갱신된 토큰으로 재시도
                val retryRequest = originalRequest.newBuilder()
                    .header(HEADER_AUTHORIZATION, newToken)
                    .build()
                
                Log.d(TAG, "=== 갱신된 토큰으로 재시도 ===")
                Log.d(TAG, "새 토큰: ${newToken.take(50)}...")
                return chain.proceed(retryRequest)
            } else {
                Log.e(TAG, "토큰 갱신 실패 - 모든 토큰 삭제")
                tokenManager.clearTokens()
            }
        }
        
        return response
    }
    
    /**
     * 유효한 토큰 가져오기 (필요시 자동 갱신)
     */
    private fun getValidToken(): String? {
        Log.d(TAG, "=== 토큰 유효성 확인 ===")
        
        // 게스트 사용자인지 확인
        if (tokenManager.isGuestUser()) {
            Log.d(TAG, "게스트 사용자 - 게스트 토큰 확인")
            
            if (tokenManager.isGuestTokenValid()) {
                val guestToken = tokenManager.getAccessToken()
                Log.d(TAG, "게스트 토큰 유효함: ${guestToken?.take(50)}...")
                // 게스트 토큰도 Bearer 형식으로 반환
                return "Bearer $guestToken"
            } else {
                Log.e(TAG, "게스트 토큰 만료됨 - 재로그인 필요")
                // 게스트 토큰은 갱신 불가능하므로 null 반환
                return null
            }
        }
        
        // 일반 사용자 토큰 확인
        Log.d(TAG, "일반 사용자 - 토큰 확인")
        Log.d(TAG, "액세스 토큰 존재: ${!tokenManager.getAccessToken().isNullOrEmpty()}")
        Log.d(TAG, "리프레시 토큰 존재: ${!tokenManager.getRefreshToken().isNullOrEmpty()}")
        Log.d(TAG, "토큰 만료 여부: ${tokenManager.isTokenExpired()}")
        
        // 토큰이 만료되지 않았으면 그대로 반환
        if (!tokenManager.isTokenExpired()) {
            val token = tokenManager.getAuthorizationHeader()
            Log.d(TAG, "유효한 토큰 반환: ${token?.take(50)}...")
            return token
        }
        
        // 토큰이 만료되었으면 갱신
        Log.d(TAG, "토큰 만료 - 갱신 시도")
        return refreshAccessToken()
    }
    
    /**
     * 액세스 토큰 갱신 (단순화된 버전)
     * 1회만 시도하고 실패 시 플로팅 로그인 다이얼로그 표시
     */
    private fun refreshAccessToken(): String? {
        // 게스트 사용자인 경우 갱신 불가능
        if (tokenManager.isGuestUser()) {
            Log.e(TAG, "게스트 사용자는 토큰 갱신 불가능 - 플로팅 로그인 필요")
            tokenManager.clearTokens()
            return null
        }
        
        val refreshToken = tokenManager.getRefreshToken()
        if (refreshToken.isNullOrEmpty()) {
            Log.e(TAG, "리프레시 토큰이 없습니다 - 플로팅 로그인 필요")
            tokenManager.clearTokens()
            return null
        }
        
        Log.d(TAG, "=== 토큰 갱신 시도 (1회) ===")
        
        return try {
            runBlocking {
                val authApi = createAuthApiForRefresh()
                val request = RefreshTokenRequest(refreshToken)
                
                Log.d(TAG, "토큰 갱신 요청 전송")
                val response = authApi.refreshToken(request)
                
                if (response.isSuccessful && response.body() != null) {
                    val tokenResponse = response.body()!!
                    
                    // 새 토큰 저장
                    tokenManager.saveTokens(
                        accessToken = tokenResponse.accessToken,
                        refreshToken = tokenResponse.refreshToken,
                        expiresIn = tokenResponse.expiresIn,
                        tokenType = tokenResponse.tokenType
                    )
                    
                    Log.d(TAG, "토큰 갱신 성공")
                    tokenManager.getAuthorizationHeader()
                } else {
                    Log.e(TAG, "토큰 갱신 실패: ${response.code()} - 플로팅 로그인 필요")
                    // 갱신 실패 시 모든 토큰 삭제
                    tokenManager.clearTokens()
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "토큰 갱신 중 오류 - 플로팅 로그인 필요", e)
            null
        }
    }
    
    /**
     * 토큰 갱신용 AuthApi 인스턴스 생성
     * (순환 참조 방지를 위해 별도 인스턴스 사용)
     */
    private fun createAuthApiForRefresh(): com.mv.toki.api.AuthApi {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://toki-auth-964943834069.asia-northeast3.run.app/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        return retrofit.create(com.mv.toki.api.AuthApi::class.java)
    }
    
    /**
     * 인증이 필요 없는 엔드포인트 확인
     */
    private fun shouldSkipAuth(url: String): Boolean {
        val skipPatterns = listOf(
            "/api/v1/auth/google/callback",
            "/api/v1/auth/kakao/callback",
            "/api/v1/auth/refresh"
        )
        
        return skipPatterns.any { url.contains(it) }
    }
    
    /**
     * 401 Unauthorized 응답 생성
     */
    private fun createUnauthorizedResponse(originalRequest: okhttp3.Request, message: String): Response {
        val responseBody = """{"error": "unauthorized", "message": "$message"}""".toResponseBody(
            "application/json".toMediaTypeOrNull()
        )
        
        return Response.Builder()
            .request(originalRequest)
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .body(responseBody)
            .build()
    }
}

