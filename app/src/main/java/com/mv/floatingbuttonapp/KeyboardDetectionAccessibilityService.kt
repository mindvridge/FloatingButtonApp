package com.mv.floatingbuttonapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

class KeyboardDetectionAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "KeyboardAccessibility"
        const val ACTION_KEYBOARD_SHOWN = "com.mv.floatingbuttonapp.KEYBOARD_SHOWN"
        const val ACTION_KEYBOARD_HIDDEN = "com.mv.floatingbuttonapp.KEYBOARD_HIDDEN"
        const val ACTION_TAKE_SCREENSHOT = "com.mv.floatingbuttonapp.TAKE_SCREENSHOT"
        const val EXTRA_KEYBOARD_HEIGHT = "keyboard_height"
        const val EXTRA_SCREENSHOT_BITMAP = "screenshot_bitmap"

        @Volatile
        var instance: KeyboardDetectionAccessibilityService? = null

        // 키보드 상태를 전역적으로 확인할 수 있는 변수
        @Volatile
        var isKeyboardVisible = false

        @Volatile
        var keyboardHeight = 0
    }

    private var lastKeyboardState = false
    private val screenBounds = Rect()
    private var screenHeight = 0

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "AccessibilityService created")

        // 화면 크기 가져오기
        screenHeight = resources.displayMetrics.heightPixels
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

    // 키보드 표시 상태를 브로드캐스트
    private fun broadcastKeyboardShown() {
        val intent = Intent(ACTION_KEYBOARD_SHOWN).apply {
            putExtra(EXTRA_KEYBOARD_HEIGHT, keyboardHeight)
            setPackage(packageName)  // 자체 앱에만 전송
        }
        sendBroadcast(intent)
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
                takeScreenshot(
                    android.view.Display.DEFAULT_DISPLAY,
                    java.util.concurrent.Executors.newSingleThreadExecutor(),
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                            Log.d(TAG, "화면 캡처 성공")
                            // ScreenshotResult를 Bitmap으로 변환 (Android API에 따라 다를 수 있음)
                            try {
                                // reflection을 사용하여 bitmap 속성에 접근
                                val bitmapField = screenshot.javaClass.getDeclaredField("bitmap")
                                bitmapField.isAccessible = true
                                val bitmap = bitmapField.get(screenshot) as? Bitmap
                                
                                bitmap?.let {
                                    Log.d(TAG, "화면 캡처 결과: ${it.width}x${it.height}")
                                    // 화면 캡처 결과를 브로드캐스트로 전송
                                    broadcastScreenshot(it)
                                } ?: run {
                                    Log.e(TAG, "화면 캡처 결과 bitmap이 null")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "화면 캡처 결과 처리 중 오류", e)
                            }
                        }
                        
                        override fun onFailure(errorCode: Int) {
                            Log.e(TAG, "화면 캡처 실패: errorCode = $errorCode")
                        }
                    }
                )
                null // 비동기이므로 즉시 null 반환
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

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "AccessibilityService destroyed")
    }
}