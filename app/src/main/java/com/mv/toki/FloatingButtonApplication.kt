package com.mv.toki

import android.app.Application
import android.util.Log

/**
 * 플로팅 버튼 앱의 메인 Application 클래스
 * 
 * 이 클래스는 앱의 전역 설정과 초기화를 담당합니다.
 * 주요 기능:
 * - 카카오 SDK 초기화 및 설정
 * - 전역 로그인 매니저 인스턴스 관리
 * - 앱 시작 시 필요한 리소스 초기화
 * 
 * @author FloatingButtonApp Team
 * @version 1.0
 * @since 2024
 */
class FloatingButtonApplication : Application() {
    
    companion object {
        // 로그 태그
        private const val TAG = "FloatingButtonApp"
        
        // 전역 카카오 로그인 매니저 인스턴스
        // 다른 클래스에서 접근 가능하도록 lateinit으로 선언
        lateinit var kakaoLoginManager: KakaoLoginManager
            private set
    }
    
    /**
     * Application 생명주기 메서드
     * 앱이 시작될 때 가장 먼저 호출되는 메서드
     * 
     * 수행 작업:
     * 1. 카카오 로그인 매니저 인스턴스 생성
     * 2. 카카오 SDK 초기화 및 설정
     * 3. 초기화 완료 로그 출력
     */
    override fun onCreate() {
        super.onCreate()
        
        // ApiClient 초기화 (Context 전달)
        com.mv.toki.api.ApiClient.init(this)
        
        // 카카오 로그인 매니저 초기화
        // Application Context를 전달하여 전역적으로 사용 가능하도록 설정
        kakaoLoginManager = KakaoLoginManager(this)
        
        // 카카오 SDK 초기화
        // 앱 키와 관련 설정을 수행
        kakaoLoginManager.initializeKakaoSdk()
        
        // 초기화 완료 로그
        Log.d(TAG, "Application 초기화 완료")
    }
}
