package com.mv.toki

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
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import com.mv.toki.R
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
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
import com.mv.toki.ocr.OcrClassifier
import com.mv.toki.ocr.Sender
import com.mv.toki.ocr.ChatMessage
import kotlinx.coroutines.launch
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
    val participants: List<String>,    // 참여자 목록
    val otherPersonName: String?       // 상대방 이름
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
 * 텍스트 라인 데이터 클래스
 * OCR 결과에서 추출된 각 텍스트 라인의 정보를 저장
 */
data class TextLine(
    val text: String,
    val y: Int,
    val left: Int,
    val right: Int,
    val center: Int,
    val isName: Boolean,  // 이름인지 여부
    val fontSize: Float,  // 폰트 크기 (높이 기준)
    val height: Int       // 텍스트 높이
)

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
     * 플로팅 버튼 제거 애니메이션 진행 중 여부
     * true: 애니메이션 진행 중, false: 애니메이션 완료
     */
    private var isRemovingAnimation = false
    
    /**
     * 플로팅 버튼의 마지막 X 좌표
     * 드래그로 이동한 위치를 기억하기 위해 사용
     */
    private var lastButtonXPosition = 0
    
    /**
     * 플로팅 버튼의 마지막 Y 좌표
     * 드래그로 이동한 위치를 기억하기 위해 사용
     */
    private var lastButtonYPosition = 0
    
    /**
     * 사용자가 드래그로 위치를 변경했는지 여부
     * true: 사용자가 드래그로 이동함, false: 기본 위치 사용 중
     */
    private var isUserMovedPosition = false
    
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
    
    /**
     * OCR 실행을 위해 키보드가 숨겨지기를 기다리는지 여부
     * true: 키보드가 숨겨지면 OCR 실행, false: 일반적인 키보드 숨김
     */
    private var isWaitingForKeyboardHide = false
    
    /**
     * 토큰 관리자
     */
    private lateinit var tokenManager: com.mv.toki.auth.TokenManager
    
    /**
     * 백그라운드 토큰 갱신을 위한 핸들러
     */
    private val tokenRefreshHandler = Handler(Looper.getMainLooper())
    
    /**
     * 현재 포그라운드 앱 패키지명
     * 카카오톡일 때만 플로팅 버튼 표시
     */
    private var currentForegroundPackage: String? = null
    
    /**
     * 카카오톡 패키지명
     */
    private val KAKAO_TALK_PACKAGE = "com.kakao.talk"
    
    /**
     * 포그라운드 변경 시 무시할 패키지 목록 (키보드/자체 앱/시스템 UI 등)
     * 이 목록의 패키지로 잠깐 포그라운드가 바뀌어도 플로팅 버튼 상태를 바꾸지 않음
     */
    private val FOREGROUND_IGNORE_PACKAGES = setOf(
        "com.mv.toki", // 자체 앱
        // 주요 키보드들
        "com.samsung.android.honeyboard",
        "com.sec.android.inputmethod",
        "com.google.android.inputmethod.latin",
        "com.google.android.apps.inputmethod.korean",
        // 시스템 UI
        "com.android.systemui"
    )
    
    /**
     * 백그라운드 토큰 갱신을 위한 Runnable
     */
    private val tokenRefreshRunnable = object : Runnable {
        override fun run() {
            checkAndRefreshToken()
            // 24시간마다 체크 (간소화된 주기)
            tokenRefreshHandler.postDelayed(this, 24 * 60 * 60 * 1000L)
        }
    }

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
                    val messageInputBarHeight = intent.getIntExtra(
                        KeyboardDetectionAccessibilityService.EXTRA_MESSAGE_INPUT_BAR_HEIGHT, 0
                    )
                    // 현재 포그라운드 앱 패키지명도 함께 받기
                    val packageName = intent.getStringExtra(KeyboardDetectionAccessibilityService.EXTRA_PACKAGE_NAME)
                    if (packageName != null) {
                        if (!FOREGROUND_IGNORE_PACKAGES.contains(packageName)) {
                            currentForegroundPackage = packageName
                            Log.d(TAG, "키보드 표시 브로드캐스트에서 포그라운드 앱 확인: $packageName")
                        } else {
                            Log.d(TAG, "무시 패키지이므로 포그라운드 업데이트 생략: $packageName")
                        }
                    } else {
                        Log.w(TAG, "키보드 표시 브로드캐스트에 포그라운드 앱 정보가 없음")
                    }
                    Log.d(TAG, "Keyboard shown broadcast received, keyboard height: $keyboardHeight, input bar height: $messageInputBarHeight, package: $packageName")
                    onKeyboardShown(keyboardHeight, messageInputBarHeight)
                }
                KeyboardDetectionAccessibilityService.ACTION_KEYBOARD_HIDDEN -> {
                    Log.d(TAG, "Keyboard hidden broadcast received")
                    
                    // 키보드 숨김 브로드캐스트를 받았지만, 실제로 키보드가 숨겨졌는지 다시 확인
                    // 지연을 두고 확인하여 키 입력 중 일시적인 키보드 상태 변경을 무시
                    handler.postDelayed({
                        val accessibilityService = KeyboardDetectionAccessibilityService.instance
                        val isKeyboardActuallyVisible = accessibilityService?.let {
                            KeyboardDetectionAccessibilityService.isKeyboardVisible
                        } ?: false
                        
                        val isKakaoTalk = currentForegroundPackage == KAKAO_TALK_PACKAGE
                        
                        Log.d(TAG, "키보드 숨김 재확인 - 실제 키보드 상태: $isKeyboardActuallyVisible, 카카오톡: $isKakaoTalk")
                        
                        // 실제로 키보드가 여전히 표시되어 있고 카카오톡이면 무시
                        if (isKeyboardActuallyVisible && isKakaoTalk) {
                            Log.d(TAG, "키보드가 실제로는 여전히 활성화되어 있으므로 키보드 숨김 무시")
                            return@postDelayed
                        }
                        
                        // OCR을 위해 키보드 숨김을 기다리고 있었다면 OCR 실행
                        if (isWaitingForKeyboardHide) {
                            Log.d(TAG, "키보드 숨김 확인됨, OCR 실행 시작")
                            isWaitingForKeyboardHide = false
                            
                            // 키보드가 완전히 숨겨진 후 약간의 지연을 두고 화면 캡처 실행
                            handler.postDelayed({
                                performOcrCapture()
                            }, 300) // 300ms 지연: 키보드 애니메이션이 완전히 끝나도록 대기
                            
                            // 키보드가 숨겨졌으므로 플로팅 버튼도 제거
                            onKeyboardHidden()
                        } else {
                            // 일반적인 키보드 숨김 처리
                            onKeyboardHidden()
                        }
                    }, 100) // 100ms 지연으로 키 입력 중 일시적인 상태 변경 무시
                }
                KeyboardDetectionAccessibilityService.ACTION_FOREGROUND_APP_CHANGED -> {
                    val packageName = intent?.getStringExtra(KeyboardDetectionAccessibilityService.EXTRA_PACKAGE_NAME)
                    Log.d(TAG, "포그라운드 앱 변경: $packageName")
                    val previousPackage = currentForegroundPackage
                    // 무시 패키지는 포그라운드로 취급하지 않음
                    if (packageName != null && FOREGROUND_IGNORE_PACKAGES.contains(packageName)) {
                        Log.d(TAG, "무시 패키지 포그라운드 변경 감지됨, 상태 유지: $packageName")
                        return
                    }
                    currentForegroundPackage = packageName
                    
                    // 카카오톡에서 다른 앱으로 변경된 경우에만 플로팅 버튼 숨김
                    // 다른 앱에서 카카오톡으로 변경되고 키보드가 활성화된 경우는 키보드 표시 브로드캐스트에서 처리됨
                    val isKakaoTalk = packageName == KAKAO_TALK_PACKAGE
                    if (!isKakaoTalk && previousPackage == KAKAO_TALK_PACKAGE) {
                        // 카카오톡에서 다른 앱으로 변경됨 - 플로팅 버튼 숨김
                        handler.post {
                            floatingView?.visibility = View.GONE
                            Log.d(TAG, "카카오톡에서 다른 앱으로 변경 - 플로팅 버튼 숨김: $packageName")
                        }
                    } else if (isKakaoTalk && isKeyboardVisible && floatingView != null) {
                        // 다른 앱에서 카카오톡으로 변경되고 키보드가 활성화된 경우 - 플로팅 버튼 표시
                        handler.post {
                            floatingView?.visibility = View.VISIBLE
                            Log.d(TAG, "다른 앱에서 카카오톡으로 변경되고 키보드 활성화 - 플로팅 버튼 표시")
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 현재 포그라운드 앱이 카카오톡인지 확인하여 플로팅 버튼 표시/숨김 제어
     */
    private fun updateFloatingButtonVisibility() {
        val isKakaoTalk = currentForegroundPackage == KAKAO_TALK_PACKAGE
        
        handler.post {
            if (isKakaoTalk) {
                // 카카오톡이 실행 중이고 키보드가 보이는 경우에만 플로팅 버튼 표시
                if (isKeyboardVisible) {
                    if (floatingView == null && !isRemovingAnimation) {
                        // 플로팅 버튼이 없으면 생성
                        val keyboardHeight = KeyboardDetectionAccessibilityService.keyboardHeight
                        val defaultInputBarHeight = (120 * resources.displayMetrics.density).toInt()
                        onKeyboardShown(keyboardHeight, defaultInputBarHeight)
                    } else {
                        // 플로팅 버튼이 있으면 표시
                        floatingView?.visibility = View.VISIBLE
                        Log.d(TAG, "카카오톡 실행 중 - 플로팅 버튼 표시")
                    }
                } else {
                    // 키보드가 안 보이면 숨김
                    floatingView?.visibility = View.GONE
                    Log.d(TAG, "카카오톡 실행 중이지만 키보드가 안 보임 - 플로팅 버튼 숨김")
                }
            } else {
                // 카카오톡이 아니면 플로팅 버튼 숨김
                floatingView?.visibility = View.GONE
                Log.d(TAG, "카카오톡이 아님 - 플로팅 버튼 숨김: $currentForegroundPackage")
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

        // 토큰 관리자 초기화
        tokenManager = com.mv.toki.auth.TokenManager.getInstance(this)

        updateScreenDimensions()
        registerKeyboardReceiver()
        
        // 백그라운드 토큰 갱신 시작 (24시간 후 첫 실행)
        startBackgroundTokenRefresh()
        
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
            addAction(KeyboardDetectionAccessibilityService.ACTION_FOREGROUND_APP_CHANGED)
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
        Log.d(TAG, "createFloatingView() 시작 - 현재 floatingView: $floatingView, isRemovingAnimation: $isRemovingAnimation")
        if (floatingView != null) {
            Log.d(TAG, "floatingView가 이미 존재하므로 생성 건너뜀")
            return
        }

        Log.d(TAG, "setupLayoutParams() 호출 중...")
        setupLayoutParams()
        Log.d(TAG, "setupLayoutParams() 완료")

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
                    onDragEnd = {
                        this@FloatingButtonService.finalizeButtonPosition()
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
            Log.d(TAG, "windowManager.addView 호출 전: floatingView=$floatingView, layoutParams.x=${layoutParams.x}, layoutParams.y=${layoutParams.y}")
            windowManager.addView(floatingView, layoutParams)
            Log.d(TAG, "windowManager.addView 성공 - 플로팅 버튼이 윈도우에 추가됨")
            
            // 생성 후 페이드 인 및 스케일 인 애니메이션 적용
            try {
                addFloatingViewWithAnimation(floatingView!!)
                Log.d(TAG, "플로팅 버튼 애니메이션 추가 완료")
            } catch (e: Exception) {
                Log.e(TAG, "애니메이션 추가 중 오류 발생 (플로팅 버튼은 이미 추가됨)", e)
                // 애니메이션 추가 실패해도 플로팅 버튼은 이미 추가되었으므로 null로 설정하지 않음
                // 단순히 표시만 하도록 함
                floatingView?.alpha = 1f
                floatingView?.scaleX = 1f
                floatingView?.scaleY = 1f
                floatingView?.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding floating view", e)
            Log.e(TAG, "예외 상세 정보: ${e.message}, ${e.stackTraceToString()}")
            floatingView = null  // 실패 시 null로 설정하여 재시도 가능하도록
        }
    }

    private fun removeFloatingView() {
        Log.d(TAG, "removeFloatingView 시작")
        
        floatingView?.let { view ->
            try {
                Log.d(TAG, "플로팅 뷰 애니메이션 제거 시도")
                // 애니메이션과 함께 제거 (위치 저장은 removeFloatingViewWithAnimation에서 처리)
                removeFloatingViewWithAnimation(view)
            } catch (e: Exception) {
                Log.e(TAG, "애니메이션 제거 실패, 즉시 제거 시도", e)
                // 애니메이션 실패 시 즉시 제거
                try {
                    // 위치 저장
                    lastButtonXPosition = layoutParams.x
                    lastButtonYPosition = layoutParams.y
                    windowManager.removeView(view)
                    floatingView = null
                    isRemovingAnimation = false
                    Log.d(TAG, "플로팅 뷰 즉시 제거 완료")
                } catch (removeException: Exception) {
                    Log.e(TAG, "플로팅 뷰 강제 제거 실패", removeException)
                    // 최후의 수단: 변수만 null로 설정
                    floatingView = null
                    isRemovingAnimation = false
                }
            }
        } ?: run {
            Log.d(TAG, "플로팅 뷰가 이미 null입니다")
        }
        
        Log.d(TAG, "removeFloatingView 완료")
    }
    
    /**
     * 애니메이션과 함께 플로팅 뷰 추가
     */
    private fun addFloatingViewWithAnimation(view: View) {
        // 초기 상태 설정 (투명하고 작게)
        view.alpha = 0f
        view.scaleX = 0.3f
        view.scaleY = 0.3f
        
        // 페이드 인 및 스케일 인 애니메이션
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300)
            .setInterpolator(android.view.animation.OvershootInterpolator(0.5f))
            .start()
    }
    
    /**
     * 애니메이션과 함께 플로팅 뷰 제거
     */
    private fun removeFloatingViewWithAnimation(view: View) {
        // 현재 위치 저장
        lastButtonXPosition = layoutParams.x
        lastButtonYPosition = layoutParams.y
        
        // 제거할 view가 현재 floatingView와 같은지 확인
        // 다르면 이미 새로 생성된 것이므로 null로 설정하지 않음
        val isCurrentView = (floatingView == view)
        Log.d(TAG, "removeFloatingViewWithAnimation 호출 - 제거할 view: $view, 현재 floatingView: $floatingView, 같은 뷰인가: $isCurrentView")
        
        // 애니메이션 시작 전에 상태 설정 (같은 뷰일 때만 null로 설정)
        isRemovingAnimation = true
        if (isCurrentView) {
            floatingView = null
            Log.d(TAG, "현재 floatingView를 null로 설정함")
        } else {
            Log.d(TAG, "다른 뷰이므로 floatingView를 null로 설정하지 않음")
        }
        
        // 페이드 아웃 및 스케일 아웃 애니메이션
        view.animate()
            .alpha(0f)
            .scaleX(0.3f)
            .scaleY(0.3f)
            .setDuration(200)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .withEndAction {
                // 애니메이션 완료 후 뷰 제거
                try {
                    windowManager.removeView(view)
                    Log.d(TAG, "플로팅 버튼 애니메이션 완료 후 제거됨 (view: $view, 현재 floatingView: $floatingView)")
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing view after animation", e)
                } finally {
                    // 애니메이션 완료 상태로 설정
                    // 제거한 view가 현재 floatingView와 같을 때만 상태 초기화
                    if (isCurrentView || floatingView == null) {
                        isRemovingAnimation = false
                        Log.d(TAG, "애니메이션 완료 - isRemovingAnimation을 false로 설정")
                    } else {
                        Log.d(TAG, "새로운 플로팅 버튼이 있으므로 isRemovingAnimation을 유지")
                    }
                }
            }
            .start()
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
            
            // 사용자가 드래그로 이동한 위치가 있으면 사용, 없으면 기본 위치 설정
            if (isUserMovedPosition && lastButtonXPosition > 0 && lastButtonYPosition > 0) {
                x = lastButtonXPosition
                y = lastButtonYPosition
                Log.d(TAG, "사용자가 이동한 위치 사용: x=$lastButtonXPosition, y=$lastButtonYPosition")
            } else {
                // 초기 위치를 화면 하단으로 설정 (메시지 입력바 위쪽 고려)
                val messageInputBarHeight = 120 // 메시지 입력바 예상 높이 (dp)
                val buttonHeight = 56 // 플로팅 버튼 높이 (dp) - 원본 크기
                val margin = 20 // 여백 (dp)
                
                val density = resources.displayMetrics.density
                val messageInputBarHeightPx = (messageInputBarHeight * density).toInt()
                val buttonHeightPx = (buttonHeight * density).toInt()
                val marginPx = (margin * density).toInt()
                
                x = screenWidth - 200 // 오른쪽에서 200px 떨어진 위치
                y = screenHeight - messageInputBarHeightPx - buttonHeightPx - marginPx
                Log.d(TAG, "기본 위치 설정: x=$x, y=$y")
            }
        }
    }

    private fun onKeyboardShown(keyboardHeight: Int, messageInputBarHeight: Int) {
        Log.d(TAG, "키보드 표시됨, 키보드 높이: $keyboardHeight, 입력바 높이: $messageInputBarHeight, 현재 포그라운드 앱: $currentForegroundPackage")
        isKeyboardVisible = true
        
        // 포그라운드 앱 정보가 없으면 접근성 서비스를 통해 직접 확인
        var foregroundPackage = currentForegroundPackage
        if (foregroundPackage == null) {
            val accessibilityService = KeyboardDetectionAccessibilityService.instance
            if (accessibilityService != null) {
                try {
                    foregroundPackage = accessibilityService.getCurrentForegroundPackageName()
                    if (foregroundPackage != null) {
                        currentForegroundPackage = foregroundPackage
                        Log.d(TAG, "접근성 서비스를 통해 포그라운드 앱 확인: $foregroundPackage")
                    } else {
                        Log.w(TAG, "접근성 서비스에서 포그라운드 앱을 가져올 수 없음")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "접근성 서비스에서 포그라운드 앱 확인 실패", e)
                }
            } else {
                Log.w(TAG, "접근성 서비스 인스턴스가 null")
            }
        }
        
        // 카카오톡이 실행 중인지 확인
        val isKakaoTalk = foregroundPackage == KAKAO_TALK_PACKAGE
        
        if (!isKakaoTalk) {
            Log.d(TAG, "카카오톡이 아님 - 플로팅 버튼 생성하지 않음: $foregroundPackage")
            // 카카오톡이 아니면 기존 플로팅 버튼도 숨김
            floatingView?.visibility = View.GONE
            return
        }
        
        // 플로팅 버튼이 없을 때 생성
        // isRemovingAnimation이 true이면 애니메이션 완료를 기다리지 않고 새로 생성
        if (floatingView == null) {
            Log.d(TAG, "플로팅 버튼 생성 중... (카카오톡: $isKakaoTalk, 포그라운드 앱: $foregroundPackage, isRemovingAnimation: $isRemovingAnimation)")
            
            // 애니메이션 중이면 상태 초기화
            if (isRemovingAnimation) {
                Log.d(TAG, "이전 애니메이션이 진행 중이었지만 플로팅 버튼을 다시 생성합니다")
                isRemovingAnimation = false
            }
            
            try {
                createFloatingView()
                
                // 생성 후 visibility 확인
                if (floatingView == null) {
                    Log.e(TAG, "플로팅 버튼 생성 실패! - createFloatingView() 호출 후 floatingView가 null")
                    // 재시도하지 않고 반환
                    return
                } else {
                    Log.d(TAG, "플로팅 버튼 생성 성공! - floatingView=$floatingView")
                }
            } catch (e: Exception) {
                Log.e(TAG, "createFloatingView() 호출 중 예외 발생", e)
                Log.e(TAG, "예외 상세: ${e.message}, ${e.stackTraceToString()}")
                return
            }
        } else {
            Log.d(TAG, "플로팅 버튼이 이미 존재함 - floatingView=$floatingView, isRemovingAnimation=$isRemovingAnimation")
        }
        
        // 플로팅 버튼 표시
        if (floatingView != null) {
            try {
                floatingView?.visibility = View.VISIBLE
                Log.d(TAG, "플로팅 버튼 표시 완료 (visibility: ${floatingView?.visibility})")
            } catch (e: Exception) {
                Log.e(TAG, "플로팅 버튼 표시 중 예외 발생", e)
            }
        } else {
            Log.e(TAG, "플로팅 버튼이 null이어서 표시할 수 없음 (isRemovingAnimation: $isRemovingAnimation)")
        }
        
        // 사용자가 드래그로 이동한 위치가 아닌 경우에만 키보드 높이에 맞춰 위치 조정
        floatingView?.let {
            if (!isUserMovedPosition) {
                val buttonHeight = 56 // 플로팅 버튼 높이 (dp) - 원본 크기
                val margin = 20 // 여백 (dp)
                
                // dp를 픽셀로 변환
                val density = resources.displayMetrics.density
                val buttonHeightPx = (buttonHeight * density).toInt()
                val marginPx = (margin * density).toInt()
                
                // 입력바 높이가 0이면 기본값 사용
                val actualInputBarHeight = if (messageInputBarHeight > 0) {
                    messageInputBarHeight
                } else {
                    (120 * density).toInt() // 기본 입력바 높이
                }
                
                // 키보드에 맞춰 Y 위치 조정
                val newY = screenHeight - keyboardHeight - actualInputBarHeight - buttonHeightPx - marginPx
                layoutParams.y = newY
                windowManager.updateViewLayout(it, layoutParams)
                
                Log.d(TAG, "키보드에 맞춰 위치 조정: x = ${layoutParams.x}, y = ${layoutParams.y}, 키보드높이 = $keyboardHeight, 입력바높이 = $actualInputBarHeight")
            } else {
                Log.d(TAG, "사용자가 이동한 위치 유지: x = ${layoutParams.x}, y = ${layoutParams.y}")
            }
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
            
            // 카카오톡이 실행 중인지 확인
            val isKakaoTalk = currentForegroundPackage == KAKAO_TALK_PACKAGE
            
            if (isKeyboardCurrentlyVisible && isKakaoTalk) {
                // 키보드가 이미 활성화되어 있고 카카오톡이 실행 중이면 플로팅 버튼 생성
                val keyboardHeight = KeyboardDetectionAccessibilityService.keyboardHeight
                // 초기 상태에서는 입력바 높이를 기본값으로 사용
                val defaultInputBarHeight = (120 * resources.displayMetrics.density).toInt()
                onKeyboardShown(keyboardHeight, defaultInputBarHeight)
            } else if (!isKakaoTalk) {
                Log.d(TAG, "초기 상태 확인: 카카오톡이 아님 - 플로팅 버튼 생성 안함: $currentForegroundPackage")
            }
        } else {
            Log.d(TAG, "접근성 서비스가 아직 초기화되지 않음, 플로팅 버튼 생성 안함")
        }
    }

    private fun updateButtonPositionByDrag(dragAmountX: Float, dragAmountY: Float) {
        layoutParams.x += dragAmountX.roundToInt()
        layoutParams.y += dragAmountY.roundToInt()
        
        // 화면 경계 체크 및 조정
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val buttonSize = (44 * resources.displayMetrics.density).toInt() // 44dp를 픽셀로 변환
        
        // X 좌표 경계 체크
        layoutParams.x = layoutParams.x.coerceIn(0, screenWidth - buttonSize)
        
        // Y 좌표 경계 체크 (상태바와 네비게이션 바 고려)
        val statusBarHeight = getStatusBarHeight()
        val navigationBarHeight = getNavigationBarHeight()
        val minY = statusBarHeight
        val maxY = screenHeight - navigationBarHeight - buttonSize
        
        layoutParams.y = layoutParams.y.coerceIn(minY, maxY)
        
        floatingView?.let { windowManager.updateViewLayout(it, layoutParams) }
        Log.d(TAG, "드래그 위치 업데이트: x=${layoutParams.x}, y=${layoutParams.y}")
    }
    
    /**
     * 드래그 종료 시 최종 위치를 저장하고 고정
     */
    private fun finalizeButtonPosition() {
        // 최종 위치 저장
        lastButtonXPosition = layoutParams.x
        lastButtonYPosition = layoutParams.y
        
        // 사용자가 드래그로 위치를 변경했음을 표시
        isUserMovedPosition = true
        
        Log.d(TAG, "플로팅 버튼 위치 고정: x=$lastButtonXPosition, y=$lastButtonYPosition, 사용자 이동: $isUserMovedPosition")
    }
    
    /**
     * 상태바 높이 가져오기
     */
    private fun getStatusBarHeight(): Int {
        return try {
            val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resourceId > 0) {
                resources.getDimensionPixelSize(resourceId)
            } else {
                // 기본값 (대부분의 기기에서 24dp)
                (24 * resources.displayMetrics.density).toInt()
            }
        } catch (e: Exception) {
            Log.w(TAG, "상태바 높이 가져오기 실패, 기본값 사용", e)
            (24 * resources.displayMetrics.density).toInt()
        }
    }
    
    /**
     * 네비게이션 바 높이 가져오기
     */
    private fun getNavigationBarHeight(): Int {
        return try {
            val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
            if (resourceId > 0) {
                resources.getDimensionPixelSize(resourceId)
            } else {
                // 기본값 (대부분의 기기에서 48dp)
                (48 * resources.displayMetrics.density).toInt()
            }
        } catch (e: Exception) {
            Log.w(TAG, "네비게이션 바 높이 가져오기 실패, 기본값 사용", e)
            (48 * resources.displayMetrics.density).toInt()
        }
    }

    private fun handleButtonClick() {
        Log.d(TAG, "handleButtonClick() started")

        // AccessibilityService 확인
        val accessibilityService = KeyboardDetectionAccessibilityService.instance
        if (accessibilityService == null) {
            Log.e(TAG, "AccessibilityService가 활성화되지 않음")
            Toast.makeText(this, "접근성 서비스를 활성화해주세요", Toast.LENGTH_LONG).show()
            return
        }
        
        // 키보드가 표시되어 있는지 확인
        val isKeyboardCurrentlyVisible = KeyboardDetectionAccessibilityService.isKeyboardVisible
        Log.d(TAG, "현재 키보드 상태: ${if (isKeyboardCurrentlyVisible) "표시됨" else "숨겨짐"}")
        
        if (isKeyboardCurrentlyVisible) {
            // 1. 먼저 입력창의 텍스트 지우기
            Log.d(TAG, "입력창 텍스트 지우기 시작")
            val clearSuccess = accessibilityService.clearInputField()
            if (clearSuccess) {
                Log.d(TAG, "입력창 텍스트 지우기 성공")
            } else {
                Log.w(TAG, "입력창 텍스트 지우기 실패 (계속 진행)")
            }
            
            // 2. 약간의 지연 후 키보드 숨김
            handler.postDelayed({
                Log.d(TAG, "키보드를 숨기고 OCR 실행 대기")
                isWaitingForKeyboardHide = true
                
                // 키보드 숨김 실행
                val hideSuccess = accessibilityService.hideKeyboard()
                if (!hideSuccess) {
                    Log.e(TAG, "키보드 숨김 실패")
                    isWaitingForKeyboardHide = false
                    Toast.makeText(this, "키보드를 숨기는데 실패했습니다.", Toast.LENGTH_SHORT).show()
                    return@postDelayed
                }
                
                // 키보드가 숨겨지면 keyboardStateReceiver에서 OCR이 자동 실행됩니다
                Log.d(TAG, "키보드 숨김 명령 전송 완료, 키보드 숨김 대기 중...")
            }, 150) // 150ms 딜레이: 텍스트 지우기가 완료될 시간
            
        } else {
            // 키보드가 이미 숨겨져 있으면 바로 OCR 실행
            Log.d(TAG, "키보드가 이미 숨겨져 있음, OCR 즉시 실행")
            performOcrCapture()
        }
    }
    
    /**
     * OCR을 위한 화면 캡처 및 텍스트 인식 실행
     * 키보드가 완전히 숨겨진 후 호출됩니다
     */
    private fun performOcrCapture() {
        Log.d(TAG, "performOcrCapture() 시작")
        
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
     * 상태바 영역을 제외한 부분만 OCR 수행
     */
    private fun processScreenshot(bitmap: Bitmap) {
        Log.d(TAG, "processScreenshot 호출됨: ${bitmap.width}x${bitmap.height}")
        
        try {
            // 상태바 영역을 제외한 Bitmap 생성
            val croppedBitmap = cropStatusBarFromBitmap(bitmap)
            Log.d(TAG, "상태바 제외 후 크기: ${croppedBitmap.width}x${croppedBitmap.height}")
            
            // 새로운 OcrClassifier를 사용하여 OCR 수행
            lifecycleScope.launch {
                try {
                    Log.d(TAG, "=== OCR 처리 시작 ===")
                    Log.d(TAG, "이미지 크기: ${croppedBitmap.width}x${croppedBitmap.height}")
                    
                    val visionText = OcrClassifier.recognize(croppedBitmap)
                    Log.d(TAG, "=== OCR 인식 완료 ===")
                    Log.d(TAG, "인식된 원본 텍스트 (ML Kit):")
                    Log.d(TAG, visionText.text)
                    Log.d(TAG, "텍스트 블록 수: ${visionText.textBlocks.size}")
                    
                    // 위치 기반으로 발신자 분류 (개선된 로직 사용)
                    val chatMessages = OcrClassifier.classify(visionText, croppedBitmap.width, croppedBitmap.height)
                    Log.d(TAG, "=== 발신자 분류 완료 (개선된 로직) ===")
                    Log.d(TAG, "분류된 메시지 수: ${chatMessages.size}개")
                    
                    if (chatMessages.isNotEmpty()) {
                        // 상세한 분류 결과 로그
                        Log.d(TAG, "=== 분류 결과 상세 ===")
                        chatMessages.forEachIndexed { index, message ->
                            val senderLabel = when (message.sender) {
                                Sender.ME -> "나"
                                Sender.OTHER -> "상대방"
                                Sender.UNKNOWN -> "미분류"
                            }
                            Log.d(TAG, "메시지 $index: [$senderLabel] ${message.text}")
                            Log.d(TAG, "  위치: left=${message.box.left}, top=${message.box.top}, right=${message.box.right}, bottom=${message.box.bottom}")
                        }
                        
                        // 통계 정보
                        val myMessages = chatMessages.count { it.sender == Sender.ME }
                        val otherMessages = chatMessages.count { it.sender == Sender.OTHER }
                        val unknownMessages = chatMessages.count { it.sender == Sender.UNKNOWN }
                        val debugMsg = "총 ${chatMessages.size}개 (나: $myMessages, 상대: $otherMessages, 미분류: $unknownMessages)"
                        
                        Log.d(TAG, "=== 분류 통계 ===")
                        Log.d(TAG, "나의 메시지: $myMessages")
                        Log.d(TAG, "상대방 메시지: $otherMessages")
                        Log.d(TAG, "미분류 메시지: $unknownMessages")
                        
                        Toast.makeText(this@FloatingButtonService, debugMsg, Toast.LENGTH_SHORT).show()
                        
                        // 원본 OCR 텍스트 사용하되, 분류 결과를 기반으로 발신자 라벨 추가
                        val originalOcrText = visionText.text
                        Log.d(TAG, "=== 원본 OCR 텍스트 (ML Kit) ===")
                        Log.d(TAG, originalOcrText)
                        
                        // 분류 결과를 기반으로 발신자 라벨이 포함된 텍스트 생성
                        val enhancedText = enhanceOcrTextWithLabels(originalOcrText, chatMessages)
                        Log.d(TAG, "=== 발신자 라벨 추가된 텍스트 ===")
                        Log.d(TAG, enhancedText)
                        
                        // 기존 OcrAnalysis 형식으로 변환 (발신자 라벨 추가된 텍스트 사용)
                        val ocrAnalysis = OcrAnalysis(
                            originalText = enhancedText,  // 발신자 라벨 추가된 텍스트 사용
                            textType = TextType.MESSAGE,
                            language = "ko",
                            entities = emptyList(),
                            keywords = emptyList(),
                            suggestions = emptyList(),
                            confidence = 0.8f,
                            chatAnalysis = null
                        )
                        
                        // 분석된 OCR 결과를 BottomSheet로 표시
                        showOcrBottomSheet(ocrAnalysis)
                    } else {
                        Log.d(TAG, "추출된 텍스트가 없습니다")
                        showPermissionRequestToast("텍스트를 찾을 수 없습니다.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "OCR 처리 중 오류", e)
                    showPermissionRequestToast("텍스트 인식에 실패했습니다.")
                } finally {
                    // Bitmap 메모리 해제
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                    if (!croppedBitmap.isRecycled) {
                        croppedBitmap.recycle()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "화면 캡처 결과 처리 중 오류", e)
            showPermissionRequestToast("화면 캡처 처리 중 오류가 발생했습니다.")
            
            // Bitmap 메모리 해제
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
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
     * Bitmap에서 상태바 영역을 제외한 부분만 크롭
     * 상태바(wifi, 배터리, 시간 등)가 OCR 인식되는 것을 방지
     */
    private fun cropStatusBarFromBitmap(originalBitmap: Bitmap): Bitmap {
        return try {
            // 상태바 높이 가져오기
            val statusBarHeight = getStatusBarHeight()
            Log.d(TAG, "상태바 높이: $statusBarHeight px")
            
            // 상태바 영역을 제외한 나머지 영역만 크롭
            val croppedHeight = originalBitmap.height - statusBarHeight
            
            // 크롭할 영역이 유효한지 확인
            if (croppedHeight <= 0 || statusBarHeight >= originalBitmap.height) {
                Log.w(TAG, "상태바 높이가 비정상적이거나 화면 높이를 초과함, 원본 Bitmap 사용")
                return originalBitmap
            }
            
            // Bitmap 크롭 (x=0, y=statusBarHeight부터 시작, 전체 너비, 상태바 제외 높이)
            val croppedBitmap = Bitmap.createBitmap(
                originalBitmap,
                0,                          // x 시작점
                statusBarHeight,            // y 시작점 (상태바 높이만큼 아래부터)
                originalBitmap.width,       // 너비 (전체)
                croppedHeight               // 높이 (상태바 제외)
            )
            
            Log.d(TAG, "🔪 Bitmap 크롭 완료: 원본=${originalBitmap.width}x${originalBitmap.height}, " +
                    "크롭=${croppedBitmap.width}x${croppedBitmap.height}, " +
                    "제외된 높이=$statusBarHeight")
            
            croppedBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Bitmap 크롭 중 오류 발생, 원본 Bitmap 사용", e)
            originalBitmap
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
        
        // 채팅 메시지 형식으로 정리 (항상 시도)
        // 이유: 텍스트 타입 분류가 부정확할 수 있으므로 항상 메시지 포맷팅 시도
        val formattedText = formatChatMessagesLegacy(visionText)
        Log.d(TAG, "정리된 텍스트 (길이=${formattedText.length}): ${formattedText.take(100)}...")
        
        // 채팅 분석 (항상 수행하여 상대방 이름 추출)
        val chatAnalysis = analyzeChatMessage(visionText, originalText)
        Log.d(TAG, "채팅 분석 완료 - 발신자: ${chatAnalysis.sender}, 상대방 이름: ${chatAnalysis.otherPersonName}")
        
        // 추천 답변 생성 (채팅 분석 결과 반영)
        val suggestions = generateSmartSuggestions(formattedText, textType, chatAnalysis)
        Log.d(TAG, "생성된 추천: ${suggestions.size}개")
        
        // 신뢰도 계산
        val confidence = calculateConfidence(visionText)
        Log.d(TAG, "신뢰도: $confidence")
        
        return OcrAnalysis(
            originalText = formattedText, // 정리된 텍스트 사용
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
     * 원본 OCR 텍스트에 분류 결과를 기반으로 발신자 라벨을 추가하는 함수
     */
    private fun enhanceOcrTextWithLabels(originalText: String, chatMessages: List<ChatMessage>): String {
        if (chatMessages.isEmpty()) {
            Log.d(TAG, "분류된 메시지가 없어서 원본 텍스트 반환")
            return originalText
        }
        
        Log.d(TAG, "=== 발신자 라벨 추가 시작 ===")
        Log.d(TAG, "원본 텍스트 길이: ${originalText.length}")
        Log.d(TAG, "분류된 메시지 수: ${chatMessages.size}")
        
        // 원본 텍스트를 라인별로 분리
        val originalLines = originalText.split("\n").map { it.trim() }
        val enhancedLines = mutableListOf<String>()
        
        // 각 분류된 메시지에 대해 원본 텍스트에서 해당하는 부분을 찾아 라벨 추가
        for (message in chatMessages) {
            val senderLabel = when (message.sender) {
                Sender.ME -> "[나]"
                Sender.OTHER -> "[상대방]"
                Sender.UNKNOWN -> "[미분류]"
            }
            
            // 메시지 텍스트가 원본 텍스트에 포함되어 있는지 확인
            val messageText = message.text.trim()
            val foundInOriginal = originalLines.any { line ->
                line.contains(messageText, ignoreCase = true) || 
                messageText.contains(line, ignoreCase = true)
            }
            
            if (foundInOriginal) {
                // 원본에서 해당 메시지를 찾아서 라벨 추가
                val enhancedMessage = "$senderLabel\n$messageText"
                enhancedLines.add(enhancedMessage)
                Log.d(TAG, "라벨 추가: $enhancedMessage")
            } else {
                // 원본에서 찾지 못한 경우 그대로 추가
                val enhancedMessage = "$senderLabel\n$messageText"
                enhancedLines.add(enhancedMessage)
                Log.d(TAG, "원본에서 찾지 못함, 그대로 추가: $enhancedMessage")
            }
        }
        
        val result = enhancedLines.joinToString("\n\n")
        Log.d(TAG, "=== 발신자 라벨 추가 완료 ===")
        Log.d(TAG, "결과 길이: ${result.length}")
        
        return result
    }
    
    /**
     * ChatMessage 리스트를 텍스트로 변환
     */
    private fun formatChatMessages(chatMessages: List<ChatMessage>): String {
        Log.d(TAG, "=== 텍스트 포맷팅 시작 ===")
        Log.d(TAG, "입력 메시지 수: ${chatMessages.size}")
        
        val formattedText = chatMessages.joinToString("\n\n") { message ->
            val senderLabel = when (message.sender) {
                Sender.ME -> "나"
                Sender.OTHER -> "상대방"
                Sender.UNKNOWN -> "미분류"
            }
            val formattedMessage = "[$senderLabel]\n${message.text}"
            Log.d(TAG, "포맷팅: $formattedMessage")
            formattedMessage
        }
        
        Log.d(TAG, "=== 텍스트 포맷팅 완료 ===")
        Log.d(TAG, "최종 길이: ${formattedText.length}자")
        
        return formattedText
    }

    /**
     * 기존 formatChatMessages 함수 (Vision Text 기반) - 사용하지 않음
     */
    private fun formatChatMessagesLegacy(visionText: Text): String {
        Log.d(TAG, "=== 채팅 메시지 형식 정리 시작 (말풍선 기준) ===")
        
        val screenWidth = resources.displayMetrics.widthPixels
        val screenCenter = screenWidth / 2
        val screenHeight = resources.displayMetrics.heightPixels
        val statusBarHeight = getStatusBarHeight()
        
        Log.d(TAG, "화면 정보: 너비=$screenWidth, 중앙=$screenCenter, 높이=$screenHeight, 상태바=$statusBarHeight")
        
        // TextBlock을 분석하여 이름 블록과 메시지 블록으로 분류
        data class BlockInfo(
            val block: Text.TextBlock,
            val y: Int,
            val centerX: Int,
            val text: String,
            val isLeftSide: Boolean,
            val isSmallFont: Boolean // 작은 폰트인지 (이름일 가능성)
        )
        
        val allBlocks = mutableListOf<BlockInfo>()
        
        // 모든 블록 수집 및 분류
        for (block in visionText.textBlocks) {
            val blockBoundingBox = block.boundingBox ?: continue
            val blockY = blockBoundingBox.top
            val blockCenterX = (blockBoundingBox.left + blockBoundingBox.right) / 2
            val blockHeight = blockBoundingBox.height()
            
            // 상태바 영역 제외
            if (blockY < statusBarHeight) {
                Log.d(TAG, "🚫 상태바 영역 블록 제외 (Y=$blockY)")
                    continue
                }
                
            val blockText = block.lines.joinToString("\n") { it.text.trim() }
            
            // 빈 블록 제외 (공백, 빈 문자열)
            if (blockText.trim().isEmpty()) {
                Log.d(TAG, "🚫 빈 블록 제외 (Y=$blockY)")
                    continue
                }
                
            // 시간이나 UI 요소 블록 제외
            if (shouldSkipText(blockText)) {
                Log.d(TAG, "블록 스킵: $blockText")
                continue
            }
            
            val isLeftSide = blockCenterX < screenCenter
            
            // 작은 폰트 블록인지 확인 (평균 라인 높이 기준)
            val avgLineHeight = block.lines.mapNotNull { it.boundingBox?.height() }.average()
            val isSmallFont = avgLineHeight < 40 // 40px 미만은 작은 폰트
            
            // 블록의 실제 텍스트 길이 확인 (공백 제거 후)
            val cleanTextLength = blockText.replace("\\s".toRegex(), "").length
            if (cleanTextLength == 0) {
                Log.d(TAG, "🚫 내용 없는 블록 제외 (공백만): '$blockText'")
                continue
            }
            
            // 블록 크기가 너무 작은 경우 제외 (아이콘, 점 등)
            val blockWidth = blockBoundingBox.width()
            if (blockWidth < 20 || blockHeight < 10) {
                Log.d(TAG, "🚫 너무 작은 블록 제외: '$blockText' (${blockWidth}x${blockHeight})")
                continue
            }
            
            // 텍스트 길이가 너무 짧은 경우 추가 검증
            if (cleanTextLength < 2) {
                // 1글자는 의미있는 한글만 허용
                if (!blockText.matches(Regex("^[가-힣ㅋㅎㅠㅜ]$"))) {
                    Log.d(TAG, "🚫 1글자 무의미 블록 제외: '$blockText'")
                    continue
                }
            }
            
            allBlocks.add(BlockInfo(block, blockY, blockCenterX, blockText, isLeftSide, isSmallFont))
            
            val position = if (isLeftSide) "왼쪽" else "오른쪽"
            val fontType = if (isSmallFont) "작은폰트" else "큰폰트"
            Log.d(TAG, "📦 블록: '${blockText.take(20)}...' | 위치=$position | Y=$blockY | 높이=$avgLineHeight | $fontType")
        }
        
        // Y 좌표 순으로 정렬
        allBlocks.sortBy { it.y }
        
        // 이름 블록과 메시지 블록 매칭
        data class MessageBubble(
            val text: String,
            val y: Int,
            val senderName: String
        )
        
        val bubbles = mutableListOf<MessageBubble>()
        var lastLeftSenderName: String? = null
        
        // 화면 상단 채팅방 제목에서 이름 추출 (백업용)
        val chatRoomName = extractOtherPersonName(visionText)
        if (chatRoomName != null) {
            Log.d(TAG, "📌 채팅방 제목에서 이름 발견: $chatRoomName")
            lastLeftSenderName = chatRoomName // 기본 이름으로 설정
        }
        
        var i = 0
        while (i < allBlocks.size) {
            val currentBlock = allBlocks[i]
            
            if (!currentBlock.isLeftSide) {
                // 오른쪽 블록 = 내 메시지
                bubbles.add(MessageBubble(currentBlock.text, currentBlock.y, "나"))
                Log.d(TAG, "💬 오른쪽 말풍선: [나] ${currentBlock.text.take(30)}...")
                i++
                
            } else {
                // 왼쪽 블록 처리
                // 작은 폰트 + 이름 패턴 + 다음 블록이 왼쪽 = 이름 블록
                if (currentBlock.isSmallFont && 
                    isValidNamePattern(currentBlock.text) &&
                    i + 1 < allBlocks.size) {
                    
                    val nextBlock = allBlocks[i + 1]
                    val yDiff = nextBlock.y - currentBlock.y
                    
                    // 다음 블록이 왼쪽이고 가까우면 이름+메시지 조합
                    if (nextBlock.isLeftSide && yDiff < 100) {
                        // 현재 블록 = 이름, 다음 블록 = 메시지
                        val senderName = currentBlock.text
                        lastLeftSenderName = senderName
                        
                        bubbles.add(MessageBubble(nextBlock.text, nextBlock.y, senderName))
                        Log.d(TAG, "💬 왼쪽 이름+말풍선: [$senderName] ${nextBlock.text.take(30)}... (Y차이=$yDiff)")
                        
                        i += 2 // 이름 블록과 메시지 블록 둘 다 처리
                        continue
                    }
                }
                
                // 이름 블록이 아니거나 다음 블록이 없으면 일반 메시지
                if (lastLeftSenderName != null) {
                    // 이전에 발견된 이름 또는 채팅방 제목 사용
                    bubbles.add(MessageBubble(currentBlock.text, currentBlock.y, lastLeftSenderName))
                    Log.d(TAG, "💬 왼쪽 말풍선 (이전 이름 사용): [$lastLeftSenderName] ${currentBlock.text.take(30)}...")
                } else {
                    // 이름을 찾지 못한 경우 - 메시지만 표시 (발신자 표시 안함)
                    Log.d(TAG, "⚠️ 왼쪽 말풍선이지만 이름 없음, 메시지만 추가: ${currentBlock.text.take(30)}...")
                    // 이름 없이 메시지만 추가하지 않음 (혼란 방지)
                    // 또는 화면 상단 채팅방 제목을 추출하여 사용
                }
                i++
            }
        }
        
        // Y좌표 순으로 정렬
        bubbles.sortBy { it.y }
        
        Log.d(TAG, "=== 말풍선 정렬 후 (총 ${bubbles.size}개) ===")
        bubbles.forEachIndexed { index, bubble ->
            Log.d(TAG, "$index. [Y=${bubble.y}] [${bubble.senderName}] ${bubble.text.take(30)}...")
        }
        
        // 연속된 같은 발신자의 메시지 병합
        Log.d(TAG, "=== 메시지 병합 시작 ===")
        val mergedMessages = mutableListOf<Pair<String, String>>()
        var currentSender = ""
        var currentMessage = ""
        
        for (bubble in bubbles) {
            val trimmedSender = bubble.senderName.trim()
            val trimmedMessage = bubble.text.trim()
            
            Log.d(TAG, "처리 중: sender='$trimmedSender', message='$trimmedMessage'")
            
            // 빈 발신자나 빈 메시지 스킵
            if (trimmedSender.isEmpty() || trimmedMessage.isEmpty()) {
                Log.d(TAG, "  → ❌ 빈 발신자/메시지 스킵")
                continue
            }
            
            // 의미없는 패턴 제외
            val meaninglessPatterns = listOf(
                "나", "상대방", "나상대방", "상대방나",
                "나나", "상대방상대방"
            )
            if (meaninglessPatterns.contains(trimmedMessage)) {
                Log.d(TAG, "  → ❌ 의미없는 메시지 스킵: '$trimmedMessage'")
                continue
            }
            
            // 발신자와 메시지가 동일한 경우 제외
            if (trimmedSender == trimmedMessage) {
                Log.d(TAG, "  → ❌ 발신자=메시지 스킵: [$trimmedSender]")
                continue
            }
            
            if (trimmedSender == currentSender) {
                // 같은 발신자면 줄바꿈으로 메시지 병합
                currentMessage += "\n$trimmedMessage"
                Log.d(TAG, "  → ✓ 같은 발신자, 병합")
            } else {
                // 다른 발신자면 이전 메시지 저장
                if (currentSender.isNotEmpty() && currentMessage.isNotEmpty()) {
                    mergedMessages.add(Pair(currentSender, currentMessage.trim()))
                    Log.d(TAG, "  → ✓ 이전 메시지 저장: [$currentSender]")
                }
                currentSender = trimmedSender
                currentMessage = trimmedMessage
                Log.d(TAG, "  → 새 발신자: '$trimmedSender'")
            }
        }
        
        // 마지막 메시지 저장
        if (currentSender.isNotEmpty() && currentMessage.isNotEmpty()) {
            mergedMessages.add(Pair(currentSender, currentMessage.trim()))
            Log.d(TAG, "✓ 마지막 메시지 저장: [$currentSender]")
        }
        
        Log.d(TAG, "=== 병합된 메시지 목록 (총 ${mergedMessages.size}개) ===")
        mergedMessages.forEachIndexed { index, (sender, message) ->
            Log.d(TAG, "#$index: [$sender] ${message.take(50)}...")
        }
        
        // 형식화된 문자열로 변환
        val formattedText = mergedMessages.joinToString("\n\n") { (sender, message) ->
            "[$sender]\n$message"
        }
        
        Log.d(TAG, "=== 채팅 메시지 정리 완료 ===")
        Log.d(TAG, "최종 결과:\n$formattedText")
        
        if (formattedText.isEmpty()) {
            Log.w(TAG, "형식화된 텍스트가 비어있음!")
            Log.w(TAG, "원본 텍스트 길이: ${visionText.text.length}")
            
            // 원본 텍스트도 간단하게 포맷팅 시도
            if (visionText.text.isNotEmpty()) {
                Log.w(TAG, "원본 텍스트를 간단히 포맷팅하여 반환")
                return "[텍스트]\n${visionText.text}"
            }
            
            return visionText.text
        }
        
        return formattedText
    }
    
    /**
     * 유효한 이름 패턴인지 확인
     * 말풍선 첫 번째 라인에서만 사용 (매우 엄격한 기준)
     */
    private fun isValidNamePattern(text: String): Boolean {
        // 길이 제한: 2-4글자 (이름은 보통 2-4글자)
        if (text.length < 2 || text.length > 4) return false
        
        // 숫자, 특수문자 제외
        if (text.contains(Regex("\\d"))) return false
        if (text.contains(Regex("[!@#$%^&*()_+=\\[\\]{}|;:'\",.<>?/~`]"))) return false
        
        // 조사가 붙어있으면 이름이 아님
        if (text.matches(Regex(".*[은는이가을를의에게서와과도만부터까지요]$"))) return false
        
        // 일반적인 대화 단어들 제외 (더 많은 단어 추가)
        val commonChatWords = listOf(
            // 감탄사/반응
            "안녕", "좋아", "싫어", "그래", "응", "어", "네", "아니", "맞아", "틀려",
            "하하", "ㅋㅋ", "ㅎㅎ", "ㅠㅠ", "ㅜㅜ",
            
            // 일반 대화
            "그래서", "그런데", "근데", "그럼", "그치", "맞지", "아니지", "정말", "진짜",
            "완전", "너무", "엄청", "되게", "진짜", "참", "좀", "약간", "조금",
            "오늘", "내일", "어제", "지금", "나중", "이따", "곧", "다음",
            "여기", "거기", "저기", "어디", "언제", "누구", "뭐", "왜", "어떻게",
            
            // 자주 쓰이는 단어
            "그래야", "여자", "만", "ㅋ", "여기서", "이제", "미혼", "이야",
            "원웅이", "곧", "이네", "얼마", "안남음", "다음주", "다음주네", "오", "아",
            "유부남", "이", "사람들", "프로필", "죄다", "자식",
            "영포티", "로서", "화가", "난다", "준비는", "잘", "되가나",
            "그룹채팅", "리마인드", "요번주", "일요일", "노량진", "시",
        
        // UI 요소
            "메시지", "입력", "검색", "전송", "답장", "채팅", "통화", "설정",
            "사진", "동영상", "파일", "음성", "알림", "확인", "취소", "저장",
            
            // 명시적으로 제외
            "나", "상대방"
        )
        if (commonChatWords.any { text == it }) return false
        
        // 한글 2-4글자만 이름으로 인정
        if (text.matches(Regex("^[가-힣]{2,4}$"))) {
            // 반복 글자 제외 (예: "하하")
            if (text.length == 2 && text[0] == text[1]) return false
            
            // 성씨로 시작하는지 확인 (선택적 - 더 엄격하게)
            val familyNames = listOf(
                "김", "이", "박", "최", "정", "강", "조", "윤", "장", "임", 
                "한", "오", "서", "신", "권", "황", "안", "송", "전", "고",
                "문", "양", "손", "배", "백", "허", "유", "남", "심", "노",
                "하", "곽", "성", "차", "주", "우", "구", "원", "민", "진"
            )
            val firstChar = text[0].toString()
            if (familyNames.contains(firstChar)) {
                Log.d(TAG, "✓ 성씨로 시작하는 이름 패턴: $text")
                return true
            }
            
            // 성씨가 아니더라도 이름 패턴이면 인정 (더 보수적으로)
            // 단, 3글자 이상이어야 함 (2글자는 일반 단어일 가능성 높음)
            if (text.length >= 3) {
                Log.d(TAG, "✓ 3글자 이상 한글 이름 가능성: $text")
                return true
            }
            
            return false
        }
        
        // 영문 이름 (드물지만 지원)
        if (text.matches(Regex("^[a-zA-Z]+\\s?[a-zA-Z]*$")) && text.length in 3..8) {
            return true
        }
        
        return false
    }
    
    /**
     * 폰트 크기를 이용한 이름 감지
     * 상대방 이름은 메시지보다 작은 폰트 크기를 가짐
     */
    @Suppress("UNUSED_PARAMETER")
    private fun isPossibleNameByFontSize(text: String, fontSize: Float, allLines: List<TextLine>): Boolean {
        // 길이 제한: 2-8글자
        if (text.length < 2 || text.length > 8) return false
        
        // 시간 패턴 제외
        if (text.matches(Regex("(오전|오후)?\\s*\\d{1,2}:\\d{2}"))) return false
        if (text.matches(Regex("\\d{1,2}시\\s*\\d{1,2}분"))) return false
        
        // 숫자가 포함된 텍스트 제외
        if (text.contains(Regex("\\d"))) return false
        
        // 특수문자 제외
        if (text.contains(Regex("[!@#$%^&*()_+=\\[\\]{}|;:'\",.<>?/~`]"))) return false
        
        // 제외 단어들 (일반 메시지로 자주 쓰이는 단어)
        val commonWords = listOf(
            "메시지", "입력", "검색", "전송", "답장", "채팅", "통화", "설정",
            "사진", "동영상", "파일", "음성", "알림", "확인", "취소", "저장",
            "그래야", "여자", "만", "ㅋ", "여기서", "이제", "미혼", "이야",
            "곧", "이네", "얼마", "안남음", "다음주", "다음주네", "네", "오", "아",
            "유부남", "이", "사람들", "프로필", "죄다", "자식",
            "영포티", "로서", "화가", "난다", "준비는", "잘", "되가나",
            "그룹채팅", "리마인드", "요번주", "일요일", "노량진", "시",
            "나", "상대방" // "나"와 "상대방"은 이름이 아님
        )
        if (commonWords.any { text == it }) return false
        
        // 조사가 붙어있는 경우 제외
        if (text.matches(Regex(".*[은는이가을를의에게서와과도만부터까지요]$"))) return false
        
        // 한글 이름 (2-4글자)
        if (text.matches(Regex("^[가-힣]{2,4}$"))) {
            // 반복 글자 제외
            if (text.length == 2 && text[0] == text[1]) return false
            
            // 폰트 크기는 참고용으로만 사용 (필수 조건 아님)
            val averageFontSize = calculateAverageFontSize(allLines)
            val isSmallerThanAverage = fontSize < averageFontSize * 0.85f
            
            if (isSmallerThanAverage) {
                Log.d(TAG, "작은 폰트 크기로 이름 확신: '$text' (폰트크기: $fontSize, 평균: $averageFontSize)")
            }
            
            // 한글 2-4글자는 기본적으로 이름으로 인정 (제외 단어 아니면)
            return true
        }
        
        // 영문 이름 (2-8글자)
        if (text.matches(Regex("^[a-zA-Z]+\\s?[a-zA-Z]*$")) && text.length in 2..8) {
            return true
        }
        
        // 한글+영문 혼합 이름
        if (text.matches(Regex("^[가-힣]{1,3}[a-zA-Z]{1,5}$")) || 
            text.matches(Regex("^[a-zA-Z]{1,5}[가-힣]{1,3}$"))) {
            return true
        }
        
        return false
    }
    
    /**
     * 모든 텍스트 라인의 평균 폰트 크기 계산
     */
    private fun calculateAverageFontSize(allLines: List<TextLine>): Float {
        if (allLines.isEmpty()) return 30f
        
        val totalFontSize = allLines.sumOf { it.fontSize.toDouble() }
        return (totalFontSize / allLines.size).toFloat()
    }
    
    /**
     * 텍스트를 건너뛸지 판단 (시간, URL, UI 요소, 상태바 내용 등)
     * 개선: 상태바 내용(시간, WiFi, 배터리 등) 제외
     */
    private fun shouldSkipText(text: String): Boolean {
        val trimmedText = text.trim()
        
        // 빈 텍스트는 제외
        if (trimmedText.isEmpty()) return true
        
        // 공백, 줄바꿈, 탭만 있는 경우 제외
        if (trimmedText.replace("\\s".toRegex(), "").isEmpty()) {
            Log.d(TAG, "공백만 있는 텍스트 제외")
            return true
        }
        
        // 시간 패턴 (카카오톡 시간 형식 + 상태바 시간)
        if (trimmedText.matches(Regex("^(오전|오후)\\s*\\d{1,2}:\\d{2}$"))) {
            Log.d(TAG, "시간 패턴 감지 (오전/오후): $trimmedText")
            return true
        }
        if (trimmedText.matches(Regex("^\\d{1,2}:\\d{2}$"))) {
            Log.d(TAG, "시간 패턴 감지 (숫자만): $trimmedText")
            return true
        }
        if (trimmedText.matches(Regex("^\\d{1,2}시\\s*\\d{1,2}분$"))) {
            Log.d(TAG, "시간 패턴 감지 (시분): $trimmedText")
            return true
        }
        
        // 날짜 패턴
        if (trimmedText.matches(Regex("^\\d{4}[년.-]\\d{1,2}[월.-]\\d{1,2}[일]?$"))) {
            Log.d(TAG, "날짜 패턴 감지: $trimmedText")
            return true
        }
        
        // 숫자만 (1-6글자) - 시간이나 날짜일 가능성
        if (trimmedText.matches(Regex("^[\\d\\s:]+$")) && trimmedText.length <= 6) {
            Log.d(TAG, "숫자만 패턴 감지: $trimmedText")
            return true
        }
        
        // 단일 자음/모음만 있는 경우 제외
        if (trimmedText.matches(Regex("^[ㄱ-ㅎㅏ-ㅣ]+$"))) return true
        
        // UI 요소 아이콘/기호
        val uiSymbols = listOf("←", "→", "+", "×", "•", "⋮", "☰", "[]", "[", "]", "1")
        if (uiSymbols.any { trimmedText == it }) return true
        
        // UI 요소 텍스트 (정확히 일치하는 경우만)
        val uiElements = listOf(
            "메시지 입력", "검색", "전송", "답장", "채팅", "통화", "설정",
            "사진", "동영상", "파일", "음성", "위치", "연락처",
            "읽음", "안읽음", "확인", "취소", "저장", "삭제", "안 읽음"
        )
        if (uiElements.any { trimmedText == it }) return true
        
        // ✅ 상태바 키워드 필터링 삭제됨
        // 이유 1: 대화 내용에 "WiFi", "배터리", "%" 등이 포함될 수 있음
        // 이유 2: 상태바는 이미 Y 좌표로 제외됨 (lineY < statusBarHeight)
        // 이유 3: Bitmap 크롭으로도 제외됨 (cropStatusBarFromBitmap)
        
        // 의미없는 메시지 패턴 제외
        val meaninglessPatterns = listOf(
            "나", "상대방", "나상대방", "상대방나",
            "나나", "상대방상대방", "나나상대방", "상대방나나",
            "[]", "[나]", "[상대방]", "나[]", "상대방[]"
        )
        if (meaninglessPatterns.any { trimmedText == it || trimmedText.contains(it) }) {
            // "나"나 "상대방"이 포함된 짧은 문자열은 제외
            if (trimmedText.length <= 10 && 
                (trimmedText.contains("나") || trimmedText.contains("상대방")) &&
                !trimmedText.matches(Regex(".*[가-힣]{3,}.*"))) {
                return true
            }
        }
        
        // 1글자 메시지 중 의미있는 것들은 포함
        // 예: "네", "ㅋ", "ㅎ", "오", "아" 등은 의미있는 메시지
        if (trimmedText.length == 1) {
            // 완성된 한글 1글자는 포함 (예: "네", "오", "아")
            if (trimmedText.matches(Regex("^[가-힣]$"))) return false
            
            // 영문 1글자는 제외 (대부분 아이콘이나 버튼)
            if (trimmedText.matches(Regex("^[a-zA-Z]$"))) return true
            
            // 특수문자 1개는 제외
            if (trimmedText.matches(Regex("^[^가-힣a-zA-Z0-9]$"))) return true
        }
        
        return false
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
     * OCR 결과에서 상대방 이름 추출
     * 화면 최상단 중앙에서만 이름을 찾습니다 (채팅방 제목)
     */
    private fun extractOtherPersonName(visionText: Text): String? {
        Log.d(TAG, "=== 상대방 이름 추출 시작 ===")
        
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val screenCenter = screenWidth / 2
        
        // 상태바를 제외한 화면 최상단 영역만 검색
        val statusBarHeight = getStatusBarHeight()
        val topSearchStart = statusBarHeight  // 상태바 바로 아래부터
        val topSearchEnd = statusBarHeight + (screenHeight * 0.08f).toInt()  // 상단 8%까지만
        
        // 화면 중앙 영역만 검색 (채팅방 제목은 중앙에 위치)
        val centerLeftBound = (screenWidth * 0.2f).toInt()
        val centerRightBound = (screenWidth * 0.8f).toInt()
        
        Log.d(TAG, "이름 검색 영역: Y=$topSearchStart~$topSearchEnd, X=$centerLeftBound~$centerRightBound")
        
        // 이름 후보들을 저장
        val nameCandidates = mutableListOf<Triple<String, Int, Int>>()  // (이름, Y좌표, 신뢰도)
        
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val lineText = line.text.trim()
                val boundingBox = line.boundingBox ?: continue
                val lineY = boundingBox.top
                val lineCenter = (boundingBox.left + boundingBox.right) / 2
                
                // Y 좌표 필터
                if (lineY < topSearchStart || lineY > topSearchEnd) continue
                
                // X 좌표 필터
                if (lineCenter < centerLeftBound || lineCenter > centerRightBound) continue
                
                // 이름 패턴 확인
                if (lineText.length in 2..8 && lineText.matches(Regex("^[가-힣]{2,4}$"))) {
                    var confidence = 100
                    
                    // 화면 중앙에 가까울수록 신뢰도 증가
                    val distanceFromCenter = Math.abs(lineCenter - screenCenter).toFloat()
                    val maxDistance = (screenWidth * 0.3f)
                    val centerScore = (1.0f - (distanceFromCenter / maxDistance).coerceIn(0f, 1f)) * 50
                    confidence += centerScore.toInt()
                    
                    Log.d(TAG, "✓ 이름 후보: '$lineText' (Y=$lineY, X중앙=$lineCenter, 신뢰도=$confidence)")
                    nameCandidates.add(Triple(lineText, lineY, confidence))
                }
            }
        }
        
        // 신뢰도가 가장 높은 후보 선택
        val bestCandidate = nameCandidates.maxByOrNull { it.third }
        
        return if (bestCandidate != null && bestCandidate.third >= 120) {
            Log.d(TAG, "✓✓✓ 상대방 이름 확정: '${bestCandidate.first}' (신뢰도=${bestCandidate.third})")
            bestCandidate.first
        } else {
            Log.d(TAG, "이름 후보를 찾을 수 없음")
            null
        }
    }
    
    /**
     * 채팅 메시지 분석 (간소화된 버전)
     */
    private fun analyzeChatMessage(visionText: Text, text: String): ChatAnalysis {
        Log.d(TAG, "채팅 메시지 분석 시작: $text")
        
        // 기본값으로 설정 (새로운 OcrClassifier에서 발신자 구분을 처리)
        val sender = ChatSender.UNKNOWN
        val position = ChatPosition.UNKNOWN
        val timeInfo = extractTimeInfo(text)
        val messageType = analyzeMessageType(text)
        val isGroupChat = detectGroupChat(text)
        val participants = extractParticipants(text)
        val confidence = 0.8f // 새로운 분류기는 높은 신뢰도
        val otherPersonName = extractOtherPersonName(visionText)
        
        Log.d(TAG, "채팅 분석 완료 - 발신자: $sender, 상대방 이름: $otherPersonName")
        
        return ChatAnalysis(
            sender = sender,
            confidence = confidence,
            position = position,
            timeInfo = timeInfo,
            messageType = messageType,
            isGroupChat = isGroupChat,
            participants = participants,
            otherPersonName = otherPersonName
        )
    }
    
    // 기존 패턴 기반 발신자 구분 함수들은 제거됨 (새로운 OcrClassifier 사용)
    
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

    /**
     * 백그라운드 토큰 갱신 시작
     */
    private fun startBackgroundTokenRefresh() {
        Log.d(TAG, "백그라운드 토큰 갱신 시작")
        // 24시간 후 첫 실행
        tokenRefreshHandler.postDelayed(tokenRefreshRunnable, 24 * 60 * 60 * 1000L)
    }
    
    /**
     * 토큰 상태 확인 및 갱신
     */
    private fun checkAndRefreshToken() {
        Log.d(TAG, "백그라운드 토큰 상태 확인")
        
        if (!tokenManager.canAutoRefresh()) {
            Log.d(TAG, "자동 갱신 불가능 - 토큰 없음")
            return
        }
        
        if (tokenManager.isTokenExpiringSoon()) {
            Log.d(TAG, "토큰이 곧 만료될 예정 - 백그라운드 갱신 시도")
            attemptBackgroundTokenRefresh()
        } else {
            Log.d(TAG, "토큰이 아직 유효함")
        }
    }
    
    /**
     * 백그라운드에서 토큰 갱신 시도
     */
    private fun attemptBackgroundTokenRefresh() {
        // 백그라운드 스레드에서 실행
        Thread {
            try {
                val refreshToken = tokenManager.getRefreshToken()
                if (refreshToken.isNullOrEmpty()) {
                    Log.e(TAG, "리프레시 토큰이 없음")
                    return@Thread
                }
                
                // 토큰 갱신 API 호출 (runBlocking 사용)
                kotlinx.coroutines.runBlocking {
                    val authApi = createAuthApiForRefresh()
                    val request = com.mv.toki.api.RefreshTokenRequest(refreshToken)
                    val response = authApi.refreshToken(request)
                    
                    if (response.isSuccessful && response.body() != null) {
                        val tokenResponse = response.body()!!
                        
                        // 새 토큰 저장
                        tokenManager.saveTokens(
                            accessToken = tokenResponse.accessToken,
                            refreshToken = tokenResponse.refreshToken,
                            expiresIn = tokenResponse.expiresIn,
                            tokenType = tokenResponse.tokenType
                        )
                        
                        Log.d(TAG, "백그라운드 토큰 갱신 성공")
                    } else {
                        Log.e(TAG, "백그라운드 토큰 갱신 실패: ${response.code()}")
                        // 갱신 실패 시 토큰 삭제
                        tokenManager.clearTokens()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "백그라운드 토큰 갱신 중 오류", e)
            }
        }.start()
    }
    
    /**
     * 토큰 갱신용 AuthApi 인스턴스 생성
     */
    private fun createAuthApiForRefresh(): com.mv.toki.api.AuthApi {
        val retrofit = retrofit2.Retrofit.Builder()
            .baseUrl("https://toki-auth-964943834069.asia-northeast3.run.app/")
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
        
        return retrofit.create(com.mv.toki.api.AuthApi::class.java)
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy 시작")
        
        try {
            // 서비스 종료 플래그 설정
            Log.d(TAG, "서비스 종료 프로세스 시작")
            
            // 백그라운드 토큰 갱신 중지
            tokenRefreshHandler.removeCallbacks(tokenRefreshRunnable)
            Log.d(TAG, "백그라운드 토큰 갱신 중지")
            
            // 즉시 플로팅 뷰 제거 (가장 중요한 작업을 먼저)
            removeFloatingView()
            
            // 리시버 등록 해제
            try {
                unregisterReceiver(keyboardStateReceiver)
                Log.d(TAG, "keyboardStateReceiver 등록 해제 완료")
            } catch (e: Exception) {
                Log.w(TAG, "keyboardStateReceiver 등록 해제 실패", e)
            }
            
            try {
                unregisterReceiver(ocrRetryReceiver)
                Log.d(TAG, "ocrRetryReceiver 등록 해제 완료")
            } catch (e: Exception) {
                Log.w(TAG, "ocrRetryReceiver 등록 해제 실패", e)
            }
            
            try {
                unregisterReceiver(screenshotReceiver)
                Log.d(TAG, "screenshotReceiver 등록 해제 완료")
            } catch (e: Exception) {
                Log.w(TAG, "screenshotReceiver 등록 해제 실패", e)
            }
            
            // OCR 리소스 정리
            try {
                textRecognizer.close()
                Log.d(TAG, "TextRecognizer 정리 완료")
            } catch (e: Exception) {
                Log.w(TAG, "TextRecognizer 정리 실패", e)
            }
            
            // 포그라운드 서비스 중지
            try {
                stopForeground(true)
                Log.d(TAG, "포그라운드 서비스 중지 완료")
            } catch (e: Exception) {
                Log.w(TAG, "포그라운드 서비스 중지 실패", e)
            }
            
            // 상태 저장
            try {
                savedStateRegistryController.performSave(Bundle())
                _viewModelStore.clear()
                Log.d(TAG, "상태 저장 완료")
            } catch (e: Exception) {
                Log.w(TAG, "상태 저장 실패", e)
            }

            Log.d(TAG, "Service onDestroy 완료")
        } catch (e: Exception) {
            Log.e(TAG, "Service onDestroy 중 오류", e)
        } finally {
            super.onDestroy()
            Log.d(TAG, "Service onDestroy finally 완료")
        }
    }
}

// UI 컴포저블
@Composable
fun FloatingButtonContent(
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onButtonClick: () -> Unit
) {
    Box(
        modifier = Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragEnd = {
                    onDragEnd()
                }
            ) { change, dragAmount ->
                change.consume()
                onDrag(dragAmount.x, dragAmount.y)
            }
        }
    ) {
        // 클릭 효과(ripple effect) 제거를 위해 FloatingActionButton 대신 Box + clickable 사용
        Box(
            modifier = Modifier
                .size(56.dp)  // 원본 크기 (Material Design 표준)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,  // 클릭 효과(ripple) 제거
                    onClick = onButtonClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_floating_button_hand_new),
                contentDescription = "텍스트 인식",
                modifier = Modifier.size(48.dp)  // 원본 아이콘 크기
            )
        }
    }
}