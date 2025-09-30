package com.mv.floatingbuttonapp.api

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.Interceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * API 클라이언트 싱글톤 객체
 * 
 * 이 객체는 Retrofit을 사용하여 외부 AI API와의 통신을 담당합니다.
 * 주요 기능:
 * - HTTP 요청/응답 로깅
 * - 공통 헤더 추가
 * - 타임아웃 설정
 * - JSON 직렬화/역직렬화
 * - 에러 처리
 * 
 * 사용하는 API:
 * - Gemini AI API (Google Cloud Run)
 * - 답변 생성 및 텍스트 분석
 * 
 * @author FloatingButtonApp Team
 * @version 1.0
 * @since 2024
 */
object ApiClient {
    /**
     * API 서버의 기본 URL
     * Google Cloud Run에 배포된 Gemini AI API 엔드포인트
     */
    private const val BASE_URL = "https://gemini-api-964943834069.asia-northeast3.run.app/"
    
    /**
     * HTTP 요청/응답 로깅 인터셉터
     * 개발 및 디버깅 목적으로 모든 HTTP 트래픽을 로그로 출력
     */
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    /**
     * 공통 헤더 추가 인터셉터
     * 모든 API 요청에 공통적으로 필요한 헤더들을 자동으로 추가
     * 
     * 추가되는 헤더:
     * - Content-Type: application/json
     * - Accept: application/json
     * - User-Agent: FloatingButtonApp/1.0
     * - Cache-Control: no-cache
     * - X-Requested-With: XMLHttpRequest
     */
    private val headerInterceptor = Interceptor { chain ->
        val original = chain.request()
        val requestBuilder = original.newBuilder()
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "FloatingButtonApp/1.0")
            .addHeader("Cache-Control", "no-cache")
            .addHeader("X-Requested-With", "XMLHttpRequest")
        val request = requestBuilder.build()
        Log.d("API_DEBUG", "Request headers: ${request.headers}")
        chain.proceed(request)
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(headerInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)  // AI 응답 생성 시간을 고려하여 읽기 타임아웃 증가
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    
    private val gson = com.google.gson.GsonBuilder()
        .setLenient() // 더 관대한 JSON 파싱
        .setPrettyPrinting() // JSON 포맷팅
        .serializeNulls() // null 값도 직렬화
        .create()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
    
    val apiService: ApiService = retrofit.create(ApiService::class.java)
}
