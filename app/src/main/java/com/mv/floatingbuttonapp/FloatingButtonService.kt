package com.mv.floatingbuttonapp

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.media.projection.MediaProjection.Callback
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import java.nio.ByteBuffer
import kotlin.math.roundToInt

class FloatingButtonService :
    LifecycleService(),
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    // 화면/키보드 상태
    private var isKeyboardVisible = false
    private var lastButtonXPosition = 0
    private var lastButtonYPosition = 300
    private var screenHeight = 0
    private var screenWidth = 0

    // 화면 캡처는 AccessibilityService를 통해 수행

    // OCR 관련
    private lateinit var textRecognizer: TextRecognizer
    private val handler = Handler(Looper.getMainLooper())

    private val _viewModelStore: ViewModelStore = ViewModelStore()
    private val savedStateRegistryController: SavedStateRegistryController =
        SavedStateRegistryController.create(this)

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = _viewModelStore

    private val keyboardStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                KeyboardDetectionAccessibilityService.ACTION_KEYBOARD_SHOWN -> {
                    val keyboardHeight = intent.getIntExtra(
                        KeyboardDetectionAccessibilityService.EXTRA_KEYBOARD_HEIGHT, 0
                    )
                    Log.d(TAG, "Keyboard shown broadcast received, height: $keyboardHeight")
                    onKeyboardShown(keyboardHeight)
                }
                KeyboardDetectionAccessibilityService.ACTION_KEYBOARD_HIDDEN -> {
                    Log.d(TAG, "Keyboard hidden broadcast received")
                    onKeyboardHidden()
                }
            }
        }
    }

    // OCR 재시도 리시버
    private val ocrRetryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.mv.floatingbuttonapp.RETRY_OCR") {
                // captureScreen() // This is incorrect
                handleButtonClick() // Correct: Reuse the full capture logic
            }
        }
    }
    
    // 화면 캡처 결과 리시버
    private val screenshotReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "screenshotReceiver onReceive 호출됨")
            Log.d(TAG, "intent: $intent")
            Log.d(TAG, "intent action: ${intent?.action}")
            Log.d(TAG, "예상 action: ${KeyboardDetectionAccessibilityService.ACTION_TAKE_SCREENSHOT}")
            
            if (intent?.action == KeyboardDetectionAccessibilityService.ACTION_TAKE_SCREENSHOT) {
                Log.d(TAG, "화면 캡처 브로드캐스트 수신됨")
                val bitmap = intent.getParcelableExtra<Bitmap>(KeyboardDetectionAccessibilityService.EXTRA_SCREENSHOT_BITMAP)
                if (bitmap != null) {
                    Log.d(TAG, "화면 캡처 결과 수신됨: ${bitmap.width}x${bitmap.height}")
                    processScreenshot(bitmap)
                } else {
                    Log.e(TAG, "화면 캡처 결과가 null입니다")
                }
            } else {
                Log.d(TAG, "다른 브로드캐스트 수신됨: ${intent?.action}")
            }
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "floating_button_channel"
        private const val TAG = "FloatingButtonService"
    }

    override fun onCreate() {
        savedStateRegistryController.performRestore(null)
        super.onCreate()
        Log.d(TAG, "Service onCreate")

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // OCR 초기화 (한국어 지원)
        val koreanOptions = KoreanTextRecognizerOptions.Builder().build()
        textRecognizer = TextRecognition.getClient(koreanOptions)

        updateScreenDimensions()
        registerKeyboardReceiver()
        
        // 알림 채널 생성
        createNotificationChannel()

        // 권한 확인
        if (!Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Overlay permission not granted")
            Toast.makeText(this, "다른 앱 위에 표시 권한이 필요합니다.", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        if (!isAccessibilityServiceEnabled()) {
            Log.e(TAG, "Accessibility service not enabled")
            Toast.makeText(this, "접근성 서비스를 활성화해주세요", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        // 권한 확인 후 포그라운드 서비스 시작
        try {
            startForegroundService()
            Log.d(TAG, "포그라운드 서비스 시작됨")
        } catch (e: Exception) {
            Log.e(TAG, "포그라운드 서비스 시작 실패", e)
            Toast.makeText(this, "서비스 시작 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called")
        
        // 플로팅 버튼은 키보드 상태에 따라 동적으로 생성/제거
        // 초기에는 키보드가 비활성화 상태이므로 플로팅 버튼을 생성하지 않음
        // 키보드 상태 확인 후 필요시 플로팅 버튼 생성
        checkInitialKeyboardState()
        
        return START_STICKY
    }

    // MediaProjection 관련 메서드들은 더 이상 사용하지 않음 (AccessibilityService 사용)

    private fun registerKeyboardReceiver() {
        val keyboardFilter = IntentFilter().apply {
            addAction(KeyboardDetectionAccessibilityService.ACTION_KEYBOARD_SHOWN)
            addAction(KeyboardDetectionAccessibilityService.ACTION_KEYBOARD_HIDDEN)
        }
        
        val ocrFilter = IntentFilter().apply {
            addAction("com.mv.floatingbuttonapp.RETRY_OCR")
        }
        
        val screenshotFilter = IntentFilter().apply {
            addAction(KeyboardDetectionAccessibilityService.ACTION_TAKE_SCREENSHOT)
        }
        
        Log.d(TAG, "브로드캐스트 리시버 등록 중...")
        Log.d(TAG, "키보드 필터: ${keyboardFilter.actionsIterator().asSequence().toList()}")
        Log.d(TAG, "OCR 필터: ${ocrFilter.actionsIterator().asSequence().toList()}")
        Log.d(TAG, "스크린샷 필터: ${screenshotFilter.actionsIterator().asSequence().toList()}")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(keyboardStateReceiver, keyboardFilter, RECEIVER_NOT_EXPORTED)
            registerReceiver(ocrRetryReceiver, ocrFilter, RECEIVER_NOT_EXPORTED)
            registerReceiver(screenshotReceiver, screenshotFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(keyboardStateReceiver, keyboardFilter)
            registerReceiver(ocrRetryReceiver, ocrFilter)
            registerReceiver(screenshotReceiver, screenshotFilter)
        }
        Log.d(TAG, "브로드캐스트 리시버 등록 완료")
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = "$packageName/${KeyboardDetectionAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun openAccessibilitySettings() {
        startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }

    private fun updateScreenDimensions() {
        val dm = resources.displayMetrics
        screenWidth = dm.widthPixels
        screenHeight = dm.heightPixels
        lastButtonXPosition = screenWidth - 200
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "플로팅 버튼 서비스",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "플로팅 버튼 서비스 알림"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "알림 채널 생성됨: $CHANNEL_ID")
        }
    }

    private fun startForegroundService() {
        startForeground(NOTIFICATION_ID, createNotification())
    }

    private fun createNotification(): Notification {
        // 알림 클릭 시 앱을 열지 않도록 null로 설정
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("플로팅 버튼 서비스")
            .setContentText("플로팅 버튼이 활성화되었습니다")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(null) // 앱을 열지 않도록 null로 설정
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createFloatingView() {
        if (floatingView != null) return

        setupLayoutParams()

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingButtonService)
            setViewTreeViewModelStoreOwner(this@FloatingButtonService)
            setViewTreeSavedStateRegistryOwner(this@FloatingButtonService)

            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )

            setContent {
                FloatingButtonContent(
                    onDrag = { dragAmountX, dragAmountY ->
                        this@FloatingButtonService.updateButtonPositionByDrag(dragAmountX, dragAmountY)
                    },
                    onButtonClick = {
                        // 플로팅 버튼 클릭 시 화면 캡처 및 OCR 수행
                        this@FloatingButtonService.handleButtonClick()
                    }
                )
            }
        }

        floatingView = composeView
        try {
            windowManager.addView(floatingView, layoutParams)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding floating view", e)
        }
    }

    private fun removeFloatingView() {
        floatingView?.let {
            try {
                lastButtonXPosition = layoutParams.x
                lastButtonYPosition = layoutParams.y
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing floating view", e)
            }
        }
        floatingView = null
    }

    private fun setupLayoutParams() {
        val windowType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = lastButtonXPosition
            // 초기 위치를 화면 하단으로 설정
            y = screenHeight - 200
        }
    }

    private fun onKeyboardShown(keyboardHeight: Int) {
        Log.d(TAG, "키보드 표시됨, 높이: $keyboardHeight")
        isKeyboardVisible = true
        
        // 플로팅 버튼이 없으면 생성
        if (floatingView == null) {
            createFloatingView()
        }
        
        // 키보드 높이에 맞춰 위치 조정
        floatingView?.let {
            layoutParams.y = screenHeight - keyboardHeight - 200
            windowManager.updateViewLayout(it, layoutParams)
            Log.d(TAG, "플로팅 버튼 위치 조정: y = ${layoutParams.y}")
        }
    }

    private fun onKeyboardHidden() {
        Log.d(TAG, "키보드 숨겨짐")
        isKeyboardVisible = false
        
        // 키보드가 숨겨지면 플로팅 버튼 제거
        removeFloatingView()
    }
    
    private fun checkInitialKeyboardState() {
        // 접근성 서비스를 통해 현재 키보드 상태 확인
        val accessibilityService = KeyboardDetectionAccessibilityService.instance
        if (accessibilityService != null) {
            val isKeyboardCurrentlyVisible = KeyboardDetectionAccessibilityService.isKeyboardVisible
            Log.d(TAG, "초기 키보드 상태 확인: $isKeyboardCurrentlyVisible")
            
            if (isKeyboardCurrentlyVisible) {
                // 키보드가 이미 활성화되어 있으면 플로팅 버튼 생성
                val keyboardHeight = KeyboardDetectionAccessibilityService.keyboardHeight
                onKeyboardShown(keyboardHeight)
            }
        } else {
            Log.d(TAG, "접근성 서비스가 아직 초기화되지 않음, 플로팅 버튼 생성 안함")
        }
    }

    private fun updateButtonPositionByDrag(dragAmountX: Float, dragAmountY: Float) {
        layoutParams.x += dragAmountX.roundToInt()
        layoutParams.y += dragAmountY.roundToInt()
        floatingView?.let { windowManager.updateViewLayout(it, layoutParams) }
    }

    private fun handleButtonClick() {
        Log.d(TAG, "handleButtonClick() started")

        // AccessibilityService를 통한 화면 캡처 사용
        val accessibilityService = KeyboardDetectionAccessibilityService.instance
        if (accessibilityService != null) {
            Log.d(TAG, "AccessibilityService를 통한 화면 캡처 시작")
            // 버튼을 숨기고 캡처를 진행합니다.
            floatingView?.visibility = View.GONE
            Log.d(TAG, "플로팅 버튼 숨김 완료")
            
            try {
                // AccessibilityService에 화면 캡처 요청
                val screenshot = accessibilityService.takeScreenshot()
                if (screenshot != null) {
                    Log.d(TAG, "화면 캡처 성공: ${screenshot.width}x${screenshot.height}")
                    processScreenshot(screenshot)
                } else {
                    Log.e(TAG, "화면 캡처 실패: null 반환")
                    showPermissionRequestToast("화면 캡처에 실패했습니다. 접근성 서비스 설정을 확인해주세요.")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "화면 캡처 권한 없음", e)
                showPermissionRequestToast("화면 캡처 권한이 없습니다. 접근성 서비스 설정을 확인해주세요.")
            } catch (e: Exception) {
                Log.e(TAG, "화면 캡처 중 오류", e)
                showPermissionRequestToast("화면 캡처 중 오류가 발생했습니다: ${e.message}")
            }
        } else {
            Log.e(TAG, "AccessibilityService가 활성화되지 않음")
            Toast.makeText(this, "접근성 서비스를 활성화해주세요", Toast.LENGTH_LONG).show()
            // 플로팅 버튼 다시 표시
            handler.postDelayed({
                floatingView?.visibility = View.VISIBLE
            }, 500)
        }
    }
    // MediaProjection 관련 메서드들은 더 이상 사용하지 않음 (AccessibilityService 사용)

    private fun showPermissionRequestToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

        // 플로팅 버튼 다시 표시
        handler.postDelayed({
            floatingView?.visibility = View.VISIBLE
        }, 500)
    }
    
    // MediaProjection 관련 메서드들은 더 이상 사용하지 않음 (AccessibilityService 사용)
    
    /**
     * 화면 캡처 결과 처리
     */
    private fun processScreenshot(bitmap: Bitmap) {
        Log.d(TAG, "processScreenshot 호출됨: ${bitmap.width}x${bitmap.height}")
        
        try {
            // InputImage 생성
            Log.d(TAG, "InputImage 생성 중...")
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            Log.d(TAG, "InputImage 생성 완료")
            
            // OCR 수행
            Log.d(TAG, "OCR 처리 시작...")
            textRecognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    Log.d(TAG, "OCR 처리 성공")
                    val extractedText = visionText.text
                    Log.d(TAG, "추출된 텍스트 길이: ${extractedText.length}")
                    Log.d(TAG, "추출된 텍스트: $extractedText")
                    
                    if (extractedText.isNotEmpty()) {
                        Log.d(TAG, "OCR 결과를 BottomSheet로 표시")
                        // OCR 결과를 BottomSheet로 표시
                        showOcrBottomSheet(extractedText)
                    } else {
                        Log.d(TAG, "추출된 텍스트가 없습니다")
                        showPermissionRequestToast("텍스트를 찾을 수 없습니다.")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "OCR 처리 실패", e)
                    showPermissionRequestToast("텍스트 인식에 실패했습니다.")
                }
        } catch (e: Exception) {
            Log.e(TAG, "화면 캡처 결과 처리 중 오류", e)
            showPermissionRequestToast("화면 캡처 처리 중 오류가 발생했습니다.")
        } finally {
            // 플로팅 버튼 다시 표시
            Log.d(TAG, "플로팅 버튼 다시 표시 예약")
            handler.postDelayed({
                floatingView?.visibility = View.VISIBLE
                Log.d(TAG, "플로팅 버튼 다시 표시됨")
            }, 500)
        }
    }
    
    /**
     * OCR 결과를 BottomSheet로 표시
     */
    private fun showOcrBottomSheet(extractedText: String) {
        try {
            val intent = Intent(this, OcrBottomSheetActivity::class.java).apply {
                putExtra("extracted_text", extractedText)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            Log.d(TAG, "OCR BottomSheet 표시됨")
        } catch (e: Exception) {
            Log.e(TAG, "OCR BottomSheet 표시 중 오류", e)
            showPermissionRequestToast("OCR 결과 표시에 실패했습니다.")
        }
    }


    // MediaProjection 관련 메서드들은 더 이상 사용하지 않음 (AccessibilityService 사용)

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy 시작")
        
        try {
            savedStateRegistryController.performSave(Bundle())
            _viewModelStore.clear()

            // 리시버 등록 해제
            try {
                unregisterReceiver(keyboardStateReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "keyboardStateReceiver 등록 해제 실패", e)
            }
            
            try {
                unregisterReceiver(ocrRetryReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "ocrRetryReceiver 등록 해제 실패", e)
            }
            
            try {
                unregisterReceiver(screenshotReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "screenshotReceiver 등록 해제 실패", e)
            }
            
            // 플로팅 뷰 제거
            removeFloatingView()

            // OCR 리소스 정리
            try {
                textRecognizer.close()
            } catch (e: Exception) {
                Log.w(TAG, "TextRecognizer 정리 실패", e)
            }
            
            // 포그라운드 서비스 중지
            try {
                stopForeground(true)
            } catch (e: Exception) {
                Log.w(TAG, "포그라운드 서비스 중지 실패", e)
            }

            Log.d(TAG, "Service onDestroy 완료")
        } catch (e: Exception) {
            Log.e(TAG, "Service onDestroy 중 오류", e)
        } finally {
            super.onDestroy()
        }
    }
}

// UI 컴포저블
@Composable
fun FloatingButtonContent(
    onDrag: (Float, Float) -> Unit,
    onButtonClick: () -> Unit
) {
    Box(
        modifier = Modifier.pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                onDrag(dragAmount.x, dragAmount.y)
            }
        }
    ) {
        FloatingActionButton(
            onClick = onButtonClick,
            modifier = Modifier
                .size(44.dp)  // 적당한 크기로 조정 (40dp → 44dp)
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.15f),
                    spotColor = Color.Black.copy(alpha = 0.25f)
                ),
            containerColor = Color(0xFF2196F3),  // 깔끔한 파란색
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 6.dp,
                pressedElevation = 12.dp
            )
        ) {
            Icon(
                Icons.Default.TextFields,  // 텍스트 인식에 더 적합한 아이콘
                contentDescription = "텍스트 인식",
                modifier = Modifier.size(20.dp)  // 아이콘 크기 조정
            )
        }
    }
}