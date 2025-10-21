package com.mv.toki

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.mv.toki.api.ApiClient
import com.mv.toki.api.ErrorResponse
import com.mv.toki.api.LoginRequest
import com.mv.toki.api.RegisterRequest
import com.mv.toki.api.TokenResponse
import com.mv.toki.auth.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 이메일/아이디 비밀번호 로그인 및 회원가입을 관리하는 매니저 클래스
 * 
 * API를 통해 서버와 통신하여 로그인/회원가입을 처리하고,
 * 성공 시 JWT 토큰을 TokenManager에 저장합니다.
 */
class EmailLoginManager(private val context: Context) {
    
    companion object {
        private const val TAG = "EmailLoginManager"
    }
    
    private val tokenManager = TokenManager.getInstance(context)
    
    /**
     * 아이디/비밀번호 로그인 실행
     * 
     * @param login 사용자 아이디 또는 이메일
     * @param password 비밀번호
     * @return 로그인 결과 (성공 시 UserInfo, 실패 시 예외)
     */
    suspend fun loginWithCredentials(login: String, password: String): Result<UserInfo> {
        return try {
            Log.d(TAG, "이메일/아이디 로그인 시작: $login")
            
            // 로그인 요청 데이터 생성
            val request = LoginRequest(
                login = login.trim(),
                password = password
            )
            
            // API 호출
            val response = ApiClient.authApi.login(request)
            
            if (response.isSuccessful && response.body() != null) {
                val tokenResponse = response.body()!!
                Log.d(TAG, "로그인 API 응답 성공")
                
                // 토큰 저장
                tokenManager.saveTokens(
                    accessToken = tokenResponse.accessToken,
                    refreshToken = tokenResponse.refreshToken,
                    expiresIn = tokenResponse.expiresIn
                )
                
                Log.d(TAG, "토큰 저장 완료")
                
                // 사용자 프로필 조회
                val profileResponse = ApiClient.authApi.getUserProfile("Bearer ${tokenResponse.accessToken}")
                
                if (profileResponse.isSuccessful && profileResponse.body() != null) {
                    val profile = profileResponse.body()!!
                    Log.d(TAG, "사용자 프로필 조회 성공: ${profile.name}")
                    
                    val loginResult = UserInfo(
                        userId = profile.id,
                        nickname = profile.name ?: profile.nickname ?: "사용자",
                        email = profile.email ?: login,
                        profileImageUrl = profile.picture
                    )
                    
                    Result.success(loginResult)
                } else {
                    // 프로필 조회 실패 시에도 로그인은 성공으로 처리
                    Log.w(TAG, "프로필 조회 실패하지만 로그인 성공으로 처리")
                    val loginResult = UserInfo(
                        userId = "unknown",
                        nickname = "사용자",
                        email = login,
                        profileImageUrl = null
                    )
                    Result.success(loginResult)
                }
            } else {
                val errorMessage = getErrorMessage(response.code(), response.message(), response.errorBody())
                Log.e(TAG, "로그인 API 실패: $errorMessage")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Log.e(TAG, "로그인 중 예외 발생", e)
            Result.failure(e)
        }
    }
    
    /**
     * 회원가입 실행
     * 
     * @param username 사용자 아이디
     * @param email 이메일 주소
     * @param password 비밀번호
     * @param name 사용자 이름
     * @return 회원가입 결과 (성공 시 UserInfo, 실패 시 예외)
     */
    suspend fun registerWithCredentials(
        username: String, 
        email: String, 
        password: String, 
        name: String
    ): Result<UserInfo> {
        return try {
            Log.d(TAG, "회원가입 시작: $username, $email")
            
            // 회원가입 요청 데이터 생성
            val request = RegisterRequest(
                username = username.trim(),
                email = email.trim(),
                password = password,
                name = name.trim()
            )
            
            // API 호출
            val response = ApiClient.authApi.register(request)
            
            if (response.isSuccessful && response.body() != null) {
                val tokenResponse = response.body()!!
                Log.d(TAG, "회원가입 API 응답 성공")
                
                // 토큰 저장
                tokenManager.saveTokens(
                    accessToken = tokenResponse.accessToken,
                    refreshToken = tokenResponse.refreshToken,
                    expiresIn = tokenResponse.expiresIn
                )
                
                Log.d(TAG, "토큰 저장 완료")
                
                // 회원가입 성공 시 로그인 결과 반환
                val loginResult = UserInfo(
                    userId = "new_user",
                    nickname = name.trim(),
                    email = email.trim(),
                    profileImageUrl = null
                )
                
                Result.success(loginResult)
            } else {
                val errorMessage = getErrorMessage(response.code(), response.message(), response.errorBody())
                Log.e(TAG, "회원가입 API 실패: $errorMessage")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Log.e(TAG, "회원가입 중 예외 발생", e)
            Result.failure(e)
        }
    }
    
    /**
     * HTTP 상태 코드에 따른 에러 메시지 반환
     * 서버에서 오는 detail 메시지를 우선 표시
     */
    private fun getErrorMessage(code: Int, message: String, errorBody: okhttp3.ResponseBody?): String {
        // 먼저 서버에서 오는 detail 메시지 시도
        try {
            errorBody?.let { body ->
                val errorJson = body.string()
                Log.d(TAG, "Error response body: $errorJson")
                
                val gson = Gson()
                val errorResponse = gson.fromJson(errorJson, ErrorResponse::class.java)
                
                // detail 필드가 있으면 우선 사용
                errorResponse.detail?.let { detail ->
                    Log.d(TAG, "Server detail message: $detail")
                    return detail
                }
                
                // detail이 없으면 message 필드 사용
                errorResponse.message?.let { msg ->
                    Log.d(TAG, "Server message: $msg")
                    return msg
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse error response", e)
        }
        
        // 파싱 실패 시 기본 메시지 사용
        return when (code) {
            400 -> "잘못된 요청입니다. 입력 정보를 확인해주세요."
            401 -> "로그인 정보가 올바르지 않습니다."
            403 -> "접근이 거부되었습니다."
            404 -> "서비스를 찾을 수 없습니다."
            409 -> "이미 존재하는 사용자입니다."
            422 -> "입력 형식이 올바르지 않습니다."
            500 -> "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
            else -> "오류가 발생했습니다: $message"
        }
    }
}

