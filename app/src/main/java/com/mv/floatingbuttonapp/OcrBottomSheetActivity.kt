package com.mv.floatingbuttonapp

import android.content.Intent
import android.content.SharedPreferences
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.mv.floatingbuttonapp.api.ApiClient
import com.mv.floatingbuttonapp.api.ReplyRequest
import android.widget.Toast
import android.util.Log
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import java.util.regex.Pattern

// 대화 분석을 위한 데이터 클래스
data class ConversationMessage(
    val speaker: String, // "나" 또는 "상대방"
    val content: String,
    val timestamp: String? = null
)

data class ConversationAnalysis(
    val myMessages: List<ConversationMessage>,
    val otherMessages: List<ConversationMessage>,
    val conversationSummary: String,
    val context: String
)

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
    val context = LocalContext.current
    var ocrText by remember { mutableStateOf(initialText) }
    var showCopiedMessage by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }
    var showResponseOptions by remember { mutableStateOf(false) }
    var generatedResponses by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var conversationAnalysis by remember { mutableStateOf<ConversationAnalysis?>(null) }
    var showConversationAnalysis by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // SharedPreferences를 사용하여 선택된 내용 저장/불러오기
    val prefs = remember { 
        context.getSharedPreferences("response_preferences", Context.MODE_PRIVATE) 
    }
    
    // 저장된 선택 내용 불러오기
    val savedSituation = remember { prefs.getString("selected_situation", "썸") ?: "썸" }
    val savedMood = remember { prefs.getString("selected_mood", "질문형") ?: "질문형" }
    val savedLength = remember { prefs.getString("selected_length", "짧게") ?: "짧게" }
    
    // 선택 내용 저장 함수
    fun saveSelection(situation: String, mood: String, length: String) {
        prefs.edit().apply {
            putString("selected_situation", situation)
            putString("selected_mood", mood)
            putString("selected_length", length)
            apply()
        }
    }

    val clipboardManager = LocalClipboardManager.current

    // 키보드 상태 감지
    val isKeyboardOpen by rememberImeState()

    // 대상자 (단일 선택 유지) - 저장된 값으로 초기화
    var selectedSituation by remember { mutableStateOf(savedSituation) }
    val situations = listOf("썸", "연인", "친구", "가족", "동료")

    // ✅ 답변 모드 (단일 선택으로 변경) - 저장된 값으로 초기화
    var selectedMood by remember { mutableStateOf(savedMood) }
    val moods = listOf("질문형", "공감형", "호응형")

    // ✅ 답변 길이 (단일 선택으로 변경) - 저장된 값으로 초기화
    var selectedLength by remember { mutableStateOf(savedLength) }
    val lengths = listOf("짧게", "중간", "길게")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { if (!isEditMode && !showResponseOptions) onDismiss() }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .align(Alignment.BottomCenter)        // 🔧 항상 하단 정렬 유지
                .navigationBarsPadding()
                .imePadding()                         // 🔧 키보드 높이만큼 하단 패딩 부여
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
                        } else onDismiss()
                    }) {
                        Icon(
                            if (showResponseOptions) Icons.Default.ArrowBack else Icons.Default.Close,
                            contentDescription = "닫기",
                            tint = Color.Gray
                        )
                    }
                }

                // 답변 추천 화면
                if (showResponseOptions) {
                    Column(modifier = Modifier.fillMaxWidth()) {

                        // 생성된 답변 목록
                        if (isLoading) {
                            // 로딩 상태 표시
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF2196F3)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "AI가 답변을 생성하고 있습니다...",
                                        fontSize = 14.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        } else if (generatedResponses.isNotEmpty()) {
                            Text(
                                text = "추천 답변",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            generatedResponses.forEach { response ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                        .clickable {
                                            try {
                                                // 클립보드에 복사
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("text", response)
                                                clipboard.setPrimaryClip(clip)
                                                
                                                // 텍스트 입력 필드에 직접 입력
                                                val intent = Intent("com.mv.floatingbuttonapp.INSERT_TEXT").apply {
                                                    putExtra("text", response)
                                                }
                                                context.sendBroadcast(intent)
                                                
                                                Toast.makeText(
                                                    context,
                                                    "답변이 입력되었습니다.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                
                                                onDismiss() // 하단 UI 닫기
                                            } catch (e: Exception) {
                                                Log.e("TextInsert", "Failed to insert text: ${e.message}")
                                                // 실패시 클립보드 복사만 수행
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("text", response)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(context, "클립보드에 복사되었습니다.", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
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
                            
                            // 재 추천 버튼
                            Button(
                                onClick = {
                                    isLoading = true
                                    scope.launch {
                                        try {
                                            val responses = generateResponses(
                                                context = ocrText,
                                                situation = selectedSituation,
                                                mood = selectedMood,
                                                length = selectedLength
                                            )
                                            generatedResponses = responses
                                        } catch (e: Exception) {
                                            Log.e("API_ERROR", "Failed to regenerate responses: ${e.message}", e)
                                            generatedResponses = listOf("오류가 발생했습니다. 다시 시도해주세요.")
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                shape = RoundedCornerShape(24.dp),
                                enabled = !isLoading
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isLoading) "재생성 중..." else "다시 추천받기")
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // 옵션 타이틀
                        Text(
                            text = "답변 추천 :",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // 대상자 (단일 선택)
                        Text("대상자", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            situations.forEach { situation ->
                                FilterChip(
                                    selected = selectedSituation == situation,
                                    onClick = { 
                                        selectedSituation = situation
                                        saveSelection(selectedSituation, selectedMood, selectedLength)
                                    },
                                    label = { Text(situation) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // ✅ 답변 모드 (단일 선택)
                        Text("답변 모드", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            moods.forEach { mood ->
                                FilterChip(
                                    selected = selectedMood == mood,
                                    onClick = { 
                                        selectedMood = mood
                                        saveSelection(selectedSituation, selectedMood, selectedLength)
                                    },
                                    label = { Text(mood) }
                                )
                            }
                        }

                        // ✅ 답변 길이 (단일 선택)
                        Text("답변 길이", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            lengths.forEach { length ->
                                FilterChip(
                                    selected = selectedLength == length,
                                    onClick = { 
                                        selectedLength = length
                                        saveSelection(selectedSituation, selectedMood, selectedLength)
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
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F4FF))
                        ) {
                            Text(
                                text = "현재 설정 : [$selectedSituation] [$selectedMood] [$selectedLength]",
                                fontSize = 12.sp,
                                color = Color(0xFF0066CC),
                                modifier = Modifier.padding(12.dp)
                            )
                        }

                        // 생성 버튼 (필드 형태는 유지)
                        OutlinedTextField(
                            value = "추천 답변",
                            onValueChange = {},
                            enabled = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            trailingIcon = {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF0066CC)
                                    )
                                } else {
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
                    // 기존 OCR 기본 화면 (변경 없음)
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

                    if (!isKeyboardOpen) {
                        // 안내 및 버튼들 (기존 그대로)
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


                       /* TextButton(
                            onClick = {  옵션 선택  },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Text(text = "옵션을 선택하세요", color = Color(0xFF4A90E2), fontSize = 14.sp)
                        }*/

                        Text(text = "옵션을 선택하세요", color = Color(0xFF4A90E2), fontSize = 14.sp)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onRetry,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("다시 선택")
                            }

                            OutlinedButton(
                                onClick = {
                                    conversationAnalysis = analyzeConversation(ocrText)
                                    showConversationAnalysis = true
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF4A90E2)
                                )
                            ) {
                                Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("대화 분석")
                            }

                            Button(
                                onClick = { 
                                    showResponseOptions = true
                                    isLoading = true
                                    scope.launch {
                                        try {
                                            val responses = generateResponses(
                                                context = ocrText,
                                                situation = selectedSituation,
                                                mood = selectedMood,
                                                length = selectedLength
                                            )
                                            generatedResponses = responses
                                        } catch (e: Exception) {
                                            Log.e("API_ERROR", "Failed to generate responses: ${e.message}", e)
                                            generatedResponses = listOf("오류가 발생했습니다. 다시 시도해주세요.")
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(24.dp),
                                enabled = !isLoading
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isLoading) "생성 중..." else "답변 추천")
                            }
                        }

                    }
                }

                if (showCopiedMessage) {
                    Snackbar(
                        modifier = Modifier.padding(top = 8.dp),
                        action = { TextButton(onClick = { showCopiedMessage = false }) { Text("확인") } }
                    ) { Text("텍스트가 복사되었습니다") }
                }

                // 대화 분석 다이얼로그
                if (showConversationAnalysis && conversationAnalysis != null) {
                    ConversationAnalysisDialog(
                        analysis = conversationAnalysis!!,
                        onDismiss = { showConversationAnalysis = false }
                    )
                }
            }
        }
    }
}


// API를 통한 답변 생성 함수
suspend fun generateResponses(
    context: String,
    situation: String,
    mood: String,
    length: String
): List<String> {
    return try {
        // 실제 API에 맞는 요청 데이터 생성
        val request = ReplyRequest(
            대상자 = mapSituationToApiValue(situation), // API가 기대하는 값으로 매핑
            답변모드 = mapMoodToApiValue(mood), // API가 기대하는 값으로 매핑
            답변길이 = mapLengthToApiValue(length), // API가 기대하는 값으로 매핑
            대화내용 = context,
            추가지침 = "" // 추가지침은 빈 문자열로 설정
        )
        
        // 요청 데이터 유효성 검사
        if (request.대상자.isBlank()) {
            Log.e("API_DEBUG", "대상자가 비어있습니다")
            return listOf("대상자를 선택해주세요.", "설정을 확인해주세요.", "다시 시도해주세요.")
        }
        if (request.답변모드.isBlank()) {
            Log.e("API_DEBUG", "답변모드가 비어있습니다")
            return listOf("답변모드를 선택해주세요.", "설정을 확인해주세요.", "다시 시도해주세요.")
        }
        if (request.답변길이.isBlank()) {
            Log.e("API_DEBUG", "답변길이가 비어있습니다")
            return listOf("답변길이를 선택해주세요.", "설정을 확인해주세요.", "다시 시도해주세요.")
        }
        if (request.대화내용.isBlank()) {
            Log.e("API_DEBUG", "대화내용이 비어있습니다")
            return listOf("대화내용이 없습니다.", "텍스트를 다시 캡처해주세요.", "다시 시도해주세요.")
        }

        Log.d("API_DEBUG", "=== API Request Details ===")
        Log.d("API_DEBUG", "원본 값 - situation: '$situation', mood: '$mood', length: '$length'")
        Log.d("API_DEBUG", "매핑된 값 - 대상자: '${request.대상자}', 답변모드: '${request.답변모드}', 답변길이: '${request.답변길이}'")
        Log.d("API_DEBUG", "대화내용: '${request.대화내용}'")
        Log.d("API_DEBUG", "추가지침: '${request.추가지침}'")
        Log.d("API_DEBUG", "Full Request: $request")
        
        // JSON 직렬화 테스트
        try {
            val gson = com.google.gson.Gson()
            val jsonString = gson.toJson(request)
            Log.d("API_DEBUG", "JSON Request: $jsonString")
        } catch (e: Exception) {
            Log.e("API_DEBUG", "JSON serialization failed: ${e.message}")
        }
        
        // API 호출 시작 시간 기록
        val startTime = System.currentTimeMillis()
        Log.d("API_DEBUG", "=== API 호출 시작 ===")
        Log.d("API_DEBUG", "시작 시간: ${java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date(startTime))}")
        
        val response = ApiClient.apiService.getReplies(request)
        
        // API 호출 완료 시간 기록
        val endTime = System.currentTimeMillis()
        val responseTime = endTime - startTime
        Log.d("API_DEBUG", "=== API 호출 완료 ===")
        Log.d("API_DEBUG", "완료 시간: ${java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date(endTime))}")
        Log.d("API_DEBUG", "응답 시간: ${responseTime}ms (${responseTime / 1000.0}초)")
        
        Log.d("API_DEBUG", "=== API Response Details ===")
        Log.d("API_DEBUG", "Response code: ${response.code()}")
        Log.d("API_DEBUG", "Response successful: ${response.isSuccessful}")
        Log.d("API_DEBUG", "Response headers: ${response.headers()}")
        
        if (response.isSuccessful) {
            Log.d("API_DEBUG", "Response body: ${response.body()}")
        } else {
            val errorBody = response.errorBody()?.string()
            Log.e("API_DEBUG", "Error body: $errorBody")
            Log.e("API_DEBUG", "Error headers: ${response.headers()}")
            Log.e("API_DEBUG", "Error message: ${response.message()}")
        }
        
        if (response.isSuccessful) {
            val responseBody = response.body()
            val replies = extractRepliesFromResponse(responseBody)
            replies.ifEmpty { 
                listOf(
                    "죄송합니다. 답변을 생성할 수 없습니다.",
                    "다시 시도해주세요.",
                    "잠시 후 다시 시도해주세요."
                )
            }
        } else {
            val errorMessage = response.errorBody()?.string() ?: "Unknown error"
            Log.e("API_ERROR", "API Error: ${response.code()} - $errorMessage")
            Log.e("API_ERROR", "Request was: $request")
            
            when (response.code()) {
                422 -> {
                    Log.e("API_DEBUG", "422 Error Details: $errorMessage")
                    Log.e("API_DEBUG", "Request that caused 422: $request")
                    listOf(
                        "요청 데이터가 올바르지 않습니다. (422)",
                        "오류: $errorMessage",
                        "설정을 확인해주세요.",
                        "대상자: ${request.대상자}, 답변모드: ${request.답변모드}, 답변길이: ${request.답변길이}"
                    )
                }
                400 -> listOf(
                    "잘못된 요청입니다. (400)",
                    "오류: $errorMessage",
                    "다시 시도해주세요."
                )
                500 -> listOf(
                    "서버 내부 오류가 발생했습니다. (500)",
                    "오류: $errorMessage",
                    "잠시 후 다시 시도해주세요."
                )
                else -> listOf(
                    "서버 오류가 발생했습니다. (${response.code()})",
                    "오류: $errorMessage",
                    "잠시 후 다시 시도해주세요."
                )
            }
        }
    } catch (e: Exception) {
        Log.e("API_EXCEPTION", "API Exception: ${e.message}", e)
            listOf(
            "네트워크 오류가 발생했습니다.",
            "오류: ${e.message}",
            "잠시 후 다시 시도해주세요."
        )
    }
}

// API 응답에서 답변 추출 함수
fun extractRepliesFromResponse(response: com.mv.floatingbuttonapp.api.ReplyResponse?): List<String> {
    if (response == null) return emptyList()
    
    // answers 필드에서 답변 추출
    val answers = response.answers
    if (!answers.isNullOrEmpty()) {
        return answers.filter { it.isNotBlank() }
    }
    
    return emptyList()
}

// 대상자를 API가 기대하는 값으로 매핑하는 함수
fun mapSituationToApiValue(situation: String): String {
    return when (situation.lowercase()) {
        "썸" -> "썸"
        "연인" -> "연인"
        "친구" -> "친구"
        "가족" -> "가족"
        "동료" -> "동료"
        else -> "썸" // 기본값
    }
}

// 답변모드를 API가 기대하는 값으로 매핑하는 함수
fun mapMoodToApiValue(mood: String): String {
    return when (mood.lowercase()) {
        "질문형" -> "질문형"
        "공감형" -> "공감형"
        "호응형" -> "호응형"
        else -> "공감형" // 기본값
    }
}

// 길이를 API가 기대하는 값으로 매핑하는 함수
fun mapLengthToApiValue(length: String): String {
    return when (length.lowercase()) {
        "짧게", "짧은", "간단" -> "짧게"
        "중간", "보통", "적당" -> "중간"
        "길게", "긴", "자세" -> "길게"
        else -> "중간" // 기본값
    }
}

// OCR 텍스트에서 대화 내용을 분석하여 상대방과 내 대화를 분류하는 함수
fun analyzeConversation(ocrText: String): ConversationAnalysis {
    Log.d("CONVERSATION_ANALYSIS", "=== 대화 분석 시작 ===")
    Log.d("CONVERSATION_ANALYSIS", "원본 OCR 텍스트: $ocrText")
    
    val myMessages = mutableListOf<ConversationMessage>()
    val otherMessages = mutableListOf<ConversationMessage>()
    
    // 대화 패턴 분석을 위한 정규식들
    val patterns = listOf(
        // 시간 패턴 (오전/오후, 24시간 형식)
        Pattern.compile("(오전|오후)\\s*\\d{1,2}:\\d{2}"),
        Pattern.compile("\\d{1,2}:\\d{2}"),
        Pattern.compile("\\d{1,2}시\\s*\\d{1,2}분"),
        
        // 이름 패턴 (한글 이름, 영어 이름)
        Pattern.compile("[가-힣]{2,4}\\s*:"),
        Pattern.compile("[A-Za-z]{2,10}\\s*:"),
        
        // 메신저 앱 패턴
        Pattern.compile("(나|내가|저는|제가)\\s*:"),
        Pattern.compile("(상대방|친구|가족|동료)\\s*:"),
        
        // 일반적인 대화 시작 패턴
        Pattern.compile("(안녕|하이|헬로|좋은|오늘|어제|내일)"),
        Pattern.compile("(고마워|감사|미안|죄송|괜찮아|응|네|아니|그래)")
    )
    
    // 텍스트를 줄 단위로 분할
    val lines = ocrText.split("\n").filter { it.trim().isNotEmpty() }
    
    var currentSpeaker = "상대방" // 기본값
    var currentMessage = StringBuilder()
    
    for (line in lines) {
        val trimmedLine = line.trim()
        if (trimmedLine.isEmpty()) continue
        
        Log.d("CONVERSATION_ANALYSIS", "분석 중인 줄: $trimmedLine")
        
        // 시간 패턴 확인
        val hasTimePattern = patterns[0].matcher(trimmedLine).find() || 
                            patterns[1].matcher(trimmedLine).find() || 
                            patterns[2].matcher(trimmedLine).find()
        
        // 이름 패턴 확인
        val hasNamePattern = patterns[3].matcher(trimmedLine).find() || 
                            patterns[4].matcher(trimmedLine).find()
        
        // 메신저 앱 패턴 확인
        val hasMessengerPattern = patterns[5].matcher(trimmedLine).find() || 
                                 patterns[6].matcher(trimmedLine).find()
        
        // 대화 시작 패턴 확인
        val hasConversationPattern = patterns[7].matcher(trimmedLine).find() || 
                                    patterns[8].matcher(trimmedLine).find()
        
        when {
            // 시간 패턴이 있으면 새로운 대화 시작
            hasTimePattern -> {
                // 이전 메시지 저장
                if (currentMessage.isNotEmpty()) {
                    val message = ConversationMessage(
                        speaker = currentSpeaker,
                        content = currentMessage.toString().trim(),
                        timestamp = null
                    )
                    if (currentSpeaker == "나") {
                        myMessages.add(message)
                    } else {
                        otherMessages.add(message)
                    }
                    currentMessage.clear()
                }
                currentSpeaker = "상대방" // 시간 패턴 후에는 상대방 메시지로 가정
            }
            
            // 이름 패턴이 있으면 화자 변경
            hasNamePattern -> {
                // 이전 메시지 저장
                if (currentMessage.isNotEmpty()) {
                    val message = ConversationMessage(
                        speaker = currentSpeaker,
                        content = currentMessage.toString().trim(),
                        timestamp = null
                    )
                    if (currentSpeaker == "나") {
                        myMessages.add(message)
                    } else {
                        otherMessages.add(message)
                    }
                    currentMessage.clear()
                }
                
                // 화자 판단
                currentSpeaker = when {
                    trimmedLine.contains("나") || trimmedLine.contains("내가") || 
                    trimmedLine.contains("저는") || trimmedLine.contains("제가") -> "나"
                    else -> "상대방"
                }
            }
            
            // 메신저 앱 패턴이 있으면 화자 변경
            hasMessengerPattern -> {
                // 이전 메시지 저장
                if (currentMessage.isNotEmpty()) {
                    val message = ConversationMessage(
                        speaker = currentSpeaker,
                        content = currentMessage.toString().trim(),
                        timestamp = null
                    )
                    if (currentSpeaker == "나") {
                        myMessages.add(message)
                    } else {
                        otherMessages.add(message)
                    }
                    currentMessage.clear()
                }
                
                // 화자 판단
                currentSpeaker = when {
                    trimmedLine.contains("나") || trimmedLine.contains("내가") || 
                    trimmedLine.contains("저는") || trimmedLine.contains("제가") -> "나"
                    else -> "상대방"
                }
            }
            
            // 대화 시작 패턴이 있으면 새로운 메시지 시작
            hasConversationPattern -> {
                // 이전 메시지 저장
                if (currentMessage.isNotEmpty()) {
                    val message = ConversationMessage(
                        speaker = currentSpeaker,
                        content = currentMessage.toString().trim(),
                        timestamp = null
                    )
                    if (currentSpeaker == "나") {
                        myMessages.add(message)
                    } else {
                        otherMessages.add(message)
                    }
                    currentMessage.clear()
                }
                currentSpeaker = "상대방" // 대화 시작은 상대방으로 가정
            }
        }
        
        // 현재 줄을 메시지에 추가
        if (currentMessage.isNotEmpty()) {
            currentMessage.append(" ")
        }
        currentMessage.append(trimmedLine)
    }
    
    // 마지막 메시지 저장
    if (currentMessage.isNotEmpty()) {
        val message = ConversationMessage(
            speaker = currentSpeaker,
            content = currentMessage.toString().trim(),
            timestamp = null
        )
        if (currentSpeaker == "나") {
            myMessages.add(message)
        } else {
            otherMessages.add(message)
        }
    }
    
    // 대화 요약 생성
    val conversationSummary = generateConversationSummary(myMessages, otherMessages)
    
    // 분석 결과 로깅
    Log.d("CONVERSATION_ANALYSIS", "=== 대화 분석 결과 ===")
    Log.d("CONVERSATION_ANALYSIS", "내 메시지 수: ${myMessages.size}")
    Log.d("CONVERSATION_ANALYSIS", "상대방 메시지 수: ${otherMessages.size}")
    Log.d("CONVERSATION_ANALYSIS", "내 메시지: ${myMessages.map { it.content }}")
    Log.d("CONVERSATION_ANALYSIS", "상대방 메시지: ${otherMessages.map { it.content }}")
    
    return ConversationAnalysis(
        myMessages = myMessages,
        otherMessages = otherMessages,
        conversationSummary = conversationSummary,
        context = ocrText
    )
}

// 대화 요약을 생성하는 함수
fun generateConversationSummary(myMessages: List<ConversationMessage>, otherMessages: List<ConversationMessage>): String {
    val allMessages = (myMessages + otherMessages).sortedBy { it.content.length }
    
    return when {
        allMessages.isEmpty() -> "대화 내용이 없습니다."
        allMessages.size == 1 -> "단일 메시지: ${allMessages.first().content}"
        allMessages.size <= 3 -> "짧은 대화: ${allMessages.joinToString(" | ") { it.content.take(20) }}"
        else -> "긴 대화 (${allMessages.size}개 메시지): ${allMessages.take(3).joinToString(" | ") { it.content.take(15) }}..."
    }
}

// 대화 분석 결과를 표시하는 다이얼로그
@Composable
fun ConversationAnalysisDialog(
    analysis: ConversationAnalysis,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // 헤더
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "대화 분석 결과",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2C3E50)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "닫기",
                            tint = Color.Gray
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 대화 요약
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "대화 요약",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF4A90E2)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = analysis.conversationSummary,
                            fontSize = 12.sp,
                            color = Color(0xFF2C3E50)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 내 메시지
                if (analysis.myMessages.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E8)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "내 메시지 (${analysis.myMessages.size}개)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF27AE60)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            analysis.myMessages.forEach { message ->
                                Text(
                                    text = "• ${message.content}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF2C3E50),
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                // 상대방 메시지
                if (analysis.otherMessages.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "상대방 메시지 (${analysis.otherMessages.size}개)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFE67E22)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            analysis.otherMessages.forEach { message ->
                                Text(
                                    text = "• ${message.content}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF2C3E50),
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // 닫기 버튼
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4A90E2)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("확인", color = Color.White)
                }
            }
        }
    }
}

