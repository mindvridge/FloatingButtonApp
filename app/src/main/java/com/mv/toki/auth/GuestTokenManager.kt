package com.mv.toki.auth

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * 게스트 토큰 관리 클래스
 * 
 * 임시 토큰을 발급하고 관리합니다.
 * 게스트 사용자는 제한된 기능만 사용할 수 있으며, 토큰 만료 시 재로그인이 필요합니다.
 */
class GuestTokenManager(private val context: Context) {
    
    companion object {
        private const val TAG = "GuestTokenManager"
        private const val GUEST_TOKEN_PREFIX = "guest_"
        private const val GUEST_TOKEN_EXPIRY_HOURS = 24L // 24시간 후 만료
        
        // 게스트 토큰 정보를 저장할 SharedPreferences 키
        private const val PREF_GUEST_TOKEN = "guest_token"
        private const val PREF_GUEST_TOKEN_EXPIRY = "guest_token_expiry"
        private const val PREF_GUEST_USER_ID = "guest_user_id"
    }
    
    private val sharedPreferences = context.getSharedPreferences("guest_token_prefs", Context.MODE_PRIVATE)
    
    /**
     * 게스트 토큰 발급
     * @return 발급 성공 여부
     */
    suspend fun requestGuestToken(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "게스트 토큰 발급 시작")
            
            // 임시 게스트 토큰 생성 (실제로는 서버에서 발급받아야 함)
            val guestUserId = generateGuestUserId()
            val guestToken = generateGuestToken(guestUserId)
            val expiryTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(GUEST_TOKEN_EXPIRY_HOURS)
            
            // 토큰 정보 저장
            sharedPreferences.edit()
                .putString(PREF_GUEST_TOKEN, guestToken)
                .putLong(PREF_GUEST_TOKEN_EXPIRY, expiryTime)
                .putString(PREF_GUEST_USER_ID, guestUserId)
                .apply()
            
            Log.d(TAG, "게스트 토큰 발급 완료 - 사용자 ID: $guestUserId")
            Log.d(TAG, "토큰 만료 시간: ${Date(expiryTime)}")
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "게스트 토큰 발급 실패", e)
            false
        }
    }
    
    /**
     * 현재 게스트 토큰이 유효한지 확인
     * @return 토큰 유효성
     */
    fun isGuestTokenValid(): Boolean {
        val token = sharedPreferences.getString(PREF_GUEST_TOKEN, null)
        val expiryTime = sharedPreferences.getLong(PREF_GUEST_TOKEN_EXPIRY, 0L)
        
        if (token == null || expiryTime == 0L) {
            Log.d(TAG, "게스트 토큰이 없습니다")
            return false
        }
        
        val currentTime = System.currentTimeMillis()
        val isValid = currentTime < expiryTime
        
        if (!isValid) {
            Log.d(TAG, "게스트 토큰이 만료되었습니다")
            Log.d(TAG, "만료 시간: ${Date(expiryTime)}, 현재 시간: ${Date(currentTime)}")
        } else {
            val remainingTime = expiryTime - currentTime
            val remainingHours = TimeUnit.MILLISECONDS.toHours(remainingTime)
            Log.d(TAG, "게스트 토큰 유효 - 남은 시간: ${remainingHours}시간")
        }
        
        return isValid
    }
    
    /**
     * 게스트 토큰 가져오기
     * @return 게스트 토큰 (유효한 경우에만)
     */
    fun getGuestToken(): String? {
        return if (isGuestTokenValid()) {
            sharedPreferences.getString(PREF_GUEST_TOKEN, null)
        } else {
            null
        }
    }
    
    /**
     * 게스트 사용자 ID 가져오기
     * @return 게스트 사용자 ID
     */
    fun getGuestUserId(): String? {
        return sharedPreferences.getString(PREF_GUEST_USER_ID, null)
    }
    
    /**
     * 게스트 토큰 만료 시간 가져오기
     * @return 만료 시간 (밀리초), 0L이면 만료 시간 없음
     */
    fun getGuestTokenExpiryTime(): Long {
        return sharedPreferences.getLong(PREF_GUEST_TOKEN_EXPIRY, 0L)
    }
    
    /**
     * 게스트 토큰 삭제 (로그아웃 시)
     */
    fun clearGuestToken() {
        sharedPreferences.edit()
            .remove(PREF_GUEST_TOKEN)
            .remove(PREF_GUEST_TOKEN_EXPIRY)
            .remove(PREF_GUEST_USER_ID)
            .apply()
        
        Log.d(TAG, "게스트 토큰 삭제 완료")
    }
    
    /**
     * 게스트 사용자인지 확인
     * @return 게스트 사용자 여부
     */
    fun isGuestUser(): Boolean {
        val token = sharedPreferences.getString(PREF_GUEST_TOKEN, null)
        return token != null && token.startsWith(GUEST_TOKEN_PREFIX)
    }
    
    /**
     * 게스트 토큰 만료까지 남은 시간 (시간 단위)
     * @return 남은 시간 (시간 단위), 만료된 경우 0
     */
    fun getRemainingHours(): Long {
        val expiryTime = sharedPreferences.getLong(PREF_GUEST_TOKEN_EXPIRY, 0L)
        if (expiryTime == 0L) return 0L
        
        val currentTime = System.currentTimeMillis()
        val remainingTime = expiryTime - currentTime
        
        return if (remainingTime > 0) {
            TimeUnit.MILLISECONDS.toHours(remainingTime)
        } else {
            0L
        }
    }
    
    /**
     * 게스트 사용자 ID 생성
     * @return 생성된 게스트 사용자 ID
     */
    private fun generateGuestUserId(): String {
        val timestamp = System.currentTimeMillis()
        val random = (1000..9999).random()
        return "guest_${timestamp}_${random}"
    }
    
    /**
     * 게스트 토큰 생성
     * @param userId 게스트 사용자 ID
     * @return 생성된 게스트 토큰
     */
    private fun generateGuestToken(userId: String): String {
        // 실제로는 서버에서 발급받아야 하지만, 
        // 데모용으로 클라이언트에서 임시 토큰 생성
        val timestamp = System.currentTimeMillis()
        val random = (10000..99999).random()
        
        // Base64 인코딩된 형태로 토큰 생성 (간단한 예시)
        val tokenData = "$GUEST_TOKEN_PREFIX${userId}_${timestamp}_${random}"
        return android.util.Base64.encodeToString(
            tokenData.toByteArray(),
            android.util.Base64.NO_WRAP
        )
    }
    
    /**
     * 게스트 토큰 정보 로그 출력
     */
    fun logGuestTokenInfo() {
        val token = sharedPreferences.getString(PREF_GUEST_TOKEN, null)
        val expiryTime = sharedPreferences.getLong(PREF_GUEST_TOKEN_EXPIRY, 0L)
        val userId = sharedPreferences.getString(PREF_GUEST_USER_ID, null)
        
        Log.d(TAG, "=== 게스트 토큰 정보 ===")
        Log.d(TAG, "사용자 ID: $userId")
        Log.d(TAG, "토큰: ${token?.take(20)}...") // 보안상 일부만 출력
        Log.d(TAG, "만료 시간: ${if (expiryTime > 0) Date(expiryTime) else "없음"}")
        Log.d(TAG, "유효성: ${isGuestTokenValid()}")
        Log.d(TAG, "남은 시간: ${getRemainingHours()}시간")
    }
}
