package com.mv.toki

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import android.widget.Toast

/**
 * 약관동의 전체 팝업
 * 첨부된 디자인을 정확히 구현
 */
@Composable
fun TermsAgreementPopup(
    onClose: () -> Unit,
    onSaveTempConsent: () -> Unit,
    onClearTempConsent: () -> Unit,
    onOpenTermsLink: () -> Unit,
    onOpenPrivacyLink: () -> Unit,
    onAgreementComplete: () -> Unit
) {
    var isAllTermsAgreed by remember { mutableStateOf(false) }
    var isServiceTermsAgreed by remember { mutableStateOf(false) }
    var isPrivacyTermsAgreed by remember { mutableStateOf(false) }
    
    // 전체 동의 상태 업데이트 함수
    fun updateAllTermsState() {
        isAllTermsAgreed = isServiceTermsAgreed && isPrivacyTermsAgreed
    }
    
    // 동의 상태에 따른 임시 저장/삭제 함수
    fun updateConsentState() {
        if (isServiceTermsAgreed && isPrivacyTermsAgreed) {
            onSaveTempConsent()
        } else {
            onClearTempConsent()
        }
    }
    
    // 다음 버튼 활성화 조건 (필수 항목만)
    val canProceed = isServiceTermsAgreed && isPrivacyTermsAgreed
    
    // Context 가져오기
    val context = LocalContext.current
    
    // 전체 화면 다이얼로그
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 0.dp, vertical = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                
                // 앱 아이콘
                Image(
                    painter = painterResource(id = R.drawable.toki_white),
                    contentDescription = "앱 아이콘",
                    modifier = Modifier
                        .size(240.dp)
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = 16.dp),
                    contentScale = ContentScale.Fit
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 환영 메시지 (굵은 글씨, 중앙 정렬)
                Text(
                    text = "고객님 환영합니다!",
                    fontSize = 24.sp,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = 16.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 약관 동의 섹션 (연한 분홍색 배경)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(
                            color = Color(0x1AFFC0CB), // 연한 분홍색 (30% 투명도)
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp)
                ) {
                    // 전체 동의
                    TermsRow(
                        text = "약관 전체동의",
                        isChecked = isAllTermsAgreed,
                        onCheckedChange = { 
                            isAllTermsAgreed = it
                            isServiceTermsAgreed = it
                            isPrivacyTermsAgreed = it
                            updateConsentState()
                        },
                        isRequired = false,
                        showArrow = false
                    )
                    
                    // 구분선
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = Color(0xFFE0E0E0),
                        thickness = 1.dp
                    )
                    
                    // 이용약관 동의
                    TermsRow(
                        text = "이용약관 동의",
                        isChecked = isServiceTermsAgreed,
                        onCheckedChange = { 
                            isServiceTermsAgreed = it
                            updateAllTermsState()
                            updateConsentState()
                        },
                        isRequired = true,
                        showArrow = true,
                        onArrowClick = onOpenTermsLink
                    )
                    
                    // 구분선
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = Color(0xFFE0E0E0),
                        thickness = 1.dp
                    )
                    
                    // 개인정보 수집 및 이용동의
                    TermsRow(
                        text = "개인정보 수집 및 이용동의",
                        isChecked = isPrivacyTermsAgreed,
                        onCheckedChange = { 
                            isPrivacyTermsAgreed = it
                            updateAllTermsState()
                            updateConsentState()
                        },
                        isRequired = true,
                        showArrow = true,
                        onArrowClick = onOpenPrivacyLink
                    )
                    
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 동의하고 계속하기 버튼 (이미지 버튼)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                        .clickable(
                            enabled = canProceed
                        ) {
                            if (canProceed) {
                                // 로컬에 영구 저장
                                try {
                                    Log.d("TermsPopup", "약관동의 저장 시작")
                                    
                                    // SharedPreferences 저장 시도
                                    val prefs = context.getSharedPreferences("terms_consent", Context.MODE_PRIVATE)
                                    
                                    // 저장할 데이터 준비
                                    val termsData = mapOf(
                                        "terms_agreed" to true,
                                        "terms_version" to "v1.0",
                                        "consent_timestamp" to System.currentTimeMillis()
                                    )
                                    
                                    Log.d("TermsPopup", "저장할 데이터: $termsData")
                                    
                                    // 여러 방법으로 저장 시도
                                    var saveSuccess = false
                                    var lastException: Exception? = null
                                    
                                    // 방법 1: apply() 사용
                                    try {
                                        val editor = prefs.edit()
                                        editor.putBoolean("terms_agreed", true)
                                        editor.putString("terms_version", "v1.0")
                                        editor.putLong("consent_timestamp", System.currentTimeMillis())
                                        editor.apply()
                                        
                                        // 잠시 대기 후 값 확인
                                        Thread.sleep(50)
                                        val savedValue = prefs.getBoolean("terms_agreed", false)
                                        
                                        if (savedValue) {
                                            saveSuccess = true
                                            Log.d("TermsPopup", "약관동의 저장 성공 (apply 방법)")
                                        } else {
                                            Log.w("TermsPopup", "apply() 후 값 확인 실패")
                                        }
                                    } catch (e: Exception) {
                                        Log.w("TermsPopup", "apply() 방법 실패", e)
                                        lastException = e
                                    }
                                    
                                    // 방법 2: commit() 사용 (apply 실패 시)
                                    if (!saveSuccess) {
                                        try {
                                            val editor = prefs.edit()
                                            editor.putBoolean("terms_agreed", true)
                                            editor.putString("terms_version", "v1.0")
                                            editor.putLong("consent_timestamp", System.currentTimeMillis())
                                            
                                            val result = editor.commit()
                                            Log.d("TermsPopup", "commit() 결과: $result")
                                            
                                            if (result) {
                                                val savedValue = prefs.getBoolean("terms_agreed", false)
                                                if (savedValue) {
                                                    saveSuccess = true
                                                    Log.d("TermsPopup", "약관동의 저장 성공 (commit 방법)")
                                                } else {
                                                    Log.w("TermsPopup", "commit() 후 값 확인 실패")
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.w("TermsPopup", "commit() 방법 실패", e)
                                            lastException = e
                                        }
                                    }
                                    
                                    // 결과 처리
                                    if (saveSuccess) {
                                        Log.d("TermsPopup", "약관동의 로컬 영구 저장 완료")
                                        Toast.makeText(context, "약관 동의가 완료되었습니다.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Log.e("TermsPopup", "약관동의 저장 실패: 모든 방법 실패")
                                        Log.e("TermsPopup", "마지막 예외: ${lastException?.message}")
                                        Toast.makeText(context, "약관 동의 저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
                                    }
                                    
                                } catch (e: Exception) {
                                    Log.e("TermsPopup", "약관동의 로컬 영구 저장 실패", e)
                                    Log.e("TermsPopup", "예외 상세: ${e.message}")
                                    Log.e("TermsPopup", "스택 트레이스: ${e.stackTraceToString()}")
                                    Toast.makeText(context, "약관 동의 저장 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                                }
                                
                                onAgreementComplete()
                                onClose()
                            }
                        }
                ) {
                    // 배경 (약관 동의 상태에 따라 색상 변경)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .background(
                                color = if (canProceed) Color(0xFFF27B13) else Color(0xFFCCCCCC),
                                shape = RoundedCornerShape(12.dp)
                            )
                    )
                    
                    // 텍스트
                    Text(
                        text = "동의하고 계속하기",
                        fontSize = 16.sp,
                        color = if (canProceed) Color.White else Color(0xFF888888),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@Composable
private fun TermsRow(
    text: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isRequired: Boolean,
    showArrow: Boolean,
    onArrowClick: (() -> Unit)? = null,
    isOptional: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 체크박스 (원형)
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(
                    color = if (isChecked) Color(0xFFF27B13) else Color.White,
                    shape = CircleShape
                )
                .border(
                    width = 1.dp,
                    color = if (isChecked) Color(0xFFF27B13) else Color(0xFFCCCCCC),
                    shape = CircleShape
                )
                .clickable { onCheckedChange(!isChecked) },
            contentAlignment = Alignment.Center
        ) {
            if (isChecked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "체크됨",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // 텍스트
        Text(
            text = text,
            fontSize = 14.sp,
            color = Color.Black,
            modifier = Modifier.weight(1f)
        )
        
        // 필수/선택 표시
        if (isRequired) {
            Text(
                text = "(필수)",
                fontSize = 14.sp,
                color = Color(0xFFF44336),
                modifier = Modifier.padding(end = 8.dp)
            )
        } else if (isOptional) {
            Text(
                text = "(선택)",
                fontSize = 14.sp,
                color = Color(0xFF666666),
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        
        // 화살표 아이콘
        if (showArrow && onArrowClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "보기",
                tint = Color(0xFF999999),
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onArrowClick() }
            )
        }
    }
}
