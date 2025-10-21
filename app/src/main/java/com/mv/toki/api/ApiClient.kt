package com.mv.toki.api

import android.content.Context
import android.util.Log
import com.mv.toki.auth.AuthInterceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.Interceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * API 클라이언트 싱글톤 객체
 * 
 * Toki Auth와 Gemini API 두 개의 서비스를 관리합니다.
 * - Toki Auth: 로그인/토큰 관리
 * - Gemini API: AI 답변 생성 (Authorization 헤더 자동 추가)
 * 
 * @author FloatingButtonApp Team
 * @version 2.0
 * @since 2024
 */
object ApiClient {
    
    /**
     * Toki Auth 서버 URL
     */
    private const val TOKI_AUTH_BASE_URL = 
        "https://toki-auth-964943834069.asia-northeast3.run.app/"
    
    /**
     * Gemini API 서버 URL
     */
    private const val GEMINI_API_BASE_URL = 
        "https://gemini-api-964943834069.asia-northeast3.run.app/"
    
    private var context: Context? = null
    
    /**
     * Context 초기화 (Application.onCreate()에서 호출)
     */
    fun init(appContext: Context) {
        context = appContext.applicationContext
        Log.d("ApiClient", "ApiClient 초기화 완료")
    }
    
    /**
     * HTTP 요청/응답 로깅 인터셉터
     */
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    /**
     * 공통 헤더 추가 인터셉터
     */
    private val headerInterceptor = Interceptor { chain ->
        val original = chain.request()
        val requestBuilder = original.newBuilder()
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "FloatingButtonApp/2.0")
            .addHeader("Cache-Control", "no-cache")
        val request = requestBuilder.build()
        
        try {
            Log.d("API_DEBUG", "Request: ${request.method} ${request.url}")
        } catch (e: Exception) {
            Log.w("API_DEBUG", "Request logging failed", e)
        }
        
        chain.proceed(request)
    }
    
    /**
     * Gson 인스턴스
     */
    private val gson = com.google.gson.GsonBuilder()
        .setLenient()
        .setPrettyPrinting()
        .serializeNulls()
        .create()
    
    /**
     * Toki Auth용 OkHttpClient (인증 불필요)
     */
    private val authHttpClient = OkHttpClient.Builder()
        .addInterceptor(headerInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    
    /**
     * Gemini API용 OkHttpClient (인증 필요 - AuthInterceptor 포함)
     */
    private val geminiHttpClient: OkHttpClient
        get() {
            val builder = OkHttpClient.Builder()
                .addInterceptor(headerInterceptor)
                .addInterceptor(loggingInterceptor)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)  // AI 응답 생성 시간 고려
                .writeTimeout(10, TimeUnit.SECONDS)
            
            // AuthInterceptor 추가 (Context가 필요)
            context?.let {
                builder.addInterceptor(AuthInterceptor(it))
            }
            
            return builder.build()
        }
    
    /**
     * Toki Auth Retrofit 인스턴스
     */
    private val authRetrofit = Retrofit.Builder()
        .baseUrl(TOKI_AUTH_BASE_URL)
        .client(authHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
    
    /**
     * Gemini API Retrofit 인스턴스
     */
    private val geminiRetrofit: Retrofit
        get() = Retrofit.Builder()
            .baseUrl(GEMINI_API_BASE_URL)
            .client(geminiHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    
    /**
     * Toki Auth API 서비스
     * 로그인/토큰 관리에 사용
     */
    val authApi: AuthApi = authRetrofit.create(AuthApi::class.java)
    
    /**
     * Gemini API 서비스
     * AI 답변 생성에 사용 (Authorization 헤더 자동 추가)
     */
    val geminiApi: GeminiApi
        get() = geminiRetrofit.create(GeminiApi::class.java)
    
    /**
     * 레거시 호환성을 위한 apiService (Deprecated)
     * @deprecated geminiApi 사용 권장
     */
    @Deprecated(
        message = "Use geminiApi instead",
        replaceWith = ReplaceWith("ApiClient.geminiApi")
    )
    val apiService: ApiService
        get() = geminiRetrofit.create(ApiService::class.java)
}
