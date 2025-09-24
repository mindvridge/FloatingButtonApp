package com.mv.floatingbuttonapp

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OcrBottomSheetActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OCR_TEXT = "ocr_text"
        const val EXTRA_SUGGESTIONS = "suggestions"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 키보드와 함께 레이아웃 조정
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val ocrText = intent.getStringExtra(EXTRA_OCR_TEXT) ?: ""
        val suggestions = intent.getStringArrayListExtra(EXTRA_SUGGESTIONS) ?: arrayListOf()

        setContent {
            MaterialTheme {
                OcrBottomSheetContent(
                    initialText = ocrText,
                    suggestions = suggestions,
                    onDismiss = { finish() },
                    onRetry = {
                        val intent = Intent("com.mv.floatingbuttonapp.RETRY_OCR")
                        sendBroadcast(intent)
                        finish()
                    }
                )
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(0, android.R.anim.slide_out_right)
    }
}

// WindowCompat 클래스 (androidx.core.view.WindowCompat이 없는 경우)
object WindowCompat {
    fun setDecorFitsSystemWindows(window: android.view.Window, decorFitsSystemWindows: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(decorFitsSystemWindows)
        } else {
            val decorView = window.decorView
            val sysUiVis = decorView.systemUiVisibility
            decorView.systemUiVisibility = if (decorFitsSystemWindows) {
                sysUiVis and View.SYSTEM_UI_FLAG_LAYOUT_STABLE.inv() and View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN.inv()
            } else {
                sysUiVis or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            }
        }
    }
}

// 키보드 높이를 정확하게 감지하는 Composable
@Composable
fun rememberImeState(): State<Boolean> {
    val imeState = remember { mutableStateOf(false) }
    val view = LocalView.current

    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val isKeyboardOpen = ViewCompat.getRootWindowInsets(view)
                ?.isVisible(WindowInsetsCompat.Type.ime()) ?: false
            imeState.value = isKeyboardOpen
        }

        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose {
            view.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }

    return imeState
}

// 답변 카테고리 데이터
data class ResponseCategory(
    val id: String,
    val name: String,
    val selected: Boolean = false
)

data class ResponseSubCategory(
    val id: String,
    val name: String,
    val selected: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrBottomSheetContent(
    initialText: String,
    suggestions: List<String>,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    var ocrText by remember { mutableStateOf(initialText) }
    var showCopiedMessage by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }
    var showResponseOptions by remember { mutableStateOf(false) }
    var generatedResponses by remember { mutableStateOf<List<String>>(emptyList()) }

    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    // 키보드 상태 감지 (개선된 방식)
    val isKeyboardOpen by rememberImeState()

    // 답변 카테고리 상태
    var selectedSituation by remember { mutableStateOf("쌈") }
    val situations = listOf("쌈", "연인")

    var selectedMood by remember { mutableStateOf<Set<String>>(setOf("질문형")) }
    val moods = listOf("질문형", "공감형", "호응형")

    var selectedLength by remember { mutableStateOf<Set<String>>(setOf("짧게")) }
    val lengths = listOf("짧게", "중간", "길게")

    var selectedTypes by remember { mutableStateOf<Set<String>>(setOf("질문형")) }
    val responseTypes = listOf("쌈", "질문형", "중간")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable {
                if (!isEditMode && !showResponseOptions) onDismiss()
            }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .align(if (isKeyboardOpen) Alignment.TopCenter else Alignment.BottomCenter)
                .then(
                    if (isKeyboardOpen) {
                        Modifier
                            .systemBarsPadding()
                            .imePadding()
                            .padding(top = 100.dp) // 키보드 열렸을 때 상단 여백
                    } else {
                        Modifier.navigationBarsPadding()
                    }
                )
                .animateContentSize(),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 헤더
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showResponseOptions) "답변 추천" else "대화 등록",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = {
                        if (showResponseOptions) {
                            showResponseOptions = false
                            generatedResponses = emptyList()
                        } else {
                            onDismiss()
                        }
                    }) {
                        Icon(
                            if (showResponseOptions) Icons.Default.ArrowBack else Icons.Default.Close,
                            contentDescription = "닫기",
                            tint = Color.Gray
                        )
                    }
                }

                // 답변 추천 옵션 화면
                if (showResponseOptions) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 생성된 답변들 표시
                        if (generatedResponses.isNotEmpty()) {
                            Text(
                                text = "추천 답변",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            generatedResponses.forEachIndexed { index, response ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                        .clickable {
                                            clipboardManager.setText(AnnotatedString(response))
                                            scope.launch {
                                                showCopiedMessage = true
                                                delay(2000)
                                                showCopiedMessage = false
                                            }
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFFF5F5F5)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = response,
                                            fontSize = 14.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "복사",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // 답변 추천 옵션들
                        Text(
                            text = "답변 추천 :",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // 대상자 선택 (토글)
                        Text(
                            text = "대상자",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            situations.forEach { situation ->
                                FilterChip(
                                    selected = selectedSituation == situation,
                                    onClick = { selectedSituation = situation },
                                    label = { Text(situation) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // 답변 모드
                        Text(
                            text = "답변 모드",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            moods.forEach { mood ->
                                FilterChip(
                                    selected = selectedMood.contains(mood),
                                    onClick = {
                                        selectedMood = if (selectedMood.contains(mood)) {
                                            selectedMood - mood
                                        } else {
                                            selectedMood + mood
                                        }
                                    },
                                    label = { Text(mood) }
                                )
                            }
                        }

                        // 답변 길이
                        Text(
                            text = "답변 길이",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            lengths.forEach { length ->
                                FilterChip(
                                    selected = selectedLength.contains(length),
                                    onClick = {
                                        selectedLength = if (selectedLength.contains(length)) {
                                            selectedLength - length
                                        } else {
                                            selectedLength + length
                                        }
                                    },
                                    label = { Text(length) }
                                )
                            }
                        }

                        // 현재 설정 표시
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFE8F4FF)
                            )
                        ) {
                            Text(
                                text = "현재 설정 : [$selectedSituation] [${selectedMood.joinToString()}] [${selectedLength.joinToString()}]",
                                fontSize = 12.sp,
                                color = Color(0xFF0066CC),
                                modifier = Modifier.padding(12.dp)
                            )
                        }

                        // 추천 답변 입력
                        OutlinedTextField(
                            value = "추천 답변",
                            onValueChange = {},
                            enabled = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            trailingIcon = {
                                IconButton(onClick = {
                                    // 답변 생성 로직
                                    generatedResponses = generateResponses(
                                        ocrText,
                                        selectedSituation,
                                        selectedMood,
                                        selectedLength
                                    )
                                }) {
                                    Icon(
                                        Icons.Default.Send,
                                        contentDescription = "생성",
                                        tint = Color(0xFF0066CC)
                                    )
                                }
                            }
                        )
                    }
                } else {
                    // 기본 OCR 화면
                    Text(
                        text = "대화 내용이 등록되었습니다.",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Text(
                        text = "OCR 인식 결과 :",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // OCR 텍스트 편집 영역
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isKeyboardOpen) 120.dp else 200.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            if (suggestions.isNotEmpty() && !isEditMode) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    suggestions.forEach { suggestion ->
                                        Text(
                                            text = suggestion,
                                            fontSize = 14.sp,
                                            color = Color(0xFF666666),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    ocrText = suggestion
                                                    isEditMode = true
                                                }
                                                .padding(vertical = 4.dp)
                                        )
                                    }
                                }
                            } else {
                                OutlinedTextField(
                                    value = ocrText,
                                    onValueChange = {
                                        ocrText = it
                                        isEditMode = true
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    ),
                                    textStyle = LocalTextStyle.current.copy(
                                        fontSize = 14.sp,
                                        color = Color(0xFF333333)
                                    )
                                )
                            }
                        }
                    }

                    // 키보드가 열려있지 않을 때만 버튼들 표시
                    if (!isKeyboardOpen) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF666666)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "인식된 텍스트가 정확하지 않다면 직접 수정할 수 있습니다.",
                                fontSize = 12.sp,
                                color = Color(0xFF666666)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = onRetry,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFE0E0E0)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("다시 인식하기", color = Color(0xFF666666))
                            }

                            Button(
                                onClick = { isEditMode = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFE0E0E0)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("직접 수정하기", color = Color(0xFF666666))
                            }
                        }

                        TextButton(
                            onClick = { /* 옵션 선택 */ },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Text(
                                text = "옵션을 선택하세요",
                                color = Color(0xFF4A90E2),
                                fontSize = 14.sp
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onRetry,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("다시 선택")
                            }

                            Button(
                                onClick = {
                                    showResponseOptions = true
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("답변 추천")
                            }
                        }
                    }
                }

                // 복사 완료 메시지
                if (showCopiedMessage) {
                    Snackbar(
                        modifier = Modifier.padding(top = 8.dp),
                        action = {
                            TextButton(onClick = { showCopiedMessage = false }) {
                                Text("확인")
                            }
                        }
                    ) {
                        Text("텍스트가 복사되었습니다")
                    }
                }
            }
        }
    }
}

// 답변 생성 함수
fun generateResponses(
    context: String,
    situation: String,
    moods: Set<String>,
    lengths: Set<String>
): List<String> {
    // 실제로는 AI API를 호출하거나 더 복잡한 로직을 사용해야 함
    // 여기서는 예시 답변을 반환

    return when {
        context.contains("내일 약속") || context.contains("내일 뭣시") -> {
            when {
                moods.contains("질문형") -> listOf(
                    "오 좋지! !! 내일 뭣시에 만날래?",
                    "힘 아이엑스라나 대박....! 이디로 갈까??",
                    "넘 좋아 ㅎㅎ 나 스파이더맨 진짜 좋아해!! 😊"
                )
                moods.contains("공감형") -> listOf(
                    "와 진짜 재밌겠다! 나도 보고 싶었어",
                    "오 대박! 스파이더맨 완전 기대돼",
                    "좋아좋아! 영화 본지 오래됐는데 딱이다"
                )
                else -> listOf(
                    "좋아! 몇 시에 볼까?",
                    "오케이! 어디서 볼까?",
                    "응응 가자!"
                )
            }
        }
        context.contains("영화") -> {
            listOf(
                "오 좋지! !! 내일 뭣시에 만날래?",
                "힘 아이엑스라나 대박....! 이디로 갈까??",
                "넘 좋아 ㅎㅎ 나 스파이더맨 진짜 좋아해!! 😊"
            )
        }
        else -> {
            listOf(
                "응 좋아!",
                "오 괜찮네~",
                "ㅇㅋㅇㅋ 가자!"
            )
        }
    }
}