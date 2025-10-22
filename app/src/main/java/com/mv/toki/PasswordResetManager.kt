package com.mv.toki

import android.content.Context
import android.util.Log
import com.mv.toki.api.ApiClient
import com.mv.toki.api.PasswordResetRequest
import com.mv.toki.api.PasswordResetResponse
import com.mv.toki.api.ResetPasswordRequest
import com.mv.toki.api.ResetPasswordResponse

/**
 * 비밀번호 재설정을 관리하는 매니저 클래스
 * 이메일을 통한 비밀번호 재설정 기능을 제공합니다.
 */
class PasswordResetManager(private val context: Context) {
    
    companion object {
        private const val TAG = "PasswordResetManager"
    }
    
    /**
     * 비밀번호 재설정 요청 (이메일 발송)
     * 
     * @param email 재설정을 요청할 이메일 주소
     * @return Result<PasswordResetResponse> - 성공 시 응답 데이터, 실패 시 예외
     */
    suspend fun requestPasswordReset(email: String): Result<PasswordResetResponse> {
        return try {
            Log.d(TAG, "비밀번호 재설정 요청 시작: $email")
            
            val request = PasswordResetRequest(email = email)
            val response = ApiClient.authApi.requestPasswordReset(request)
            
            if (response.isSuccessful && response.body() != null) {
                val result = response.body()!!
                Log.d(TAG, "비밀번호 재설정 요청 성공: ${result.message}")
                Result.success(result)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Log.e(TAG, "비밀번호 재설정 요청 실패: ${response.code()} - $errorBody")
                Result.failure(Exception("비밀번호 재설정 요청 실패: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "비밀번호 재설정 요청 중 예외 발생", e)
            Result.failure(e)
        }
    }
    
    /**
     * 비밀번호 재설정 실행
     * 
     * @param token 이메일로 받은 재설정 토큰
     * @param newPassword 새로운 비밀번호
     * @return Result<ResetPasswordResponse> - 성공 시 응답 데이터, 실패 시 예외
     */
    suspend fun resetPassword(token: String, newPassword: String): Result<ResetPasswordResponse> {
        return try {
            Log.d(TAG, "비밀번호 재설정 실행 시작")
            
            val request = ResetPasswordRequest(token = token, newPassword = newPassword)
            val response = ApiClient.authApi.resetPassword(request)
            
            if (response.isSuccessful && response.body() != null) {
                val result = response.body()!!
                Log.d(TAG, "비밀번호 재설정 성공: ${result.message}")
                Result.success(result)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Log.e(TAG, "비밀번호 재설정 실패: ${response.code()} - $errorBody")
                Result.failure(Exception("비밀번호 재설정 실패: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "비밀번호 재설정 중 예외 발생", e)
            Result.failure(e)
        }
    }
}
