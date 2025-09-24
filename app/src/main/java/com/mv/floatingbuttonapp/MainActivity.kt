package com.mv.floatingbuttonapp

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mv.floatingbuttonapp.ui.theme.FloatingButtonAppTheme

class MainActivity : ComponentActivity() {

    private lateinit var mediaProjectionManager: MediaProjectionManager

    // MediaProjection 권한 요청 런처
    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                // MediaProjection 권한 획득 성공 - 정적 변수에 저장
                FloatingButtonService.mediaProjectionResultCode = result.resultCode
                FloatingButtonService.mediaProjectionResultData = data.clone() as Intent

                Toast.makeText(this, "화면 캡처 권한이 허용되었습니다", Toast.LENGTH_SHORT).show()

                // 서비스 시작
                checkAndStartService()
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
            MaterialTheme {
                MainScreen(
                    onStartServiceClick = {
                        checkAllPermissionsAndStart()
                    },
                    onStopServiceClick = {
                        stopFloatingService()
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

    private fun requestMediaProjection() {
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

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = "$packageName/${KeyboardDetectionAccessibilityService::class.java.name}"

        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(':').any { componentName ->
            componentName.equals(expectedComponentName, ignoreCase = true)
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
        }
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
                    checkAllPermissionsAndStart()
                } else {
                    Toast.makeText(this, "알림 권한이 필요합니다", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    onStartServiceClick: () -> Unit,
    onStopServiceClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "플로팅 버튼 컨트롤러",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            Text(
                text = "이 앱은 다른 앱 위에 떠 있는 버튼을 만듭니다.\n" +
                        "키보드가 나타나면 자동으로 키보드 위로 이동하며,\n" +
                        "버튼을 클릭하면 화면을 캡처하여 OCR로 텍스트를 추출합니다.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            Button(
                onClick = onStartServiceClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text("플로팅 버튼 시작")
            }

            OutlinedButton(
                onClick = onStopServiceClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("플로팅 버튼 중지")
            }
        }
    }
}