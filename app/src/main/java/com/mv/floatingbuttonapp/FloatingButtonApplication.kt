package com.mv.floatingbuttonapp

import android.app.Application
import android.util.Log

/**
 * 앱의 Application 클래스
 * 카카오 SDK 초기화 및 전역 설정 관리
 */
class FloatingButtonApplication : Application() {
    
    companion object {
        private const val TAG = "FloatingButtonApp"
        lateinit var kakaoLoginManager: KakaoLoginManager
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // 카카오 로그인 매니저 초기화
        kakaoLoginManager = KakaoLoginManager(this)
        kakaoLoginManager.initializeKakaoSdk()
        
        Log.d(TAG, "Application 초기화 완료")
    }
}
