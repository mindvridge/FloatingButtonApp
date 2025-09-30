package com.mv.floatingbuttonapp

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.PixelFormat
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
import kotlin.math.roundToInt

/**
 * OCR 분석 결과를 담는 데이터 클래스
 * 
 * ML Kit OCR과 AI 분석을 통해 추출된 텍스트의 상세 정보를 포함합니다.
 * 
 * @param originalText OCR로 추출된 원본 텍스트
 * @param textType 텍스트의 분류 타입 (질문, 메시지, URL 등)
 * @param confidence OCR 인식 신뢰도 (0.0 ~ 1.0)
 * @param language 감지된 언어 코드 (ko, en, mixed 등)
 * @param suggestions AI가 생성한 추천 답변 목록
 * @param keywords 텍스트에서 추출된 주요 키워드
 * @param entities 텍스트에서 추출된 엔티티 정보 (이메일, 전화번호 등)
 * @param chatAnalysis 채팅 메시지 분석 결과 (메시지 타입인 경우)
 */
data class OcrAnalysis(
    val originalText: String,           // 원본 텍스트
    val textType: TextType,            // 텍스트 타입
    val confidence: Float,             // 신뢰도 (0.0 ~ 1.0)
    val language: String,              // 감지된 언어
    val suggestions: List<String>,     // 추천 답변
    val keywords: List<String>,        // 키워드
    val entities: List<TextEntity>,    // 추출된 엔티티
    val chatAnalysis: ChatAnalysis?    // 채팅 분석 결과
)

/**
 * 채팅 분석 결과 데이터 클래스
 */
data class ChatAnalysis(
    val sender: ChatSender,            // 발신자 (나/상대방/알수없음)
    val confidence: Float,             // 발신자 구분 신뢰도
    val position: ChatPosition,        // 메시지 위치
    val timeInfo: String?,             // 시간 정보
    val messageType: MessageType,      // 메시지 타입
    val isGroupChat: Boolean,          // 그룹 채팅 여부
    val participants: List<String>     // 참여자 목록
)

/**
 * 채팅 발신자 열거형
 */
enum class ChatSender {
    ME,             // 나
    OTHER,          // 상대방
    UNKNOWN,        // 알 수 없음
    SYSTEM          // 시스템 메시지
}

/**
 * 채팅 위치 열거형
 */
enum class ChatPosition {
    LEFT,           // 왼쪽 (상대방)
    RIGHT,          // 오른쪽 (나)
    CENTER,         // 중앙 (시스템)
    UNKNOWN         // 알 수 없음
}

/**
 * 메시지 타입 열거형
 */
enum class MessageType {
    TEXT,           // 일반 텍스트
    IMAGE,          // 이미지
    FILE,           // 파일
    EMOJI,          // 이모지
    STICKER,        // 스티커
    SYSTEM,         // 시스템 메시지
    NOTIFICATION    // 알림
}

/**
 * 텍스트 타입 열거형
 */
enum class TextType {
    QUESTION,           // 질문
    MESSAGE,           // 메시지/채팅
    URL,               // URL/링크
    PHONE_NUMBER,      // 전화번호
    EMAIL,             // 이메일
    ADDRESS,           // 주소
    DATE_TIME,         // 날짜/시간
    NUMBER,            // 숫자
    CODE,              // 코드/프로그래밍
    GENERAL_TEXT       // 일반 텍스트
}

/**
 * 텍스트 엔티티 데이터 클래스
 */
data class TextEntity(
    val text: String,
    val type: EntityType,
    val startIndex: Int,
    val endIndex: Int
)

/**
 * 엔티티 타입 열거형
 */
enum class EntityType {
    PERSON,            // 사람 이름
    LOCATION,          // 장소
    ORGANIZATION,      // 조직/회사
    MONEY,             // 금액
    PERCENT,           // 퍼센트
    TIME,              // 시간
    DATE,              // 날짜
    EMAIL,             // 이메일
    PHONE,             // 전화번호
    URL,               // URL
    HASHTAG,           // 해시태그
    MENTION            // 멘션
}

/**
 * 플로팅 버튼 서비스 클래스
 * 
 * 이 서비스는 앱의 핵심 기능을 담당하며 다음과 같은 역할을 수행합니다:
 * - 키보드 상태 감지 및 플로팅 버튼 동적 표시/숨김
 * - 화면 캡처 및 OCR 텍스트 인식
 * - AI 기반 텍스트 분석 및 답변 추천
 * - 포그라운드 서비스로 백그라운드 실행
 * - 접근성 서비스와의 통신
 * 
 * 주요 생명주기:
 * 1. onCreate: 서비스 초기화 및 권한 확인
 * 2. onStartCommand: 키보드 상태 확인 및 플로팅 버튼 생성
 * 3. onDestroy: 리소스 정리 및 서비스 종료
 * 
 * @author FloatingButtonApp Team
 * @version 1.0
 * @since 2024
 */
class FloatingButtonService :
    LifecycleService(),
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    // ==================== UI 관리 ====================
    
    /**
     * 윈도우 매니저 인스턴스
     * 플로팅 버튼을 화면에 표시하기 위해 사용
     */
    private lateinit var windowManager: WindowManager
    
    /**
     * 플로팅 버튼 뷰
     * ComposeView로 구현된 플로팅 버튼 UI
     */
    private var floatingView: View? = null
    
    /**
     * 플로팅 버튼의 레이아웃 파라미터
     * 위치, 크기, 타입 등의 속성 설정
     */
    private lateinit var layoutParams: WindowManager.LayoutParams

    // ==================== 화면/키보드 상태 관리 ====================
    
    /**
     * 현재 키보드 표시 상태
     * true: 키보드 표시됨, false: 키보드 숨겨짐
     */
    private var isKeyboardVisible = false
    
    /**
     * 플로팅 버튼의 마지막 X 좌표
     * 드래그로 이동한 위치를 기억하기 위해 사용
     */
    private var lastButtonXPosition = 0
    
    /**
     * 플로팅 버튼의 마지막 Y 좌표
     * 드래그로 이동한 위치를 기억하기 위해 사용
     */
    private var lastButtonYPosition = 300
    
    /**
     * 화면 높이 (픽셀 단위)
     * 플로팅 버튼 위치 계산에 사용
     */
    private var screenHeight = 0
    
    /**
     * 화면 너비 (픽셀 단위)
     * 플로팅 버튼 위치 계산에 사용
     */
    private var screenWidth = 0

    // 화면 캡처는 AccessibilityService를 통해 수행

    // ==================== OCR 관련 ====================
    
    /**
     * ML Kit 텍스트 인식기
     * 한국어 지원 OCR 기능 제공
     */
    private lateinit var textRecognizer: TextRecognizer
    
    /**
     * 메인 스레드 핸들러
     * UI 업데이트 및 비동기 작업 처리를 위해 사용
     */
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
                    
                    // OCR 결과 분석 및 분류
                    val ocrAnalysis = analyzeOcrResult(visionText)
                    Log.d(TAG, "OCR 분석 완료: ${ocrAnalysis.textType}")
                    Log.d(TAG, "추출된 텍스트: ${ocrAnalysis.originalText}")
                    
                    if (ocrAnalysis.originalText.isNotEmpty()) {
                        Log.d(TAG, "OCR 결과를 BottomSheet로 표시")
                        // 분석된 OCR 결과를 BottomSheet로 표시
                        showOcrBottomSheet(ocrAnalysis)
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
     * OCR 결과 분석 및 분류
     */
    private fun analyzeOcrResult(visionText: Text): OcrAnalysis {
        val originalText = visionText.text
        Log.d(TAG, "OCR 분석 시작: $originalText")
        
        // 텍스트 타입 분류
        val textType = classifyTextType(originalText)
        Log.d(TAG, "텍스트 타입: $textType")
        
        // 언어 감지
        val language = detectLanguage(originalText)
        Log.d(TAG, "감지된 언어: $language")
        
        // 엔티티 추출
        val entities = extractEntities(originalText)
        Log.d(TAG, "추출된 엔티티: ${entities.size}개")
        
        // 키워드 추출
        val keywords = extractKeywords(originalText)
        Log.d(TAG, "추출된 키워드: $keywords")
        
        // 채팅 분석 (텍스트가 메시지 타입인 경우)
        val chatAnalysis = if (textType == TextType.MESSAGE) {
            analyzeChatMessage(visionText, originalText)
        } else {
            null
        }
        Log.d(TAG, "채팅 분석: ${chatAnalysis?.sender}")
        
        // 추천 답변 생성 (채팅 분석 결과 반영)
        val suggestions = generateSmartSuggestions(originalText, textType, chatAnalysis)
        Log.d(TAG, "생성된 추천: ${suggestions.size}개")
        
        // 신뢰도 계산
        val confidence = calculateConfidence(visionText)
        Log.d(TAG, "신뢰도: $confidence")
        
        return OcrAnalysis(
            originalText = originalText,
            textType = textType,
            confidence = confidence,
            language = language,
            suggestions = suggestions,
            keywords = keywords,
            entities = entities,
            chatAnalysis = chatAnalysis
        )
    }
    
    /**
     * 텍스트 타입 분류
     */
    private fun classifyTextType(text: String): TextType {
        val cleanText = text.trim().lowercase()
        
        return when {
            // 질문 패턴
            cleanText.contains("?") || 
            cleanText.contains("어떻게") || 
            cleanText.contains("언제") || 
            cleanText.contains("어디서") || 
            cleanText.contains("왜") || 
            cleanText.contains("무엇") ||
            cleanText.contains("뭐") ||
            cleanText.contains("어떤") -> TextType.QUESTION
            
            // URL 패턴
            cleanText.contains("http://") || 
            cleanText.contains("https://") || 
            cleanText.contains("www.") ||
            cleanText.contains(".com") ||
            cleanText.contains(".kr") ||
            cleanText.contains(".net") -> TextType.URL
            
            // 전화번호 패턴
            cleanText.matches(Regex(".*\\d{2,3}-?\\d{3,4}-?\\d{4}.*")) -> TextType.PHONE_NUMBER
            
            // 이메일 패턴
            cleanText.matches(Regex(".*[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}.*")) -> TextType.EMAIL
            
            // 날짜/시간 패턴
            cleanText.matches(Regex(".*\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}.*")) ||
            cleanText.matches(Regex(".*\\d{1,2}시\\s*\\d{1,2}분.*")) ||
            cleanText.contains("오전") || cleanText.contains("오후") ||
            cleanText.contains("월") || cleanText.contains("일") -> TextType.DATE_TIME
            
            // 숫자 패턴
            cleanText.matches(Regex(".*\\d+.*")) && !cleanText.contains("http") -> TextType.NUMBER
            
            // 코드 패턴
            cleanText.contains("function") || 
            cleanText.contains("class") || 
            cleanText.contains("import") ||
            cleanText.contains("def ") ||
            cleanText.contains("public") ||
            cleanText.contains("private") ||
            cleanText.contains("{") && cleanText.contains("}") -> TextType.CODE
            
            // 메시지/채팅 패턴
            cleanText.contains("안녕") || 
            cleanText.contains("고마워") || 
            cleanText.contains("미안") ||
            cleanText.contains("ㅋ") || cleanText.contains("ㅎ") ||
            cleanText.contains("ㅠ") || cleanText.contains("ㅜ") -> TextType.MESSAGE
            
            else -> TextType.GENERAL_TEXT
        }
    }
    
    /**
     * 언어 감지
     */
    private fun detectLanguage(text: String): String {
        val koreanPattern = Regex("[가-힣]")
        val englishPattern = Regex("[a-zA-Z]")
        val numberPattern = Regex("[0-9]")
        
        val koreanCount = koreanPattern.findAll(text).count()
        val englishCount = englishPattern.findAll(text).count()
        val numberCount = numberPattern.findAll(text).count()
        
        return when {
            koreanCount > englishCount && koreanCount > 0 -> "ko"
            englishCount > koreanCount && englishCount > 0 -> "en"
            numberCount > 0 -> "number"
            else -> "mixed"
        }
    }
    
    /**
     * 엔티티 추출
     */
    private fun extractEntities(text: String): List<TextEntity> {
        val entities = mutableListOf<TextEntity>()
        
        // 이메일 추출
        val emailPattern = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
        emailPattern.findAll(text).forEach { matchResult ->
            entities.add(TextEntity(
                text = matchResult.value,
                type = EntityType.EMAIL,
                startIndex = matchResult.range.first,
                endIndex = matchResult.range.last + 1
            ))
        }
        
        // 전화번호 추출
        val phonePattern = Regex("\\d{2,3}-?\\d{3,4}-?\\d{4}")
        phonePattern.findAll(text).forEach { matchResult ->
            entities.add(TextEntity(
                text = matchResult.value,
                type = EntityType.PHONE,
                startIndex = matchResult.range.first,
                endIndex = matchResult.range.last + 1
            ))
        }
        
        // URL 추출
        val urlPattern = Regex("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=]+")
        urlPattern.findAll(text).forEach { matchResult ->
            entities.add(TextEntity(
                text = matchResult.value,
                type = EntityType.URL,
                startIndex = matchResult.range.first,
                endIndex = matchResult.range.last + 1
            ))
        }
        
        // 해시태그 추출
        val hashtagPattern = Regex("#[가-힣a-zA-Z0-9_]+")
        hashtagPattern.findAll(text).forEach { matchResult ->
            entities.add(TextEntity(
                text = matchResult.value,
                type = EntityType.HASHTAG,
                startIndex = matchResult.range.first,
                endIndex = matchResult.range.last + 1
            ))
        }
        
        // 멘션 추출
        val mentionPattern = Regex("@[가-힣a-zA-Z0-9_]+")
        mentionPattern.findAll(text).forEach { matchResult ->
            entities.add(TextEntity(
                text = matchResult.value,
                type = EntityType.MENTION,
                startIndex = matchResult.range.first,
                endIndex = matchResult.range.last + 1
            ))
        }
        
        // 금액 추출
        val moneyPattern = Regex("\\d+[원,만,억,조]")
        moneyPattern.findAll(text).forEach { matchResult ->
            entities.add(TextEntity(
                text = matchResult.value,
                type = EntityType.MONEY,
                startIndex = matchResult.range.first,
                endIndex = matchResult.range.last + 1
            ))
        }
        
        return entities
    }
    
    /**
     * 키워드 추출
     */
    private fun extractKeywords(text: String): List<String> {
        val keywords = mutableListOf<String>()
        
        // 불용어 제거 및 키워드 추출
        val stopWords = setOf("은", "는", "이", "가", "을", "를", "의", "에", "에서", "로", "으로", "와", "과", "도", "만", "부터", "까지", "한테", "에게", "께", "한테서", "에게서", "께서", "이랑", "랑", "이든", "든", "이든지", "든지", "이야", "야", "이에요", "에요", "입니다", "다", "어요", "아요", "지요", "죠", "네요", "어", "아", "지", "네", "고", "며", "면서", "으면서", "면서", "으니", "니", "으니까", "니까", "으므로", "므로", "어서", "아서", "으려고", "려고", "으려면", "려면", "으면", "면", "으면서", "면서", "으니", "니", "으니까", "니까", "으므로", "므로", "어서", "아서", "으려고", "려고", "으려면", "려면", "으면", "면")
        
        val words = text.split(Regex("\\s+"))
            .filter { it.length > 1 }
            .filter { !stopWords.contains(it) }
            .filter { it.matches(Regex("[가-힣a-zA-Z0-9]+")) }
        
        // 빈도수 기반으로 상위 키워드 선택
        val wordCount = words.groupingBy { it }.eachCount()
        keywords.addAll(wordCount.toList().sortedByDescending { it.second }.take(5).map { it.first })
        
        return keywords
    }
    
    /**
     * 채팅 메시지 분석
     */
    private fun analyzeChatMessage(visionText: Text, text: String): ChatAnalysis {
        Log.d(TAG, "채팅 메시지 분석 시작: $text")
        
        // 발신자 구분
        val sender = identifySender(visionText, text)
        Log.d(TAG, "발신자: $sender")
        
        // 메시지 위치 분석
        val position = analyzeMessagePosition(visionText)
        Log.d(TAG, "메시지 위치: $position")
        
        // 시간 정보 추출
        val timeInfo = extractTimeInfo(text)
        Log.d(TAG, "시간 정보: $timeInfo")
        
        // 메시지 타입 분석
        val messageType = analyzeMessageType(text)
        Log.d(TAG, "메시지 타입: $messageType")
        
        // 그룹 채팅 여부 확인
        val isGroupChat = detectGroupChat(text)
        Log.d(TAG, "그룹 채팅: $isGroupChat")
        
        // 참여자 목록 추출
        val participants = extractParticipants(text)
        Log.d(TAG, "참여자: $participants")
        
        // 발신자 구분 신뢰도 계산
        val confidence = calculateSenderConfidence(sender, position, text)
        Log.d(TAG, "발신자 구분 신뢰도: $confidence")
        
        return ChatAnalysis(
            sender = sender,
            confidence = confidence,
            position = position,
            timeInfo = timeInfo,
            messageType = messageType,
            isGroupChat = isGroupChat,
            participants = participants
        )
    }
    
    /**
     * 발신자 구분
     */
    private fun identifySender(visionText: Text, text: String): ChatSender {
        // 1. 텍스트 위치 기반 분석
        val positionBasedSender = analyzePositionBasedSender(visionText)
        
        // 2. 텍스트 내용 기반 분석
        val contentBasedSender = analyzeContentBasedSender(text)
        
        // 3. 시간 패턴 기반 분석
        val timeBasedSender = analyzeTimeBasedSender(text)
        
        // 4. 종합 판단
        val scores = mutableMapOf<ChatSender, Int>()
        
        // 위치 기반 점수
        when (positionBasedSender) {
            ChatSender.ME -> scores[ChatSender.ME] = (scores[ChatSender.ME] ?: 0) + 3
            ChatSender.OTHER -> scores[ChatSender.OTHER] = (scores[ChatSender.OTHER] ?: 0) + 3
            else -> {}
        }
        
        // 내용 기반 점수
        when (contentBasedSender) {
            ChatSender.ME -> scores[ChatSender.ME] = (scores[ChatSender.ME] ?: 0) + 2
            ChatSender.OTHER -> scores[ChatSender.OTHER] = (scores[ChatSender.OTHER] ?: 0) + 2
            else -> {}
        }
        
        // 시간 기반 점수
        when (timeBasedSender) {
            ChatSender.ME -> scores[ChatSender.ME] = (scores[ChatSender.ME] ?: 0) + 1
            ChatSender.OTHER -> scores[ChatSender.OTHER] = (scores[ChatSender.OTHER] ?: 0) + 1
            else -> {}
        }
        
        // 최고 점수 발신자 반환
        return scores.maxByOrNull { it.value }?.key ?: ChatSender.UNKNOWN
    }
    
    /**
     * 위치 기반 발신자 분석
     */
    private fun analyzePositionBasedSender(visionText: Text): ChatSender {
        // 텍스트 블록들의 위치 분석
        var leftSideCount = 0
        var rightSideCount = 0
        var centerCount = 0
        
        for (block in visionText.textBlocks) {
            val boundingBox = block.boundingBox
            if (boundingBox != null) {
                val centerX = (boundingBox.left + boundingBox.right) / 2
                val screenWidth = resources.displayMetrics.widthPixels
                
                when {
                    centerX < screenWidth * 0.3 -> leftSideCount++
                    centerX > screenWidth * 0.7 -> rightSideCount++
                    else -> centerCount++
                }
            }
        }
        
        return when {
            rightSideCount > leftSideCount && rightSideCount > centerCount -> ChatSender.ME
            leftSideCount > rightSideCount && leftSideCount > centerCount -> ChatSender.OTHER
            centerCount > leftSideCount && centerCount > rightSideCount -> ChatSender.SYSTEM
            else -> ChatSender.UNKNOWN
        }
    }
    
    /**
     * 내용 기반 발신자 분석
     */
    private fun analyzeContentBasedSender(text: String): ChatSender {
        val cleanText = text.lowercase().trim()
        
        // 나의 메시지 패턴
        val myMessagePatterns = listOf(
            "나", "내가", "저는", "제가", "우리", "우리집", "우리회사",
            "제가", "저희", "제가", "제가", "제가", "제가"
        )
        
        // 상대방 메시지 패턴
        val otherMessagePatterns = listOf(
            "너", "당신", "자네", "그대", "님", "씨", "선생님",
            "어떻게", "언제", "어디서", "왜", "무엇", "뭐"
        )
        
        // 시스템 메시지 패턴
        val systemMessagePatterns = listOf(
            "입장", "퇴장", "초대", "추방", "관리자", "알림",
            "시스템", "봇", "자동", "공지"
        )
        
        val myScore = myMessagePatterns.count { cleanText.contains(it) }
        val otherScore = otherMessagePatterns.count { cleanText.contains(it) }
        val systemScore = systemMessagePatterns.count { cleanText.contains(it) }
        
        return when {
            systemScore > myScore && systemScore > otherScore -> ChatSender.SYSTEM
            myScore > otherScore -> ChatSender.ME
            otherScore > myScore -> ChatSender.OTHER
            else -> ChatSender.UNKNOWN
        }
    }
    
    /**
     * 시간 기반 발신자 분석
     */
    private fun analyzeTimeBasedSender(text: String): ChatSender {
        // 시간 패턴 분석 (예: "오전 10:30", "14:25" 등)
        val timePattern = Regex("(오전|오후)?\\s*\\d{1,2}:\\d{2}")
        val hasTimeInfo = timePattern.containsMatchIn(text)
        
        // 시간 정보가 있으면 상대방 메시지일 가능성이 높음 (보통 상대방 메시지에 시간이 표시됨)
        return if (hasTimeInfo) ChatSender.OTHER else ChatSender.UNKNOWN
    }
    
    /**
     * 메시지 위치 분석
     */
    private fun analyzeMessagePosition(visionText: Text): ChatPosition {
        var leftCount = 0
        var rightCount = 0
        var centerCount = 0
        
        for (block in visionText.textBlocks) {
            val boundingBox = block.boundingBox
            if (boundingBox != null) {
                val centerX = (boundingBox.left + boundingBox.right) / 2
                val screenWidth = resources.displayMetrics.widthPixels
                
                when {
                    centerX < screenWidth * 0.3 -> leftCount++
                    centerX > screenWidth * 0.7 -> rightCount++
                    else -> centerCount++
                }
            }
        }
        
        return when {
            rightCount > leftCount && rightCount > centerCount -> ChatPosition.RIGHT
            leftCount > rightCount && leftCount > centerCount -> ChatPosition.LEFT
            centerCount > leftCount && centerCount > rightCount -> ChatPosition.CENTER
            else -> ChatPosition.UNKNOWN
        }
    }
    
    /**
     * 시간 정보 추출
     */
    private fun extractTimeInfo(text: String): String? {
        val timePatterns = listOf(
            Regex("(오전|오후)\\s*\\d{1,2}:\\d{2}"),
            Regex("\\d{1,2}:\\d{2}"),
            Regex("\\d{1,2}시\\s*\\d{1,2}분"),
            Regex("\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}")
        )
        
        for (pattern in timePatterns) {
            val match = pattern.find(text)
            if (match != null) {
                return match.value
            }
        }
        
        return null
    }
    
    /**
     * 메시지 타입 분석
     */
    private fun analyzeMessageType(text: String): MessageType {
        val cleanText = text.lowercase()
        
        return when {
            // 이미지 관련
            cleanText.contains("사진") || cleanText.contains("이미지") || 
            cleanText.contains("그림") || cleanText.contains("photo") -> MessageType.IMAGE
            
            // 파일 관련
            cleanText.contains("파일") || cleanText.contains("첨부") || 
            cleanText.contains("다운로드") || cleanText.contains("file") -> MessageType.FILE
            
            // 이모지 관련
            cleanText.matches(Regex(".*[😀-🙏🌀-🗿🚀-🛿]+.*")) -> MessageType.EMOJI
            
            // 스티커 관련
            cleanText.contains("스티커") || cleanText.contains("sticker") -> MessageType.STICKER
            
            // 시스템 메시지
            cleanText.contains("입장") || cleanText.contains("퇴장") || 
            cleanText.contains("초대") || cleanText.contains("알림") -> MessageType.SYSTEM
            
            // 알림
            cleanText.contains("알림") || cleanText.contains("notification") -> MessageType.NOTIFICATION
            
            // 기본적으로 텍스트
            else -> MessageType.TEXT
        }
    }
    
    /**
     * 그룹 채팅 감지
     */
    private fun detectGroupChat(text: String): Boolean {
        val groupChatIndicators = listOf(
            "님", "씨", "선생님", "여러분", "모두", "다들",
            "그룹", "단체", "팀", "회의", "모임"
        )
        
        return groupChatIndicators.any { text.contains(it) }
    }
    
    /**
     * 참여자 목록 추출
     */
    private fun extractParticipants(text: String): List<String> {
        val participants = mutableListOf<String>()
        
        // 이름 패턴 추출 (한글 이름, 영문 이름 등)
        val namePatterns = listOf(
            Regex("[가-힣]{2,4}(님|씨|선생님)"),
            Regex("[A-Za-z]{2,10}(님|씨|선생님)"),
            Regex("@[가-힣a-zA-Z0-9_]+")
        )
        
        for (pattern in namePatterns) {
            pattern.findAll(text).forEach { matchResult ->
                val name = matchResult.value.replace(Regex("(님|씨|선생님|@)"), "")
                if (name.isNotEmpty()) {
                    participants.add(name)
                }
            }
        }
        
        return participants.distinct()
    }
    
    /**
     * 발신자 구분 신뢰도 계산
     */
    private fun calculateSenderConfidence(sender: ChatSender, position: ChatPosition, text: String): Float {
        var confidence = 0.5f
        
        // 위치와 발신자 일치 여부
        when {
            sender == ChatSender.ME && position == ChatPosition.RIGHT -> confidence += 0.3f
            sender == ChatSender.OTHER && position == ChatPosition.LEFT -> confidence += 0.3f
            sender == ChatSender.SYSTEM && position == ChatPosition.CENTER -> confidence += 0.3f
        }
        
        // 텍스트 길이에 따른 신뢰도 조정
        when {
            text.length > 50 -> confidence += 0.1f
            text.length < 10 -> confidence -= 0.1f
        }
        
        // 시간 정보 존재 여부
        if (extractTimeInfo(text) != null) {
            confidence += 0.1f
        }
        
        return confidence.coerceIn(0.0f, 1.0f)
    }
    
    /**
     * 스마트 추천 답변 생성
     */
    private fun generateSmartSuggestions(text: String, textType: TextType, chatAnalysis: ChatAnalysis?): List<String> {
        val suggestions = mutableListOf<String>()

        when (textType) {
            TextType.QUESTION -> {
                suggestions.addAll(generateQuestionSuggestions(text, chatAnalysis))
            }
            TextType.MESSAGE -> {
                suggestions.addAll(generateMessageSuggestions(text, chatAnalysis))
            }
            TextType.URL -> {
                suggestions.addAll(generateUrlSuggestions())
            }
            TextType.PHONE_NUMBER -> {
                suggestions.addAll(generatePhoneSuggestions())
            }
            TextType.EMAIL -> {
                suggestions.addAll(generateEmailSuggestions())
            }
            else -> {
                suggestions.addAll(generateGeneralSuggestions(chatAnalysis))
            }
        }
        
        return suggestions.distinct().take(5)
    }
    
    /**
     * 질문에 대한 추천 답변 생성
     */
    private fun generateQuestionSuggestions(text: String, chatAnalysis: ChatAnalysis?): List<String> {
        val suggestions = mutableListOf<String>()
        
        // 채팅 분석 결과에 따른 맞춤 답변
        val senderPrefix = when (chatAnalysis?.sender) {
            ChatSender.ME -> "제가 "
            ChatSender.OTHER -> "상대방이 "
            ChatSender.SYSTEM -> "시스템에서 "
            else -> ""
        }
        
        when {
            text.contains("어떻게") -> {
                suggestions.add("${senderPrefix}좋은 질문이네요! 구체적으로 어떤 부분이 궁금하신가요?")
                suggestions.add("${senderPrefix}자세히 설명해드릴게요. 어떤 관점에서 알고 싶으신가요?")
            }
            text.contains("언제") -> {
                suggestions.add("${senderPrefix}시간에 대한 질문이시군요. 구체적인 날짜나 기간을 알려주시면 더 정확한 답변을 드릴 수 있어요.")
            }
            text.contains("어디서") || text.contains("어디") -> {
                suggestions.add("${senderPrefix}장소에 대한 질문이네요. 어떤 지역이나 위치를 말씀하시는 건가요?")
            }
            text.contains("왜") -> {
                suggestions.add("${senderPrefix}이유를 묻는 질문이군요. 어떤 상황에서 이런 질문을 하게 되셨나요?")
            }
            text.contains("뭐") || text.contains("무엇") -> {
                suggestions.add("${senderPrefix}구체적으로 무엇에 대해 알고 싶으신가요?")
            }
            else -> {
                suggestions.add("${senderPrefix}흥미로운 질문이네요! 더 자세히 설명해주시면 도움을 드릴 수 있을 것 같아요.")
                suggestions.add("${senderPrefix}좋은 질문입니다. 어떤 관점에서 답변을 원하시나요?")
            }
        }
        
        // 그룹 채팅인 경우 추가 답변
        if (chatAnalysis?.isGroupChat == true) {
            suggestions.add("그룹 채팅에서 좋은 질문이네요! 다른 분들도 궁금해하실 것 같아요.")
        }

        return suggestions
    }
    
    /**
     * 메시지에 대한 추천 답변 생성
     */
    private fun generateMessageSuggestions(text: String, chatAnalysis: ChatAnalysis?): List<String> {
        val suggestions = mutableListOf<String>()
        
        // 발신자에 따른 맞춤 답변
        when (chatAnalysis?.sender) {
            ChatSender.ME -> {
                // 내가 보낸 메시지에 대한 답변
                when {
                    text.contains("안녕") -> {
                        suggestions.add("인사 잘 드렸네요! 😊")
                        suggestions.add("친근한 인사가 좋아요!")
                    }
                    text.contains("고마워") || text.contains("감사") -> {
                        suggestions.add("예의 바른 표현이에요! 👍")
                        suggestions.add("감사 인사 잘 드렸네요!")
                    }
                    text.contains("미안") || text.contains("죄송") -> {
                        suggestions.add("사과 잘 드렸어요! 😊")
                        suggestions.add("예의 바른 사과네요!")
                    }
                    text.contains("ㅋ") || text.contains("ㅎ") -> {
                        suggestions.add("유쾌한 메시지네요! 😄")
                        suggestions.add("재미있게 대화하고 계시네요!")
                    }
                    text.contains("ㅠ") || text.contains("ㅜ") -> {
                        suggestions.add("힘든 상황을 공유하셨네요 😢")
                        suggestions.add("마음을 나눠주셔서 고마워요")
                    }
                    else -> {
                        suggestions.add("좋은 메시지를 보내셨네요!")
                        suggestions.add("의미 있는 대화를 하고 계시네요!")
                    }
                }
            }
            ChatSender.OTHER -> {
                // 상대방이 보낸 메시지에 대한 답변
                when {
                    text.contains("안녕") -> {
                        suggestions.add("안녕하세요! 좋은 하루 보내세요 😊")
                        suggestions.add("안녕! 반가워요!")
                    }
                    text.contains("고마워") || text.contains("감사") -> {
                        suggestions.add("천만에요! 도움이 되었다니 기뻐요 😊")
                        suggestions.add("별 말씀을요! 언제든지 도와드릴게요")
                    }
                    text.contains("미안") || text.contains("죄송") -> {
                        suggestions.add("괜찮아요! 걱정하지 마세요 😊")
                        suggestions.add("전혀 문제없어요. 이해해요")
                    }
                    text.contains("ㅋ") || text.contains("ㅎ") -> {
                        suggestions.add("웃음이 나오는 상황이네요! 😄")
                        suggestions.add("재미있는 이야기인 것 같아요!")
                    }
                    text.contains("ㅠ") || text.contains("ㅜ") -> {
                        suggestions.add("슬픈 일이 있으신가요? 😢")
                        suggestions.add("힘든 일이 있으시면 언제든 말씀해주세요")
                    }
                    else -> {
                        suggestions.add("좋은 이야기네요! 더 들려주세요")
                        suggestions.add("흥미로운 내용이에요!")
                    }
                }
            }
            ChatSender.SYSTEM -> {
                suggestions.add("시스템 메시지가 왔네요")
                suggestions.add("알림을 확인해보세요")
            }
            else -> {
                suggestions.add("메시지를 확인해보세요")
                suggestions.add("흥미로운 내용이에요!")
            }
        }
        
        // 그룹 채팅인 경우 추가 답변
        if (chatAnalysis?.isGroupChat == true) {
            suggestions.add("그룹 채팅에서 좋은 대화네요!")
            if (chatAnalysis.participants.isNotEmpty()) {
                suggestions.add("${chatAnalysis.participants.joinToString(", ")}님들과 대화하고 계시네요!")
            }
        }
        
        // 시간 정보가 있는 경우
        chatAnalysis?.timeInfo?.let { time ->
            suggestions.add("${time}에 보낸 메시지네요")
        }
        
        return suggestions
    }
    
    /**
     * URL에 대한 추천 답변 생성
     */
    private fun generateUrlSuggestions(): List<String> {
        return listOf(
            "이 링크를 확인해보시겠어요?",
            "관련 정보를 더 찾아보시겠어요?",
            "이 사이트에 대해 더 알고 싶으시나요?",
            "링크를 공유해주셔서 감사해요!",
            "유용한 정보인 것 같네요!"
        )
    }
    
    /**
     * 전화번호에 대한 추천 답변 생성
     */
    private fun generatePhoneSuggestions(): List<String> {
        return listOf(
            "이 번호로 연락드릴까요?",
            "전화번호를 저장하시겠어요?",
            "이 번호에 대해 더 알고 싶으시나요?",
            "연락처 정보를 정리해드릴까요?",
            "번호를 복사하시겠어요?"
        )
    }
    
    /**
     * 이메일에 대한 추천 답변 생성
     */
    private fun generateEmailSuggestions(): List<String> {
        return listOf(
            "이 이메일로 연락드릴까요?",
            "이메일 주소를 저장하시겠어요?",
            "메일을 보내시겠어요?",
            "이메일 주소를 복사하시겠어요?",
            "연락처에 추가하시겠어요?"
        )
    }
    
    /**
     * 일반 텍스트에 대한 추천 답변 생성
     */
    private fun generateGeneralSuggestions(chatAnalysis: ChatAnalysis?): List<String> {
        val suggestions = mutableListOf<String>()
        
        // 기본 추천 답변
        suggestions.addAll(listOf(
            "흥미로운 내용이네요!",
            "더 자세히 알고 싶어요",
            "이에 대해 더 이야기해볼까요?",
            "좋은 정보 감사해요!",
            "도움이 필요하시면 언제든 말씀해주세요"
        ))
        
        // 채팅 분석 결과 반영
        chatAnalysis?.let { analysis ->
            when (analysis.sender) {
                ChatSender.ME -> {
                    suggestions.add("제가 작성한 내용이네요!")
                    suggestions.add("좋은 생각을 정리하셨네요!")
                }
                ChatSender.OTHER -> {
                    suggestions.add("상대방이 보낸 내용이네요!")
                    suggestions.add("흥미로운 관점이에요!")
                }
                ChatSender.SYSTEM -> {
                    suggestions.add("시스템에서 생성된 내용이네요!")
                    suggestions.add("알림이나 공지를 확인해보세요!")
                }
                else -> {
                    suggestions.add("텍스트를 분석해보세요!")
                }
            }
            
            // 그룹 채팅인 경우
            if (analysis.isGroupChat) {
                suggestions.add("그룹 대화의 일부네요!")
            }
            
            // 시간 정보가 있는 경우
            analysis.timeInfo?.let { time ->
                suggestions.add("${time}에 작성된 내용이네요!")
            }
        }
        
        return suggestions
    }
    
    /**
     * OCR 신뢰도 계산
     */
    private fun calculateConfidence(visionText: Text): Float {
        // ML Kit의 Text 객체에서 신뢰도 정보 추출
        var totalConfidence = 0f
        var blockCount = 0
        
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    // 각 텍스트 요소의 신뢰도 (0.0 ~ 1.0)
                    val confidence = element.confidence ?: 0.5f
                    totalConfidence += confidence
                    blockCount++
                }
            }
        }
        
        return if (blockCount > 0) totalConfidence / blockCount else 0.5f
    }
    
    /**
     * OCR 결과를 BottomSheet로 표시
     */
    private fun showOcrBottomSheet(ocrAnalysis: OcrAnalysis) {
        try {
            val intent = Intent(this, OcrBottomSheetActivity::class.java).apply {
                // 기존 호환성을 위한 텍스트 전달
                putExtra("extracted_text", ocrAnalysis.originalText)
                
                // 분석된 OCR 결과 전달 (개별 필드로)
                putExtra("text_type", ocrAnalysis.textType.name)
                putExtra("confidence", ocrAnalysis.confidence)
                putExtra("language", ocrAnalysis.language)
                putStringArrayListExtra("suggestions", ArrayList(ocrAnalysis.suggestions))
                putStringArrayListExtra("keywords", ArrayList(ocrAnalysis.keywords))
                
                // 엔티티 정보 전달
                val entityTexts = ocrAnalysis.entities.map { it.text }
                val entityTypes = ocrAnalysis.entities.map { it.type.name }
                putStringArrayListExtra("entities", ArrayList(entityTexts))
                putStringArrayListExtra("entity_types", ArrayList(entityTypes))
                
                // 채팅 분석 결과 전달
                ocrAnalysis.chatAnalysis?.let { chatAnalysis ->
                    putExtra("chat_sender", chatAnalysis.sender.name)
                    putExtra("chat_confidence", chatAnalysis.confidence)
                    putExtra("chat_position", chatAnalysis.position.name)
                    putExtra("chat_time_info", chatAnalysis.timeInfo ?: "")
                    putExtra("chat_message_type", chatAnalysis.messageType.name)
                    putExtra("chat_is_group", chatAnalysis.isGroupChat)
                    putStringArrayListExtra("chat_participants", ArrayList(chatAnalysis.participants))
                }
                
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            Log.d(TAG, "OCR BottomSheet 표시됨 - 타입: ${ocrAnalysis.textType}, 신뢰도: ${ocrAnalysis.confidence}, 발신자: ${ocrAnalysis.chatAnalysis?.sender}")
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