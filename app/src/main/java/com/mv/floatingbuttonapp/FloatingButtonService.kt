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

    // 화면 캡처 관련
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private lateinit var mediaProjectionManager: MediaProjectionManager

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

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "floating_button_channel"
        private const val TAG = "FloatingButtonService"
        const val REQUEST_MEDIA_PROJECTION = 1002

        // MediaProjection 결과를 저장할 정적 변수
        var mediaProjectionResultData: Intent? = null
        var mediaProjectionResultCode: Int = 0
    }

    override fun onCreate() {
        savedStateRegistryController.performRestore(null)
        super.onCreate()
        Log.d(TAG, "Service onCreate")

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // OCR 초기화 (한국어 지원)
        val koreanOptions = KoreanTextRecognizerOptions.Builder().build()
        textRecognizer = TextRecognition.getClient(koreanOptions)

        updateScreenDimensions()
        registerKeyboardReceiver()

        if (!Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Overlay permission not granted")
            Toast.makeText(this, "다른 앱 위에 표시 권한이 필요합니다.", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "접근성 서비스를 활성화해주세요", Toast.LENGTH_LONG).show()
            openAccessibilitySettings()
        }

        // 권한 확인 후 포그라운드 서비스 시작
        startForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // MainActivity로부터 MediaProjection 데이터를 받았을 때 객체를 생성하고 저장합니다.
        if (mediaProjectionResultData != null && mediaProjection == null) {
            try {
                mediaProjection = mediaProjectionManager.getMediaProjection(
                    mediaProjectionResultCode,
                    mediaProjectionResultData!!
                )
                Log.d(TAG, "MediaProjection created and stored in service.")

                // 프로젝션이 예기치 않게 중지될 때를 대비해 콜백을 등록합니다.
                mediaProjection?.registerCallback(object : Callback() {
                    override fun onStop() {
                        Log.w(TAG, "MediaProjection was stopped unexpectedly. Cleaning up.")
                        mediaProjection = null
                    }
                }, handler)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to create MediaProjection onStartCommand: ${e.message}", e)
                mediaProjection = null
            }
        }
        return START_STICKY
    }

    // MediaProjection 업데이트 메서드
    fun updateMediaProjection() {
        Log.d(TAG, "updateMediaProjection() called")
        
        // 기존 MediaProjection 정리
        if (mediaProjection != null) {
            Log.d(TAG, "Stopping existing MediaProjection")
            mediaProjection?.stop()
            mediaProjection = null
        }
        
        // 새로운 MediaProjection 생성
        if (mediaProjectionResultData != null && mediaProjectionResultCode != 0) {
            try {
                mediaProjection = mediaProjectionManager.getMediaProjection(
                    mediaProjectionResultCode,
                    mediaProjectionResultData!!
                )
                Log.d(TAG, "MediaProjection created successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create MediaProjection: ${e.message}", e)
            }
        } else {
            Log.w(TAG, "Cannot create MediaProjection: missing result data")
        }
    }

    // 리소스 정리 메서드
    private fun cleanupResources() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        // MediaProjection은 매번 새로 생성하므로 여기서는 정리하지 않음
    }

    // VirtualDisplay만 정리하는 메서드 (MediaProjection은 유지)
    private fun cleanupVirtualDisplay() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
    }

    // MediaProjection 유효성 확인 메서드 (간단한 버전)
    private fun isMediaProjectionValid(): Boolean {
        return try {
            val isValid = mediaProjection != null && mediaProjectionResultData != null && mediaProjectionResultCode != 0
            Log.d(TAG, "MediaProjection validation: $isValid (mediaProjection: ${mediaProjection != null}, resultData: ${mediaProjectionResultData != null}, resultCode: $mediaProjectionResultCode)")
            isValid
        } catch (e: Exception) {
            Log.e(TAG, "MediaProjection validation error: ${e.message}")
            false
        }
    }

    // MediaProjection 재생성 메서드
    private fun recreateMediaProjection(): Boolean {
        return try {
            Log.d(TAG, "Attempting to recreate MediaProjection...")
            
            if (mediaProjectionResultData != null && mediaProjectionResultCode != 0) {
                // 기존 MediaProjection 정리
                if (mediaProjection != null) {
                    Log.d(TAG, "Stopping existing MediaProjection...")
                    try {
                        mediaProjection?.stop()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error stopping MediaProjection: ${e.message}")
                    }
                    mediaProjection = null
                }
                
                // 잠시 대기 후 새로운 MediaProjection 생성
                Thread.sleep(100)
                
                // 새로운 MediaProjection 생성
                Log.d(TAG, "Creating new MediaProjection...")
                mediaProjection = mediaProjectionManager.getMediaProjection(
                    mediaProjectionResultCode,
                    mediaProjectionResultData!!
                )
                
                Log.d(TAG, "MediaProjection recreated successfully")
                true
            } else {
                Log.w(TAG, "Cannot recreate MediaProjection: missing result data")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to recreate MediaProjection: ${e.message}", e)
            mediaProjection?.stop()
            mediaProjection = null
            false
        }
    }

    private fun registerKeyboardReceiver() {
        val filter = IntentFilter().apply {
            addAction(KeyboardDetectionAccessibilityService.ACTION_KEYBOARD_SHOWN)
            addAction(KeyboardDetectionAccessibilityService.ACTION_KEYBOARD_HIDDEN)
            addAction("com.mv.floatingbuttonapp.RETRY_OCR")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(keyboardStateReceiver, filter, RECEIVER_NOT_EXPORTED)
            registerReceiver(ocrRetryReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            //registerReceiver(keyboardStateReceiver, filter)
            //registerReceiver(ocrRetryReceiver, filter)
        }
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

    private fun startForegroundService() {
        startForeground(NOTIFICATION_ID, createNotification())
    }

    private fun createNotification(): Notification {
        // 알림 클릭 시 앱을 열지 않도록 null로 설정
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("플로팅 버튼 대기 중")
            .setContentText("키보드 활성화를 감지합니다.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(null) // 앱을 열지 않도록 null로 설정
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
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
            y = lastButtonYPosition
        }
    }

    private fun onKeyboardShown(keyboardHeight: Int) {
        if (!isKeyboardVisible) {
            isKeyboardVisible = true
            createFloatingView()
        }

        floatingView?.let {
            layoutParams.y = screenHeight - keyboardHeight - 200
            windowManager.updateViewLayout(it, layoutParams)
        }
    }

    private fun onKeyboardHidden() {
        if (isKeyboardVisible) {
            isKeyboardVisible = false
            removeFloatingView()
        }
    }

    private fun updateButtonPositionByDrag(dragAmountX: Float, dragAmountY: Float) {
        layoutParams.x += dragAmountX.roundToInt()
        layoutParams.y += dragAmountY.roundToInt()
        floatingView?.let { windowManager.updateViewLayout(it, layoutParams) }
    }

    private fun handleButtonClick() {
        Log.d(TAG, "handleButtonClick() started")

        // 1. 서비스가 유효한 MediaProjection 객체를 가지고 있는지 확인합니다.
        val currentProjection = mediaProjection
        if (currentProjection == null) {
            Log.e(TAG, "MediaProjection is not available. Requesting permission again.")
            showPermissionRequestToast("화면 캡처 권한이 만료되었습니다. 앱을 열어 권한을 다시 허용해주세요.")

            // MainActivity에 권한 재요청을 알립니다.
            val intent = Intent(this, MainActivity::class.java).apply {
                action = "REQUEST_MEDIA_PROJECTION"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            return
        }

        // 2. 버튼을 숨기고 캡처를 진행합니다.
        floatingView?.visibility = View.GONE
        captureScreen(currentProjection) // 유효한 객체를 전달합니다.
    }
    private fun showPermissionRequestToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

        // 플로팅 버튼 다시 표시
        handler.postDelayed({
            floatingView?.visibility = View.VISIBLE
        }, 500)
    }


    private fun captureScreen(projection: MediaProjection) {
        Log.d(TAG, "captureScreen() started")

        // VirtualDisplay와 ImageReader만 매번 새로 생성하고 정리합니다.
        cleanupVirtualDisplay()

        val metrics = resources.displayMetrics

        try {
            imageReader = ImageReader.newInstance(
                metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2
            )

            virtualDisplay = projection.createVirtualDisplay(
                "ScreenCapture",
                metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )
            Log.d(TAG, "VirtualDisplay created successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to create VirtualDisplay: ${e.message}", e)
            showPermissionRequestToast("화면 캡처에 실패했습니다. 다시 시도해주세요.")
            cleanupVirtualDisplay()
            // 여기서 projection.stop()을 호출하지 않습니다. 서비스가 살아있는 동안 유지해야 합니다.
            return
        }

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                val bitmap = convertImageToBitmap(image)
                image.close()

                // 중요: VirtualDisplay만 정리하고 MediaProjection 객체는 그대로 둡니다.
                virtualDisplay?.release()
                virtualDisplay = null

                // projection.stop()을 호출하지 않습니다.

                processOCR(bitmap)
            }
        }, handler)
    }

    private fun convertImageToBitmap(image: android.media.Image): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        return bitmap
    }

    private fun processOCR(bitmap: Bitmap) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        textRecognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                handleOCRResult(visionText)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "OCR failed", e)
                Toast.makeText(this, "텍스트 인식에 실패했습니다", Toast.LENGTH_SHORT).show()
                floatingView?.visibility = View.VISIBLE
            }
    }

    private fun handleOCRResult(visionText: Text) {
        val recognizedText = visionText.text

        // 추천 텍스트 생성 (예시)
        val suggestions = generateSuggestions(recognizedText)

        // OcrBottomSheetActivity 실행 (기존 앱을 완전히 분리하여 실행)
        val intent = Intent(this, OcrBottomSheetActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                    Intent.FLAG_ACTIVITY_NO_HISTORY or 
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            putExtra(OcrBottomSheetActivity.EXTRA_OCR_TEXT, recognizedText)
            putStringArrayListExtra(
                OcrBottomSheetActivity.EXTRA_SUGGESTIONS,
                ArrayList(suggestions)
            )
        }
        startActivity(intent)

        // 플로팅 버튼 다시 표시
        handler.postDelayed({
            floatingView?.visibility = View.VISIBLE
        }, 500)
    }

    private fun generateSuggestions(text: String): List<String> {
        val suggestions = mutableListOf<String>()

        // 텍스트 분석하여 자동 추천 생성
        when {
            text.contains("내일 약속 있어?") -> {
                suggestions.add("내일 약속 있어?")
            }
            text.contains("아냐 내일은 없던") -> {
                suggestions.add("아냐 내일은 없던 ~ 왜?")
            }
            text.contains("저녁에 영화") -> {
                suggestions.add("내일 저녁에 영화 보러갈래?")
            }
            text.contains("무슨 영화") -> {
                suggestions.add("좋아 ㅎㅎ 무슨 영화?")
            }
            text.contains("스파이더맨") -> {
                suggestions.add("스파이더맨 좋아해?")
                suggestions.add("이미엑스로 보던 좀을 것 같은데")
            }
        }

        return suggestions
    }

    override fun onDestroy() {
        savedStateRegistryController.performSave(Bundle())
        _viewModelStore.clear()

        unregisterReceiver(keyboardStateReceiver)
        unregisterReceiver(ocrRetryReceiver)
        removeFloatingView()

        // OCR 리소스 정리
        textRecognizer.close()
        cleanupVirtualDisplay()
        mediaProjection?.stop()
        mediaProjection = null
        super.onDestroy()
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