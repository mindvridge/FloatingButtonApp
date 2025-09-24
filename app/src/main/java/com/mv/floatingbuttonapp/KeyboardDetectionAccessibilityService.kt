package com.mv.floatingbuttonapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

class KeyboardDetectionAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "KeyboardAccessibility"
        const val ACTION_KEYBOARD_SHOWN = "com.mv.floatingbuttonapp.KEYBOARD_SHOWN"
        const val ACTION_KEYBOARD_HIDDEN = "com.mv.floatingbuttonapp.KEYBOARD_HIDDEN"
        const val EXTRA_KEYBOARD_HEIGHT = "keyboard_height"

        @Volatile
        var instance: KeyboardDetectionAccessibilityService? = null
            private set

        // 키보드 상태를 전역적으로 확인할 수 있는 변수
        @Volatile
        var isKeyboardVisible = false
            private set

        @Volatile
        var keyboardHeight = 0
            private set
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

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "AccessibilityService destroyed")
    }
}