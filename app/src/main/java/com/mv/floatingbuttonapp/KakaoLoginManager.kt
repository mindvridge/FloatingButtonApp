package com.mv.floatingbuttonapp

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.kakao.sdk.auth.AuthCodeClient
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.KakaoSdk
import com.kakao.sdk.common.util.Utility
import com.kakao.sdk.user.UserApiClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 카카오 로그인을 관리하는 매니저 클래스
 * 자동 로그인, 로그아웃, 사용자 정보 관리 기능 제공
 */
class KakaoLoginManager(private val context: Context) {
    
    companion object {
        private const val TAG = "KakaoLoginManager"
        private const val PREFS_NAME = "kakao_login_prefs"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_NICKNAME = "nickname"
        private const val KEY_PROFILE_IMAGE = "profile_image"
        private const val KEY_EMAIL = "email"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // 로그인 상태 확인
    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        private set(value) = prefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()
    
    // 사용자 정보
    var userId: String?
        get() {
            return try {
                // 기존 Long 타입 데이터가 있는지 확인
                if (prefs.contains(KEY_USER_ID)) {
                    // String으로 읽기 시도
                    prefs.getString(KEY_USER_ID, null)
                } else {
                    null
                }
            } catch (e: ClassCastException) {
                // Long 타입으로 저장된 경우 String으로 변환
                Log.d(TAG, "기존 Long 타입 userId를 String으로 변환")
                val longUserId = prefs.getLong(KEY_USER_ID, -1)
                if (longUserId != -1L) {
                    val stringUserId = longUserId.toString()
                    // 새로운 String 타입으로 저장
                    prefs.edit().putString(KEY_USER_ID, stringUserId).apply()
                    stringUserId
                } else {
                    null
                }
            }
        }
        private set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()
    
    var nickname: String?
        get() = prefs.getString(KEY_NICKNAME, null)
        private set(value) = prefs.edit().putString(KEY_NICKNAME, value).apply()
    
    var profileImageUrl: String?
        get() = prefs.getString(KEY_PROFILE_IMAGE, null)
        private set(value) = prefs.edit().putString(KEY_PROFILE_IMAGE, value).apply()
    
    var email: String?
        get() = prefs.getString(KEY_EMAIL, null)
        private set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()
    
    /**
     * 카카오 SDK 초기화
     * Application 클래스에서 호출해야 함
     */
    fun initializeKakaoSdk() {
        try {
            // 카카오 SDK 초기화
            KakaoSdk.init(context, context.getString(R.string.kakao_app_key))
            Log.d(TAG, "카카오 SDK 초기화 완료")
            
            // 키 해시 출력 (개발 시 디버깅용)
            val keyHash = Utility.getKeyHash(context)
            Log.d(TAG, "카카오 키 해시: $keyHash")
            Log.d(TAG, "이 키 해시를 카카오 개발자 콘솔에 등록하세요!")
            
        } catch (e: Exception) {
            Log.e(TAG, "카카오 SDK 초기화 실패", e)
        }
    }
    
    /**
     * 카카오 로그인 실행
     * @param activityContext Activity Context (카카오 로그인에 필요)
     * @param callback 로그인 결과 콜백
     */
    suspend fun loginWithKakao(activityContext: android.content.Context): Result<LoginResult> = suspendCancellableCoroutine { continuation ->
        // 실제 카카오 SDK 로그인
        Log.d(TAG, "카카오 로그인 시도")
        
        if (UserApiClient.instance.isKakaoTalkLoginAvailable(activityContext)) {
            // 카카오톡으로 로그인
            UserApiClient.instance.loginWithKakaoTalk(activityContext) { token, error ->
                if (error != null) {
                    Log.e(TAG, "카카오톡 로그인 실패", error)
                    continuation.resume(Result.failure(error))
                } else if (token != null) {
                    Log.d(TAG, "카카오톡 로그인 성공")
                    handleLoginSuccess(token, continuation)
                }
            }
        } else {
            // 카카오 계정으로 로그인 (웹뷰)
            UserApiClient.instance.loginWithKakaoAccount(activityContext) { token, error ->
                if (error != null) {
                    Log.e(TAG, "카카오 계정 로그인 실패", error)
                    continuation.resume(Result.failure(error))
                } else if (token != null) {
                    Log.d(TAG, "카카오 계정 로그인 성공")
                    handleLoginSuccess(token, continuation)
                }
            }
        }
        
        /* 테스트용 더미 로그인 (주석 처리)
        GlobalScope.launch {
            delay(2000)
            
            val dummyUser = LoginResult(
                isSuccess = true,
                userId = "123456789",
                nickname = "테스트사용자",
                profileImageUrl = "https://via.placeholder.com/100x100/FFE812/000000?text=K",
                email = "test@kakao.com"
            )
            
            saveUserInfo(
                dummyUser.userId,
                dummyUser.nickname,
                dummyUser.profileImageUrl,
                dummyUser.email
            )
            
            Log.d(TAG, "✅ 테스트 로그인 성공: ${dummyUser.nickname}")
            continuation.resume(Result.success(dummyUser))
        } */
    }
    
    /**
     * 로그인 성공 처리
     */
    private fun handleLoginSuccess(
        token: OAuthToken,
        continuation: kotlin.coroutines.Continuation<Result<LoginResult>>
    ) {
        // 사용자 정보 요청
        UserApiClient.instance.me { user, error ->
            if (error != null) {
                Log.e(TAG, "사용자 정보 요청 실패", error)
                continuation.resume(Result.failure(error))
            } else if (user != null) {
                // 사용자 정보 디버깅
                Log.d(TAG, "사용자 정보 디버깅:")
                Log.d(TAG, "  - user.id: ${user.id}")
                Log.d(TAG, "  - user.kakaoAccount: ${user.kakaoAccount}")
                Log.d(TAG, "  - user.kakaoAccount?.profile: ${user.kakaoAccount?.profile}")
                Log.d(TAG, "  - user.kakaoAccount?.profile?.nickname: ${user.kakaoAccount?.profile?.nickname}")
                Log.d(TAG, "  - user.kakaoAccount?.email: ${user.kakaoAccount?.email}")
                
                // 기본 사용자 정보 (kakaoAccount가 null인 경우를 대비)
                val nickname = user.kakaoAccount?.profile?.nickname ?: "사용자${user.id}"
                val profileImageUrl = user.kakaoAccount?.profile?.profileImageUrl
                val email = user.kakaoAccount?.email
                
                // 사용자 정보 저장
                saveUserInfo(user.id.toString(), nickname, profileImageUrl, email)
                
                val loginResult = LoginResult(
                    isSuccess = true,
                    userId = user.id.toString(),
                    nickname = nickname,
                    profileImageUrl = profileImageUrl,
                    email = email
                )
                
                Log.d(TAG, "로그인 완료: ${loginResult.nickname}")
                continuation.resume(Result.success(loginResult))
            } else {
                Log.e(TAG, "사용자 정보가 null입니다")
                continuation.resume(Result.failure(Exception("사용자 정보를 가져올 수 없습니다")))
            }
        }
    }
    
    /**
     * 사용자 정보를 SharedPreferences에 저장
     */
    private fun saveUserInfo(
        userId: String?, 
        nickname: String?, 
        profileImageUrl: String?, 
        email: String?
    ) {
        this.userId = userId
        this.nickname = nickname
        this.profileImageUrl = profileImageUrl
        this.email = email
        this.isLoggedIn = true
        
        Log.d(TAG, "사용자 정보 저장 완료: $nickname")
    }
    
    /**
     * 자동 로그인 확인
     * 앱 시작 시 호출하여 저장된 토큰으로 자동 로그인
     */
    suspend fun checkAutoLogin(): Result<LoginResult> = suspendCancellableCoroutine { continuation ->
        if (!isLoggedIn) {
            continuation.resume(Result.failure(Exception("자동 로그인 정보 없음")))
            return@suspendCancellableCoroutine
        }
        
        // 실제 카카오 SDK 자동 로그인
        Log.d(TAG, "자동 로그인 확인")
        
        UserApiClient.instance.accessTokenInfo { _, error ->
            if (error != null) {
                Log.e(TAG, "자동 로그인 실패 - 토큰 만료", error)
                // 사용자 정보만 초기화 (비동기 로그아웃은 별도 처리)
                clearUserInfo()
                continuation.resume(Result.failure(error))
            } else {
                val loginResult = LoginResult(
                    isSuccess = true,
                    userId = userId,
                    nickname = nickname,
                    profileImageUrl = profileImageUrl,
                    email = email
                )
                
                Log.d(TAG, "자동 로그인 성공: ${loginResult.nickname}")
                continuation.resume(Result.success(loginResult))
            }
        }
    }
    
    /**
     * 로그아웃 (suspend 함수)
     * 외부에서 호출할 때 사용
     */
    suspend fun logout(): Result<Unit> = suspendCancellableCoroutine { continuation ->
        // 실제 카카오 SDK 로그아웃
        Log.d(TAG, "카카오 로그아웃 시도")
        
        UserApiClient.instance.logout { error ->
            if (error != null) {
                Log.e(TAG, "로그아웃 실패", error)
                continuation.resume(Result.failure(error))
            } else {
                clearUserInfo()
                Log.d(TAG, "로그아웃 완료")
                continuation.resume(Result.success(Unit))
            }
        }
    }
    
    
    /**
     * 사용자 정보 초기화
     */
    private fun clearUserInfo() {
        prefs.edit().clear().apply()
        isLoggedIn = false
        userId = null
        nickname = null
        profileImageUrl = null
        email = null
    }
    
    /**
     * 현재 로그인된 사용자 정보 가져오기
     */
    fun getCurrentUser(): UserInfo? {
        return if (isLoggedIn) {
            UserInfo(
                userId = userId,
                nickname = nickname,
                profileImageUrl = profileImageUrl,
                email = email
            )
        } else {
            null
        }
    }
}

/**
 * 로그인 결과 데이터 클래스
 */
data class LoginResult(
    val isSuccess: Boolean,
    val userId: String? = null,
    val nickname: String? = null,
    val profileImageUrl: String? = null,
    val email: String? = null,
    val errorMessage: String? = null
)

/**
 * 사용자 정보 데이터 클래스
 */
data class UserInfo(
    val userId: String?,
    val nickname: String?,
    val profileImageUrl: String?,
    val email: String?
)