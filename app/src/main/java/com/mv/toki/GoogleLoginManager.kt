package com.mv.toki

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
import com.mv.toki.api.ApiClient
import com.mv.toki.api.GoogleAuthRequest
import com.mv.toki.auth.TokenManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
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
        val webClientId = context.getString(R.string.default_web_client_id)
        Log.d(TAG, "웹 클라이언트 ID: $webClientId")
        
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .requestProfile()
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
    fun getSignInIntent(): android.content.Intent {
        Log.d(TAG, "구글 로그인 Intent 생성")
        return googleSignInClient.signInIntent
    }
    
    /**
     * 구글 로그인 결과 처리
     * @param data Activity 결과 데이터
     * @return 로그인 결과
     */
    suspend fun handleSignInResult(data: android.content.Intent?): Result<LoginResult> = suspendCancellableCoroutine { continuation ->
        try {
            Log.d(TAG, "구글 로그인 결과 처리 시작")
            Log.d(TAG, "Intent 데이터: $data")
            
            if (data == null) {
                Log.e(TAG, "Intent 데이터가 null입니다")
                continuation.resume(Result.failure(Exception("로그인 결과 데이터가 없습니다")))
                return@suspendCancellableCoroutine
            }
            
            // Intent의 extras를 자세히 로그로 출력
            data.extras?.let { extras ->
                Log.d(TAG, "Intent extras 키들: ${extras.keySet()}")
                for (key in extras.keySet()) {
                    Log.d(TAG, "Intent extra [$key]: ${extras.get(key)}")
                }
            }
            
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            Log.d(TAG, "GoogleSignIn 태스크: $task")
            Log.d(TAG, "태스크 성공 여부: ${task.isSuccessful}")
            Log.d(TAG, "태스크 완료 여부: ${task.isComplete}")
            Log.d(TAG, "태스크 예외: ${task.exception}")
            
            if (task.isSuccessful) {
                val account = task.result
                Log.d(TAG, "계정 정보: $account")
                if (account != null) {
                    Log.d(TAG, "구글 로그인 성공: ${account.displayName}, 이메일: ${account.email}")
                    Log.d(TAG, "계정 ID: ${account.id}")
                    Log.d(TAG, "ID 토큰 존재 여부: ${account.idToken != null}")
                    Log.d(TAG, "ID 토큰 길이: ${account.idToken?.length ?: 0}")
                    firebaseAuthWithGoogle(account, continuation)
                } else {
                    Log.e(TAG, "구글 계정 정보가 null입니다")
                    continuation.resume(Result.failure(Exception("구글 계정 정보를 가져올 수 없습니다")))
                }
            } else {
                val exception = task.exception
                Log.e(TAG, "구글 로그인 태스크 실패: ${exception?.message}")
                Log.e(TAG, "예외 타입: ${exception?.javaClass?.simpleName}")
                Log.e(TAG, "예외 상세: ${exception?.localizedMessage}")
                
                // ApiException인 경우 상태 코드 확인
                if (exception is ApiException) {
                    Log.e(TAG, "ApiException 상태 코드: ${exception.statusCode}")
                    when (exception.statusCode) {
                        7 -> continuation.resume(Result.failure(Exception("네트워크 연결을 확인해주세요")))
                        8 -> continuation.resume(Result.failure(Exception("구글 서비스에 연결할 수 없습니다")))
                        10 -> continuation.resume(Result.failure(Exception("개발자 콘솔에서 OAuth 클라이언트 ID를 확인해주세요")))
                        12501 -> continuation.resume(Result.failure(Exception("사용자가 로그인을 취소했습니다")))
                        else -> continuation.resume(Result.failure(Exception("구글 로그인 실패 (코드: ${exception.statusCode}): ${exception.message}")))
                    }
                } else {
                    continuation.resume(Result.failure(exception ?: Exception("구글 로그인 태스크 실패")))
                }
            }
        } catch (e: ApiException) {
            Log.e(TAG, "구글 로그인 ApiException: ${e.statusCode} - ${e.message}")
            Log.e(TAG, "ApiException 상세: ${e.localizedMessage}")
            when (e.statusCode) {
                7 -> continuation.resume(Result.failure(Exception("네트워크 연결을 확인해주세요")))
                8 -> continuation.resume(Result.failure(Exception("구글 서비스에 연결할 수 없습니다")))
                10 -> continuation.resume(Result.failure(Exception("개발자 콘솔에서 OAuth 클라이언트 ID를 확인해주세요")))
                12501 -> continuation.resume(Result.failure(Exception("사용자가 로그인을 취소했습니다")))
                else -> continuation.resume(Result.failure(Exception("구글 로그인 실패: ${e.message}")))
            }
        } catch (e: Exception) {
            Log.e(TAG, "구글 로그인 일반 오류", e)
            continuation.resume(Result.failure(e))
        }
    }
    
    /**
     * Firebase Auth로 구글 인증 + Toki Auth 서버 로그인
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
                        // 서버에 로그인 요청 (Toki Auth)
                        kotlinx.coroutines.GlobalScope.launch {
                            try {
                                Log.d(TAG, "Toki Auth 서버 로그인 요청")
                                val authRequest = GoogleAuthRequest(
                                    idToken = account.idToken ?: "",
                                    email = user.email ?: account.email,
                                    name = user.displayName ?: account.displayName,
                                    picture = user.photoUrl?.toString() ?: account.photoUrl?.toString()
                                )
                                
                                // 요청 데이터 로깅
                                Log.d(TAG, "=== 서버 요청 데이터 ===")
                                Log.d(TAG, "idToken: ${account.idToken?.take(20)}...")
                                Log.d(TAG, "email: ${user.email}")
                                Log.d(TAG, "name: ${user.displayName}")
                                Log.d(TAG, "picture: ${user.photoUrl}")
                                
                                // JSON 직렬화 확인
                                try {
                                    val gson = com.google.gson.Gson()
                                    val jsonString = gson.toJson(authRequest)
                                    Log.d(TAG, "요청 JSON: $jsonString")
                                } catch (e: Exception) {
                                    Log.e(TAG, "JSON 직렬화 실패", e)
                                }
                                
                                Log.d(TAG, "=== API 호출 시작 ===")
                                val response = ApiClient.authApi.loginWithGoogle(authRequest)
                                
                                Log.d(TAG, "=== 서버 응답 ===")
                                Log.d(TAG, "응답 코드: ${response.code()}")
                                Log.d(TAG, "응답 메시지: ${response.message()}")
                                Log.d(TAG, "성공 여부: ${response.isSuccessful}")
                                
                                if (response.isSuccessful && response.body() != null) {
                                    val tokenResponse = response.body()!!
                                    
                                    // JWT 토큰 저장
                                    val tokenManager = TokenManager.getInstance(context)
                                    tokenManager.saveTokens(
                                        accessToken = tokenResponse.accessToken,
                                        refreshToken = tokenResponse.refreshToken,
                                        expiresIn = tokenResponse.expiresIn,
                                        tokenType = tokenResponse.tokenType
                                    )
                                    
                                    // 로컬 사용자 정보 저장
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
                                    
                                    Log.d(TAG, "구글 로그인 완료 (서버 연동 성공): ${loginResult.nickname}")
                                    continuation.resume(Result.success(loginResult))
                                } else {
                                    val errorBody = response.errorBody()?.string()
                                    Log.e(TAG, "=== 서버 로그인 실패 ===")
                                    Log.e(TAG, "응답 코드: ${response.code()}")
                                    Log.e(TAG, "응답 메시지: ${response.message()}")
                                    Log.e(TAG, "에러 본문: $errorBody")
                                    Log.e(TAG, "응답 헤더: ${response.headers()}")
                                    Log.e(TAG, "요청 URL: ${response.raw().request.url}")
                                    
                                    // 에러 메시지 상세화
                                    val errorMessage = when (response.code()) {
                                        400 -> "잘못된 요청입니다. 요청 데이터를 확인하세요."
                                        401 -> "인증 실패. 구글 ID 토큰이 유효하지 않습니다."
                                        403 -> "권한이 없습니다."
                                        404 -> "API 엔드포인트를 찾을 수 없습니다."
                                        422 -> "요청 데이터 형식이 올바르지 않습니다. $errorBody"
                                        500 -> "서버 내부 오류입니다. 서버 로그를 확인하세요. $errorBody"
                                        502 -> "게이트웨이 오류입니다."
                                        503 -> "서비스를 사용할 수 없습니다."
                                        else -> "알 수 없는 오류입니다. (${response.code()}) $errorBody"
                                    }
                                    
                                    continuation.resume(Result.failure(Exception(errorMessage)))
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "서버 로그인 중 오류", e)
                                continuation.resume(Result.failure(e))
                            }
                        }
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
                // 로컬 사용자 정보 삭제
                clearUserInfo()
                
                // JWT 토큰 삭제
                val tokenManager = TokenManager.getInstance(context)
                tokenManager.clearTokens()
                
                Log.d(TAG, "구글 로그아웃 완료 (JWT 토큰 삭제)")
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
