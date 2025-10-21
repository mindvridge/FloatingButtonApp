package com.mv.toki.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * JWT 토큰 관리 클래스
 * 
 * 액세스 토큰과 리프레시 토큰을 안전하게 저장하고 관리합니다.
 * EncryptedSharedPreferences를 사용하여 토큰을 암호화하여 저장합니다.
 */
class TokenManager(private val context: Context) {
    
    companion object {
        private const val TAG = "TokenManager"
        private const val PREFS_NAME = "toki_auth_tokens"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_TOKEN_TYPE = "token_type"
        private const val KEY_IS_GUEST_USER = "is_guest_user"
        
        @Volatile
        private var INSTANCE: TokenManager? = null
        
        fun getInstance(context: Context): TokenManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TokenManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
    
    private val prefs: SharedPreferences by lazy {
        try {
            Log.d(TAG, "=== EncryptedSharedPreferences 생성 시도 ===")
            Log.d(TAG, "PREFS_NAME: $PREFS_NAME")
            Log.d(TAG, "Context: ${context.javaClass.simpleName}")
            
            // 안전한 암호화된 SharedPreferences 사용
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            Log.d(TAG, "MasterKey 생성 완료")
            
            val encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            
            Log.d(TAG, "✅ EncryptedSharedPreferences 생성 성공")
            encryptedPrefs
        } catch (e: Exception) {
            Log.e(TAG, "❌ EncryptedSharedPreferences 생성 실패, 일반 SharedPreferences 사용", e)
            Log.e(TAG, "예외 상세: ${e.message}")
            Log.e(TAG, "예외 타입: ${e.javaClass.simpleName}")
            
            try {
                val fallbackPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                Log.d(TAG, "✅ 일반 SharedPreferences 생성 성공")
                fallbackPrefs
            } catch (fallbackException: Exception) {
                Log.e(TAG, "❌ 일반 SharedPreferences 생성도 실패", fallbackException)
                throw RuntimeException("SharedPreferences 생성 실패", fallbackException)
            }
        }
    }
    
    /**
     * 토큰 저장
     */
    fun saveTokens(
        accessToken: String,
        refreshToken: String,
        expiresIn: Long,
        tokenType: String = "Bearer"
    ) {
        Log.d(TAG, "=== saveTokens 시작 ===")
        Log.d(TAG, "입력된 액세스 토큰: ${accessToken.take(50)}...")
        Log.d(TAG, "입력된 리프레시 토큰: ${refreshToken.take(50)}...")
        Log.d(TAG, "만료 시간: ${expiresIn}초")
        Log.d(TAG, "토큰 타입: $tokenType")
        
        // 입력 값 검증
        if (accessToken.isBlank()) {
            Log.e(TAG, "❌ 액세스 토큰이 비어있음")
            throw IllegalArgumentException("액세스 토큰이 비어있습니다")
        }
        if (refreshToken.isBlank()) {
            Log.e(TAG, "❌ 리프레시 토큰이 비어있음")
            throw IllegalArgumentException("리프레시 토큰이 비어있습니다")
        }
        
        val expiresAt = System.currentTimeMillis() + (expiresIn * 1000)
        Log.d(TAG, "계산된 만료 시간: $expiresAt (${java.util.Date(expiresAt)})")
        
        try {
            val editor = prefs.edit()
            editor.putString(KEY_ACCESS_TOKEN, accessToken)
            editor.putString(KEY_REFRESH_TOKEN, refreshToken)
            editor.putLong(KEY_EXPIRES_AT, expiresAt)
            editor.putString(KEY_TOKEN_TYPE, tokenType)
            editor.apply()
            
            Log.d(TAG, "SharedPreferences 저장 완료")
            
            // 저장 후 확인
            val savedAccessToken = prefs.getString(KEY_ACCESS_TOKEN, null)
            val savedRefreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
            val savedExpiresAt = prefs.getLong(KEY_EXPIRES_AT, 0)
            val savedTokenType = prefs.getString(KEY_TOKEN_TYPE, null)
            
            Log.d(TAG, "=== 저장 후 확인 ===")
            Log.d(TAG, "저장된 액세스 토큰: ${savedAccessToken?.take(50)}...")
            Log.d(TAG, "저장된 리프레시 토큰: ${savedRefreshToken?.take(50)}...")
            Log.d(TAG, "저장된 만료 시간: $savedExpiresAt")
            Log.d(TAG, "저장된 토큰 타입: $savedTokenType")
            
            // 저장 성공 여부 확인
            val accessTokenMatches = accessToken == savedAccessToken
            val refreshTokenMatches = refreshToken == savedRefreshToken
            
            Log.d(TAG, "액세스 토큰 일치: $accessTokenMatches")
            Log.d(TAG, "리프레시 토큰 일치: $refreshTokenMatches")
            
            if (!accessTokenMatches) {
                Log.e(TAG, "❌ 액세스 토큰 저장 불일치!")
                throw RuntimeException("액세스 토큰 저장에 실패했습니다")
            }
            if (!refreshTokenMatches) {
                Log.e(TAG, "❌ 리프레시 토큰 저장 불일치!")
                throw RuntimeException("리프레시 토큰 저장에 실패했습니다")
            }
            
            Log.d(TAG, "✅ 토큰 저장 완료 (만료: ${expiresIn}초 후)")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 토큰 저장 중 예외 발생", e)
            throw e
        }
    }
    
    /**
     * 액세스 토큰 가져오기
     */
    fun getAccessToken(): String? {
        return prefs.getString(KEY_ACCESS_TOKEN, null)
    }
    
    /**
     * 리프레시 토큰 가져오기
     */
    fun getRefreshToken(): String? {
        return prefs.getString(KEY_REFRESH_TOKEN, null)
    }
    
    /**
     * Authorization 헤더 값 가져오기
     * @return "Bearer {accessToken}" 형식 (항상 대문자 Bearer 사용)
     */
    fun getAuthorizationHeader(): String? {
        val accessToken = getAccessToken() ?: return null
        // 서버에서 "bearer"로 오더라도 항상 "Bearer"로 변환
        return "Bearer $accessToken"
    }
    
    /**
     * 토큰 만료 여부 확인
     * @return true: 만료됨, false: 유효함
     */
    fun isTokenExpired(): Boolean {
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        val now = System.currentTimeMillis()
        
        // 만료 5분 전을 만료로 간주 (버퍼)
        val buffer = 5 * 60 * 1000L
        return now >= (expiresAt - buffer)
    }
    
    /**
     * 토큰 존재 여부 확인
     */
    fun hasValidToken(): Boolean {
        val accessToken = getAccessToken()
        val refreshToken = getRefreshToken()
        return !accessToken.isNullOrEmpty() && 
               !refreshToken.isNullOrEmpty() && 
               !isTokenExpired()
    }
    
    /**
     * 토큰 삭제 (로그아웃 시)
     */
    fun clearTokens() {
        prefs.edit().clear().apply()
        Log.d(TAG, "토큰 삭제 완료")
    }
    
    /**
     * 토큰 정보 로깅 (디버깅용)
     */
    fun logTokenInfo() {
        val accessToken = getAccessToken()
        val refreshToken = getRefreshToken()
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        val now = System.currentTimeMillis()
        val remainingTime = (expiresAt - now) / 1000
        
        Log.d(TAG, "=== 토큰 정보 ===")
        Log.d(TAG, "액세스 토큰 존재: ${!accessToken.isNullOrEmpty()}")
        Log.d(TAG, "리프레시 토큰 존재: ${!refreshToken.isNullOrEmpty()}")
        Log.d(TAG, "토큰 만료까지 남은 시간: ${remainingTime}초")
        Log.d(TAG, "토큰 유효 여부: ${hasValidToken()}")
    }
    
    /**
     * 토큰이 곧 만료될 예정인지 확인 (24시간 이내)
     * @return true: 곧 만료됨, false: 아직 유효함
     */
    fun isTokenExpiringSoon(): Boolean {
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        val now = System.currentTimeMillis()
        
        // 24시간 이내 만료 예정인지 확인
        val warningTime = 24 * 60 * 60 * 1000L // 24시간
        return now >= (expiresAt - warningTime)
    }
    
    /**
     * 토큰 만료 시간 가져오기
     * @return 만료 시간 (밀리초), 0L이면 만료 시간 없음
     */
    fun getExpiresAt(): Long {
        return prefs.getLong(KEY_EXPIRES_AT, 0L)
    }
    
    /**
     * 자동 갱신 가능한지 확인
     * @return true: 자동 갱신 가능, false: 수동 로그인 필요
     */
    fun canAutoRefresh(): Boolean {
        val refreshToken = getRefreshToken()
        val accessToken = getAccessToken()
        
        // 리프레시 토큰이 있고 액세스 토큰도 있어야 자동 갱신 가능
        return !refreshToken.isNullOrEmpty() && !accessToken.isNullOrEmpty()
    }
    
    // ========== 게스트 토큰 관련 메서드 ==========
    
    /**
     * 게스트 사용자 여부 확인
     * @return 게스트 사용자 여부
     */
    fun isGuestUser(): Boolean {
        return prefs.getBoolean(KEY_IS_GUEST_USER, false)
    }
    
    /**
     * 게스트 사용자로 설정
     * @param isGuest 게스트 사용자 여부
     */
    fun setGuestUser(isGuest: Boolean) {
        prefs.edit()
            .putBoolean(KEY_IS_GUEST_USER, isGuest)
            .apply()
        
        Log.d(TAG, "게스트 사용자 설정: $isGuest")
    }
    
    /**
     * 게스트 토큰 저장
     * @param guestToken 게스트 토큰
     * @param expiresAt 만료 시간 (밀리초)
     */
    fun saveGuestToken(guestToken: String, expiresAt: Long) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, guestToken)
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .putBoolean(KEY_IS_GUEST_USER, true)
            .remove(KEY_REFRESH_TOKEN) // 게스트는 리프레시 토큰 없음
            .remove(KEY_TOKEN_TYPE)
            .apply()
        
        Log.d(TAG, "게스트 토큰 저장 완료")
        Log.d(TAG, "만료 시간: ${java.util.Date(expiresAt)}")
    }
    
    /**
     * 게스트 토큰 유효성 확인
     * @return 게스트 토큰 유효성
     */
    fun isGuestTokenValid(): Boolean {
        if (!isGuestUser()) return false
        
        val guestToken = getAccessToken()
        val expiresAt = getExpiresAt()
        
        if (guestToken.isNullOrEmpty() || expiresAt == 0L) {
            Log.d(TAG, "게스트 토큰이 없거나 만료 시간이 없습니다")
            return false
        }
        
        val currentTime = System.currentTimeMillis()
        val isValid = currentTime < expiresAt
        
        if (!isValid) {
            Log.d(TAG, "게스트 토큰이 만료되었습니다")
            Log.d(TAG, "만료 시간: ${java.util.Date(expiresAt)}, 현재 시간: ${java.util.Date(currentTime)}")
        }
        
        return isValid
    }
    
    /**
     * 게스트 토큰 만료까지 남은 시간 (시간 단위)
     * @return 남은 시간 (시간 단위), 만료된 경우 0
     */
    fun getGuestTokenRemainingHours(): Long {
        if (!isGuestUser()) return 0L
        
        val expiresAt = getExpiresAt()
        if (expiresAt == 0L) return 0L
        
        val currentTime = System.currentTimeMillis()
        val remainingTime = expiresAt - currentTime
        
        return if (remainingTime > 0) {
            java.util.concurrent.TimeUnit.MILLISECONDS.toHours(remainingTime)
        } else {
            0L
        }
    }
    
    /**
     * 게스트 토큰 정보 로그 출력
     */
    fun logGuestTokenInfo() {
        if (!isGuestUser()) {
            Log.d(TAG, "게스트 사용자가 아닙니다")
            return
        }
        
        val token = getAccessToken()
        val expiresAt = getExpiresAt()
        val remainingHours = getGuestTokenRemainingHours()
        
        Log.d(TAG, "=== 게스트 토큰 정보 ===")
        Log.d(TAG, "토큰: ${token?.take(20)}...") // 보안상 일부만 출력
        Log.d(TAG, "만료 시간: ${if (expiresAt > 0) java.util.Date(expiresAt) else "없음"}")
        Log.d(TAG, "유효성: ${isGuestTokenValid()}")
        Log.d(TAG, "남은 시간: ${remainingHours}시간")
    }
}

