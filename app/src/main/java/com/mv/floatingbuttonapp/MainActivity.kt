package com.mv.floatingbuttonapp

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

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
    
    
    // 구글 로그인 결과 처리
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            lifecycleScope.launch {
                try {
                    val loginResult = googleLoginManager.handleSignInResult(result.data)
                    loginResult.onSuccess { loginData ->
                        isLoggedIn = true
                        currentUser = UserInfo(
                            userId = loginData.userId,
                            nickname = loginData.nickname,
                            profileImageUrl = loginData.profileImageUrl,
                            email = loginData.email
                        )
                        Toast.makeText(this@MainActivity, "구글 로그인 성공: ${loginData.nickname}", Toast.LENGTH_SHORT).show()
                        Log.d("MainActivity", "구글 로그인 성공: ${loginData.nickname}")
                    }.onFailure { error ->
                        Toast.makeText(this@MainActivity, "구글 로그인 실패: ${error.message}", Toast.LENGTH_SHORT).show()
                        Log.e("MainActivity", "구글 로그인 실패", error)
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "구글 로그인 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("MainActivity", "구글 로그인 오류", e)
                }
            }
        } else {
            Log.d("MainActivity", "구글 로그인 취소됨")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 로그인 매니저 초기화
        kakaoLoginManager = KakaoLoginManager(this)
        googleLoginManager = GoogleLoginManager(this)
        
        // 카카오 SDK 초기화
        kakaoLoginManager.initializeKakaoSdk()
        
        // 권한 상태 확인
        checkPermissions()
        
        // 자동 로그인 확인
        checkAutoLogin()
        

        setContent {
            FloatingButtonAppTheme {
                if (isLoggedIn) {
                    // 로그인된 경우: 권한 관리 화면
                    MainScreen(
                        onStartServiceClick = {
                            if (checkAllPermissionsAndStart()) {
                                startFloatingService()
                            } else {
                                Toast.makeText(this@MainActivity, "필요한 권한을 먼저 설정해주세요.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onStopServiceClick = {
                            stopFloatingService()
                        },
                        onLogoutClick = { logoutFromKakao() },
                        onOverlayPermissionClick = { requestOverlayPermission() },
                        onAccessibilityPermissionClick = { requestAccessibilityPermission() },
                        currentUser = currentUser,
                        hasOverlayPermission = hasOverlayPermission,
                        hasAccessibilityPermission = hasAccessibilityPermission,
                        isServiceRunning = isServiceRunning()
                    )
                } else {
                    // 로그인되지 않은 경우: 로그인 화면
                    LoginScreen(
                        onKakaoLoginClick = { loginWithKakao() },
                        onGoogleLoginClick = { loginWithGoogle() },
                        onTestLoginClick = { loginWithTest() }
                    )
                }
            }
        }
    }


    override fun onResume() {
        super.onResume()
        // 앱이 다시 활성화될 때 권한 상태 확인
        checkPermissions()
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
    
    override fun onDestroy() {
        super.onDestroy()
    }
    
    /**
     * 자동 로그인 확인
     */
    private fun checkAutoLogin() {
        lifecycleScope.launch {
            try {
                // 카카오 자동 로그인 먼저 시도
                val kakaoResult = kakaoLoginManager.checkAutoLogin()
                kakaoResult.onSuccess { loginResult ->
                    isLoggedIn = true
                    currentUser = UserInfo(
                        userId = loginResult.userId,
                        nickname = loginResult.nickname,
                        profileImageUrl = loginResult.profileImageUrl,
                        email = loginResult.email
                    )
                    Log.d("MainActivity", "카카오 자동 로그인 성공: ${loginResult.nickname}")
                }.onFailure {
                    // 카카오 자동 로그인 실패 시 구글 자동 로그인 시도
                    val googleResult = googleLoginManager.checkAutoLogin()
                    googleResult.onSuccess { loginResult ->
                        isLoggedIn = true
                        currentUser = UserInfo(
                            userId = loginResult.userId,
                            nickname = loginResult.nickname,
                            profileImageUrl = loginResult.profileImageUrl,
                            email = loginResult.email
                        )
                        Log.d("MainActivity", "구글 자동 로그인 성공: ${loginResult.nickname}")
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "자동 로그인 확인 중 오류", e)
            }
        }
    }
    
    /**
     * 카카오 로그인 실행
     */
    private fun loginWithKakao() {
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
                    Toast.makeText(this@MainActivity, "카카오 로그인 성공: ${loginResult.nickname}", Toast.LENGTH_SHORT).show()
                    Log.d("MainActivity", "카카오 로그인 성공: ${loginResult.nickname}")
                }.onFailure { error ->
                    Toast.makeText(this@MainActivity, "카카오 로그인 실패: ${error.message}", Toast.LENGTH_SHORT).show()
                    Log.e("MainActivity", "카카오 로그인 실패", error)
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "카카오 로그인 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("MainActivity", "카카오 로그인 오류", e)
            }
        }
    }
    
    /**
     * 구글 로그인 실행
     */
    private fun loginWithGoogle() {
        try {
            val signInIntent = googleLoginManager.getSignInIntent()
            googleSignInLauncher.launch(signInIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "구글 로그인 오류: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("MainActivity", "구글 로그인 오류", e)
        }
    }
    
    /**
     * 테스트용 임시 로그인 실행
     */
    private fun loginWithTest() {
        try {
            // 테스트용 임시 사용자 정보로 로그인
            isLoggedIn = true
            currentUser = UserInfo(
                userId = "test_user_001",
                nickname = "테스트 사용자",
                profileImageUrl = null,
                email = "test@example.com"
            )
            Toast.makeText(this, "테스트 로그인 성공: ${currentUser?.nickname}", Toast.LENGTH_SHORT).show()
            Log.d("MainActivity", "테스트 로그인 성공: ${currentUser?.nickname}")
        } catch (e: Exception) {
            Toast.makeText(this, "테스트 로그인 오류: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("MainActivity", "테스트 로그인 오류", e)
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
            
            // 서비스 시작 후 UI 상태 업데이트를 위해 약간의 지연 후 권한 상태 재확인
            lifecycleScope.launch {
                delay(500) // 서비스 시작 완료를 위한 짧은 지연
                checkPermissions() // 권한 상태 재확인으로 UI 업데이트 트리거
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "서비스 시작 중 오류", e)
            Toast.makeText(this, "서비스 시작 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    

    private fun checkAllPermissionsAndStart(): Boolean {
        return checkOverlayPermission() && checkAccessibilityPermission()
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
            val intent = Intent(this, FloatingButtonService::class.java)
            stopService(intent)
            Log.d("MainActivity", "서비스 중지 요청됨")
            Toast.makeText(this, "서비스가 중지되었습니다", Toast.LENGTH_SHORT).show()
            
            // 서비스 중지 후 UI 상태 업데이트를 위해 약간의 지연 후 권한 상태 재확인
            lifecycleScope.launch {
                delay(500) // 서비스 중지 완료를 위한 짧은 지연
                checkPermissions() // 권한 상태 재확인으로 UI 업데이트 트리거
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "서비스 중지 중 오류", e)
            Toast.makeText(this, "서비스 중지 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 서비스 실행 상태 확인
     */
    private fun isServiceRunning(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)
        return runningServices.any { it.service.className == FloatingButtonService::class.java.name }
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 사용자 정보 표시
            if (currentUser != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "사용자",
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "안녕하세요!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                text = currentUser.nickname ?: "사용자",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
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
    onGoogleLoginClick: () -> Unit,
    onTestLoginClick: () -> Unit
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
            // 앱 로고/아이콘 영역
            Card(
                modifier = Modifier
                    .size(120.dp)
                    .padding(bottom = 32.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "앱 로고",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            
            // 앱 제목
            Text(
                text = "플로팅 버튼 앱",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // 앱 설명
            Text(
                text = "스마트한 텍스트 인식과 AI 답변 추천을 위한\n플로팅 버튼 서비스입니다",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 48.dp)
            )
            
            // 구분선
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
                Text(
                    text = "또는",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            }
            
            // 카카오 로그인 버튼
            Card(
                onClick = onKakaoLoginClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFE812) // 카카오 노란색
                ),
                shape = MaterialTheme.shapes.medium,
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    // 카카오 아이콘 (말풍선 모양)
                    Box(
                        modifier = Modifier
                            .size(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "💬",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Text(
                        text = "카카오톡 계정으로 로그인",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Black,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 구글 로그인 버튼
            Card(
                onClick = onGoogleLoginClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                shape = MaterialTheme.shapes.medium,
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    // 구글 아이콘 (G 로고)
                    Box(
                        modifier = Modifier
                            .size(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "G",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFF4285F4),
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Text(
                        text = "구글 계정으로 로그인",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF333333),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 테스트 로그인 버튼 (개발/테스트용)
            Card(
                onClick = onTestLoginClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF9C27B0) // 보라색으로 테스트 버튼임을 표시
                ),
                shape = MaterialTheme.shapes.medium,
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    // 테스트 아이콘 (T 로고)
                    Box(
                        modifier = Modifier
                            .size(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "T",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Text(
                        text = "테스트 로그인 (개발용)",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 이용약관 및 개인정보처리방침 동의 텍스트
            Text(
                text = "회원가입 없이 이용 가능하며 첫 로그인시 이용약관 및 개인정보처리방침 동의로 간주됩니다.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF666666),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 이용약관 및 개인정보처리방침 링크
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "이용약관",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4285F4),
                    modifier = Modifier.clickable { /* 이용약관 링크 */ }
                )
                Text(
                    text = " 및 ",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF666666)
                )
                Text(
                    text = "개인정보처리방침",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4285F4),
                    modifier = Modifier.clickable { /* 개인정보처리방침 링크 */ }
                )
            }
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