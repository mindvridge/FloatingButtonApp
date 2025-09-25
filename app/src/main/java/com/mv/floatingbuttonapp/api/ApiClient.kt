package com.mv.floatingbuttonapp.api

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.Interceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val BASE_URL = "https://gemini-api-964943834069.asia-northeast3.run.app/"
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    // 헤더 추가 인터셉터
    private val headerInterceptor = Interceptor { chain ->
        val original = chain.request()
        val requestBuilder = original.newBuilder()
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "FloatingButtonApp/1.0")
            .addHeader("Cache-Control", "no-cache")
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
        .create()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
    
    val apiService: ApiService = retrofit.create(ApiService::class.java)
}
