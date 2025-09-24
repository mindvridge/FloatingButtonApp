package com.mv.floatingbuttonapp.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
import com.mv.floatingbuttonapp.ui.theme.FloatingButtonAppTheme

/**
 * OCR로 추출된 텍스트를 수정할 수 있는 액티비티
 * 첨부된 이미지와 유사한 UI를 제공합니다.
 */
class TextEditActivity : ComponentActivity() {

    companion object {
        const val EXTRA_EXTRACTED_TEXT = "extracted_text"
        const val EXTRA_CAPTURED_IMAGE = "captured_image"
        const val RESULT_EDITED_TEXT = "edited_text"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val extractedText = intent.getStringExtra(EXTRA_EXTRACTED_TEXT) ?: ""
        val capturedImage = intent.getParcelableExtra<Bitmap>(EXTRA_CAPTURED_IMAGE)

        setContent {
            FloatingButtonAppTheme {
                TextEditScreen(
                    extractedText = extractedText,
                    capturedImage = capturedImage,
                    onBackClick = { finish() },
                    onSaveClick = { editedText ->
                        val resultIntent = Intent().apply {
                            putExtra(RESULT_EDITED_TEXT, editedText)
                        }
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    }
                )
            }
        }
    }
}

/**
 * 텍스트 수정 화면의 UI 컴포저블
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditScreen(
    extractedText: String,
    capturedImage: Bitmap?,
    onBackClick: () -> Unit,
    onSaveClick: (String) -> Unit
) {
    var editedText by remember { mutableStateOf(extractedText) }
    var isEditing by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 상단 헤더 (다크 블루 그레이)
            TopAppBar(
                title = {
                    Text(
                        text = "대화 등록",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF2C3E50)
                )
            )

            // 확인 메시지
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2C3E50))
                    .padding(16.dp)
            ) {
                Text(
                    text = "대화 내용이 등록되었습니다.",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }

            // 메인 콘텐츠 영역
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // OCR 인식 결과 헤더
                Text(
                    text = "OCR 인식 결과 :",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // 인식된 텍스트 박스
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF5F5F5)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    if (isEditing) {
                        // 편집 모드
                        BasicTextField(
                            value = editedText,
                            onValueChange = { editedText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        )
                    } else {
                        // 읽기 모드
                        Text(
                            text = editedText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = Color.Black
                        )
                    }
                }

                // 안내 텍스트
                Text(
                    text = "① 인식된 텍스트가 정확하지 않다면 직접 수정할 수 있습니다.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // 액션 버튼들
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 다시 인식하기 버튼
                    TextButton(
                        onClick = { /* TODO: 다시 인식 기능 구현 */ }
                    ) {
                        Text(
                            text = "다시 인식하기",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }

                    // 직접 수정하기 버튼
                    TextButton(
                        onClick = { 
                            isEditing = !isEditing
                            if (!isEditing) {
                                // 편집 완료 시 저장
                                onSaveClick(editedText)
                            }
                        }
                    ) {
                        Text(
                            text = if (isEditing) "수정 완료" else "직접 수정하기",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }

                // 구분선
                Divider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    color = Color.Gray
                )

                // 옵션 선택 텍스트
                Text(
                    text = "옵션을 선택하세요",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    textAlign = TextAlign.Center,
                    color = Color.Gray,
                    fontSize = 14.sp
                )

                // 하단 버튼들
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 다시 선택 버튼
                    OutlinedButton(
                        onClick = { /* TODO: 다시 선택 기능 구현 */ },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "다시 선택",
                            fontSize = 14.sp
                        )
                    }

                    // 답변 추천 버튼
                    Button(
                        onClick = { /* TODO: 답변 추천 기능 구현 */ },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF3498DB)
                        )
                    ) {
                        Icon(
                            androidx.compose.material.icons.Icons.Default.Refresh, // TODO: 말풍선 아이콘으로 변경
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "답변 추천",
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
