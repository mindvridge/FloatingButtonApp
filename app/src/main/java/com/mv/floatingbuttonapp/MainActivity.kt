package com.mv.floatingbuttonapp

import android.app.Activity
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.mv.floatingbuttonapp.ui.theme.FloatingButtonAppTheme
import com.mv.floatingbuttonapp.api.ApiClient
import com.mv.floatingbuttonapp.api.ReplyRequest
import android.util.Log

// 권한 정보를 담는 데이터 클래스
data class PermissionInfo(
    val name: String,
    val description: String,
    val isGranted: Boolean,
    val onToggle: () -> Unit
)

class MainActivity : ComponentActivity() {

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    // MediaProjection 권한 요청 런처
    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                FloatingButtonService.mediaProjectionResultCode = result.resultCode
                // clone()을 사용하여 안전하게 Intent 복사
                FloatingButtonService.mediaProjectionResultData = data.clone() as Intent

                Toast.makeText(this, "화면 캡처 권한이 허용되었습니다", Toast.LENGTH_SHORT).show()

                // 서비스를 재시작하는 대신, 시작만 하여 onStartCommand를 호출합니다.
                startFloatingService()
            }
        } else {
            Toast.makeText(
                this,
                "화면 캡처 권한이 필요합니다",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // 오버레이 권한 요청 결과를 처리하는 런처
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkAndStartService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        createNotificationChannel()

        // 서비스에서 MediaProjection 요청이 온 경우
        if (intent?.action == "REQUEST_MEDIA_PROJECTION") {
            requestMediaProjection()
        }

        setContent {
            FloatingButtonAppTheme {
                MainScreen(
                    onStartServiceClick = {
                        checkAllPermissionsAndStart()
                    },
                    onStopServiceClick = {
                        stopFloatingService()
                    },
                    onTestApiClick = {
                        testApiResponseTime()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.action == "REQUEST_MEDIA_PROJECTION") {
            requestMediaProjection()
        }
    }

    private fun checkAllPermissionsAndStart() {
        when {
            // 1. 오버레이 권한 확인
            !hasOverlayPermission() -> {
                Toast.makeText(
                    this,
                    "다른 앱 위에 표시 권한이 필요합니다",
                    Toast.LENGTH_LONG
                ).show()
                requestOverlayPermission()
            }

            // 2. 접근성 권한 확인
            !isAccessibilityServiceEnabled() -> {
                Toast.makeText(
                    this,
                    "키보드 감지를 위해 접근성 권한이 필요합니다",
                    Toast.LENGTH_LONG
                ).show()
                openAccessibilitySettings()
            }

            // 3. 알림 권한 확인 (Android 13+)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !hasNotificationPermission() -> {
                requestNotificationPermission()
            }

            // 4. MediaProjection 권한 요청 (화면 캡처용)
            FloatingButtonService.mediaProjectionResultData == null -> {
                requestMediaProjection()
            }

            // 5. 모든 권한이 있으면 서비스 시작
            else -> {
                startFloatingService()
            }
        }
    }

    fun requestMediaProjection() {
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        mediaProjectionLauncher.launch(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "floating_button_channel",
                "플로팅 버튼 서비스",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "플로팅 버튼이 실행 중일 때 표시되는 알림"
                setSound(null, null)
                enableVibration(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
    }

    fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = "$packageName/${KeyboardDetectionAccessibilityService::class.java.name}"

        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(':').any { componentName ->
            componentName.equals(expectedComponentName, ignoreCase = true)
        }
    }

    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
        }
    }

    // API 응답 시간 테스트 함수
    fun testApiResponseTime() {
        lifecycleScope.launch {
            try {
                val testRequest = ReplyRequest(
                    대상자 = "친구",
                    답변모드 = "친근하게", 
                    답변길이 = "짧게",
                    대화내용 = "안녕하세요! 오늘 날씨가 정말 좋네요.",
                    추가지침 = "",
                    모델 = "gemini-2.5-flash"
                )
                
                val startTime = System.currentTimeMillis()
                Log.d("API_TEST", "=== API 테스트 시작 ===")
                Log.d("API_TEST", "시작 시간: ${java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date(startTime))}")
                
                val response = ApiClient.apiService.getReplies(testRequest)
                
                val endTime = System.currentTimeMillis()
                val responseTime = endTime - startTime
                
                Log.d("API_TEST", "=== API 테스트 완료 ===")
                Log.d("API_TEST", "완료 시간: ${java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date(endTime))}")
                Log.d("API_TEST", "응답 시간: ${responseTime}ms (${responseTime / 1000.0}초)")
                Log.d("API_TEST", "응답 코드: ${response.code()}")
                Log.d("API_TEST", "응답 성공: ${response.isSuccessful}")
                
                if (response.isSuccessful) {
                    val responseBody = response.body()
                    Log.d("API_TEST", "응답 내용: $responseBody")
                    Toast.makeText(this@MainActivity, "API 테스트 성공! 응답시간: ${responseTime}ms", Toast.LENGTH_LONG).show()
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("API_TEST", "API 오류: ${response.code()} - $errorBody")
                    Toast.makeText(this@MainActivity, "API 테스트 실패: ${response.code()}", Toast.LENGTH_LONG).show()
                }
                
            } catch (e: Exception) {
                Log.e("API_TEST", "API 테스트 예외: ${e.message}", e)
                Toast.makeText(this@MainActivity, "API 테스트 오류: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(android.Manifest.permission.CAMERA),
            1002
        )
    }

    private fun checkAndStartService() {
        if (hasOverlayPermission()) {
            if (hasNotificationPermission()) {
                if (FloatingButtonService.mediaProjectionResultData != null) {
                    startFloatingService()
                } else {
                    requestMediaProjection()
                }
            } else {
                requestNotificationPermission()
            }
        } else {
            Toast.makeText(
                this,
                "오버레이 권한이 필요합니다",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun updateFloatingService() {
        // 서비스가 실행 중이면 MediaProjection 업데이트
        val serviceIntent = Intent(this, FloatingButtonService::class.java)
        val serviceRunning = isServiceRunning(FloatingButtonService::class.java)
        
        if (serviceRunning) {
            // 서비스에 MediaProjection 업데이트 요청
            serviceIntent.action = "UPDATE_MEDIA_PROJECTION"
            startService(serviceIntent)
        } else {
            // 서비스가 실행 중이 아니면 시작
            startFloatingService()
        }
    }

    private fun restartFloatingService() {
        // 기존 서비스 중지
        val stopIntent = Intent(this, FloatingButtonService::class.java)
        stopService(stopIntent)
        
        // 잠시 대기 후 새로 시작
        handler.postDelayed({
            startFloatingService()
        }, 500)
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val services = activityManager.getRunningServices(Integer.MAX_VALUE)
        
        for (serviceInfo in services) {
            if (serviceClass.name == serviceInfo.service.className) {
                return true
            }
        }
        return false
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingButtonService::class.java)
        ContextCompat.startForegroundService(this, intent)
        Toast.makeText(this, "플로팅 버튼이 활성화되었습니다", Toast.LENGTH_SHORT).show()
    }

    private fun stopFloatingService() {
        val intent = Intent(this, FloatingButtonService::class.java)
        stopService(intent)

        // MediaProjection 데이터 초기화
        FloatingButtonService.mediaProjectionResultData = null
        FloatingButtonService.mediaProjectionResultCode = 0

        Toast.makeText(this, "플로팅 버튼이 비활성화되었습니다", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            1001 -> { // 알림 권한
                if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "알림 권한이 허용되었습니다", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "알림 권한이 거부되었습니다", Toast.LENGTH_SHORT).show()
                }
            }
            1002 -> { // 카메라 권한
                if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "카메라 권한이 허용되었습니다", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "카메라 권한이 거부되었습니다", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    onStartServiceClick: () -> Unit,
    onStopServiceClick: () -> Unit,
    onTestApiClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as MainActivity
    
    // 각 권한의 상태를 실시간으로 확인하는 상태
    var overlayPermission by remember { mutableStateOf(activity.hasOverlayPermission()) }
    var accessibilityPermission by remember { mutableStateOf(activity.isAccessibilityServiceEnabled()) }
    var notificationPermission by remember { mutableStateOf(activity.hasNotificationPermission()) }
    var mediaProjectionPermission by remember { mutableStateOf(FloatingButtonService.mediaProjectionResultData != null) }
    var cameraPermission by remember { mutableStateOf(activity.hasCameraPermission()) }
    
    // 권한 상태를 주기적으로 업데이트
    LaunchedEffect(Unit) {
        while (true) {
            overlayPermission = activity.hasOverlayPermission()
            accessibilityPermission = activity.isAccessibilityServiceEnabled()
            notificationPermission = activity.hasNotificationPermission()
            mediaProjectionPermission = FloatingButtonService.mediaProjectionResultData != null
            cameraPermission = activity.hasCameraPermission()
            kotlinx.coroutines.delay(1000) // 1초마다 업데이트
        }
    }
    
    // 권한 목록 생성
    val permissions = listOf(
        PermissionInfo(
            name = "오버레이 권한",
            description = "다른 앱 위에 플로팅 버튼을 표시하기 위한 권한",
            isGranted = overlayPermission,
            onToggle = {
                if (!overlayPermission) {
                    activity.requestOverlayPermission()
                } else {
                    Toast.makeText(context, "오버레이 권한은 설정에서만 해제할 수 있습니다", Toast.LENGTH_SHORT).show()
                }
            }
        ),
        PermissionInfo(
            name = "접근성 서비스",
            description = "키보드 감지를 위한 접근성 서비스 권한",
            isGranted = accessibilityPermission,
            onToggle = {
                if (!accessibilityPermission) {
                    activity.openAccessibilitySettings()
                } else {
                    Toast.makeText(context, "접근성 서비스는 설정에서만 해제할 수 있습니다", Toast.LENGTH_SHORT).show()
                }
            }
        ),
        PermissionInfo(
            name = "알림 권한",
            description = "Android 13+ 알림 표시를 위한 권한",
            isGranted = notificationPermission,
            onToggle = {
                if (!notificationPermission) {
                    activity.requestNotificationPermission()
                } else {
                    Toast.makeText(context, "알림 권한은 설정에서만 해제할 수 있습니다", Toast.LENGTH_SHORT).show()
                }
            }
        ),
        PermissionInfo(
            name = "화면 캡처 권한",
            description = "화면을 캡처하여 OCR을 수행하기 위한 권한",
            isGranted = mediaProjectionPermission,
            onToggle = {
                if (!mediaProjectionPermission) {
                    activity.requestMediaProjection()
                } else {
                    // MediaProjection 권한은 재요청으로 해제
                    FloatingButtonService.mediaProjectionResultData = null
                    FloatingButtonService.mediaProjectionResultCode = 0
                    Toast.makeText(context, "화면 캡처 권한이 해제되었습니다", Toast.LENGTH_SHORT).show()
                }
            }
        ),
        PermissionInfo(
            name = "카메라 권한",
            description = "OCR 기능을 위한 카메라 권한",
            isGranted = cameraPermission,
            onToggle = {
                if (!cameraPermission) {
                    activity.requestCameraPermission()
                } else {
                    Toast.makeText(context, "카메라 권한은 설정에서만 해제할 수 있습니다", Toast.LENGTH_SHORT).show()
                }
            }
        )
    )
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // 헤더
            Text(
                text = "플로팅 버튼 권한 관리",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
            
            Text(
                text = "각 권한을 개별적으로 관리할 수 있습니다. 권한이 승인되면 토글이 체크됩니다.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            )
            
            // 권한 목록
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(permissions) { permission ->
                    PermissionToggleCard(
                        permission = permission,
                        onToggle = permission.onToggle
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 서비스 제어 버튼들
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onStartServiceClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("서비스 시작")
                }
                
                OutlinedButton(
                    onClick = onStopServiceClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("서비스 중지")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // API 테스트 버튼
            Button(
                onClick = onTestApiClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("API 응답 시간 테스트")
            }
        }
    }
}

@Composable
fun PermissionToggleCard(
    permission: PermissionInfo,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }, // 전체 카드를 클릭 가능하게 변경
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = permission.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (permission.isGranted) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = permission.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            // 상태 표시 아이콘 (클릭 불가능)
            Icon(
                imageVector = if (permission.isGranted) Icons.Default.Check else Icons.Default.Close,
                contentDescription = if (permission.isGranted) "권한 승인됨" else "권한 거부됨",
                tint = if (permission.isGranted) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}