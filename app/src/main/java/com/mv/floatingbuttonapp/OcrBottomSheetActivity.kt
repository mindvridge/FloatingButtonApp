package com.mv.floatingbuttonapp

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.mv.floatingbuttonapp.api.ApiClient
import com.mv.floatingbuttonapp.api.ReplyRequest
import android.widget.Toast
import android.util.Log
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.platform.LocalContext


class OcrBottomSheetActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OCR_TEXT = "ocr_text"
        const val EXTRA_SUGGESTIONS = "suggestions"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 키보드와 함께 레이아웃 조정
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // OCR 텍스트와 추천 답변
        val ocrText = intent.getStringExtra("extracted_text") ?: intent.getStringExtra(EXTRA_OCR_TEXT) ?: ""
        val suggestions = intent.getStringArrayListExtra("suggestions") ?: intent.getStringArrayListExtra(EXTRA_SUGGESTIONS) ?: arrayListOf()
        
        // 디버깅을 위한 로그
        Log.d("OcrBottomSheet", "받은 OCR 텍스트: '$ocrText'")
        Log.d("OcrBottomSheet", "받은 추천 답변: $suggestions")
        Log.d("OcrBottomSheet", "Intent extras: ${intent.extras?.keySet()}")
        

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
    
    // OCR 텍스트를 자동으로 정리하여 초기화
    var ocrText by remember { 
        mutableStateOf(
            if (initialText.isNotEmpty()) {
                val cleanedText = cleanAndFormatOcrText(initialText)
                Log.d("OCR_CLEANUP", "초기 텍스트 정리 완료: '$cleanedText'")
                cleanedText
            } else {
                initialText
            }
        )
    }
    var showCopiedMessage by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }
    var showResponseOptions by remember { mutableStateOf(false) }
    var generatedResponses by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
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

                    }
                } else {
                    // 인식된 텍스트만 표시
                    Text(
                        text = "인식된 텍스트",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2C3E50),
                        modifier = Modifier.padding(bottom = 16.dp)
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
                            if (ocrText.isNotEmpty()) {
                                // OCR 텍스트가 있는 경우
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
                            } else if (suggestions.isNotEmpty()) {
                                // OCR 텍스트가 없고 추천 답변만 있는 경우
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = "인식된 텍스트가 없습니다. 추천 답변을 확인해주세요:",
                                        fontSize = 12.sp,
                                        color = Color(0xFF666666),
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
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
                                // 아무것도 없는 경우
                                Text(
                                    text = "인식된 텍스트가 없습니다.",
                                    fontSize = 14.sp,
                                    color = Color(0xFF999999),
                                    modifier = Modifier.align(Alignment.Center)
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
                                text = "인식된 텍스트가 정확하지 않다면 직접 수정하거나 다시 정리할 수 있습니다.",
                                fontSize = 12.sp,
                                color = Color(0xFF666666)
                            )
                        }
                        
                        // 다시 정리 버튼 (화면 가로 전체)
                        if (ocrText.isNotEmpty()) {
                            Button(
                                onClick = {
                                    val originalText = initialText // 원본 텍스트에서 다시 정리
                                    val cleanedText = cleanAndFormatOcrText(originalText)
                                    ocrText = cleanedText
                                    isEditMode = true
                                    Toast.makeText(context, "텍스트를 다시 정리했습니다.", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4A90E2)
                                ),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "다시 정리",
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }


                      
                        // 답변 추천 버튼 (화면 가로 전체)
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
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

                if (showCopiedMessage) {
                    Snackbar(
                        modifier = Modifier.padding(top = 8.dp),
                        action = { TextButton(onClick = { showCopiedMessage = false }) { Text("확인") } }
                    ) { Text("텍스트가 복사되었습니다") }
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

/**
 * OCR 인식된 텍스트를 정리하여 대화 형식으로 변환하는 함수
 * 
 * @param rawOcrText OCR로 인식된 원본 텍스트
 * @return 정리된 대화 형식의 텍스트
 */
fun cleanAndFormatOcrText(rawOcrText: String): String {
    if (rawOcrText.isBlank()) return ""
    
    Log.d("OCR_CLEANUP", "원본 OCR 텍스트: $rawOcrText")
    
    // 1. 줄바꿈으로 분할하고 정리
    val lines = rawOcrText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    Log.d("OCR_CLEANUP", "분할된 줄들: $lines")
    
    val messages = mutableListOf<Pair<String, String>>() // (화자, 메시지)
    var currentSpeaker = ""
    var currentMessage = ""
    
    for (line in lines) {
        // 2. 시간 패턴 제거 (오후 4:43, 오후 4:46 등)
        if (isTimePattern(line)) {
            Log.d("OCR_CLEANUP", "시간 패턴 제거: $line")
            continue
        }
        
        // 3. URL 패턴 제거
        if (isUrlPattern(line)) {
            Log.d("OCR_CLEANUP", "URL 패턴 제거: $line")
            continue
        }
        
        // 4. 숫자만 있는 줄 제거 (1, 2, 3, 4, 5, 6, 7, 8, 9, 0 등)
        if (isNumberOnlyPattern(line)) {
            Log.d("OCR_CLEANUP", "숫자만 있는 줄 제거: $line")
            continue
        }
        
        // 5. 키보드 관련 텍스트 제거 (ㄷ, ㅋ, 트, 초, 요, 이, ㅠ 등)
        if (isKeyboardPattern(line)) {
            Log.d("OCR_CLEANUP", "키보드 패턴 제거: $line")
            continue
        }
        
        // 6. UI 관련 텍스트 제거
        if (isUIElementPattern(line)) {
            Log.d("OCR_CLEANUP", "UI 요소 패턴 제거: $line")
            continue
        }
        
        // 7. 화자 식별 (개선된 로직)
        val speaker = identifySpeakerImproved(line)
        if (speaker.isNotEmpty()) {
            // 이전 메시지가 있으면 저장
            if (currentSpeaker.isNotEmpty() && currentMessage.isNotEmpty()) {
                messages.add(Pair(currentSpeaker, currentMessage.trim()))
                Log.d("OCR_CLEANUP", "메시지 저장: [$currentSpeaker] $currentMessage")
            }
            // 새 화자로 시작
            currentSpeaker = speaker
            currentMessage = extractMessageFromLine(line, speaker)
        } else if (isValidMessage(line)) {
            // 유효한 메시지인 경우
            val messageOwner = identifyMessageOwner(line)
            
            if (messageOwner.isNotEmpty()) {
                // 명확히 화자가 식별된 경우
                if (currentSpeaker == messageOwner && currentMessage.isNotEmpty()) {
                    // 같은 화자의 메시지를 연결
                    currentMessage += " " + line
                } else {
                    // 다른 화자이거나 새로운 메시지인 경우
                    if (currentSpeaker.isNotEmpty() && currentMessage.isNotEmpty()) {
                        messages.add(Pair(currentSpeaker, currentMessage.trim()))
                        Log.d("OCR_CLEANUP", "메시지 저장: [$currentSpeaker] $currentMessage")
                    }
                    currentSpeaker = messageOwner
                    currentMessage = line
                }
            } else if (currentSpeaker.isNotEmpty()) {
                // 현재 화자가 있으면 같은 메시지로 병합 (여러 줄로 나뉜 메시지)
                currentMessage += " " + line
            } else {
                // 화자를 추정해야 하는 경우
                currentSpeaker = estimateSpeaker(line, messages)
                currentMessage = line
            }
        }
    }
    
    // 마지막 메시지 저장
    if (currentSpeaker.isNotEmpty() && currentMessage.isNotEmpty()) {
        messages.add(Pair(currentSpeaker, currentMessage.trim()))
        Log.d("OCR_CLEANUP", "마지막 메시지 저장: [$currentSpeaker] $currentMessage")
    }
    
    // 8. "상대방"을 실제 상대방 이름으로 교체
    // 먼저 실제 상대방 이름을 찾기 (엄마, 아빠, 친구 등)
    val actualSpeakerName = messages.firstOrNull { it.first != "나" && it.first != "상대방" }?.first
    val finalMessages = messages.map { (speaker, message) ->
        val finalSpeaker = when {
            speaker == "상대방" && actualSpeakerName != null -> actualSpeakerName
            speaker == "상대방" -> "상대방" // 실제 이름을 찾지 못한 경우
            else -> speaker
        }
        Pair(finalSpeaker, message)
    }
    
    // 9. 최종 형식으로 변환
    val result = finalMessages.joinToString("\n") { (speaker, message) ->
        "[$speaker] $message"
    }
    
    Log.d("OCR_CLEANUP", "최종 결과: $result")
    return result
}

/**
 * 시간 패턴인지 확인하는 함수
 */
private fun isTimePattern(line: String): Boolean {
    val timePatterns = listOf(
        Regex("오후\\s*\\d{1,2}:\\d{2}"),
        Regex("오전\\s*\\d{1,2}:\\d{2}"),
        Regex("\\d{1,2}:\\d{2}"),
        Regex("\\d{1,2}시\\s*\\d{1,2}분"),
        Regex("AM\\s*\\d{1,2}:\\d{2}"),
        Regex("PM\\s*\\d{1,2}:\\d{2}")
    )
    return timePatterns.any { it.matches(line) }
}

/**
 * URL 패턴인지 확인하는 함수
 */
private fun isUrlPattern(line: String): Boolean {
    return line.contains("http") || line.contains("www.") || 
           line.contains(".com") || line.contains(".kr") ||
           line.contains("://") || line.contains("/")
}

/**
 * 숫자만 있는 패턴인지 확인하는 함수
 */
private fun isNumberOnlyPattern(line: String): Boolean {
    return line.matches(Regex("^[\\d\\s\\+\\-\\*\\/\\=\\.]+$")) && line.length <= 5
}

/**
 * 키보드 관련 패턴인지 확인하는 함수
 */
private fun isKeyboardPattern(line: String): Boolean {
    val keyboardPatterns = listOf(
        "ㄷ", "ㅋ", "트", "초", "요", "이", "ㅠ", "L", "Pass"
    )
    return keyboardPatterns.contains(line) || line.matches(Regex("^[ㄱ-ㅎㅏ-ㅣ\\s]+$"))
}

/**
 * UI 요소 패턴인지 확인하는 함수
 */
private fun isUIElementPattern(line: String): Boolean {
    val uiPatterns = listOf(
        "메시지 입력", "←", "→", "+", "!", "#", "Ut"
    )
    // UI 요소나 너무 짧은 텍스트(특수문자, 기호 등)는 제외
    // 단, 한글로만 구성된 3자 이상의 텍스트는 유지
    val isShortAndMeaningless = line.length <= 3 && !line.matches(Regex("^[가-힣]+$"))
    return uiPatterns.contains(line) || isShortAndMeaningless
}

/**
 * 유효한 메시지인지 확인하는 함수
 */
private fun isValidMessage(line: String): Boolean {
    return line.length > 3 && !isTimePattern(line) && !isUrlPattern(line) && 
           !isNumberOnlyPattern(line) && !isKeyboardPattern(line) && !isUIElementPattern(line)
}

/**
 * 개선된 화자 식별 함수
 */
private fun identifySpeakerImproved(line: String): String {
    // 엄마 관련 패턴
    if (line.contains("엄마") && line.length <= 10) {
        return "엄마"
    }
    
    // 다른 일반적인 화자 패턴들
    val speakerPatterns = listOf(
        "아빠", "할머니", "할아버지", "언니", "누나", "형", "오빠",
        "친구", "동생", "선생님", "회장님", "과장님", "부장님"
    )
    
    for (pattern in speakerPatterns) {
        if (line.contains(pattern) && line.length <= 15) {
            return pattern
        }
    }
    
    return ""
}

/**
 * 메시지 내용을 분석하여 화자를 식별하는 함수
 * "나"의 메시지인지 상대방의 메시지인지 명확히 판단 가능한 경우에만 반환
 */
private fun identifyMessageOwner(line: String): String {
    // "나"의 전형적인 짧은 대답이나 동의 표현
    val myPatterns = listOf(
        Regex("^네$"), Regex("^응$"), Regex("^알겠어(요)?$"),
        Regex("^좋아요?$"), Regex("^괜찮아요?$"), Regex("^예$"),
        Regex(".*안가도.*되잖아$"), // "약수동 안가도 되잖아"와 같은 패턴
        Regex(".*괜찮아요?$"), Regex(".*알겠어요?$"), Regex(".*좋아요?$")
    )
    
    // 상대방의 전형적인 설명이나 요청 표현
    val theirPatterns = listOf(
        Regex(".*가려고.*약속했어.*"), // "원래 2일날 같이 택사시타고 가려고 약속했어"
        Regex(".*물어보는거야$"), // "그래서 물어보는거야"
        Regex(".*연락할[거게께]$"), // "연락해 그리고나서 연락할께"
        Regex(".*해줘.*"), Regex(".*해봐.*"),
        Regex(".*어때\\??"), Regex(".*할거.*")
    )
    
    // "나"의 메시지 패턴 확인
    if (myPatterns.any { it.matches(line) }) {
        return "나"
    }
    
    // 상대방의 메시지 패턴 확인
    if (theirPatterns.any { it.matches(line) }) {
        return "상대방"
    }
    
    return "" // 명확하지 않은 경우 빈 문자열 반환
}

/**
 * 화자를 추정하는 함수 (이전 메시지 패턴 기반)
 */
private fun estimateSpeaker(line: String, messages: List<Pair<String, String>>): String {
    // 먼저 내용을 보고 명확히 판단 가능한 경우 먼저 처리
    val speaker = identifyMessageOwner(line)
    if (speaker.isNotEmpty()) {
        return speaker
    }
    
    // 이전 메시지가 있으면 마지막 화자와 반대 화자 추정
    if (messages.isNotEmpty()) {
        val lastSpeaker = messages.last().first
        return when (lastSpeaker) {
            "엄마" -> "나"
            "나" -> "엄마"
            "상대방" -> "나"
            else -> "나" // 기본적으로 "나"로 추정
        }
    }
    
    // 첫 번째 메시지인 경우, 내용을 보고 추정
    return when {
        // "나"의 전형적인 응답 패턴
        line.contains("네") || line.contains("알겠어요") || line.contains("좋아요") || 
        line.contains("감사합니다") || line.contains("죄송합니다") || 
        line.contains("연락주세요") || line.contains("괜찮아요") ||
        line.contains("예") || line.contains("응") || line.contains("그래요") ||
        line.contains("맞아요") || line.contains("알겠습니다") -> "나"
        // 질문이나 요청하는 패턴은 상대방
        line.contains("?") || line.contains("어떻게") ||
        line.contains("가려고") || line.contains("할거") || line.contains("해줘") ||
        line.contains("약속했어") || line.contains("물어보는거야") -> "상대방"
        else -> "나" // 기본적으로 "나"로 추정
    }
}

/**
 * "나"의 메시지 패턴을 더 정확히 식별하는 함수
 */
private fun identifyMyMessage(line: String): Boolean {
    val myMessagePatterns = listOf(
        "네", "알겠어요", "좋아요", "감사합니다", "죄송합니다", "연락주세요",
        "괜찮아요", "예", "응", "그래요", "맞아요", "알겠습니다", "네 알겠어요",
        "좋아요 연락주세요", "네 감사합니다", "네 괜찮아요", "네 알겠습니다",
        "좋습니다", "알겠어요", "네 좋아요", "네 괜찮아요"
    )
    
    // 부분 일치도 확인
    val partialPatterns = listOf(
        "네 ", "알겠", "좋아", "감사", "죄송", "연락", "괜찮", "예", "응"
    )
    
    return myMessagePatterns.any { pattern -> 
        line.contains(pattern, ignoreCase = true) 
    } || partialPatterns.any { pattern ->
        line.startsWith(pattern, ignoreCase = true) && line.length <= 20
    }
}

/**
 * 라인에서 화자를 제외한 메시지를 추출하는 함수
 */
private fun extractMessageFromLine(line: String, speaker: String): String {
    return line.replace(speaker, "").trim()
}


