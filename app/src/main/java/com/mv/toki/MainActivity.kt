package com.mv.toki

import android.app.Activity
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.Context.RECEIVER_NOT_EXPORTED
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Button
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.mv.toki.api.ApiClient
import com.mv.toki.auth.TokenManager
import com.mv.toki.ui.UpdateDialog
import com.mv.toki.version.AppUpdateChecker
import com.mv.toki.api.AppUpdateCheckResponse
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * 화면 상태를 나타내는 Enum
 * 앱의 온보딩 플로우를 관리합니다
 */
enum class AppScreen {
    LOGIN,                  // 로그인 화면
    REGISTER,               // 회원가입 화면 (분리된 화면)
    FIND_USERNAME,          // 아이디 찾기 화면
    FIND_PASSWORD,          // 비밀번호 찾기 화면
    PERMISSION_OVERLAY,     // 권한 설정 1: 다른 앱 위에 그리기
    PERMISSION_ACCESSIBILITY, // 권한 설정 2: 접근성 서비스
    INSTALLATION_COMPLETE,  // 설치 완료 화면
    SERVICE_CONTROL         // 서비스 실행 화면
}

/**
 * 플로팅 버튼 앱의 메인 액티비티
 * 
 * 이 액티비티는 앱의 진입점이며 다음과 같은 주요 기능을 제공합니다:
 * - 사용자 로그인/로그아웃 관리 (카카오, 구글)
 * - 필수 권한 확인 및 요청 (오버레이, 접근성, 화면 캡처)
 * - 플로팅 버튼 서비스 시작/중지
 * - 로그인 상태에 따른 UI 전환
 * - 권한 설정 가이드 제공
 * 
 * @author FloatingButtonApp Team
 * @version 1.0
 * @since 2024
 */
class MainActivity : ComponentActivity() {

    // ==================== 로그인 상태 관리 ====================
    
    /**
     * 자동 로그인 확인 상태
     * true: 자동 로그인 확인 완료, false: 아직 확인 중
     */
    private var isAutoLoginChecked by mutableStateOf(false)
    
    /**
     * 현재 화면 상태
     * 온보딩 플로우를 단계별로 관리합니다
     */
    private var currentScreen by mutableStateOf(AppScreen.LOGIN)
    
    /**
     * 현재 로그인 상태를 나타내는 변수
     * true: 로그인됨, false: 로그인되지 않음
     */
    private var isLoggedIn by mutableStateOf(false)
    
    /**
     * 현재 로그인된 사용자 정보
     * 로그인되지 않은 경우 null
     */
    private var currentUser by mutableStateOf<UserInfo?>(null)

    // ==================== 로그인 매니저들 ====================
    
    /**
     * 카카오 로그인을 관리하는 매니저
     * 카카오 SDK를 사용한 로그인 처리
     */
    private lateinit var kakaoLoginManager: KakaoLoginManager
    
    /**
     * 구글 로그인을 관리하는 매니저
     * Firebase Auth와 Google Sign-In을 사용한 로그인 처리
     */
    private lateinit var googleLoginManager: GoogleLoginManager
    
    /**
     * 이메일/아이디 비밀번호 로그인을 관리하는 매니저
     */
    private lateinit var emailLoginManager: EmailLoginManager
    
    /**
     * 비밀번호 재설정을 관리하는 매니저
     */
    private lateinit var passwordResetManager: PasswordResetManager
    
    // ==================== 로딩 상태 관리 ====================
    
    /**
     * 카카오 로그인 진행 상태
     * true: 로그인 진행 중, false: 로그인 대기 중
     */
    private var isKakaoLoginLoading by mutableStateOf(false)
    
    /**
     * 구글 로그인 진행 상태
     * true: 로그인 진행 중, false: 로그인 대기 중
     */
    private var isGoogleLoginLoading by mutableStateOf(false)
    
    /**
     * 이메일 로그인 진행 상태
     * true: 로그인 진행 중, false: 로그인 대기 중
     */
    private var isEmailLoginLoading by mutableStateOf(false)
    
    /**
     * 회원가입 진행 상태
     * true: 회원가입 진행 중, false: 회원가입 대기 중
     */
    private var isRegisterLoading by mutableStateOf(false)
    
    /**
     * 아이디 찾기 진행 상태
     * true: 아이디 찾기 진행 중, false: 아이디 찾기 대기 중
     */
    private var isFindUsernameLoading by mutableStateOf(false)
    
    /**
     * 비밀번호 재설정 진행 상태
     * true: 비밀번호 재설정 진행 중, false: 비밀번호 재설정 대기 중
     */
    private var isPasswordResetLoading by mutableStateOf(false)
    
    // ==================== 권한 상태 관리 ====================
    
    /**
     * 다른 앱 위에 그리기 권한 상태
     * 플로팅 버튼 표시에 필요
     */
    private var hasOverlayPermission by mutableStateOf(false)
    
    /**
     * 접근성 서비스 권한 상태
     * 키보드 감지 및 화면 캡처에 필요
     */
    private var hasAccessibilityPermission by mutableStateOf(false)
    
    /**
     * 서비스 실행 상태
     * UI 갱신을 위해 State로 관리
     */
    private var isServiceRunningState by mutableStateOf(false)
    
    // 사용자가 의도적으로 서비스를 중지했는지 여부
    private var userStoppedService by mutableStateOf(false)
    
    // 약관동의 팝업 상태
    private var showTermsPopup by mutableStateOf(false)
    
    // 접근성 권한 동의 팝업 상태
    private var showAccessibilityConsentDialog by mutableStateOf(false)
    
    // 앱 업데이트 관련 상태
    private var showUpdateDialog by mutableStateOf(false)
    private var updateInfo by mutableStateOf<AppUpdateCheckResponse?>(null)
    private lateinit var appUpdateChecker: AppUpdateChecker
    
    
    // 구글 로그인 결과 처리
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("MainActivity", "구글 로그인 결과 코드: ${result.resultCode}")
        Log.d("MainActivity", "구글 로그인 결과 데이터: ${result.data}")
        Log.d("MainActivity", "구글 로그인 결과 데이터 extras: ${result.data?.extras}")
        
        // RESULT_CANCELED인 경우에도 실제 오류를 확인해보기 위해 처리 시도
        lifecycleScope.launch {
            try {
                Log.d("MainActivity", "구글 로그인 결과 처리 시작 (결과 코드: ${result.resultCode})")
                val loginResult = googleLoginManager.handleSignInResult(result.data)
                loginResult.onSuccess { loginData ->
                    isLoggedIn = true
                    currentUser = UserInfo(
                        userId = loginData.userId,
                        nickname = loginData.nickname,
                        profileImageUrl = loginData.profileImageUrl,
                        email = loginData.email
                    )
                    // 로그인 성공 후 첫 번째 권한 화면으로 이동
                    currentScreen = AppScreen.PERMISSION_OVERLAY
                    Toast.makeText(this@MainActivity, "🎉 구글 로그인이 완료되었습니다!", Toast.LENGTH_SHORT).show()
                    Log.d("MainActivity", "구글 로그인 성공: ${loginData.nickname}")
                }.onFailure { error ->
                    Log.e("MainActivity", "구글 로그인 실패", error)
                    when (result.resultCode) {
                        Activity.RESULT_OK -> {
                            Toast.makeText(this@MainActivity, "구글 로그인 실패: ${error.message}", Toast.LENGTH_LONG).show()
                        }
                        Activity.RESULT_CANCELED -> {
                            Toast.makeText(this@MainActivity, "구글 로그인이 취소되었습니다: ${error.message}", Toast.LENGTH_LONG).show()
                        }
                        else -> {
                            Toast.makeText(this@MainActivity, "구글 로그인 오류 (코드: ${result.resultCode}): ${error.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "구글 로그인 처리 중 예외 발생", e)
                Toast.makeText(this@MainActivity, "구글 로그인 처리 오류: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                // 로딩 상태 해제
                isGoogleLoginLoading = false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 앱 업데이트 체커 초기화
        appUpdateChecker = AppUpdateChecker(this)
        
        // 앱 업데이트 체크 (가장 처음 단계)
        checkAppUpdate()
        
        // 로그인 매니저 초기화
        kakaoLoginManager = KakaoLoginManager(this)
        googleLoginManager = GoogleLoginManager(this)
        emailLoginManager = EmailLoginManager(this)
        passwordResetManager = PasswordResetManager(this)
        
        // 카카오 SDK 초기화
        kakaoLoginManager.initializeKakaoSdk()
        
        // 권한 상태 확인
        checkPermissions()
        
        // 초기 화면 결정 (자동 로그인 확인 전에 미리 설정)
        initializeScreen()
        
        // 자동 로그인 확인
        checkAutoLogin()
        
        // 약관 동의 상태 확인 (로그인된 사용자만)
        checkTermsConsentStatus()
        
        // 로컬 약관동의 상태 확인 및 자동 팝업 표시
        checkLocalTermsAndShowPopup()
        
        // 서비스 실행 상태 초기화
        updateServiceRunningState()

        setContent {
            FloatingButtonAppTheme {
                // 현재 화면 상태에 따라 다른 화면 표시
                when (currentScreen) {
                    AppScreen.LOGIN -> {
                        // 로그인 화면
                        LoginScreen(
                            onKakaoLoginClick = { loginWithKakao() },
                            onEmailLoginClick = { login, password -> loginWithEmail(login, password) },
                            onEmailRegisterClick = { username, email, password, name -> registerWithEmail(username, email, password, name) },
                            onOpenRegister = { currentScreen = AppScreen.REGISTER },
                            onOpenFindUsername = { currentScreen = AppScreen.FIND_USERNAME },
                            onOpenFindPassword = { currentScreen = AppScreen.FIND_PASSWORD },
                            onSaveTempConsent = { saveTempConsent() },
                            onClearTempConsent = { clearTempConsent() },
                            onOpenTermsLink = { openTermsLink() },
                            onOpenPrivacyLink = { openPrivacyLink() },
                            isKakaoLoginLoading = isKakaoLoginLoading,
                            isEmailLoginLoading = isEmailLoginLoading,
                            isRegisterLoading = isRegisterLoading,
                            showTermsPopup = showTermsPopup,
                            onShowTermsPopup = { showTermsPopup = it },
                            initialTermsAgreed = checkLocalTermsConsent()
                        )
                    }
                    
                    AppScreen.REGISTER -> {
                        // 회원가입 화면
                        RegisterScreen(
                            onRegisterClick = { username, email, password, name -> registerWithEmail(username, email, password, name) },
                            onBackClick = { currentScreen = AppScreen.LOGIN },
                            isRegisterLoading = isRegisterLoading
                        )
                    }
                    
                    AppScreen.FIND_USERNAME -> {
                        // 아이디 찾기 화면
                        FindUsernameScreen(
                            onFindUsernameClick = { email -> findUsernameWithEmail(email) },
                            onBackClick = { currentScreen = AppScreen.LOGIN },
                            isFindUsernameLoading = isFindUsernameLoading
                        )
                    }
                    
                    AppScreen.FIND_PASSWORD -> {
                        // 비밀번호 찾기 화면
                        FindPasswordScreen(
                            onRequestPasswordReset = { email, onResult -> 
                                requestPasswordReset(email, onResult)
                            },
                            onResetPassword = { token, newPassword -> resetPassword(token, newPassword) },
                            onBackClick = { currentScreen = AppScreen.LOGIN },
                            isPasswordResetLoading = isPasswordResetLoading
                        )
                    }
                    
                    AppScreen.PERMISSION_OVERLAY -> {
                        // 권한 설정 1: 다른 앱 위에 그리기
                        PermissionOverlayScreen(
                            currentUser = currentUser,
                            hasPermission = hasOverlayPermission,
                            onRequestPermission = { requestOverlayPermission() },
                            onNextClick = {
                                // 권한이 설정되어 있어야만 다음 화면으로 이동
                                if (hasOverlayPermission) {
                                    currentScreen = AppScreen.PERMISSION_ACCESSIBILITY
                                } else {
                                    Toast.makeText(this, "다른 앱 위에 그리기 권한을 먼저 설정해주세요.", Toast.LENGTH_SHORT).show()
                                    requestOverlayPermission()
                                }
                            },
                            onSkipClick = {
                                // 건너뛰고 다음 권한 화면으로 이동
                                currentScreen = AppScreen.PERMISSION_ACCESSIBILITY
                            }
                        )
                    }
                    
                    AppScreen.PERMISSION_ACCESSIBILITY -> {
                        // 권한 설정 2: 접근성 서비스
                        PermissionAccessibilityScreen(
                            currentUser = currentUser,
                            hasPermission = hasAccessibilityPermission,
                            onRequestPermission = { requestAccessibilityPermission() },
                            onNextClick = {
                                // 권한이 설정되어 있어야만 다음 화면으로 이동
                                if (hasAccessibilityPermission) {
                                    currentScreen = AppScreen.INSTALLATION_COMPLETE
                                } else {
                                    Toast.makeText(this, "접근성 서비스 권한을 먼저 설정해주세요.", Toast.LENGTH_SHORT).show()
                                    requestAccessibilityPermission()
                                }
                            },
                            onSkipClick = {
                                // 건너뛰고 설치 완료 화면으로 이동
                                currentScreen = AppScreen.INSTALLATION_COMPLETE
                            },
                            showAccessibilityConsentDialog = showAccessibilityConsentDialog,
                            onShowAccessibilityConsentDialog = { showAccessibilityConsentDialog = it },
                            onAccessibilityPermissionAgreed = {
                                // 팝업에서 동의 버튼 클릭 시 권한 설정으로 이동
                                requestAccessibilityPermission()
                            }
                        )
                    }
                    
                    AppScreen.INSTALLATION_COMPLETE -> {
                        // 설치 완료 화면
                        InstallationCompleteScreen(
                            currentUser = currentUser,
                            onStartClick = {
                                // 서비스 실행 화면으로 이동
                                currentScreen = AppScreen.SERVICE_CONTROL
                            }
                        )
                    }
                    
                    AppScreen.SERVICE_CONTROL -> {
                        // 서비스 실행 화면
                        // 권한이 모두 허용되어 있고 서비스가 실행 중이 아니라면 진입 즉시 자동 시작 (사용자가 중지하지 않은 경우만)
                        LaunchedEffect(hasOverlayPermission, hasAccessibilityPermission) {
                            if (hasOverlayPermission && hasAccessibilityPermission && !isServiceRunningState && !userStoppedService) {
                                Log.d("MainActivity", "SERVICE_CONTROL 진입 - 권한 완료, 서비스 자동 시작 트리거")
                                startFloatingService()
                                // 상태 새로고침
                                updateServiceRunningState()
                            }
                        }
                        ServiceControlScreen(
                            currentUser = currentUser,
                            hasOverlayPermission = hasOverlayPermission,
                            hasAccessibilityPermission = hasAccessibilityPermission,
                            isServiceRunning = isServiceRunningState,
                            userStoppedService = userStoppedService,
                            onStartServiceClick = {
                                if (checkAllPermissionsAndStart()) {
                                    userStoppedService = false // 사용자가 서비스를 시작했으므로 플래그 리셋
                                    startFloatingService()
                                } else {
                                    Toast.makeText(this@MainActivity, "필요한 권한을 먼저 설정해주세요.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onStopServiceClick = {
                                userStoppedService = true // 사용자가 의도적으로 서비스를 중지
                                stopFloatingService()
                            },
                            onLogoutClick = { 
                                logoutFromKakao()
                                currentScreen = AppScreen.LOGIN
                            },
                            onOverlayPermissionClick = { requestOverlayPermission() },
                            onAccessibilityPermissionClick = { requestAccessibilityPermission() }
                        )
                    }
                }
            }
            
            
            // 앱 업데이트 다이얼로그 (전역적으로 표시)
            if (showUpdateDialog && updateInfo != null) {
                Log.d("MainActivity", "UpdateDialog 표시: ${updateInfo?.latestVersion}")
                UpdateDialog(
                    updateInfo = updateInfo!!,
                    onDismiss = { 
                        Log.d("MainActivity", "UpdateDialog onDismiss 호출")
                        showUpdateDialog = false
                        updateInfo = null
                    },
                    onUpdate = {
                        Log.d("MainActivity", "UpdateDialog onUpdate 호출")
                        appUpdateChecker.openAppStore(updateInfo?.storeUrl)
                        showUpdateDialog = false
                        updateInfo = null
                    }
                )
            }
        }
    }


    override fun onResume() {
        super.onResume()
        // 앱이 다시 활성화될 때 권한 상태 확인
        checkPermissions()
        // 서비스 상태는 사용자가 명시적으로 제어한 경우를 고려하여 조건부로만 업데이트
        // (권한 변경이나 앱 재시작 시에만)
        Log.d("MainActivity", "onResume - 서비스 상태 확인 생략 (사용자 제어 우선)")
    }

    /**
     * 권한 상태 확인
     */
    private fun checkPermissions() {
        val previousOverlayPermission = hasOverlayPermission
        val previousAccessibilityPermission = hasAccessibilityPermission
        
        hasOverlayPermission = checkOverlayPermission()
        hasAccessibilityPermission = checkAccessibilityPermission()
        
        Log.d("MainActivity", "권한 상태 확인 - 오버레이: $hasOverlayPermission, 접근성: $hasAccessibilityPermission")
        Log.d("MainActivity", "현재 화면: $currentScreen")
        
        // 권한 설정 화면에서 권한이 완료되면 자동으로 다음 스텝으로 이동
        when (currentScreen) {
            AppScreen.PERMISSION_OVERLAY -> {
                if (hasOverlayPermission && !previousOverlayPermission) {
                    Log.d("MainActivity", "오버레이 권한 설정 완료 - 다음 단계로 이동")
                    currentScreen = AppScreen.PERMISSION_ACCESSIBILITY
                }
            }
            AppScreen.PERMISSION_ACCESSIBILITY -> {
                if (hasAccessibilityPermission && !previousAccessibilityPermission) {
                    Log.d("MainActivity", "접근성 권한 설정 완료 - 설치 완료 화면으로 이동")
                    currentScreen = AppScreen.INSTALLATION_COMPLETE
                }
            }
            AppScreen.SERVICE_CONTROL -> {
                // 사용자 페이지에서 권한이 모두 허용되어 있으면 서비스 자동 시작
                if (hasOverlayPermission && hasAccessibilityPermission) {
                    if (!isServiceRunning()) {
                        Log.d("MainActivity", "사용자 페이지 - 모든 권한 허용됨, 서비스 자동 시작")
                        startFloatingService()
                    }
                }
            }
            else -> {
                // 두 권한이 모두 허용되었고, 이전에 허용되지 않았던 경우 자동으로 서비스 시작
                if (hasOverlayPermission && hasAccessibilityPermission) {
                    if (!previousOverlayPermission || !previousAccessibilityPermission) {
                        // 서비스가 이미 실행 중인지 확인
                        if (!isServiceRunning()) {
                            Log.d("MainActivity", "모든 권한이 허용됨 - 서비스 자동 시작")
                            startFloatingService()
                            Toast.makeText(this, "모든 권한이 설정되었습니다. 서비스를 시작합니다.", Toast.LENGTH_SHORT).show()
                        } else {
                            Log.d("MainActivity", "서비스가 이미 실행 중입니다.")
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 권한 상태에 따라 초기 화면 결정
     * 자동 로그인 시 사용됩니다
     */
    private fun determineInitialScreen(): AppScreen {
        return when {
            !hasOverlayPermission -> AppScreen.PERMISSION_OVERLAY
            !hasAccessibilityPermission -> AppScreen.PERMISSION_ACCESSIBILITY
            else -> AppScreen.SERVICE_CONTROL  // 권한이 모두 있으면 사용자 페이지로 이동
        }
    }
    
    /**
     * 초기 화면 설정
     * 토큰 존재 여부를 빠르게 확인하여 적절한 초기 화면을 설정합니다
     */
    private fun initializeScreen() {
        lifecycleScope.launch {
            try {
                val tokenManager = com.mv.toki.auth.TokenManager.getInstance(this@MainActivity)
                
                // 토큰이 있으면 자동 로그인 가능성이 높으므로 권한 상태에 따라 화면 설정
                if (tokenManager.hasValidToken()) {
                    Log.d("MainActivity", "토큰 존재 - 초기 화면을 권한/서비스 화면으로 설정")
                    currentScreen = determineInitialScreen()
                } else {
                    Log.d("MainActivity", "토큰 없음 - 로그인 화면 유지")
                    // currentScreen은 이미 AppScreen.LOGIN으로 초기화되어 있음
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "초기 화면 설정 중 오류", e)
                // 오류 발생 시 로그인 화면 유지
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
    }
    
    /**
     * 자동 로그인 확인
     */
    private fun checkAutoLogin() {
        lifecycleScope.launch {
            try {
                // JWT 토큰 존재 여부 확인
                val tokenManager = com.mv.toki.auth.TokenManager.getInstance(this@MainActivity)
                
                if (!tokenManager.hasValidToken()) {
                    Log.d("MainActivity", "유효한 JWT 토큰이 없습니다")
                    // 토큰이 없으면 로그인 화면으로 설정
                    currentScreen = AppScreen.LOGIN
                    // 자동 로그인 확인 완료
                    isAutoLoginChecked = true
                    return@launch
                }
                
                Log.d("MainActivity", "JWT 토큰 존재 - 자동 로그인 시도")
                tokenManager.logTokenInfo()
                
                // 이메일 자동 로그인 먼저 시도 (JWT 토큰 기반)
                val emailResult = emailLoginManager.checkAutoLogin()
                emailResult.onSuccess { userInfo ->
                    isLoggedIn = true
                    // 자동 로그인 시 권한 상태에 따라 화면 결정
                    currentScreen = determineInitialScreen()
                    currentUser = userInfo
                    Log.d("MainActivity", "이메일 자동 로그인 성공: ${userInfo.nickname}")
                    // 자동 로그인 확인 완료
                    isAutoLoginChecked = true
                }.onFailure {
                    // 이메일 자동 로그인 실패 시 카카오 자동 로그인 시도
                    Log.d("MainActivity", "이메일 자동 로그인 실패 - 카카오 자동 로그인 시도")
                    val kakaoResult = kakaoLoginManager.checkAutoLogin()
                    kakaoResult.onSuccess { loginResult ->
                        isLoggedIn = true
                        // 자동 로그인 시 권한 상태에 따라 화면 결정
                        currentScreen = determineInitialScreen()
                        currentUser = UserInfo(
                            userId = loginResult.userId,
                            nickname = loginResult.nickname,
                            profileImageUrl = loginResult.profileImageUrl,
                            email = loginResult.email
                        )
                        Log.d("MainActivity", "카카오 자동 로그인 성공: ${loginResult.nickname}")
                        // 자동 로그인 확인 완료
                        isAutoLoginChecked = true
                    }.onFailure {
                        // 카카오 자동 로그인 실패 시 구글 자동 로그인 시도
                        val googleResult = googleLoginManager.checkAutoLogin()
                        googleResult.onSuccess { loginResult ->
                            isLoggedIn = true
                            // 자동 로그인 시 권한 상태에 따라 화면 결정
                            currentScreen = determineInitialScreen()
                            currentUser = UserInfo(
                                userId = loginResult.userId,
                                nickname = loginResult.nickname,
                                profileImageUrl = loginResult.profileImageUrl,
                                email = loginResult.email
                            )
                            Log.d("MainActivity", "구글 자동 로그인 성공: ${loginResult.nickname}")
                            // 자동 로그인 확인 완료
                            isAutoLoginChecked = true
                        }.onFailure {
                            Log.d("MainActivity", "모든 자동 로그인 실패 - JWT 토큰 삭제")
                            tokenManager.clearTokens()
                            // 자동 로그인 실패 시 로그인 화면으로 돌아가기
                            currentScreen = AppScreen.LOGIN
                            // 자동 로그인 확인 완료
                            isAutoLoginChecked = true
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "자동 로그인 확인 중 오류", e)
                // 오류 발생 시 로그인 화면으로 돌아가기
                currentScreen = AppScreen.LOGIN
                // 오류 발생 시에도 자동 로그인 확인 완료 처리
                isAutoLoginChecked = true
            }
        }
    }
    
    /**
     * 카카오 로그인 실행
     */
    private fun loginWithKakao() {
        // 이미 로딩 중이면 중복 실행 방지
        if (isKakaoLoginLoading) {
            Log.d("MainActivity", "카카오 로그인 이미 진행 중")
            return
        }
        
        isKakaoLoginLoading = true
        lifecycleScope.launch {
            try {
                val result = kakaoLoginManager.loginWithKakao(this@MainActivity)
                result.onSuccess { loginResult ->
                    isLoggedIn = true
                    currentUser = UserInfo(
                        userId = loginResult.userId,
                        nickname = loginResult.nickname,
                        profileImageUrl = loginResult.profileImageUrl,
                        email = loginResult.email
                    )
                    
                    // 로그인 성공 즉시 페이지 전환
                    Log.d("MainActivity", "카카오 로그인 성공 - 즉시 페이지 전환")
                    Toast.makeText(this@MainActivity, "🎉 로그인이 완료되었습니다!", Toast.LENGTH_SHORT).show()
                    currentScreen = AppScreen.PERMISSION_OVERLAY
                    
                    // 백그라운드에서 토큰 처리 및 서버 저장
                    lifecycleScope.launch {
                        Log.d("MainActivity", "백그라운드에서 토큰 처리 시작")
                        
                        // 토큰이 준비될 때까지 대기 (최대 5초)
                        var retryCount = 0
                        val maxRetries = 50 // 5초 (100ms * 50)
                        var tokenReady = false
                        
                        while (retryCount < maxRetries) {
                            delay(100) // 100ms 대기
                            
                            val tokenManager = TokenManager.getInstance(this@MainActivity)
                            val accessToken = tokenManager.getAccessToken()
                            val refreshToken = tokenManager.getRefreshToken()
                            
                            Log.d("MainActivity", "토큰 확인 시도 ${retryCount + 1}:")
                            Log.d("MainActivity", "  - accessToken: ${accessToken?.take(50)}...")
                            Log.d("MainActivity", "  - refreshToken: ${refreshToken?.take(50)}...")
                            Log.d("MainActivity", "  - hasValidToken: ${tokenManager.hasValidToken()}")
                            Log.d("MainActivity", "  - isTokenExpired: ${tokenManager.isTokenExpired()}")
                            
                            // 토큰이 있으면 바로 처리하거나, 약간의 지연 후에도 없으면 바로 진행
                            if ((accessToken != null && accessToken.isNotEmpty() && refreshToken != null && refreshToken.isNotEmpty()) || 
                                (retryCount >= 10 && accessToken != null && accessToken.isNotEmpty())) {
                                Log.d("MainActivity", "✅ 토큰 준비 완료 - 서버 저장 시작")
                                tokenReady = true
                                
                                // 서버에 동의 정보 저장 (백그라운드에서 처리)
                                saveConsentToServerWithCompletion(loginResult.userId ?: "") { success ->
                                    if (success) {
                                        Log.d("MainActivity", "🎉 백그라운드 서버 저장 완료")
                                    } else {
                                        Log.e("MainActivity", "❌ 백그라운드 서버 저장 실패 - 하지만 로그인은 이미 완료됨")
                                    }
                                }
                                return@launch
                            }
                            
                            retryCount++
                        }
                        
                        // 5초 후에도 토큰이 없으면 로그만 남기고 계속 진행
                        if (!tokenReady) {
                            Log.e("MainActivity", "❌ 토큰 준비 시간 초과 - 하지만 로그인은 이미 완료됨")
                        }
                    }
                }.onFailure { error ->
                    Toast.makeText(this@MainActivity, "카카오 로그인 실패: ${error.message}", Toast.LENGTH_SHORT).show()
                    Log.e("MainActivity", "카카오 로그인 실패", error)
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "카카오 로그인 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("MainActivity", "카카오 로그인 오류", e)
            } finally {
                // 로딩 상태 해제
                isKakaoLoginLoading = false
            }
        }
    }
    
    /**
     * 구글 로그인 실행
     */
    private fun loginWithGoogle() {
        // 이미 로딩 중이면 중복 실행 방지
        if (isGoogleLoginLoading) {
            Log.d("MainActivity", "구글 로그인 이미 진행 중")
            return
        }
        
        isGoogleLoginLoading = true
        try {
            Log.d("MainActivity", "구글 로그인 시작")
            val signInIntent = googleLoginManager.getSignInIntent()
            Log.d("MainActivity", "구글 로그인 Intent: $signInIntent")
            Log.d("MainActivity", "구글 로그인 Intent extras: ${signInIntent.extras}")
            Log.d("MainActivity", "구글 로그인 Intent action: ${signInIntent.action}")
            Log.d("MainActivity", "구글 로그인 Intent component: ${signInIntent.component}")
            Log.d("MainActivity", "구글 로그인 Intent package: ${signInIntent.`package`}")
            googleSignInLauncher.launch(signInIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "구글 로그인 오류: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("MainActivity", "구글 로그인 오류", e)
            isGoogleLoginLoading = false
        }
    }
    
    /**
     * 이메일/아이디 비밀번호 로그인 실행
     */
    private fun loginWithEmail(login: String, password: String) {
        if (isEmailLoginLoading) {
            Log.d("MainActivity", "이메일 로그인 이미 진행 중")
            return
        }
        
        isEmailLoginLoading = true
        lifecycleScope.launch {
            try {
                val result = emailLoginManager.loginWithCredentials(login, password)
                result.onSuccess { loginResult ->
                    isLoggedIn = true
                    currentUser = loginResult
                    // 로그인 성공 후 권한 화면으로 이동
                    currentScreen = AppScreen.PERMISSION_OVERLAY
                    Toast.makeText(this@MainActivity, "로그인이 완료되었습니다!", Toast.LENGTH_SHORT).show()
                    Log.d("MainActivity", "이메일 로그인 성공: ${loginResult.nickname}")
                }.onFailure { error ->
                    Toast.makeText(this@MainActivity, "로그인 실패: ${error.message}", Toast.LENGTH_SHORT).show()
                    Log.e("MainActivity", "이메일 로그인 실패", error)
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "로그인 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("MainActivity", "이메일 로그인 오류", e)
            } finally {
                isEmailLoginLoading = false
            }
        }
    }
    
    /**
     * 회원가입 실행
     */
    private fun registerWithEmail(username: String, email: String, password: String, name: String) {
        if (isRegisterLoading) {
            Log.d("MainActivity", "회원가입 이미 진행 중")
            return
        }
        
        isRegisterLoading = true
        lifecycleScope.launch {
            try {
                val result = emailLoginManager.registerWithCredentials(username, email, password, name)
                result.onSuccess { loginResult ->
                    isLoggedIn = true
                    currentUser = loginResult
                    // 회원가입 성공 후 권한 화면으로 이동
                    currentScreen = AppScreen.PERMISSION_OVERLAY
                    Toast.makeText(this@MainActivity, "회원가입이 완료되었습니다!", Toast.LENGTH_SHORT).show()
                    Log.d("MainActivity", "회원가입 성공: ${loginResult.nickname}")
                }.onFailure { error ->
                    Toast.makeText(this@MainActivity, "회원가입 실패: ${error.message}", Toast.LENGTH_SHORT).show()
                    Log.e("MainActivity", "회원가입 실패", error)
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "회원가입 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("MainActivity", "회원가입 오류", e)
            } finally {
                isRegisterLoading = false
            }
        }
    }
    
    /**
     * 아이디 찾기 실행
     */
    private fun findUsernameWithEmail(email: String) {
        if (isFindUsernameLoading) {
            Log.d("MainActivity", "아이디 찾기 이미 진행 중")
            return
        }
        
        isFindUsernameLoading = true
        lifecycleScope.launch {
            try {
                val result = emailLoginManager.findUsername(email)
                result.onSuccess { findResponse ->
                    Toast.makeText(
                        this@MainActivity, 
                        findResponse.message ?: "아이디 찾기 결과를 이메일로 발송했습니다.", 
                        Toast.LENGTH_LONG
                    ).show()
                    Log.d("MainActivity", "아이디 찾기 성공: ${findResponse.message}")
                    
                    // 성공 후 로그인 화면으로 돌아가기
                    currentScreen = AppScreen.LOGIN
                }.onFailure { error ->
                    Toast.makeText(this@MainActivity, "아이디 찾기 실패: ${error.message}", Toast.LENGTH_LONG).show()
                    Log.e("MainActivity", "아이디 찾기 실패", error)
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "아이디 찾기 오류: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("MainActivity", "아이디 찾기 오류", e)
            } finally {
                isFindUsernameLoading = false
            }
        }
    }
    
    /**
     * 비밀번호 재설정 요청 실행
     */
    private fun requestPasswordReset(email: String, onResult: (Boolean) -> Unit = {}) {
        if (isPasswordResetLoading) {
            Log.d("MainActivity", "비밀번호 재설정 요청 이미 진행 중")
            onResult(false)
            return
        }
        
        isPasswordResetLoading = true
        lifecycleScope.launch {
            try {
                val result = passwordResetManager.requestPasswordReset(email)
                result.onSuccess { resetResponse ->
                    Toast.makeText(
                        this@MainActivity,
                        resetResponse.message ?: "비밀번호 재설정 링크를 이메일로 발송했습니다.",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.d("MainActivity", "비밀번호 재설정 요청 성공: ${resetResponse.message}")
                    onResult(true)
                }.onFailure { error ->
                    Toast.makeText(this@MainActivity, "비밀번호 재설정 요청 실패: ${error.message}", Toast.LENGTH_LONG).show()
                    Log.e("MainActivity", "비밀번호 재설정 요청 실패", error)
                    onResult(false)
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "비밀번호 재설정 요청 오류: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("MainActivity", "비밀번호 재설정 요청 오류", e)
                onResult(false)
            } finally {
                isPasswordResetLoading = false
            }
        }
    }
    
    /**
     * 비밀번호 재설정 실행
     */
    private fun resetPassword(token: String, newPassword: String) {
        if (isPasswordResetLoading) {
            Log.d("MainActivity", "비밀번호 재설정 이미 진행 중")
            return
        }
        
        isPasswordResetLoading = true
        lifecycleScope.launch {
            try {
                val result = passwordResetManager.resetPassword(token, newPassword)
                result.onSuccess { resetResponse ->
                    Toast.makeText(
                        this@MainActivity,
                        resetResponse.message ?: "비밀번호가 성공적으로 재설정되었습니다.",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.d("MainActivity", "비밀번호 재설정 성공: ${resetResponse.message}")
                    
                    // 성공 후 로그인 화면으로 돌아가기
                    currentScreen = AppScreen.LOGIN
                }.onFailure { error ->
                    Toast.makeText(this@MainActivity, "비밀번호 재설정 실패: ${error.message}", Toast.LENGTH_LONG).show()
                    Log.e("MainActivity", "비밀번호 재설정 실패", error)
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "비밀번호 재설정 오류: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("MainActivity", "비밀번호 재설정 오류", e)
            } finally {
                isPasswordResetLoading = false
            }
        }
    }
    
    /**
     * 로그아웃
     */
    private fun logoutFromKakao() {
        lifecycleScope.launch {
            try {
                // 카카오 로그아웃 시도
                val kakaoResult = kakaoLoginManager.logout()
                if (kakaoResult.isSuccess) {
                    isLoggedIn = false
                    currentUser = null
                    Toast.makeText(this@MainActivity, "로그아웃 완료", Toast.LENGTH_SHORT).show()
                    Log.d("MainActivity", "카카오 로그아웃 완료")
                } else {
                    // 카카오 로그아웃 실패 시 구글 로그아웃 시도
                    val googleResult = googleLoginManager.logout()
                    if (googleResult.isSuccess) {
                        isLoggedIn = false
                        currentUser = null
                        Toast.makeText(this@MainActivity, "로그아웃 완료", Toast.LENGTH_SHORT).show()
                        Log.d("MainActivity", "구글 로그아웃 완료")
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "로그아웃 중 오류", e)
            }
        }
    }

    // ... (나머지 코드는 동일하게 유지)
    private fun startFloatingService() {
        try {
            // AccessibilityService를 통한 화면 캡처 사용으로 MediaProjection 권한 불필요
            Log.d("MainActivity", "서비스 시작 (AccessibilityService 화면 캡처 사용)")
            val intent = Intent(this, FloatingButtonService::class.java)
            startService(intent)
            
            // 서비스 시작 후 UI 상태 업데이트
            lifecycleScope.launch {
                delay(500) // 서비스 시작 완료를 위한 짧은 지연
                updateServiceRunningState() // 서비스 상태 업데이트로 UI 재구성
                checkPermissions() // 권한 상태 재확인
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "서비스 시작 중 오류", e)
            Toast.makeText(this, "서비스 시작 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    

    private fun checkAllPermissionsAndStart(): Boolean {
        return checkOverlayPermission() && checkAccessibilityPermission()
    }
    
    /**
     * 이용약관 링크 열기
     */
    private fun openTermsLink() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://mindvridge.s3.ap-northeast-2.amazonaws.com/TalkKey.html"))
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "이용약관 링크 열기 실패", e)
            Toast.makeText(this, "이용약관을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 개인정보처리방침 링크 열기
     */
    private fun openPrivacyLink() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://mindvridge.s3.ap-northeast-2.amazonaws.com/TalkKeyPrivate.html"))
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "개인정보처리방침 링크 열기 실패", e)
            Toast.makeText(this, "개인정보처리방침을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 약관 동의 임시 저장 (로그인 전)
     */
    private fun saveTempConsent() {
        try {
            Log.d("MainActivity", "약관 동의 임시 저장 시작")
            val prefs = getSharedPreferences("temp_consent", Context.MODE_PRIVATE)
            val deviceId = generateDeviceId()
            val currentTime = System.currentTimeMillis()
            
            val editor = prefs.edit()
            editor.putBoolean("terms_agreed", true)
            editor.putLong("consent_timestamp", currentTime)
            editor.putString("terms_version", "2025-10-01")
            editor.putString("privacy_version", "2025-10-01")
            editor.putString("device_id", deviceId)
            
            val result = editor.commit() // apply() 대신 commit() 사용
            
            if (result) {
                Log.d("MainActivity", "약관 동의 임시 저장 완료: $deviceId")
            } else {
                Log.e("MainActivity", "약관 동의 임시 저장 실패: commit() 반환 false")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "약관 동의 임시 저장 실패", e)
        }
    }
    
    /**
     * 약관 동의 임시 저장 삭제
     */
    private fun clearTempConsent() {
        try {
            val prefs = getSharedPreferences("temp_consent", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            Log.d("MainActivity", "약관 동의 임시 저장 삭제 완료")
        } catch (e: Exception) {
            Log.e("MainActivity", "약관 동의 임시 저장 삭제 실패", e)
        }
    }
    
    /**
     * 디바이스 ID 생성 (임시 식별자)
     */
    private fun generateDeviceId(): String {
        val prefs = getSharedPreferences("device_info", Context.MODE_PRIVATE)
        var deviceId = prefs.getString("device_id", null)
        
        if (deviceId == null) {
            deviceId = "device_${System.currentTimeMillis()}_${(1000..9999).random()}"
            prefs.edit().putString("device_id", deviceId).apply()
        }
        
        return deviceId
    }
    
    /**
     * 앱 업데이트 체크
     * 가장 처음 단계에서 실행되어 업데이트가 필요한 경우 스토어로 이동
     * 권한 상태 확인, 로그인, 약관 동의 등 모든 팝업보다 우선 실행
     */
    private fun checkAppUpdate() {
        lifecycleScope.launch {
            try {
                Log.d("MainActivity", "=== 앱 업데이트 체크 시작 ===")
                Log.d("MainActivity", "현재 시간: ${System.currentTimeMillis()}")
                
                val updateInfo = appUpdateChecker.checkForUpdates()
                
                if (updateInfo != null) {
                    Log.d("MainActivity", "✅ 업데이트 필요 - 다이얼로그 표시")
                    Log.d("MainActivity", "업데이트 정보:")
                    Log.d("MainActivity", "  - needs_update: ${updateInfo.needsUpdate}")
                    Log.d("MainActivity", "  - latest_version: ${updateInfo.latestVersion}")
                    Log.d("MainActivity", "  - current_version: ${updateInfo.currentVersion}")
                    Log.d("MainActivity", "  - update_message: ${updateInfo.updateMessage}")
                    
                    this@MainActivity.updateInfo = updateInfo
                    showUpdateDialog = true
                    
                    Log.d("MainActivity", "업데이트 다이얼로그 표시 준비 완료")
                } else {
                    Log.d("MainActivity", "✅ 최신 버전 사용 중 - 업데이트 불필요")
                    Log.d("MainActivity", "showUpdateDialog 상태: $showUpdateDialog")
                }
                
            } catch (e: Exception) {
                Log.e("MainActivity", "❌ 앱 업데이트 체크 실패", e)
                Log.e("MainActivity", "예외 상세: ${e.message}")
                Log.e("MainActivity", "예외 타입: ${e.javaClass.simpleName}")
                // 업데이트 체크 실패 시에도 앱은 정상 동작하도록 함
            }
        }
    }
    
    /**
     * 로컬 약관동의 상태 확인 및 자동 팝업 표시
     */
    private fun checkLocalTermsAndShowPopup() {
        val hasAgreed = checkLocalTermsConsent()
        
        Log.d("MainActivity", "약관동의 상태 확인 결과: $hasAgreed")
        
        if (!hasAgreed) {
            Log.d("MainActivity", "약관동의 미완료 - 자동 팝업 표시")
            // 약관동의가 완료되지 않았으면 자동으로 팝업 표시
            showTermsPopup = true
        } else {
            Log.d("MainActivity", "약관동의 완료됨 - 팝업 표시하지 않음")
        }
    }
    
    /**
     * 로컬에 저장된 약관동의 상태 확인
     * @return true: 약관동의 완료, false: 약관동의 미완료
     */
    private fun checkLocalTermsConsent(): Boolean {
        return try {
            Log.d("MainActivity", "약관동의 상태 확인 시작")
            val prefs = getSharedPreferences("terms_consent", Context.MODE_PRIVATE)
            val isAgreed = prefs.getBoolean("terms_agreed", false)
            val consentTimestamp = prefs.getLong("consent_timestamp", 0L)
            val termsVersion = prefs.getString("terms_version", "없음")
            
            Log.d("MainActivity", "로컬 약관동의 상태 확인:")
            Log.d("MainActivity", "  - isAgreed: $isAgreed")
            Log.d("MainActivity", "  - consentTimestamp: $consentTimestamp")
            Log.d("MainActivity", "  - termsVersion: $termsVersion")
            
            if (isAgreed) {
                Log.d("MainActivity", "약관동의 완료 상태 확인됨")
            } else {
                Log.d("MainActivity", "약관동의 미완료 상태 확인됨")
            }
            
            isAgreed
        } catch (e: Exception) {
            Log.e("MainActivity", "로컬 약관동의 상태 확인 실패", e)
            false
        }
    }
    
    /**
     * 약관 동의 상태 확인 (로그인된 사용자만)
     */
    private fun checkTermsConsentStatus() {
        if (isLoggedIn && currentUser != null) {
            lifecycleScope.launch {
                try {
                    Log.d("MainActivity", "약관 동의 상태 확인 시작")
                    
                    val response = ApiClient.geminiApi.getTermsStatus()
                    
                    if (response.isSuccessful && response.body() != null) {
                        val status = response.body()!!
                        Log.d("MainActivity", "약관 동의 상태 조회 성공:")
                        Log.d("MainActivity", "  - userId: ${status.userId}")
                        Log.d("MainActivity", "  - serviceTerms: ${status.serviceTerms}")
                        Log.d("MainActivity", "  - privacyPolicy: ${status.privacyPolicy}")
                        Log.d("MainActivity", "  - termsVersion: ${status.termsVersion}")
                        Log.d("MainActivity", "  - agreedAt: ${status.agreedAt}")
                        
                        // 약관 동의가 완료된 경우 로컬에도 저장
                        if (status.serviceTerms && status.privacyPolicy) {
                            val prefs = getSharedPreferences("terms_consent", Context.MODE_PRIVATE)
                            prefs.edit()
                                .putBoolean("terms_agreed", true)
                                .putString("terms_version", status.termsVersion)
                                .putLong("consent_timestamp", System.currentTimeMillis())
                                .apply()
                                
                            Log.d("MainActivity", "약관 동의 상태가 확인됨 - 로컬 저장 완료")
                        }
                    } else {
                        val errorBody = response.errorBody()?.string() ?: "Unknown error"
                        Log.e("MainActivity", "약관 동의 상태 조회 실패: ${response.code()} - $errorBody")
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "약관 동의 상태 확인 중 오류", e)
                }
            }
        } else {
            Log.d("MainActivity", "로그인되지 않은 사용자 - 약관 동의 상태 확인 건너뜀")
        }
    }
    
    /**
     * 로컬에 약관 동의 정보 영구 저장
     */
    private fun saveConsentLocally(termsVersion: String) {
        try {
            val permanentPrefs = getSharedPreferences("terms_consent", Context.MODE_PRIVATE)
            val editor = permanentPrefs.edit()
            editor.putBoolean("terms_agreed", true)
            editor.putString("terms_version", termsVersion)
            editor.putLong("consent_timestamp", System.currentTimeMillis())
            
            val result = editor.commit()
            if (result) {
                Log.d("MainActivity", "약관 동의 로컬 영구 저장 완료: $termsVersion")
            } else {
                Log.e("MainActivity", "약관 동의 로컬 영구 저장 실패")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "약관 동의 로컬 영구 저장 중 예외", e)
        }
    }
    
    /**
     * 로그인 후 서버에 동의 정보 저장 (콜백 포함)
     */
    private fun saveConsentToServerWithCompletion(userId: String, onComplete: (Boolean) -> Unit) {
        lifecycleScope.launch {
            try {
                Log.d("MainActivity", "=== saveConsentToServerWithCompletion 시작 ===")
                Log.d("MainActivity", "입력 userId: $userId")
                
                // temp_consent와 terms_consent 모두 확인
                val tempPrefs = getSharedPreferences("temp_consent", Context.MODE_PRIVATE)
                val permanentPrefs = getSharedPreferences("terms_consent", Context.MODE_PRIVATE)
                
                val hasTempConsent = tempPrefs.getBoolean("terms_agreed", false)
                val hasPermanentConsent = permanentPrefs.getBoolean("terms_agreed", false)
                
                Log.d("MainActivity", "약관 동의 상태 확인:")
                Log.d("MainActivity", "  - hasTempConsent: $hasTempConsent")
                Log.d("MainActivity", "  - hasPermanentConsent: $hasPermanentConsent")
                
                // 임시 동의가 있거나 영구 동의가 있는 경우 서버 저장 시도
                if (hasTempConsent || hasPermanentConsent) {
                    // 임시 동의가 있으면 임시 버전을, 없으면 영구 버전을 사용
                    val termsVersion = if (hasTempConsent) {
                        tempPrefs.getString("terms_version", "v1.0")
                    } else {
                        permanentPrefs.getString("terms_version", "v1.0")
                    }
                    
                    Log.d("MainActivity", "서버에 약관 동의 저장 시도:")
                    Log.d("MainActivity", "  - userId: $userId")
                    Log.d("MainActivity", "  - termsVersion: ${termsVersion}")
                    
                    // 토큰 매니저 인스턴스 생성 (JWT 토큰에서 사용자 ID 추출용)
                    val tokenManager = TokenManager.getInstance(this@MainActivity)
                    
                    // JWT 토큰에서 사용자 ID 추출
                    var extractedUserId: Int? = null
                    try {
                        val accessToken = tokenManager.getAccessToken()
                        if (accessToken != null && accessToken.isNotEmpty()) {
                            val jwtPayload = decodeJwtPayload(accessToken)
                            Log.d("MainActivity", "JWT 토큰에서 사용자 ID 추출 시도: $jwtPayload")
                            
                            // 정규식을 사용하여 user_id 추출 (더 안전한 방법)
                            val userIdRegex = "\"user_id\"\\s*:\\s*(\\d+)".toRegex()
                            val matchResult = userIdRegex.find(jwtPayload)
                            if (matchResult != null) {
                                extractedUserId = matchResult.groupValues[1].toIntOrNull()
                                Log.d("MainActivity", "정규식으로 추출된 사용자 ID: $extractedUserId")
                            } else {
                                Log.w("MainActivity", "JWT 토큰에서 user_id를 찾을 수 없음")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "JWT 토큰에서 사용자 ID 추출 실패", e)
                    }
                    
                    // 새로운 API 형식에 맞게 요청 데이터 생성
                    val request = com.mv.toki.api.TermsAgreeMultipleRequest(
                        termsVersion = (termsVersion ?: "v1.0").replace("v", ""), // API에서 "1.0" 형식 요구
                        serviceAgreed = true,
                        privacyAgreed = true
                    )
                    
                    try {
                        // 토큰 상태 확인 (더 상세하게)
                        val accessToken = tokenManager.getAccessToken()
                        val refreshToken = tokenManager.getRefreshToken()
                        val hasValidToken = tokenManager.hasValidToken()
                        val isTokenExpired = tokenManager.isTokenExpired()
                        
                        Log.d("MainActivity", "=== 서버 저장 전 토큰 상태 재확인 ===")
                        Log.d("MainActivity", "  - accessToken: ${accessToken?.take(50)}...")
                        Log.d("MainActivity", "  - refreshToken: ${refreshToken?.take(50)}...")
                        Log.d("MainActivity", "  - hasValidToken: $hasValidToken")
                        Log.d("MainActivity", "  - isTokenExpired: $isTokenExpired")
                        
                        if (accessToken.isNullOrEmpty() || refreshToken.isNullOrEmpty()) {
                            Log.w("MainActivity", "토큰이 완전하지 않음 - 서버 저장 건너뜀")
                            Log.w("MainActivity", "  - accessToken isEmpty: ${accessToken.isNullOrEmpty()}")
                            Log.w("MainActivity", "  - refreshToken isEmpty: ${refreshToken.isNullOrEmpty()}")
                            saveConsentLocally(termsVersion ?: "v1.0")
                            clearTempConsent()
                            // 토큰이 없어도 로컬 저장으로 대체하므로 성공으로 처리
                            onComplete(true)
                            return@launch
                        }
                        
                        if (isTokenExpired) {
                            Log.w("MainActivity", "토큰이 만료됨 - 서버 저장 건너뜀")
                            saveConsentLocally(termsVersion ?: "v1.0")
                            clearTempConsent()
                            // 토큰 만료되어도 로컬 저장으로 대체하므로 성공으로 처리
                            onComplete(true)
                            return@launch
                        }
                        
                        // 요청 데이터 로깅
                        Log.d("MainActivity", "서버 요청 데이터 (새로운 API 형식):")
                        Log.d("MainActivity", "  - termsVersion: ${request.termsVersion}")
                        Log.d("MainActivity", "  - serviceAgreed: ${request.serviceAgreed}")
                        Log.d("MainActivity", "  - privacyAgreed: ${request.privacyAgreed}")
                        Log.d("MainActivity", "  - userId (입력): $userId")
                        
                        // JWT 토큰 내용 확인 (서버 오류 디버깅용)
                        try {
                            val accessToken = tokenManager.getAccessToken()
                            if (accessToken != null && accessToken.isNotEmpty()) {
                                val jwtPayload = decodeJwtPayload(accessToken)
                                Log.d("MainActivity", "=== 서버 요청 전 JWT 토큰 분석 ===")
                                Log.d("MainActivity", jwtPayload)
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "JWT 토큰 분석 실패", e)
                        }
                        
                        // 새로운 API로 약관 동의 저장 요청
                        // AuthInterceptor가 자동으로 Authorization 헤더를 추가하므로 별도 헤더 불필요
                        val response = ApiClient.geminiApi.agreeToTermsMultiple(request)
                        
                        Log.d("MainActivity", "서버 응답 상태:")
                        Log.d("MainActivity", "  - Code: ${response.code()}")
                        Log.d("MainActivity", "  - Message: ${response.message()}")
                        Log.d("MainActivity", "  - IsSuccessful: ${response.isSuccessful}")
                        
                        if (response.isSuccessful && response.body() != null) {
                            val result = response.body()!!
                            Log.d("MainActivity", "약관 동의 서버 저장 성공:")
                            Log.d("MainActivity", "  - success: ${result.success}")
                            Log.d("MainActivity", "  - message: ${result.message}")
                            
                            // 서버 저장 완료 후 로컬에 영구 저장 (요청한 버전 사용)
                            saveConsentLocally("v${request.termsVersion}")
                            
                            // 임시 데이터 정리
                            clearTempConsent()
                            
                            Log.d("MainActivity", "약관 동의 로컬 영구 저장 완료")
                            // 서버 저장 성공 콜백 호출
                            onComplete(true)
                        } else {
                            val errorBody = response.errorBody()?.string() ?: "Unknown error"
                            Log.e("MainActivity", "약관 동의 서버 저장 실패:")
                            Log.e("MainActivity", "  - HTTP Code: ${response.code()}")
                            Log.e("MainActivity", "  - Error Body: $errorBody")
                            Log.e("MainActivity", "  - Response Headers: ${response.headers()}")
                            
                            // HTTP 상태 코드별 처리
                            when (response.code()) {
                                401 -> {
                                    Log.e("MainActivity", "인증 실패 - 토큰 문제")
                                    Log.e("MainActivity", "서버 응답: $errorBody")
                                    
                                    // JWT 토큰 내용 재확인
                                    try {
                                        val accessToken = tokenManager.getAccessToken()
                                        if (accessToken != null && accessToken.isNotEmpty()) {
                                            val jwtPayload = decodeJwtPayload(accessToken)
                                            Log.e("MainActivity", "401 오류 시 JWT 토큰 분석:")
                                            Log.e("MainActivity", jwtPayload)
                                        }
                                    } catch (e: Exception) {
                                        Log.e("MainActivity", "JWT 토큰 분석 실패", e)
                                    }
                                    
                                    onComplete(false)
                                }
                                403 -> {
                                    Log.e("MainActivity", "권한 없음 - API 접근 권한 문제")
                                    onComplete(false)
                                }
                                404 -> {
                                    Log.w("MainActivity", "엔드포인트 없음 - API 경로 문제")
                                    Log.w("MainActivity", "서버 API 미구현 (404) - 로컬 저장으로 대체")
                                    saveConsentLocally(termsVersion ?: "v1.0")
                                    clearTempConsent()
                                    // 404는 로컬 저장으로 대체하므로 성공으로 처리
                                    onComplete(true)
                                }
                                422 -> {
                                    Log.e("MainActivity", "요청 데이터 형식 오류")
                                    onComplete(false)
                                }
                                500 -> {
                                    Log.e("MainActivity", "서버 내부 오류")
                                    onComplete(false)
                                }
                                else -> {
                                    Log.e("MainActivity", "알 수 없는 오류: ${response.code()}")
                                    onComplete(false)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "약관 동의 서버 저장 중 예외 발생", e)
                        Log.e("MainActivity", "예외 상세: ${e.message}")
                        Log.e("MainActivity", "예외 타입: ${e.javaClass.simpleName}")
                        
                        // 네트워크 오류인지 확인
                        if (e.message?.contains("Unable to resolve host") == true) {
                            Log.e("MainActivity", "네트워크 연결 오류")
                            onComplete(false)
                        } else {
                            Log.w("MainActivity", "서버 저장 실패 - 로컬 저장으로 대체")
                            saveConsentLocally(termsVersion ?: "v1.0")
                            clearTempConsent()
                            // 로컬 저장으로 대체하므로 성공으로 처리
                            onComplete(true)
                        }
                    }
                } else {
                    Log.d("MainActivity", "임시 동의 정보가 없음 - 서버 저장 건너뜀")
                    Log.d("MainActivity", "약관 동의 없이도 로그인 성공 처리")
                    // 동의 정보가 없어도 로그인은 성공으로 처리
                    onComplete(true)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "=== saveConsentToServerWithCompletion 예외 발생 ===")
                Log.e("MainActivity", "예외 메시지: ${e.message}")
                Log.e("MainActivity", "예외 스택: ${e.stackTraceToString()}")
                Log.e("MainActivity", "서버에 동의 정보 저장 실패 - 실패로 처리")
                onComplete(false)
            }
        }
    }

    /**
     * JWT 토큰의 페이로드를 디코딩하여 내용을 확인 (디버깅용)
     */
    private fun decodeJwtPayload(token: String): String {
        return try {
            // JWT 토큰은 header.payload.signature 형식
            val parts = token.split(".")
            if (parts.size != 3) {
                return "JWT 토큰 형식이 올바르지 않습니다 (부분 수: ${parts.size})"
            }
            
            // 페이로드 부분 디코딩 (Base64)
            val payload = parts[1]
            
            // Base64 패딩 추가 (필요한 경우)
            val paddedPayload = when (payload.length % 4) {
                2 -> "$payload=="
                3 -> "$payload="
                else -> payload
            }
            
            val decodedBytes = android.util.Base64.decode(paddedPayload, android.util.Base64.DEFAULT)
            val decodedString = String(decodedBytes, Charsets.UTF_8)
            
            "디코딩된 페이로드: $decodedString"
        } catch (e: Exception) {
            "JWT 디코딩 실패: ${e.message}"
        }
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
                } else {
            true
        }
    }

    private fun checkAccessibilityPermission(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        // 올바른 접근성 서비스 클래스 이름 사용
        val serviceName = "$packageName/${KeyboardDetectionAccessibilityService::class.java.name}"
        
        Log.d("MainActivity", "접근성 서비스 확인 - enabledServices: $enabledServices")
        Log.d("MainActivity", "찾는 서비스: $serviceName")
        
        val isEnabled = enabledServices?.contains(serviceName) == true
        Log.d("MainActivity", "접근성 서비스 활성화 상태: $isEnabled")
        
        return isEnabled
    }
    

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
            // 권한 설정 화면으로 이동 후 돌아올 때 자동으로 권한 상태 확인
            Toast.makeText(this, "권한 설정 후 앱으로 돌아오면 자동으로 서비스가 시작됩니다.", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        // 접근성 설정 화면으로 이동 후 돌아올 때 자동으로 권한 상태 확인
        Toast.makeText(this, "접근성 서비스를 활성화한 후 앱으로 돌아오면 자동으로 서비스가 시작됩니다.", Toast.LENGTH_LONG).show()
    }
    

    private fun stopFloatingService() {
        try {
            Log.d("MainActivity", "서비스 중지 시작")
            
            // 서비스 중지 전 상태 확인
            val wasRunning = isServiceRunning()
            Log.d("MainActivity", "서비스 중지 전 상태: $wasRunning")
            
            if (!wasRunning) {
                Log.d("MainActivity", "서비스가 이미 중지된 상태입니다")
                updateServiceRunningState()
                Toast.makeText(this@MainActivity, "서비스가 이미 중지된 상태입니다", Toast.LENGTH_SHORT).show()
                return
            }
            
            // 서비스 중지 요청
            val intent = Intent(this, FloatingButtonService::class.java)
            stopService(intent)
            
            Log.d("MainActivity", "서비스 중지 요청 완료")
            
            // 서비스 중지 후 검증 및 최종 상태 업데이트
            lifecycleScope.launch {
                delay(1500) // 서비스 중지 완료를 위한 충분한 지연
                
                // 여러 번 확인하여 확실히 중지되었는지 검증
                var retryCount = 0
                var isStillRunning = isServiceRunning()
                
                while (isStillRunning && retryCount < 5) {
                    Log.d("MainActivity", "서비스가 여전히 실행 중, 재시도 $retryCount")
                    
                    // 강제 종료 시도 (추가적인 stopService 호출)
                    try {
                        stopService(intent)
                        Log.d("MainActivity", "추가 서비스 중지 요청 완료")
                    } catch (e: Exception) {
                        Log.e("MainActivity", "추가 서비스 중지 요청 실패", e)
                    }
                    
                    delay(800)
                    retryCount++
                    isStillRunning = isServiceRunning()
                }
                
                val finalState = isServiceRunning()
                Log.d("MainActivity", "서비스 최종 상태: $finalState")
                
                if (!finalState) {
                    // 서비스가 성공적으로 중지된 경우에만 상태 업데이트
                    updateServiceRunningState()
                    Toast.makeText(this@MainActivity, "서비스가 중지되었습니다", Toast.LENGTH_SHORT).show()
                } else {
                    // 서비스 중지 실패 시 상태 업데이트하지 않음
                    Log.e("MainActivity", "서비스 중지 실패 - 수동으로 앱을 종료하고 다시 시작해주세요")
                    Toast.makeText(this@MainActivity, "서비스 중지에 실패했습니다. 앱을 종료하고 다시 시작해주세요.", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "서비스 중지 중 오류", e)
            Toast.makeText(this, "서비스 중지 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 서비스 실행 상태 확인 (개선된 버전)
     */
    private fun isServiceRunning(): Boolean {
        return try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)
            
            val serviceName = FloatingButtonService::class.java.name
            val isRunning = runningServices.any { serviceInfo ->
                serviceInfo.service.className == serviceName
            }
            
            Log.d("MainActivity", "서비스 상태 확인: $isRunning")
            Log.d("MainActivity", "실행 중인 서비스 목록:")
            runningServices.forEach { service ->
                if (service.service.className.contains("FloatingButton")) {
                    Log.d("MainActivity", "  - ${service.service.className}")
                }
            }
            
            isRunning
        } catch (e: Exception) {
            Log.e("MainActivity", "서비스 상태 확인 중 오류", e)
            false
        }
    }
    
    /**
     * 서비스 실행 상태를 업데이트하여 UI 재구성 트리거
     */
    private fun updateServiceRunningState() {
        isServiceRunningState = isServiceRunning()
        Log.d("MainActivity", "서비스 실행 상태 업데이트: $isServiceRunningState")
    }

}

@Composable
fun MainScreen(
    onStartServiceClick: () -> Unit,
    onStopServiceClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onOverlayPermissionClick: () -> Unit,
    onAccessibilityPermissionClick: () -> Unit,
    currentUser: UserInfo?,
    hasOverlayPermission: Boolean,
    hasAccessibilityPermission: Boolean,
    isServiceRunning: Boolean
) {
    
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 사용자 정보 표시 (중앙 정렬)
            if (currentUser != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "사용자",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "안녕하세요!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentUser.nickname ?: "사용자",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // 권한 설정 섹션
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "권한 설정",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // 오버레이 권한
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (hasOverlayPermission) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = "오버레이 권한",
                            modifier = Modifier.size(20.dp),
                            tint = if (hasOverlayPermission) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "다른 앱 위에 그리기",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
                        )
                        if (!hasOverlayPermission) {
                            Button(
                                onClick = onOverlayPermissionClick,
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    text = "설정",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    
                    // 접근성 권한
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (hasAccessibilityPermission) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = "접근성 권한",
                            modifier = Modifier.size(20.dp),
                            tint = if (hasAccessibilityPermission) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "접근성 서비스",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                        )
                        if (!hasAccessibilityPermission) {
                            Button(
                                onClick = onAccessibilityPermissionClick,
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    text = "설정",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    
                }
            }

            // 서비스 상태 표시
    Card(
        modifier = Modifier
            .fillMaxWidth()
                    .padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isServiceRunning) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant
                )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
                    Icon(
                        imageVector = if (isServiceRunning) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = "서비스 상태",
                        modifier = Modifier.size(24.dp),
                        tint = if (isServiceRunning) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                Text(
                        text = if (isServiceRunning) "서비스 실행 중" else "서비스 중지됨",
                    style = MaterialTheme.typography.titleMedium,
                        color = if (isServiceRunning) 
                            MaterialTheme.colorScheme.onPrimaryContainer 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 서비스 제어 버튼들
            if (isServiceRunning) {
                Button(
                    onClick = {
                        onStopServiceClick()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                Text(
                        text = "서비스 중지",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            } else {
                Button(
                    onClick = {
                        onStartServiceClick()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        text = "서비스 시작",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 로그아웃 버튼
            OutlinedButton(
                onClick = onLogoutClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
            Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = "로그아웃",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "로그아웃",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
fun LoginScreen(
    onKakaoLoginClick: () -> Unit,
    onEmailLoginClick: (String, String) -> Unit,
    onEmailRegisterClick: (String, String, String, String) -> Unit,
    onOpenRegister: () -> Unit,
    onOpenFindUsername: () -> Unit,
    onOpenFindPassword: () -> Unit,
    onSaveTempConsent: () -> Unit,
    onClearTempConsent: () -> Unit,
    onOpenTermsLink: () -> Unit,
    onOpenPrivacyLink: () -> Unit,
    isKakaoLoginLoading: Boolean = false,
    isEmailLoginLoading: Boolean = false,
    isRegisterLoading: Boolean = false,
    showTermsPopup: Boolean = false,
    onShowTermsPopup: (Boolean) -> Unit,
    initialTermsAgreed: Boolean = false
) {
    var isTermsAgreed by remember { mutableStateOf(initialTermsAgreed) }  // 약관 동의 완료 상태
    var showEmailLogin by remember { mutableStateOf(false) }  // 이메일 로그인 화면 표시 여부
    var showRegister by remember { mutableStateOf(false) }    // 회원가입 화면 표시 여부
    
    // 이메일 로그인 상태
    var emailLogin by remember { mutableStateOf("") }
    var emailPassword by remember { mutableStateOf("") }
    var emailPasswordVisible by remember { mutableStateOf(false) }
    
    // 회원가입 상태
    var registerUsername by remember { mutableStateOf("") }
    var registerEmail by remember { mutableStateOf("") }
    var registerPassword by remember { mutableStateOf("") }
    var registerPasswordVisible by remember { mutableStateOf(false) }
    var registerName by remember { mutableStateOf("") }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            // 스크롤 가능한 콘텐츠 영역
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 120.dp), // 각 방향별로 명시
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                // 상단 여백 추가
                Spacer(modifier = Modifier.height(40.dp))
                
                // 타이틀 이미지 영역
                Image(
                    painter = painterResource(id = R.drawable.blue_and_white_illustration_mail_logo),
                    contentDescription = "앱 로고",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .padding(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 24.dp),
                    contentScale = ContentScale.Fit
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // // 약관 동의 버튼
                // Button(
                //     onClick = { 
                //         // 약관동의 팝업 표시
                //         onShowTermsPopup(true)
                //     },
                //     modifier = Modifier
                //         .fillMaxWidth()
                //         .padding(horizontal = 16.dp),
                //     colors = ButtonDefaults.buttonColors(
                //         containerColor = Color.Transparent
                //     ),
                //     contentPadding = PaddingValues(0.dp),
                //     shape = RoundedCornerShape(12.dp)
                // ) {
                //     Row(
                //         modifier = Modifier
                //             .fillMaxWidth()
                //             .background(
                //                 color = Color(0xFFF5F5F5),
                //                 shape = RoundedCornerShape(12.dp)
                //             )
                //             .padding(16.dp),
                //         verticalAlignment = Alignment.CenterVertically
                //     ) {
                //         Text(
                //             text = "이용약관 및 개인정보처리방침에 동의합니다",
                //             style = MaterialTheme.typography.bodyMedium,
                //             color = Color(0xFF424242)
                //         )
                //         Spacer(modifier = Modifier.weight(1f))
                //         Text(
                //             text = "보기",
                //             style = MaterialTheme.typography.bodySmall,
                //             color = Color(0xFF2196F3)
                //         )
                //     }
                // }
                
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 카카오 로그인 버튼 (가장 위에 배치)
                Button(
                    onClick = onKakaoLoginClick,
                    enabled = isTermsAgreed && !isKakaoLoginLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .alpha(if (isKakaoLoginLoading) 0.6f else 1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    ),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isKakaoLoginLoading) {
                        // 로딩 중일 때는 스피너와 텍스트 표시
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .background(
                                    Color(0xFFFFE812), // 카카오 노란색
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "로그인 중...",
                                color = Color.Black,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .background(
                                    Color(0xFFFFE812), // 카카오 노란색 배경
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.kakao_login_large_wide),
                                contentDescription = "카카오 로그인",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 구분선
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = Color(0xFFE0E0E0)
                    )
                    Text(
                        text = "또는",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF666666)
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = Color(0xFFE0E0E0)
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 아이디/비밀번호 로그인 폼
                if (showEmailLogin || showRegister) {
                    EmailLoginSection(
                        showEmailLogin = showEmailLogin,
                        showRegister = showRegister,
                        emailLogin = emailLogin,
                        emailPassword = emailPassword,
                        emailPasswordVisible = emailPasswordVisible,
                        registerUsername = registerUsername,
                        registerEmail = registerEmail,
                        registerPassword = registerPassword,
                        registerPasswordVisible = registerPasswordVisible,
                        registerName = registerName,
                        isEmailLoginLoading = isEmailLoginLoading,
                        isRegisterLoading = isRegisterLoading,
                        onEmailLoginChange = { emailLogin = it },
                        onEmailPasswordChange = { emailPassword = it },
                        onEmailPasswordVisibleChange = { emailPasswordVisible = it },
                        onRegisterUsernameChange = { registerUsername = it },
                        onRegisterEmailChange = { registerEmail = it },
                        onRegisterPasswordChange = { registerPassword = it },
                        onRegisterPasswordVisibleChange = { registerPasswordVisible = it },
                        onRegisterNameChange = { registerName = it },
                        onEmailLoginClick = { onEmailLoginClick(emailLogin, emailPassword) },
                        onEmailRegisterClick = { 
                            onEmailRegisterClick(registerUsername, registerEmail, registerPassword, registerName) 
                        },
                        onBackClick = { 
                            showEmailLogin = false
                            showRegister = false
                            // 폼 초기화
                            emailLogin = ""
                            emailPassword = ""
                            registerUsername = ""
                            registerEmail = ""
                            registerPassword = ""
                            registerName = ""
                        },
                        onSwitchToRegister = { 
                            showEmailLogin = false
                            showRegister = true
                        },
                        onSwitchToLogin = { 
                            showEmailLogin = true
                            showRegister = false
                        },
                        onOpenRegister = onOpenRegister,
                        onOpenFindPassword = onOpenFindPassword
                    )
                } else {
                    // 기본 아이디/비밀번호 로그인 폼 (항상 표시)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp)
                        ) {
                            // 아이디 입력
                            OutlinedTextField(
                                value = emailLogin,
                                onValueChange = { emailLogin = it },
                                label = { Text("아이디") },
                                leadingIcon = {
                                    Icon(Icons.Default.Person, contentDescription = "아이디")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = !isEmailLoginLoading,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFE0E0E0),
                                    unfocusedBorderColor = Color(0xFFE0E0E0)
                                )
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // 비밀번호 입력
                            OutlinedTextField(
                                value = emailPassword,
                                onValueChange = { emailPassword = it },
                                label = { Text("비밀번호") },
                                leadingIcon = {
                                    Icon(Icons.Default.Lock, contentDescription = "비밀번호")
                                },
                                trailingIcon = {
                                    IconButton(onClick = { emailPasswordVisible = !emailPasswordVisible }) {
                                        Icon(
                                            imageVector = if (emailPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = if (emailPasswordVisible) "비밀번호 숨기기" else "비밀번호 보기"
                                        )
                                    }
                                },
                                visualTransformation = if (emailPasswordVisible) VisualTransformation.None 
                                                     else PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = !isEmailLoginLoading,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFE0E0E0),
                                    unfocusedBorderColor = Color(0xFFE0E0E0)
                                )
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // 로그인 버튼
                            Button(
                                onClick = { onEmailLoginClick(emailLogin, emailPassword) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                enabled = emailLogin.isNotBlank() && emailPassword.isNotBlank() && !isEmailLoginLoading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF9E9E9E)
                                )
                            ) {
                                if (isEmailLoginLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = if (isEmailLoginLoading) "로그인 중..." else "로그인",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(3.dp))
                    
                    // 하단 링크들 (아이디 찾기, 비밀번호 찾기, 회원가입) - 정렬 수정
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { 
                                onOpenFindUsername()
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "아이디 찾기",
                                color = Color(0xFF666666),
                                fontSize = 12.sp
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = "|",
                            color = Color(0xFFCCCCCC),
                            fontSize = 12.sp
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        TextButton(
                            onClick = { 
                                onOpenFindPassword()
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "비밀번호 찾기",
                                color = Color(0xFF666666),
                                fontSize = 12.sp
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = "|",
                            color = Color(0xFFCCCCCC),
                            fontSize = 12.sp
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        TextButton(
                            onClick = { onOpenRegister() },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "회원가입",
                                color = Color(0xFF4CAF50), // 녹색
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                
            }
            
            // 하단 링크 영역 (디바이스 하단에 고정)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 구분선
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    color = Color(0xFFE0E0E0),
                    thickness = 1.dp
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 이용약관 및 개인정보처리방침 링크
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "이용약관",
                        fontSize = 11.sp,
                        color = Color.Black,
                        modifier = Modifier.clickable { onOpenTermsLink() }
                    )
                    Text(
                        text = " 및 ",
                        fontSize = 11.sp,
                        color = Color.Black
                    )
                    Text(
                        text = "개인정보처리방침",
                        fontSize = 11.sp,
                        color = Color.Black,
                        modifier = Modifier.clickable { onOpenPrivacyLink() }
                    )
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                // 저작권 정보
                Text(
                    text = "Copyright © 2025 (주)마인드브이알 | 마브 All rights reserved.",
                    fontSize = 10.sp,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                )
            }
        }
        
        // 약관동의 팝업
        if (showTermsPopup) {
            TermsAgreementPopup(
                onClose = { onShowTermsPopup(false) },
                onSaveTempConsent = onSaveTempConsent,
                onClearTempConsent = onClearTempConsent,
                onOpenTermsLink = onOpenTermsLink,
                onOpenPrivacyLink = onOpenPrivacyLink,
                onAgreementComplete = { isTermsAgreed = true }
            )
        }
        
    }
}

@Composable
fun FloatingButtonAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        typography = Typography(),
        content = content
    )
}

private fun lightColorScheme() = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    inverseOnSurface = Color(0xFFF4EFF4),
    inverseSurface = Color(0xFF313033),
    inversePrimary = Color(0xFFD0BCFF),
    surfaceTint = Color(0xFF6750A4),
    outlineVariant = Color(0xFFCAC4D0),
    scrim = Color(0xFF000000)
)

private fun Typography() = Typography(
    displayLarge = androidx.compose.ui.text.TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = androidx.compose.ui.text.TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = androidx.compose.ui.text.TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = androidx.compose.ui.text.TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = androidx.compose.ui.text.TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = androidx.compose.ui.text.TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = androidx.compose.ui.text.TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = androidx.compose.ui.text.TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = androidx.compose.ui.text.TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = androidx.compose.ui.text.TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    bodyMedium = androidx.compose.ui.text.TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = androidx.compose.ui.text.TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = androidx.compose.ui.text.TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = androidx.compose.ui.text.TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = androidx.compose.ui.text.TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

/**
 * 이메일 로그인/회원가입 섹션 컴포넌트
 */
@Composable
fun EmailLoginSection(
    showEmailLogin: Boolean,
    showRegister: Boolean,
    emailLogin: String,
    emailPassword: String,
    emailPasswordVisible: Boolean,
    registerUsername: String,
    registerEmail: String,
    registerPassword: String,
    registerPasswordVisible: Boolean,
    registerName: String,
    isEmailLoginLoading: Boolean,
    isRegisterLoading: Boolean,
    onEmailLoginChange: (String) -> Unit,
    onEmailPasswordChange: (String) -> Unit,
    onEmailPasswordVisibleChange: (Boolean) -> Unit,
    onRegisterUsernameChange: (String) -> Unit,
    onRegisterEmailChange: (String) -> Unit,
    onRegisterPasswordChange: (String) -> Unit,
    onRegisterPasswordVisibleChange: (Boolean) -> Unit,
    onRegisterNameChange: (String) -> Unit,
    onEmailLoginClick: () -> Unit,
    onEmailRegisterClick: () -> Unit,
    onBackClick: () -> Unit,
    onSwitchToRegister: () -> Unit,
    onSwitchToLogin: () -> Unit,
    onOpenRegister: () -> Unit,
    onOpenFindPassword: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 헤더 (뒤로가기 버튼과 제목)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "닫기",
                    tint = Color(0xFF666666)
                )
            }
            Text(
                text = if (showRegister) "회원가입" else "이메일 로그인",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (showEmailLogin) {
            // 이메일 로그인 폼을 카드 형태로 래핑
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // 아이디 입력
                    OutlinedTextField(
                        value = emailLogin,
                        onValueChange = onEmailLoginChange,
                        label = { Text("아이디") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = "아이디")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isEmailLoginLoading,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFE0E0E0),
                            unfocusedBorderColor = Color(0xFFE0E0E0)
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 비밀번호 입력
                    OutlinedTextField(
                        value = emailPassword,
                        onValueChange = onEmailPasswordChange,
                        label = { Text("비밀번호") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = "비밀번호")
                        },
                        trailingIcon = {
                            IconButton(onClick = { onEmailPasswordVisibleChange(!emailPasswordVisible) }) {
                                Icon(
                                    imageVector = if (emailPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (emailPasswordVisible) "비밀번호 숨기기" else "비밀번호 보기"
                                )
                            }
                        },
                        visualTransformation = if (emailPasswordVisible) VisualTransformation.None 
                                             else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isEmailLoginLoading,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFE0E0E0),
                            unfocusedBorderColor = Color(0xFFE0E0E0)
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 로그인 버튼
                    Button(
                        onClick = onEmailLoginClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = emailLogin.isNotBlank() && emailPassword.isNotBlank() && !isEmailLoginLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF9E9E9E)
                        )
                    ) {
                        if (isEmailLoginLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = if (isEmailLoginLoading) "로그인 중..." else "로그인",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 하단 링크들 (아이디 찾기, 비밀번호 찾기, 회원가입) - 정렬 수정
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { 
                        // 아이디 찾기 기능 (추후 구현)
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "아이디 찾기",
                        color = Color(0xFF666666),
                        fontSize = 12.sp
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "|",
                    color = Color(0xFFCCCCCC),
                    fontSize = 12.sp
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                TextButton(
                    onClick = { 
                        onOpenFindPassword()
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "비밀번호 찾기",
                        color = Color(0xFF666666),
                        fontSize = 12.sp
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "|",
                    color = Color(0xFFCCCCCC),
                    fontSize = 12.sp
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                TextButton(
                    onClick = onOpenRegister,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "회원가입",
                        color = Color(0xFF4CAF50), // 녹색
                        fontSize = 12.sp
                    )
                }
            }
        }
        
        if (showRegister) {
            // 회원가입 폼을 카드 형태로 래핑
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    OutlinedTextField(
                        value = registerUsername,
                        onValueChange = onRegisterUsernameChange,
                        label = { Text("아이디") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = "아이디")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isRegisterLoading,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFE0E0E0),
                            unfocusedBorderColor = Color(0xFFE0E0E0)
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = registerEmail,
                        onValueChange = onRegisterEmailChange,
                        label = { Text("이메일") },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = "이메일")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isRegisterLoading,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFE0E0E0),
                            unfocusedBorderColor = Color(0xFFE0E0E0)
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = registerName,
                        onValueChange = onRegisterNameChange,
                        label = { Text("이름") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = "이름")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isRegisterLoading,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFE0E0E0),
                            unfocusedBorderColor = Color(0xFFE0E0E0)
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = registerPassword,
                        onValueChange = onRegisterPasswordChange,
                        label = { Text("비밀번호") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = "비밀번호")
                        },
                        trailingIcon = {
                            IconButton(onClick = { onRegisterPasswordVisibleChange(!registerPasswordVisible) }) {
                                Icon(
                                    imageVector = if (registerPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (registerPasswordVisible) "비밀번호 숨기기" else "비밀번호 보기"
                                )
                            }
                        },
                        visualTransformation = if (registerPasswordVisible) VisualTransformation.None 
                                             else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isRegisterLoading,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFE0E0E0),
                            unfocusedBorderColor = Color(0xFFE0E0E0)
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 회원가입 버튼
                    Button(
                        onClick = onEmailRegisterClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = registerUsername.isNotBlank() && registerEmail.isNotBlank() && 
                                 registerName.isNotBlank() && registerPassword.isNotBlank() && !isRegisterLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF9E9E9E)
                        )
                    ) {
                        if (isRegisterLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = if (isRegisterLoading) "회원가입 중..." else "회원가입",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 하단 링크들 (아이디 찾기, 비밀번호 찾기, 로그인하기) - 정렬 수정
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { 
                        // 아이디 찾기 기능 (추후 구현)
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "아이디 찾기",
                        color = Color(0xFF666666),
                        fontSize = 12.sp
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "|",
                    color = Color(0xFFCCCCCC),
                    fontSize = 12.sp
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                TextButton(
                    onClick = { 
                        onOpenFindPassword()
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "비밀번호 찾기",
                        color = Color(0xFF666666),
                        fontSize = 12.sp
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "|",
                    color = Color(0xFFCCCCCC),
                    fontSize = 12.sp
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                TextButton(
                    onClick = onSwitchToLogin,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "로그인하기",
                        color = Color(0xFF4CAF50), // 녹색
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/**
 * 아이디 찾기 화면
 */
@Composable
fun FindUsernameScreen(
    onFindUsernameClick: (email: String) -> Unit,
    onBackClick: () -> Unit,
    isFindUsernameLoading: Boolean
) {
    var email by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf("") }
    
    // 이메일 형식 검증
    fun validateEmail(inputEmail: String): Boolean {
        val emailRegex = "^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$".toRegex()
        return emailRegex.matches(inputEmail.trim())
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 상단 바
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "뒤로가기",
                    tint = Color(0xFF666666)
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = "아이디 찾기",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF333333)
            )
        }
        
        Spacer(modifier = Modifier.height(60.dp))
        
        // 제목
        Text(
            text = "아이디를 잊으셨나요?",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF333333),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 설명
        Text(
            text = "가입하신 이메일 주소를 입력해주세요.\n입력하신 이메일로 아이디를 발송해드립니다.",
            fontSize = 14.sp,
            color = Color(0xFF666666),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        
        Spacer(modifier = Modifier.height(40.dp))
        
        // 이메일 입력 필드
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // 이메일 입력
                OutlinedTextField(
                    value = email,
                    onValueChange = { 
                        email = it
                        emailError = ""
                        // 실시간 검증
                        if (it.isNotEmpty() && !validateEmail(it)) {
                            emailError = "올바른 이메일 형식을 입력해주세요"
                        }
                    },
                    label = { Text("이메일 주소") },
                    leadingIcon = {
                        Icon(Icons.Default.Email, contentDescription = "이메일")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isFindUsernameLoading,
                    isError = emailError.isNotEmpty(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (emailError.isNotEmpty()) Color.Red else Color(0xFFE0E0E0),
                        unfocusedBorderColor = if (emailError.isNotEmpty()) Color.Red else Color(0xFFE0E0E0)
                    )
                )
                
                // 에러 메시지
                if (emailError.isNotEmpty()) {
                    Text(
                        text = emailError,
                        color = Color.Red,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 아이디 찾기 버튼
                Button(
                    onClick = { 
                        if (email.isBlank()) {
                            emailError = "이메일을 입력해주세요"
                        } else if (!validateEmail(email)) {
                            emailError = "올바른 이메일 형식을 입력해주세요"
                        } else {
                            onFindUsernameClick(email)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = email.isNotBlank() && emailError.isEmpty() && !isFindUsernameLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (email.isNotBlank() && emailError.isEmpty()) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                    )
                ) {
                    if (isFindUsernameLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (isFindUsernameLoading) "발송 중..." else "아이디 찾기",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * 비밀번호 찾기 화면
 */
@Composable
fun FindPasswordScreen(
    onRequestPasswordReset: (email: String, onResult: (Boolean) -> Unit) -> Unit,
    onResetPassword: (token: String, newPassword: String) -> Unit,
    onBackClick: () -> Unit,
    isPasswordResetLoading: Boolean
) {
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(0) } // 0: 이메일 입력, 1: 토큰과 새 비밀번호 입력
    var email by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf("") }
    var confirmPasswordError by remember { mutableStateOf("") }
    
    // 이메일 형식 검증
    fun validateEmail(inputEmail: String): Boolean {
        val emailRegex = "^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$".toRegex()
        return emailRegex.matches(inputEmail.trim())
    }
    
    // 비밀번호 검증
    fun validatePassword(inputPassword: String): com.mv.toki.utils.ValidationResult {
        return com.mv.toki.utils.PasswordValidator.validatePassword(inputPassword)
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 상단 바
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "뒤로가기",
                    tint = Color(0xFF666666)
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = "비밀번호 찾기",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF333333)
            )
        }
        
        Spacer(modifier = Modifier.height(60.dp))
        
        if (currentStep == 0) {
            // 1단계: 이메일 입력
            Text(
                text = "비밀번호를 잊으셨나요?",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "가입하신 이메일 주소를 입력해주세요.\n입력하신 이메일로 비밀번호 재설정 링크를 발송해드립니다.",
                fontSize = 14.sp,
                color = Color(0xFF666666),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // 이메일 입력 필드
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { 
                            email = it
                            emailError = ""
                            if (it.isNotEmpty() && !validateEmail(it)) {
                                emailError = "올바른 이메일 형식을 입력해주세요"
                            }
                        },
                        label = { Text("이메일 주소") },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = "이메일")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isPasswordResetLoading,
                        isError = emailError.isNotEmpty(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (emailError.isNotEmpty()) Color.Red else Color(0xFFE0E0E0),
                            unfocusedBorderColor = if (emailError.isNotEmpty()) Color.Red else Color(0xFFE0E0E0)
                        )
                    )
                    
                    if (emailError.isNotEmpty()) {
                        Text(
                            text = emailError,
                            color = Color.Red,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(
                        onClick = { 
                            if (email.isBlank()) {
                                emailError = "이메일을 입력해주세요"
                            } else if (!validateEmail(email)) {
                                emailError = "올바른 이메일 형식을 입력해주세요"
                            } else {
                                onRequestPasswordReset(email) { success ->
                                    if (success) {
                                        currentStep = 1
                                    }
                                    // 실패 시에는 currentStep을 변경하지 않음
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = email.isNotBlank() && emailError.isEmpty() && !isPasswordResetLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (email.isNotBlank() && emailError.isEmpty()) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                        )
                    ) {
                        if (isPasswordResetLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = if (isPasswordResetLoading) "발송 중..." else "재설정 링크 발송",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } else {
            // 2단계: 토큰과 새 비밀번호 입력
            Text(
                text = "새 비밀번호 설정",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "이메일로 받은 토큰과 새로운 비밀번호를 입력해주세요.",
                fontSize = 14.sp,
                color = Color(0xFF666666),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // 토큰 입력
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("인증 토큰") },
                        leadingIcon = {
                            Icon(Icons.Default.Security, contentDescription = "토큰")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isPasswordResetLoading,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFE0E0E0),
                            unfocusedBorderColor = Color(0xFFE0E0E0)
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 새 비밀번호 입력
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { 
                            newPassword = it
                            passwordError = ""
                            val validation = validatePassword(it)
                            if (it.isNotEmpty() && validation is com.mv.toki.utils.ValidationResult.Error) {
                                passwordError = validation.message
                            }
                        },
                        label = { Text("새 비밀번호") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = "비밀번호")
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "비밀번호 숨기기" else "비밀번호 보기"
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isPasswordResetLoading,
                        isError = passwordError.isNotEmpty(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (passwordError.isNotEmpty()) Color.Red else Color(0xFFE0E0E0),
                            unfocusedBorderColor = if (passwordError.isNotEmpty()) Color.Red else Color(0xFFE0E0E0)
                        )
                    )
                    
                    if (passwordError.isNotEmpty()) {
                        Text(
                            text = passwordError,
                            color = Color.Red,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 비밀번호 확인 입력
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { 
                            confirmPassword = it
                            confirmPasswordError = ""
                            if (it.isNotEmpty() && it != newPassword) {
                                confirmPasswordError = "비밀번호가 일치하지 않습니다"
                            }
                        },
                        label = { Text("비밀번호 확인") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = "비밀번호 확인")
                        },
                        trailingIcon = {
                            IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                Icon(
                                    imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (confirmPasswordVisible) "비밀번호 숨기기" else "비밀번호 보기"
                                )
                            }
                        },
                        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isPasswordResetLoading,
                        isError = confirmPasswordError.isNotEmpty(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (confirmPasswordError.isNotEmpty()) Color.Red else Color(0xFFE0E0E0),
                            unfocusedBorderColor = if (confirmPasswordError.isNotEmpty()) Color.Red else Color(0xFFE0E0E0)
                        )
                    )
                    
                    if (confirmPasswordError.isNotEmpty()) {
                        Text(
                            text = confirmPasswordError,
                            color = Color.Red,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(
                        onClick = { 
                            val isValidToken = token.isNotBlank()
                            val isValidPassword = newPassword.isNotBlank() && passwordError.isEmpty()
                            val isPasswordMatch = confirmPassword == newPassword && confirmPassword.isNotBlank()
                            
                            if (!isValidToken) {
                                Toast.makeText(context, "토큰을 입력해주세요", Toast.LENGTH_SHORT).show()
                            } else if (!isValidPassword) {
                                Toast.makeText(context, "올바른 비밀번호를 입력해주세요", Toast.LENGTH_SHORT).show()
                            } else if (!isPasswordMatch) {
                                Toast.makeText(context, "비밀번호가 일치하지 않습니다", Toast.LENGTH_SHORT).show()
                            } else {
                                onResetPassword(token, newPassword)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = token.isNotBlank() && newPassword.isNotBlank() && confirmPassword.isNotBlank() && 
                                passwordError.isEmpty() && confirmPasswordError.isEmpty() && !isPasswordResetLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        )
                    ) {
                        if (isPasswordResetLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = if (isPasswordResetLoading) "재설정 중..." else "비밀번호 재설정",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
