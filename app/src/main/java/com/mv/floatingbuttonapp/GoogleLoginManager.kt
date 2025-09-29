package com.mv.floatingbuttonapp

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 구글 로그인을 관리하는 매니저 클래스
 * Firebase Auth와 Google Sign-In을 사용하여 구글 로그인 기능 제공
 */
class GoogleLoginManager(private val context: Context) {
    
    companion object {
        private const val TAG = "GoogleLoginManager"
        private const val PREFS_NAME = "google_login_prefs"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_EMAIL = "email"
        private const val KEY_PHOTO_URL = "photo_url"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    
    // Google Sign-In 클라이언트
    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }
    
    // 로그인 상태 확인
    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        private set(value) = prefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()
    
    // 사용자 정보
    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        private set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()
    
    var displayName: String?
        get() = prefs.getString(KEY_DISPLAY_NAME, null)
        private set(value) = prefs.edit().putString(KEY_DISPLAY_NAME, value).apply()
    
    var email: String?
        get() = prefs.getString(KEY_EMAIL, null)
        private set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()
    
    var photoUrl: String?
        get() = prefs.getString(KEY_PHOTO_URL, null)
        private set(value) = prefs.edit().putString(KEY_PHOTO_URL, value).apply()
    
    /**
     * 구글 로그인 Intent 가져오기
     * @return Google Sign-In Intent
     */
    fun getSignInIntent() = googleSignInClient.signInIntent
    
    /**
     * 구글 로그인 결과 처리
     * @param data Activity 결과 데이터
     * @return 로그인 결과
     */
    suspend fun handleSignInResult(data: android.content.Intent?): Result<LoginResult> = suspendCancellableCoroutine { continuation ->
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            
            if (account != null) {
                Log.d(TAG, "구글 로그인 성공: ${account.displayName}")
                firebaseAuthWithGoogle(account, continuation)
            } else {
                Log.e(TAG, "구글 계정 정보가 null입니다")
                continuation.resume(Result.failure(Exception("구글 계정 정보를 가져올 수 없습니다")))
            }
        } catch (e: ApiException) {
            Log.e(TAG, "구글 로그인 실패", e)
            continuation.resume(Result.failure(e))
        }
    }
    
    /**
     * Firebase Auth로 구글 인증
     */
    private fun firebaseAuthWithGoogle(
        account: GoogleSignInAccount,
        continuation: kotlin.coroutines.Continuation<Result<LoginResult>>
    ) {
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = firebaseAuth.currentUser
                    if (user != null) {
                        // 사용자 정보 저장
                        saveUserInfo(
                            userId = user.uid,
                            displayName = user.displayName ?: account.displayName,
                            email = user.email ?: account.email,
                            photoUrl = user.photoUrl?.toString() ?: account.photoUrl?.toString()
                        )
                        
                        val loginResult = LoginResult(
                            isSuccess = true,
                            userId = user.uid,
                            nickname = user.displayName ?: account.displayName,
                            profileImageUrl = user.photoUrl?.toString() ?: account.photoUrl?.toString(),
                            email = user.email ?: account.email
                        )
                        
                        Log.d(TAG, "Firebase 인증 성공: ${loginResult.nickname}")
                        continuation.resume(Result.success(loginResult))
                    } else {
                        Log.e(TAG, "Firebase 사용자 정보가 null입니다")
                        continuation.resume(Result.failure(Exception("Firebase 사용자 정보를 가져올 수 없습니다")))
                    }
                } else {
                    Log.e(TAG, "Firebase 인증 실패", task.exception)
                    continuation.resume(Result.failure(task.exception ?: Exception("Firebase 인증 실패")))
                }
            }
    }
    
    /**
     * 사용자 정보를 SharedPreferences에 저장
     */
    private fun saveUserInfo(
        userId: String?,
        displayName: String?,
        email: String?,
        photoUrl: String?
    ) {
        this.userId = userId
        this.displayName = displayName
        this.email = email
        this.photoUrl = photoUrl
        this.isLoggedIn = true
        
        Log.d(TAG, "사용자 정보 저장 완료: $displayName")
    }
    
    /**
     * 자동 로그인 확인
     */
    suspend fun checkAutoLogin(): Result<LoginResult> = suspendCancellableCoroutine { continuation ->
        if (!isLoggedIn || firebaseAuth.currentUser == null) {
            continuation.resume(Result.failure(Exception("자동 로그인 정보 없음")))
            return@suspendCancellableCoroutine
        }
        
        val user = firebaseAuth.currentUser
        if (user != null) {
            val loginResult = LoginResult(
                isSuccess = true,
                userId = user.uid,
                nickname = user.displayName,
                profileImageUrl = user.photoUrl?.toString(),
                email = user.email
            )
            
            Log.d(TAG, "자동 로그인 성공: ${loginResult.nickname}")
            continuation.resume(Result.success(loginResult))
        } else {
            Log.e(TAG, "Firebase 사용자 정보가 null입니다")
            continuation.resume(Result.failure(Exception("사용자 정보를 가져올 수 없습니다")))
        }
    }
    
    /**
     * 로그아웃
     */
    suspend fun logout(): Result<Unit> = suspendCancellableCoroutine { continuation ->
        try {
            // Firebase 로그아웃
            firebaseAuth.signOut()
            
            // Google Sign-In 로그아웃
            googleSignInClient.signOut().addOnCompleteListener {
                clearUserInfo()
                Log.d(TAG, "구글 로그아웃 완료")
                continuation.resume(Result.success(Unit))
            }
        } catch (e: Exception) {
            Log.e(TAG, "로그아웃 실패", e)
            continuation.resume(Result.failure(e))
        }
    }
    
    /**
     * 사용자 정보 초기화
     */
    private fun clearUserInfo() {
        prefs.edit().clear().apply()
        isLoggedIn = false
        userId = null
        displayName = null
        email = null
        photoUrl = null
    }
    
    /**
     * 현재 로그인된 사용자 정보 가져오기
     */
    fun getCurrentUser(): UserInfo? {
        return if (isLoggedIn) {
            UserInfo(
                userId = userId,
                nickname = displayName,
                profileImageUrl = photoUrl,
                email = email
            )
        } else {
            null
        }
    }
}
