package com.mv.toki

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import com.mv.toki.auth.TokenManager
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.mv.toki.FloatingButtonApplication.Companion.kakaoLoginManager

/**
 * 플로팅 로그인 다이얼로그 액티비티
 * 
 * 토큰이 만료되어 API 호출이 실패할 때 플로팅 버튼에서 바로 로그인할 수 있는 다이얼로그를 제공합니다.
 * 사용자가 앱으로 이동하지 않고도 빠르게 로그인할 수 있어 편의성을 높입니다.
 */
class FloatingLoginActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "FloatingLoginActivity"
        const val ACTION_LOGIN_SUCCESS = "com.mv.toki.LOGIN_SUCCESS"
        const val ACTION_LOGIN_CANCELLED = "com.mv.toki.LOGIN_CANCELLED"
    }
    
    private lateinit var tokenManager: TokenManager
    private lateinit var kakaoLoginManager: KakaoLoginManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "플로팅 로그인 다이얼로그 시작")
        
        // 초기화
        tokenManager = TokenManager.getInstance(this)
        kakaoLoginManager = KakaoLoginManager(this)
        
        setContent {
            MaterialTheme {
                FloatingLoginDialog(
                    onLoginSuccess = { 
                        Log.d(TAG, "로그인 성공 - 다이얼로그 종료")
                        setResult(RESULT_OK)
                        finish()
                    },
                    onLoginCancelled = {
                        Log.d(TAG, "로그인 취소 - 다이얼로그 종료")
                        setResult(RESULT_CANCELED)
                        finish()
                    },
                    kakaoLoginManager = kakaoLoginManager,
                    tokenManager = tokenManager
                )
            }
        }
    }
    
    override fun onBackPressed() {
        super.onBackPressed()
        setResult(RESULT_CANCELED)
        finish()
    }
}

/**
 * 플로팅 로그인 다이얼로그 컴포저블
 */
@Composable
fun FloatingLoginDialog(
    onLoginSuccess: () -> Unit,
    onLoginCancelled: () -> Unit,
    kakaoLoginManager: KakaoLoginManager,
    tokenManager: TokenManager
) {
    var isLoading by remember { mutableStateOf(false) }
    var loginType by remember { mutableStateOf("") } // "kakao"
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    Dialog(
        onDismissRequest = onLoginCancelled,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 헤더
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "로그인이 필요합니다",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333)
                    )
                    
                    IconButton(onClick = onLoginCancelled) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "닫기",
                            tint = Color(0xFF999999)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 설명 텍스트
                Text(
                    text = "토큰이 만료되어 로그인이 필요합니다.\n빠르게 다시 로그인해주세요.",
                    fontSize = 14.sp,
                    color = Color(0xFF666666),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                 // 카카오 로그인 버튼
                 Button(
                     onClick = {
                         if (!isLoading) {
                             isLoading = true
                             loginType = "kakao"
                             scope.launch {
                                 try {
                                     Log.d("FloatingLogin", "카카오 로그인 시도")
                                     
                                    // 실제 카카오 로그인 실행
                                    val result = kakaoLoginManager.loginWithKakao(context as androidx.activity.ComponentActivity)
                                     result.onSuccess { loginResult ->
                                         Log.d("FloatingLogin", "카카오 로그인 성공: ${loginResult.nickname}")
                                         // 로그인 성공 시 다이얼로그 닫기
                                         onLoginSuccess()
                                     }.onFailure { error ->
                                         Log.e("FloatingLogin", "카카오 로그인 실패", error)
                                         // 로그인 실패 시 로딩 상태 해제
                                         isLoading = false
                                         loginType = ""
                                     }
                                 } catch (e: Exception) {
                                     Log.e("FloatingLogin", "카카오 로그인 오류", e)
                                     isLoading = false
                                     loginType = ""
                                 }
                             }
                         }
                     },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFE812) // 카카오 노란색
                    ),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading && loginType == "kakao") {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "카카오 로그인 중...",
                            color = Color.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Text(
                            text = "카카오로 로그인",
                            color = Color.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                 }
                 
                 
                 Spacer(modifier = Modifier.height(16.dp))
                
                // 취소 버튼
                TextButton(
                    onClick = onLoginCancelled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "취소",
                        color = Color(0xFF999999),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
