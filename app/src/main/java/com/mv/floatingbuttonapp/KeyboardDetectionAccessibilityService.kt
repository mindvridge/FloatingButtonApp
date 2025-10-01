package com.mv.floatingbuttonapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * 키보드 감지 및 화면 캡처 접근성 서비스
 * 
 * 이 서비스는 접근성 서비스의 권한을 활용하여 다음과 같은 기능을 제공합니다:
 * - 키보드 표시/숨김 상태 실시간 감지
 * - 키보드 높이 측정 및 브로드캐스트 전송
 * - Android 11+ 화면 캡처 기능 제공
 * - FloatingButtonService와의 통신
 * 
 * 주요 동작:
 * 1. 윈도우 상태 변경 감지로 키보드 상태 확인
 * 2. IME 윈도우 및 화면 영역 분석으로 키보드 높이 계산
 * 3. 브로드캐스트를 통해 다른 서비스에 상태 전달
 * 4. 화면 캡처 요청 시 AccessibilityService의 takeScreenshot() 사용
 * 
 * @author FloatingButtonApp Team
 * @version 1.0
 * @since 2024
 */
class KeyboardDetectionAccessibilityService : AccessibilityService() {

    companion object {
        // 로그 태그
        private const val TAG = "KeyboardAccessibility"
        
        // 브로드캐스트 액션 상수들
        const val ACTION_KEYBOARD_SHOWN = "com.mv.floatingbuttonapp.KEYBOARD_SHOWN"
        const val ACTION_KEYBOARD_HIDDEN = "com.mv.floatingbuttonapp.KEYBOARD_HIDDEN"
        const val ACTION_TAKE_SCREENSHOT = "com.mv.floatingbuttonapp.TAKE_SCREENSHOT"
        const val ACTION_INSERT_TEXT = "com.mv.floatingbuttonapp.INSERT_TEXT"
        
        // 브로드캐스트 엑스트라 키 상수들
        const val EXTRA_KEYBOARD_HEIGHT = "keyboard_height"
        const val EXTRA_MESSAGE_INPUT_BAR_HEIGHT = "message_input_bar_height"
        const val EXTRA_SCREENSHOT_BITMAP = "screenshot_bitmap"

        /**
         * 서비스 인스턴스 (싱글톤 패턴)
         * 다른 클래스에서 접근 가능하도록 전역 변수로 관리
         */
        @Volatile
        var instance: KeyboardDetectionAccessibilityService? = null

        /**
         * 키보드 표시 상태를 전역적으로 확인할 수 있는 변수
         * FloatingButtonService에서 참조하여 플로팅 버튼 표시 결정
         */
        @Volatile
        var isKeyboardVisible = false

        /**
         * 현재 키보드 높이 (픽셀 단위)
         * 플로팅 버튼 위치 조정에 사용
         */
        @Volatile
        var keyboardHeight = 0
    }

    private var lastKeyboardState = false
    private val screenBounds = Rect()
    private var screenHeight = 0
    
    // 화면 캡처 권한 확인
    private val isScreenCaptureEnabled: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            serviceInfo?.capabilities?.and(AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT) != 0
        } else {
            false
        }

    // 텍스트 삽입 브로드캐스트 리시버
    private val textInsertReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_INSERT_TEXT -> {
                    val text = intent.getStringExtra("text")
                    if (!text.isNullOrEmpty()) {
                        Log.d(TAG, "텍스트 삽입 요청: $text")
                        insertTextToInputField(text)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "AccessibilityService created")

        // 화면 크기 가져오기
        screenHeight = resources.displayMetrics.heightPixels
        
        // 텍스트 삽입 브로드캐스트 리시버 등록
        val filter = IntentFilter(ACTION_INSERT_TEXT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(textInsertReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(textInsertReceiver, filter)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "AccessibilityService connected")

        // 접근성 서비스 설정
        val info = AccessibilityServiceInfo().apply {
            // 감지할 이벤트 타입 설정
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_FOCUSED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED

            // 피드백 타입 (없음)
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC

            // 이벤트 처리 간격
            notificationTimeout = 100

            // 플래그 설정
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }

        serviceInfo = info

        // 초기 키보드 상태 확인
        checkKeyboardState()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                // 윈도우 상태가 변경되었을 때 키보드 확인
                checkKeyboardState()
            }

            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                // EditText에 포커스가 갔을 때 키보드 확인
                if (event.className?.contains("EditText") == true) {
                    // 약간의 지연 후 키보드 상태 확인 (키보드가 열리는데 시간이 걸림)
                    //rootInActiveWindow?.performAction(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS)
                    checkKeyboardStateWithDelay()
                }
            }
        }
    }

    private fun checkKeyboardStateWithDelay() {
        // 300ms 지연 후 키보드 상태 확인
        android.os.Handler(mainLooper).postDelayed({
            checkKeyboardState()
        }, 300)
    }

    private fun checkKeyboardState() {
        try {
            val isKeyboardNowVisible = detectKeyboard()

            if (isKeyboardNowVisible != lastKeyboardState) {
                lastKeyboardState = isKeyboardNowVisible
                isKeyboardVisible = isKeyboardNowVisible

                if (isKeyboardNowVisible) {
                    Log.d(TAG, "Keyboard shown, height: $keyboardHeight")
                    broadcastKeyboardShown()
                } else {
                    Log.d(TAG, "Keyboard hidden")
                    keyboardHeight = 0
                    broadcastKeyboardHidden()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking keyboard state", e)
        }
    }

    private fun detectKeyboard(): Boolean {
        // 방법 1: IME 윈도우 확인
        val windows = windows
        for (window in windows) {
            if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                // IME 윈도우가 있으면 키보드가 표시됨
                window.getBoundsInScreen(screenBounds)
                val height = screenBounds.height()
                if (height > 100) {
                    keyboardHeight = height
                    return true
                }
            }
        }

        // 방법 2: 화면 하단 영역 확인
        val rootNode = rootInActiveWindow
        if (rootNode != null) {
            val rect = Rect()
            rootNode.getBoundsInScreen(rect)

            // 화면 높이와 실제 보이는 영역의 차이 계산
            val heightDiff = screenHeight - rect.bottom

            // 차이가 200dp 이상이면 키보드가 표시된 것으로 판단
            val threshold = (200 * resources.displayMetrics.density).toInt()
            if (heightDiff > threshold) {
                keyboardHeight = heightDiff
                return true
            }
        }

        return false
    }
    
    /**
     * 메시지 입력바 높이 감지
     * EditText나 입력 필드가 있는 영역의 높이를 계산
     */
    private fun detectMessageInputBarHeight(): Int {
        val rootNode = rootInActiveWindow ?: return 0
        
        // EditText, TextInputLayout, 입력 관련 뷰들을 찾아서 높이 계산
        val inputViews = mutableListOf<AccessibilityNodeInfo>()
        findInputViews(rootNode, inputViews)
        
        if (inputViews.isEmpty()) {
            // 입력 뷰가 없으면 기본 높이 반환
            return (120 * resources.displayMetrics.density).toInt()
        }
        
        // 가장 하단에 있는 입력 뷰의 높이 계산
        var maxBottom = 0
        var inputBarHeight = 0
        
        for (view in inputViews) {
            val rect = Rect()
            view.getBoundsInScreen(rect)
            
            if (rect.bottom > maxBottom) {
                maxBottom = rect.bottom
                inputBarHeight = rect.height()
            }
        }
        
        // 입력바 높이에 여백 추가
        val margin = (20 * resources.displayMetrics.density).toInt()
        return inputBarHeight + margin
    }
    
    /**
     * 입력 관련 뷰들을 재귀적으로 찾기
     */
    private fun findInputViews(node: AccessibilityNodeInfo, inputViews: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        
        // EditText, TextInputLayout, 입력 관련 클래스명들
        val inputClassNames = listOf(
            "android.widget.EditText",
            "androidx.compose.ui.text.input.TextField",
            "com.google.android.material.textfield.TextInputLayout",
            "android.widget.AutoCompleteTextView",
            "android.widget.MultiAutoCompleteTextView"
        )
        
        val className = node.className?.toString()
        if (className != null && inputClassNames.any { inputClassName -> 
            className.contains(inputClassName, ignoreCase = true) 
        }) {
            inputViews.add(node)
        }
        
        // 자식 노드들도 확인
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                findInputViews(child, inputViews)
            }
        }
    }

    // 키보드 표시 상태를 브로드캐스트
    private fun broadcastKeyboardShown() {
        val messageInputBarHeight = detectMessageInputBarHeight()
        val intent = Intent(ACTION_KEYBOARD_SHOWN).apply {
            putExtra(EXTRA_KEYBOARD_HEIGHT, keyboardHeight)
            putExtra(EXTRA_MESSAGE_INPUT_BAR_HEIGHT, messageInputBarHeight)
            setPackage(packageName)  // 자체 앱에만 전송
        }
        sendBroadcast(intent)
        Log.d(TAG, "키보드 표시 브로드캐스트: 키보드높이=$keyboardHeight, 입력바높이=$messageInputBarHeight")
    }

    // 키보드 숨김 상태를 브로드캐스트
    private fun broadcastKeyboardHidden() {
        val intent = Intent(ACTION_KEYBOARD_HIDDEN).apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    override fun onInterrupt() {
        Log.d(TAG, "AccessibilityService interrupted")
    }

    /**
     * 화면 캡처 메서드 (Android 11+)
     */
    fun takeScreenshot(): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Log.d(TAG, "takeScreenshot() 호출됨")
                
                // 화면 캡처 권한 확인
                if (!isScreenCaptureEnabled) {
                    Log.e(TAG, "화면 캡처 권한이 없습니다")
                    return null
                }
                
                // 동기 처리를 위한 CountDownLatch 사용
                val latch = java.util.concurrent.CountDownLatch(1)
                var resultBitmap: Bitmap? = null
                
                takeScreenshot(
                    android.view.Display.DEFAULT_DISPLAY,
                    java.util.concurrent.Executors.newSingleThreadExecutor(),
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                            Log.d(TAG, "화면 캡처 성공")
                            try {
                                // ScreenshotResult에서 Bitmap 추출
                                val bitmap = when {
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                                        // Android 11+ 에서는 HardwareBuffer 사용
                                        val hardwareBuffer = screenshot.hardwareBuffer
                                        if (hardwareBuffer != null) {
                                            Bitmap.wrapHardwareBuffer(hardwareBuffer, null)
                                        } else {
                                            null
                                        }
                                    }
                                    else -> {
                                        // 이전 버전에서는 reflection 사용
                                        try {
                                            val bitmapField = screenshot.javaClass.getDeclaredField("bitmap")
                                            bitmapField.isAccessible = true
                                            bitmapField.get(screenshot) as? Bitmap
                                        } catch (e: Exception) {
                                            Log.e(TAG, "reflection으로 bitmap 추출 실패", e)
                                            null
                                        }
                                    }
                                }
                                
                                bitmap?.let {
                                    Log.d(TAG, "화면 캡처 결과: ${it.width}x${it.height}")
                                    resultBitmap = it
                                } ?: run {
                                    Log.e(TAG, "화면 캡처 결과 bitmap이 null")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "화면 캡처 결과 처리 중 오류", e)
                            } finally {
                                latch.countDown()
                            }
                        }
                        
                        override fun onFailure(errorCode: Int) {
                            Log.e(TAG, "화면 캡처 실패: errorCode = $errorCode")
                            latch.countDown()
                        }
                    }
                )
                
                // 최대 5초 대기
                if (latch.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    Log.d(TAG, "화면 캡처 완료")
                    return resultBitmap
                } else {
                    Log.e(TAG, "화면 캡처 타임아웃")
                    return null
                }
            } catch (e: Exception) {
                Log.e(TAG, "화면 캡처 중 오류", e)
                null
            }
        } else {
            Log.w(TAG, "Android 11 미만에서는 takeScreenshot()을 사용할 수 없습니다")
            null
        }
    }
    
    /**
     * 화면 캡처 결과를 브로드캐스트로 전송
     */
    private fun broadcastScreenshot(bitmap: Bitmap) {
        Log.d(TAG, "broadcastScreenshot 호출됨: ${bitmap.width}x${bitmap.height}")
        val intent = Intent(ACTION_TAKE_SCREENSHOT).apply {
            putExtra(EXTRA_SCREENSHOT_BITMAP, bitmap)
            setPackage(packageName)
        }
        Log.d(TAG, "브로드캐스트 전송: action=${intent.action}, package=${intent.`package`}")
        sendBroadcast(intent)
        Log.d(TAG, "화면 캡처 결과 브로드캐스트 전송 완료")
    }

    /**
     * 텍스트를 입력 필드에 삽입하고 키보드를 활성화
     * 개선된 버전: 포커스된 입력 필드가 없으면 EditText를 찾아서 포커스를 맞춤
     */
    private fun insertTextToInputField(text: String) {
        try {
            Log.d(TAG, "텍스트 삽입 시작: $text")
            
            // 현재 포커스된 입력 필드 찾기
            val rootNode = rootInActiveWindow
            if (rootNode == null) {
                Log.e(TAG, "rootInActiveWindow가 null입니다")
                return
            }
            
            var inputNode = findFocusedInputField(rootNode)
            
            // 포커스된 입력 필드가 없으면 EditText를 찾아서 포커스 맞춤
            if (inputNode == null) {
                Log.d(TAG, "포커스된 입력 필드가 없음, EditText 검색 중...")
                inputNode = findAnyInputField(rootNode)
                
                if (inputNode != null) {
                    Log.d(TAG, "EditText 발견, 포커스 맞추는 중...")
                    // 입력 필드에 포커스를 맞춰 키보드 활성화
                    inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    inputNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    
                    // 키보드가 열릴 시간을 줌
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        performTextInsertion(inputNode, text)
                    }, 300) // 300ms 대기
                    return
                }
            }
            
            if (inputNode != null) {
                Log.d(TAG, "입력 필드 발견, 텍스트 삽입 시작")
                performTextInsertion(inputNode, text)
            } else {
                Log.w(TAG, "입력 필드를 찾을 수 없습니다")
            }
        } catch (e: Exception) {
            Log.e(TAG, "텍스트 삽입 중 오류", e)
        }
    }
    
    /**
     * 실제 텍스트 삽입을 수행하는 함수
     */
    private fun performTextInsertion(inputNode: AccessibilityNodeInfo, text: String) {
        try {
            // 방법 1: ACTION_SET_TEXT 사용 (가장 직접적인 방법)
            val bundle = Bundle()
            bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            val setTextSuccess = inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
            
            if (setTextSuccess) {
                Log.d(TAG, "ACTION_SET_TEXT로 텍스트 삽입 성공: $text")
                return
            }
            
            Log.d(TAG, "ACTION_SET_TEXT 실패, 클립보드 방식 시도")
            
            // 방법 2: 클립보드를 통한 붙여넣기
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("text", text)
            clipboard.setPrimaryClip(clip)
            Log.d(TAG, "클립보드에 복사됨")
            
            // 약간의 지연 후 붙여넣기
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    val pasteSuccess = inputNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                    if (pasteSuccess) {
                        Log.d(TAG, "ACTION_PASTE로 텍스트 삽입 성공")
                    } else {
                        Log.w(TAG, "ACTION_PASTE 실패")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "붙여넣기 실패", e)
                }
            }, 100)
            
        } catch (e: Exception) {
            Log.e(TAG, "텍스트 삽입 실패", e)
        }
    }
    
    /**
     * 아무 입력 필드나 찾기 (포커스 여부 상관없음)
     */
    private fun findAnyInputField(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        
        // 현재 노드가 입력 필드인지 확인
        if (isInputField(node) && node.isEnabled && node.isVisibleToUser) {
            Log.d(TAG, "입력 필드 발견: ${node.className}")
            return node
        }
        
        // 자식 노드들에서 재귀적으로 검색
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val result = findAnyInputField(child)
                if (result != null) {
                    return result
                }
            }
        }
        
        return null
    }
    
    /**
     * 포커스된 입력 필드 찾기
     */
    private fun findFocusedInputField(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node == null) return null
        
        // 현재 노드가 입력 필드이고 포커스되어 있는지 확인
        if (node.isFocused && isInputField(node)) {
            return node
        }
        
        // 자식 노드들에서 재귀적으로 검색
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val result = findFocusedInputField(child)
                if (result != null) {
                    return result
                }
            }
        }
        
        return null
    }
    
    /**
     * 노드가 입력 필드인지 확인
     */
    private fun isInputField(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString() ?: return false
        
        val inputClassNames = listOf(
            "android.widget.EditText",
            "androidx.compose.ui.text.input.TextField",
            "com.google.android.material.textfield.TextInputLayout",
            "android.widget.AutoCompleteTextView",
            "android.widget.MultiAutoCompleteTextView"
        )
        
        return inputClassNames.any { inputClassName -> 
            className.contains(inputClassName, ignoreCase = true) 
        }
    }
    
    /**
     * 입력 필드의 텍스트를 지우는 함수
     */
    fun clearInputField(): Boolean {
        return try {
            Log.d(TAG, "입력 필드 텍스트 지우기 시작")
            
            val rootNode = rootInActiveWindow
            if (rootNode == null) {
                Log.e(TAG, "rootInActiveWindow가 null입니다")
                return false
            }
            
            // 포커스된 입력 필드 찾기
            var inputNode = findFocusedInputField(rootNode)
            
            // 포커스된 입력 필드가 없으면 아무 입력 필드나 찾기
            if (inputNode == null) {
                Log.d(TAG, "포커스된 입력 필드가 없음, EditText 검색 중...")
                inputNode = findAnyInputField(rootNode)
            }
            
            if (inputNode != null) {
                Log.d(TAG, "입력 필드 발견, 텍스트 지우는 중...")
                
                // 방법 1: ACTION_SET_TEXT로 빈 문자열 설정
                val bundle = Bundle()
                bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
                val setTextSuccess = inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
                
                if (setTextSuccess) {
                    Log.d(TAG, "ACTION_SET_TEXT로 텍스트 지우기 성공")
                    return true
                }
                
                // 방법 2: ACTION_SET_SELECTION으로 전체 선택 후 삭제
                Log.d(TAG, "ACTION_SET_TEXT 실패, 전체 선택 후 삭제 시도")
                
                try {
                    // 현재 텍스트 가져오기
                    val currentText = inputNode.text
                    if (currentText != null && currentText.isNotEmpty()) {
                        // 전체 선택 (0부터 끝까지)
                        val selectionBundle = Bundle()
                        selectionBundle.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                        selectionBundle.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, currentText.length)
                        val selectSuccess = inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionBundle)
                        
                        if (selectSuccess) {
                            Log.d(TAG, "전체 선택 성공")
                            // 약간의 딜레이 후 잘라내기
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                try {
                                    val cutSuccess = inputNode.performAction(AccessibilityNodeInfo.ACTION_CUT)
                                    if (cutSuccess) {
                                        Log.d(TAG, "ACTION_CUT으로 텍스트 지우기 성공")
                                    } else {
                                        Log.w(TAG, "ACTION_CUT 실패")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "텍스트 삭제 실패", e)
                                }
                            }, 50)
                            return true
                        } else {
                            Log.w(TAG, "전체 선택 실패")
                        }
                    } else {
                        Log.d(TAG, "입력 필드가 이미 비어있음")
                        return true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "전체 선택 중 오류", e)
                }
                
                return false
            } else {
                Log.w(TAG, "입력 필드를 찾을 수 없습니다")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "입력 필드 텍스트 지우기 중 오류", e)
            false
        }
    }
    
    /**
     * 키보드를 숨기는 함수
     * EditText에서 포커스를 해제하고 Back 버튼을 눌러 키보드를 숨깁니다
     */
    fun hideKeyboard(): Boolean {
        return try {
            Log.d(TAG, "키보드 숨김 시작")
            
            // 방법 1: 포커스된 입력 필드에서 포커스 해제
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                val focusedNode = findFocusedInputField(rootNode)
                if (focusedNode != null) {
                    Log.d(TAG, "포커스된 입력 필드 발견, 포커스 해제 시도")
                    focusedNode.performAction(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS)
                }
            }
            
            // 방법 2: Back 버튼을 눌러 키보드 숨김
            // 약간의 지연을 두고 실행하여 포커스 해제가 먼저 처리되도록 함
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    val backSuccess = performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    if (backSuccess) {
                        Log.d(TAG, "Back 액션을 통해 키보드 숨김 완료")
                    } else {
                        Log.w(TAG, "Back 액션 실행 실패")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Back 액션 중 오류", e)
                }
            }, 100)
            
            Log.d(TAG, "키보드 숨김 명령 전송 완료")
            true
        } catch (e: Exception) {
            Log.e(TAG, "키보드 숨김 중 오류", e)
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(textInsertReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "textInsertReceiver 등록 해제 실패", e)
        }
        instance = null
        Log.d(TAG, "AccessibilityService destroyed")
    }
}