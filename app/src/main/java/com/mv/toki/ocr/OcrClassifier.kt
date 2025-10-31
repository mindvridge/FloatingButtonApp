package com.mv.toki.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

/**
 * 발신자 구분 열거형
 */
enum class Sender { 
    ME,        // 나
    OTHER,     // 상대방
    UNKNOWN    // 알 수 없음
}

/**
 * 채팅 메시지 데이터 클래스
 */
data class ChatMessage(
    val text: String,
    val sender: Sender,
    val box: Rect
)

/**
 * 메시지 그룹 데이터 클래스
 */
data class MessageGroup(
    val text: String,
    val sender: Sender,
    val box: Rect,
    val classificationReason: String
)

/**
 * OCR 라인 데이터 클래스
 */
data class OcrLine(
    val text: String,
    val x: Int, val y: Int,
    val width: Int, val height: Int,
    val fontPx: Int,
    val box: Rect
)

/**
 * 메시지 그룹 데이터 클래스 (개선된 버전)
 */
data class MsgGroup(
    val lines: MutableList<OcrLine> = mutableListOf()
) {
    val minX get() = lines.minOf { it.x }
    val maxX get() = lines.maxOf { it.x + it.width }
    val centerX get() = (minX + maxX) / 2f
    val rightX get() = maxX.toFloat()
    val leftX get() = minX.toFloat()
    val top get() = lines.minOf { it.y }
    val bottom get() = lines.maxOf { it.y + it.height }
    val text get() = lines.joinToString(" ") { it.text.trim() }
    val box get() = if (lines.isNotEmpty()) {
        val first = lines.first()
        Rect(first.x, first.y, first.x + first.width, first.y + first.height)
    } else Rect()
}

/**
 * 발신자 열거형 (개선된 버전)
 */
enum class Speaker { 
    ME,        // 나
    OTHER,     // 상대방
    UNKNOWN    // 알 수 없음
}

/**
 * 시간 라벨 데이터 클래스
 */
data class NeighborTime(val x: Int, val y: Int)

/**
 * OCR 분류기
 * 위치 기반으로 발신자를 구분합니다.
 */
object OcrClassifier {

    /**
     * ML Kit을 사용하여 OCR 수행
     */
    suspend fun recognize(bitmap: Bitmap): Text =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(
                KoreanTextRecognizerOptions.Builder().build()
            )
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { _ -> cont.resume(Text("", emptyList<Text.TextBlock>())) }
        }

    /**
     * 라인을 메시지 그룹으로 묶는 함수 (개선된 버전)
     * 세로 간격이 작고, 가로 겹침이 있는 라인들을 하나의 메시지로 묶습니다.
     * 동적 vGap 계산, 수평 정렬 체크, 시간 라벨 필터링 개선
     */
    private fun groupLinesToMessages(
        sortedLines: List<Pair<Text.Line, Int>>, 
        screenW: Int,
        prevGroups: List<MsgGroup> = emptyList() // 이전 프레임 그룹 전달 (현재 미사용)
    ): List<MsgGroup> {
        val groups = mutableListOf<MsgGroup>()
        val vGap = 0.9f
        val MIN_HORIZONTAL_ALIGNMENT = 0.35f // 상수화
        
        android.util.Log.d("OCR_CLASSIFIER", "=== 라인 그룹핑 시작 ===")
        android.util.Log.d("OCR_CLASSIFIER", "총 라인 수: ${sortedLines.size}")
        
        for (lineIndex in sortedLines.indices) {
            val (line, unusedBlockIndex) = sortedLines[lineIndex]
            val box = line.boundingBox ?: continue
            val text = line.text.trim()
            
            if (text.isBlank()) continue
            
            // UI/시간/날짜 라벨은 미리 필터링
            if (isUiGarbage(text)) {
                android.util.Log.d("OCR_CLASSIFIER", "라인 $lineIndex: '$text' → UI 요소 (필터링됨)")
                continue
            }
            
            // 🔥 개선: 시간 라벨도 필터링하되 따로 저장
            if (isTimeLabel(text)) {
                android.util.Log.d("OCR_CLASSIFIER", "라인 $lineIndex: '$text' → 시간 라벨 (필터링됨)")
                continue
            }
            
            // OcrLine 객체 생성
            val ocrLine = OcrLine(
                text = text,
                x = box.left,
                y = box.top,
                width = box.width(),
                height = box.height(),
                fontPx = calculateFontSize(box.height(), text.length),
                box = box
            )
            
            val last = groups.lastOrNull()
            if (last == null) {
                groups += MsgGroup(mutableListOf(ocrLine))
                android.util.Log.d("OCR_CLASSIFIER", "라인 $lineIndex: '$text' → 새 그룹 생성")
                continue
            }
            
            // 겹침 비율과 세로 거리 계산
            val overlapX = overlapRatio(
                last.minX, last.maxX,
                ocrLine.x, ocrLine.x + ocrLine.width
            )
            val vDist = (ocrLine.y - last.bottom).toFloat()
            
            // 🔥 개선: 동적 vGap 계산 (폰트 크기 고려)
            val avgHeight = (ocrLine.height + (last.bottom - last.top)) / 2f
            val vAllow = avgHeight * vGap

            // 🔥 개선: 수평 정렬 체크 추가
            val horizontalAligned = kotlin.math.abs(ocrLine.x - last.minX) <= 30 || 
                                    kotlin.math.abs((ocrLine.x + ocrLine.width) - last.maxX) <= 30

            val same = (vDist <= vAllow) && 
                       (overlapX >= MIN_HORIZONTAL_ALIGNMENT || horizontalAligned)
            
            android.util.Log.d("OCR_CLASSIFIER", "라인 $lineIndex: '$text' → 겹침비율=$overlapX, 세로거리=$vDist, 허용거리=$vAllow, 수평정렬=$horizontalAligned, 같은그룹=$same")
            
            if (same) {
                last.lines += ocrLine
                android.util.Log.d("OCR_CLASSIFIER", "  → 기존 그룹에 추가")
            } else {
                groups += MsgGroup(mutableListOf(ocrLine))
                android.util.Log.d("OCR_CLASSIFIER", "  → 새 그룹 생성")
            }
        }
        
        android.util.Log.d("OCR_CLASSIFIER", "=== 그룹핑 완료 ===")
        android.util.Log.d("OCR_CLASSIFIER", "생성된 그룹 수: ${groups.size}")
        
        return groups
    }
    
    /**
     * 두 구간의 겹침 비율 계산
     */
    private fun overlapRatio(a1: Int, a2: Int, b1: Int, b2: Int): Float {
        val inter = (minOf(a2, b2) - maxOf(a1, b1)).coerceAtLeast(0)
        val union = (maxOf(a2, b2) - minOf(a1, b1)).coerceAtLeast(1)
        return inter.toFloat() / union
    }
    
    /**
     * UI 요소나 시간 패턴인지 확인
     */
    private fun isUiGarbage(t: String): Boolean {
        val s = t.trim()
        if (s == "+" || s == "메시지 입력") return true
        val time = Regex("""(오전|오후)\s*\d{1,2}:\d{2}""")
        val date = Regex("""\d{4}년\s*\d{1,2}월\s*\d{1,2}일""")
        return time.matches(s) || date.containsMatchIn(s)
    }
    
    /**
     * 시간 라벨인지 확인 (개선된 버전)
     */
    private fun isTimeLabel(text: String): Boolean {
        val s = text.trim()
        return Regex("""(오전|오후)\s*\d{1,2}:\d{2}""").matches(s)
    }
    
    /**
     * 동적 임계값 계산 (k-means 클러스터링)
     */
    private fun calculateDynamicThresholds(messageGroups: List<MsgGroup>, screenW: Int): Pair<Float, Float> {
        if (messageGroups.size < 4) {
            // 데이터가 부족하면 기본값 사용
            return Pair(screenW * 0.4f, screenW * 0.6f)
        }
        
        val centerXs = messageGroups.map { it.centerX }
        
        // k-means 클러스터링 (k=2)
        var leftCenter = centerXs.minOrNull()!!.toFloat()
        var rightCenter = centerXs.maxOrNull()!!.toFloat()
        
        if (leftCenter == rightCenter) {
            return Pair(screenW * 0.4f, screenW * 0.6f)
        }
        
        // k-means 반복 (최대 10회)
        repeat(10) {
            val leftCluster = mutableListOf<Float>()
            val rightCluster = mutableListOf<Float>()
            
            for (centerX in centerXs) {
                if (kotlin.math.abs(centerX - leftCenter) <= kotlin.math.abs(centerX - rightCenter)) {
                    leftCluster.add(centerX)
                } else {
                    rightCluster.add(centerX)
                }
            }
            
            val newLeftCenter = if (leftCluster.isNotEmpty()) leftCluster.average().toFloat() else leftCenter
            val newRightCenter = if (rightCluster.isNotEmpty()) rightCluster.average().toFloat() else rightCenter
            
            // 수렴 체크
            if (kotlin.math.abs(newLeftCenter - leftCenter) < 0.5f && 
                kotlin.math.abs(newRightCenter - rightCenter) < 0.5f) {
                leftCenter = newLeftCenter
                rightCenter = newRightCenter
                return@repeat
            }
            
            leftCenter = newLeftCenter
            rightCenter = newRightCenter
        }
        
        // 왼/오 정렬
        val finalLeftCenter = kotlin.math.min(leftCenter, rightCenter)
        val finalRightCenter = kotlin.math.max(leftCenter, rightCenter)
        
        // 중간값 계산
        val midPoint = (finalLeftCenter + finalRightCenter) / 2f
        val margin = kotlin.math.max((finalRightCenter - finalLeftCenter) * 0.1f, 20f)
        
        val leftThreshold = midPoint - margin
        val rightThreshold = midPoint + margin
        
        android.util.Log.d("OCR_CLASSIFIER", "동적 임계값 계산: leftCenter=$finalLeftCenter, rightCenter=$finalRightCenter")
        android.util.Log.d("OCR_CLASSIFIER", "임계값: leftThreshold=$leftThreshold, rightThreshold=$rightThreshold")
        
        return Pair(leftThreshold, rightThreshold)
    }
    
    /**
     * 메시지 그룹을 분류하는 함수 (강화된 다중 특징 스코어링 + 동적 임계값 사용)
     */
    private fun classifyMessage(
        g: MsgGroup,
        screenW: Int,
        partnerName: String?, // 예: "태용" (없으면 null)
        nameLabels: List<OcrLine>, // 이름 라벨로 인식된 라인들
        timeLabels: List<NeighborTime>,
        dynamicThresholds: Pair<Float, Float>? = null
    ): Pair<Speaker, Int> {
        val rightX = g.rightX
        val leftX = g.leftX
        val centerX = g.centerX
        val width = g.maxX - g.minX
        
        var meScore = 0
        var otherScore = 0
        
        android.util.Log.d("OCR_CLASSIFIER", "=== 메시지 분류 시작 ===")
        android.util.Log.d("OCR_CLASSIFIER", "텍스트: '${g.text}'")
        android.util.Log.d("OCR_CLASSIFIER", "위치: leftX=$leftX, rightX=$rightX, centerX=$centerX, width=$width")
        
        // 동적 임계값 사용 (있는 경우)
        val (leftThreshold, rightThreshold) = dynamicThresholds ?: Pair(screenW * 0.4f, screenW * 0.6f)
        
        // 1) 위치 기반 강한 신호 (동적 임계값 사용)
        if (rightX >= screenW * 0.78f) {
            meScore += 3  // 강한 신호로 가중치 증가
            android.util.Log.d("OCR_CLASSIFIER", "  → 오른쪽 끝 도달 (meScore += 3)")
        }
        if (leftX <= screenW * 0.22f && rightX < screenW * 0.82f) {
            otherScore += 3  // 강한 신호로 가중치 증가
            android.util.Log.d("OCR_CLASSIFIER", "  → 왼쪽 영역 + 적당한 너비 (otherScore += 3)")
        }
        
        // 2) 중심 위치 보조 신호 (동적 임계값 사용)
        if (centerX >= rightThreshold) {
            meScore += 2  // 가중치 증가
            android.util.Log.d("OCR_CLASSIFIER", "  → 중심이 오른쪽 임계값 이상 (meScore += 2)")
        }
        if (centerX <= leftThreshold) {
            otherScore += 2  // 가중치 증가
            android.util.Log.d("OCR_CLASSIFIER", "  → 중심이 왼쪽 임계값 이하 (otherScore += 2)")
        }
        
        // 3) 말풍선 너비 분석 (카카오톡 특징)
        val widthRatio = width.toFloat() / screenW
        if (widthRatio > 0.6f && rightX >= screenW * 0.7f) {
            meScore += 2  // 넓은 오른쪽 말풍선은 내 메시지
            android.util.Log.d("OCR_CLASSIFIER", "  → 넓은 오른쪽 말풍선 (meScore += 2)")
        }
        if (widthRatio < 0.5f && leftX <= screenW * 0.3f) {
            otherScore += 2  // 좁은 왼쪽 말풍선은 상대방 메시지
            android.util.Log.d("OCR_CLASSIFIER", "  → 좁은 왼쪽 말풍선 (otherScore += 2)")
        }
        
        // 4) 시간 라벨 상대 위치 (개선된 근접판별)
        val nearTime = timeLabels.any { kotlin.math.abs(((g.top + g.bottom) / 2) - it.y) <= 120 }
        if (nearTime) {
            // 시간 라벨이 말풍선 좌측에 더 가까우면 내 메시지
            val timeSideLeft = timeLabels.any {
                kotlin.math.abs(((g.top + g.bottom) / 2) - it.y) <= 120 && it.x < leftX
            }
            val timeSideRight = timeLabels.any {
                kotlin.math.abs(((g.top + g.bottom) / 2) - it.y) <= 120 && it.x > rightX
            }
            if (timeSideLeft) {
                meScore += 2  // 가중치 증가
                android.util.Log.d("OCR_CLASSIFIER", "  → 시간 라벨이 왼쪽에 (meScore += 2)")
            }
            if (timeSideRight) {
                otherScore += 2  // 가중치 증가
                android.util.Log.d("OCR_CLASSIFIER", "  → 시간 라벨이 오른쪽에 (otherScore += 2)")
            }
        }
        
        // 5) 이름 라벨 근접성 (상대방)
        val hasNameAbove = nameLabels.any { lbl ->
            partnerName != null &&
            lbl.text.replace(" ", "").contains(partnerName) &&
            (g.top - (lbl.y + lbl.height)) in 0..120
        }
        if (hasNameAbove) {
            otherScore += 2  // 가중치 증가
            android.util.Log.d("OCR_CLASSIFIER", "  → 상대방 이름 위에 있음 (otherScore += 2)")
        }
        
        // 6) 텍스트 내용 분석 (새로운 특징)
        val text = g.text.lowercase()
        if (text.contains("저는") || text.contains("제가") || text.contains("나") || 
            text.contains("저") || text.contains("우리") || text.contains("내가")) {
            meScore += 1
            android.util.Log.d("OCR_CLASSIFIER", "  → 1인칭 표현 감지 (meScore += 1)")
        }
        if (text.contains("너") || text.contains("당신") || text.contains("그쪽") || 
            text.contains("형") || text.contains("누나") || text.contains("언니")) {
            otherScore += 1
            android.util.Log.d("OCR_CLASSIFIER", "  → 2인칭 표현 감지 (otherScore += 1)")
        }
        
        // 7) 화면 위치 분석 (Y 좌표 기반)
        val yRatio = g.top.toFloat() / screenW  // 화면 높이 대비 비율
        if (yRatio > 0.7f && rightX >= screenW * 0.6f) {
            meScore += 1  // 화면 하단 오른쪽은 내 메시지 가능성 높음
            android.util.Log.d("OCR_CLASSIFIER", "  → 화면 하단 오른쪽 (meScore += 1)")
        }
        if (yRatio < 0.3f && leftX <= screenW * 0.4f) {
            otherScore += 1  // 화면 상단 왼쪽은 상대방 메시지 가능성 높음
            android.util.Log.d("OCR_CLASSIFIER", "  → 화면 상단 왼쪽 (otherScore += 1)")
        }
        
        val confidence = kotlin.math.abs(meScore - otherScore)
        val spk = when {
            meScore > otherScore -> Speaker.ME
            otherScore > meScore -> Speaker.OTHER
            else -> Speaker.UNKNOWN
        }
        
        android.util.Log.d("OCR_CLASSIFIER", "=== 분류 결과 ===")
        android.util.Log.d("OCR_CLASSIFIER", "meScore: $meScore, otherScore: $otherScore")
        android.util.Log.d("OCR_CLASSIFIER", "결과: $spk, 신뢰도: $confidence")
        
        return spk to confidence
    }
    
    /**
     * 시간 라벨 추출
     */
    private fun extractTimeLabels(sortedLines: List<Pair<Text.Line, Int>>): List<NeighborTime> {
        val timeLabels = mutableListOf<NeighborTime>()
        
        for ((line, unusedBlockIndex) in sortedLines) {
            val box = line.boundingBox ?: continue
            val text = line.text.trim()
            
            if (looksLikeTimestamp(text)) {
                timeLabels.add(NeighborTime(box.left, box.top))
                android.util.Log.d("OCR_CLASSIFIER", "시간 라벨 추출: '$text' at ($box.left, $box.top)")
            }
        }
        
        return timeLabels
    }
    
    /**
     * 이름 라벨 추출
     */
    private fun extractNameLabels(sortedLines: List<Pair<Text.Line, Int>>, canvasHeight: Int): List<OcrLine> {
        val nameLabels = mutableListOf<OcrLine>()
        val topAreaThreshold = canvasHeight * 0.25f
        
        for ((line, unusedBlockIndex) in sortedLines) {
            val box = line.boundingBox ?: continue
            val text = line.text.trim()
            
            if (box.top < topAreaThreshold && 
                text.length in 2..15 && 
                !looksLikeTimestamp(text) &&
                !text.contains("오전") && !text.contains("오후") &&
                !text.contains(":") && !text.contains(".") &&
                !text.contains("년") && !text.contains("월") && !text.contains("일")) {
                
                val ocrLine = OcrLine(
                    text = text,
                    x = box.left,
                    y = box.top,
                    width = box.width(),
                    height = box.height(),
                    fontPx = calculateFontSize(box.height(), text.length),
                    box = box
                )
                nameLabels.add(ocrLine)
                android.util.Log.d("OCR_CLASSIFIER", "이름 라벨 추출: '$text' at ($box.left, $box.top)")
            }
        }
        
        return nameLabels
    }
    
    /**
     * 컨텍스트(연속구간) 해제 규칙 (강화된 버전)
     * 강한 증거가 나오면 즉시 해제
     */
    private fun shouldBreakContext(
        prevSpeaker: Speaker?,
        currentSpeaker: Speaker,
        confidence: Int,
        g: MsgGroup,
        screenW: Int
    ): Boolean {
        // 이전 스피커가 OTHER였더라도, 강한 내 메시지 신호가 나오면 즉시 ME로 전환
        if (prevSpeaker == Speaker.OTHER && currentSpeaker == Speaker.ME && confidence >= 3) {
            android.util.Log.d("OCR_CLASSIFIER", "  → 강한 내 메시지 신호로 컨텍스트 해제 (신뢰도: $confidence)")
            return true
        }
        
        // 오른쪽 말풍선이 명확히 감지되면 컨텍스트 해제
        if (g.rightX >= screenW * 0.78f) {
            android.util.Log.d("OCR_CLASSIFIER", "  → 오른쪽 말풍선 감지로 컨텍스트 해제")
            return true
        }
        
        // 왼쪽 말풍선이 명확히 감지되면 컨텍스트 해제
        if (g.leftX <= screenW * 0.22f && g.rightX < screenW * 0.82f) {
            android.util.Log.d("OCR_CLASSIFIER", "  → 왼쪽 말풍선 감지로 컨텍스트 해제")
            return true
        }
        
        // 시간/날짜 라벨로 대화 전환 감지
        if (g.text.contains("오전") || g.text.contains("오후") || 
            g.text.contains("년") || g.text.contains("월") || g.text.contains("일")) {
            android.util.Log.d("OCR_CLASSIFIER", "  → 시간/날짜 라벨로 컨텍스트 해제")
            return true
        }
        
        // 1인칭 표현이 포함된 강한 내 메시지 신호
        val text = g.text.lowercase()
        if ((text.contains("저는") || text.contains("제가") || text.contains("나") || 
             text.contains("저") || text.contains("우리") || text.contains("내가")) && 
            g.rightX >= screenW * 0.6f) {
            android.util.Log.d("OCR_CLASSIFIER", "  → 1인칭 표현 + 오른쪽 위치로 컨텍스트 해제")
            return true
        }
        
        // 2인칭 표현이 포함된 강한 상대방 메시지 신호
        if ((text.contains("너") || text.contains("당신") || text.contains("그쪽") || 
             text.contains("형") || text.contains("누나") || text.contains("언니")) && 
            g.leftX <= screenW * 0.4f) {
            android.util.Log.d("OCR_CLASSIFIER", "  → 2인칭 표현 + 왼쪽 위치로 컨텍스트 해제")
            return true
        }
        
        // 말풍선 너비가 매우 다른 경우 (대화 전환)
        val widthRatio = (g.maxX - g.minX).toFloat() / screenW
        if (widthRatio > 0.7f && g.rightX >= screenW * 0.7f) {
            android.util.Log.d("OCR_CLASSIFIER", "  → 매우 넓은 오른쪽 말풍선으로 컨텍스트 해제")
            return true
        }
        
        return false
    }
    
    /**
     * 개선된 메시지 분류 메인 함수
     */
    private fun classifyMessagesImproved(
        sortedLines: List<Pair<Text.Line, Int>>, 
        canvasWidth: Int, 
        canvasHeight: Int
    ): List<ChatMessage> {
        android.util.Log.d("OCR_CLASSIFIER", "=== 개선된 메시지 분류 시작 ===")
        
        // 1. 라인을 메시지 그룹으로 묶기
        val messageGroups = groupLinesToMessages(sortedLines, canvasWidth)
        
        // 2. 시간 라벨과 이름 라벨 추출
        val timeLabels = extractTimeLabels(sortedLines)
        val nameLabels = extractNameLabels(sortedLines, canvasHeight)
        
        // 3. 상대방 이름 추출
        val partnerName = nameLabels.firstOrNull()?.text?.let { extractNameFromText(it) }
        
        // 4. 동적 임계값 계산
        val dynamicThresholds = calculateDynamicThresholds(messageGroups, canvasWidth)
        
        android.util.Log.d("OCR_CLASSIFIER", "추출된 상대방 이름: '$partnerName'")
        android.util.Log.d("OCR_CLASSIFIER", "시간 라벨 수: ${timeLabels.size}")
        android.util.Log.d("OCR_CLASSIFIER", "이름 라벨 수: ${nameLabels.size}")
        android.util.Log.d("OCR_CLASSIFIER", "동적 임계값: left=${dynamicThresholds.first}, right=${dynamicThresholds.second}")
        
        val results = mutableListOf<ChatMessage>()
        var prevSpeaker: Speaker? = null
        
        // 5. 각 메시지 그룹 분류
        for (groupIndex in messageGroups.indices) {
            val group = messageGroups[groupIndex]
            
            // 메시지 분류 (동적 임계값 사용)
            val (speaker, confidence) = classifyMessage(
                group, canvasWidth, partnerName, nameLabels, timeLabels, dynamicThresholds
            )
            
            // 컨텍스트 해제 확인
            val shouldBreak = shouldBreakContext(prevSpeaker, speaker, confidence, group, canvasWidth)
            if (shouldBreak) {
                android.util.Log.d("OCR_CLASSIFIER", "컨텍스트 해제됨")
            }
            
            // 상대방 연속구간 업데이트 (현재 미사용)
            
            // ChatMessage로 변환
            val sender = when (speaker) {
                Speaker.ME -> Sender.ME
                Speaker.OTHER -> Sender.OTHER
                Speaker.UNKNOWN -> Sender.UNKNOWN
            }
            
            val chatMessage = ChatMessage(
                text = group.text,
                sender = sender,
                box = group.box
            )
            
            results.add(chatMessage)
            prevSpeaker = speaker
            
            val speakerLabel = when (speaker) {
                Speaker.ME -> "나"
                Speaker.OTHER -> "상대방"
                Speaker.UNKNOWN -> "미분류"
            }
            
            android.util.Log.d("OCR_CLASSIFIER", "그룹 $groupIndex: [$speakerLabel] '${group.text}' (신뢰도: $confidence)")
        }
        
        android.util.Log.d("OCR_CLASSIFIER", "=== 개선된 분류 완료 ===")
        android.util.Log.d("OCR_CLASSIFIER", "최종 메시지 수: ${results.size}")
        
        return results
        }

    /**
     * 카카오톡 라운딩(말풍선) 기반 발화자 추정
     * - 라운딩으로 대화 내용 구분
     * - 라운딩 색상으로 상대방/나 구분
     * - 프로필 사진 밑 이름으로 상대방 식별
     * - 위쪽부터 순차 처리
     */
    fun classify(visionText: Text, canvasWidth: Int, canvasHeight: Int): List<ChatMessage> {
        android.util.Log.d("OCR_CLASSIFIER", "=== OCR 분류 시작 (개선된 버전) ===")
        android.util.Log.d("OCR_CLASSIFIER", "화면 크기: ${canvasWidth}x${canvasHeight}")
        android.util.Log.d("OCR_CLASSIFIER", "원본 텍스트: ${visionText.text}")

        // 모든 텍스트 라인을 수집하고 위쪽부터 정렬
        val allLines = mutableListOf<Pair<Text.Line, Int>>() // (line, blockIndex)
        
        for (blockIndex in visionText.textBlocks.indices) {
            val block = visionText.textBlocks[blockIndex]
            for (line in block.lines) {
                allLines.add(Pair(line, blockIndex))
            }
        }
        
        // 위쪽부터 정렬 (top 값 기준, 같은 높이면 left 값으로 정렬)
        val sortedLines = allLines.sortedWith(compareBy<Pair<Text.Line, Int>> { it.first.boundingBox?.top ?: Int.MAX_VALUE }
            .thenBy { it.first.boundingBox?.left ?: Int.MAX_VALUE })
        
        android.util.Log.d("OCR_CLASSIFIER", "총 라인 수: ${sortedLines.size} (위쪽부터 정렬됨)")
        
        // 개선된 분류 로직 사용
        return classifyMessagesImproved(sortedLines, canvasWidth, canvasHeight)
    }

    /**
     * 시간 패턴인지 확인
     */
    private fun looksLikeTimestamp(t: String): Boolean {
        // 카톡 패턴(오전/오후 10:21, 2025.10.22 등) 대략 필터
        val timeRegex = Regex("(오전|오후)\\s?\\d{1,2}:\\d{2}")
        val dateRegex = Regex("\\d{4}[./-]\\d{1,2}[./-]\\d{1,2}")
        return timeRegex.containsMatchIn(t) || dateRegex.containsMatchIn(t)
    }

    /**
     * 같은 발화자 & 수평/수직 근접 시 병합(말풍선의 여러 라인)
     */
    private fun mergeAdjacent(items: List<ChatMessage>): List<ChatMessage> {
        if (items.isEmpty()) return items
        
        android.util.Log.d("OCR_CLASSIFIER", "=== 메시지 병합 시작 ===")
        android.util.Log.d("OCR_CLASSIFIER", "병합 전 메시지 수: ${items.size}")
        
        val sorted = items.sortedWith(compareBy({ it.sender.toString() }, { it.box.top }, { it.box.left }))
        val merged = mutableListOf<ChatMessage>()
        
        for (m in sorted) {
            val last = merged.lastOrNull()
            if (last != null && last.sender == m.sender && isClose(last.box, m.box)) {
                val senderLabel = when (m.sender) {
                    Sender.ME -> "나"
                    Sender.OTHER -> "상대방"
                    Sender.UNKNOWN -> "미분류"
                }
                android.util.Log.d("OCR_CLASSIFIER", "병합: [$senderLabel] '${last.text}' + '${m.text}'")
                
                val newBox = Rect(
                    min(last.box.left, m.box.left),
                    min(last.box.top, m.box.top),
                    max(last.box.right, m.box.right),
                    max(last.box.bottom, m.box.bottom)
                )
                merged[merged.lastIndex] = last.copy(
                    text = last.text + "\n" + m.text,
                    box = newBox
                )
            } else {
                val senderLabel = when (m.sender) {
                    Sender.ME -> "나"
                    Sender.OTHER -> "상대방"
                    Sender.UNKNOWN -> "미분류"
                }
                android.util.Log.d("OCR_CLASSIFIER", "추가: [$senderLabel] '${m.text}'")
                merged += m
            }
        }
        
        android.util.Log.d("OCR_CLASSIFIER", "병합 후 메시지 수: ${merged.size}")
        android.util.Log.d("OCR_CLASSIFIER", "=== 메시지 병합 완료 ===")
        
        return merged
    }

    /**
     * 두 박스가 근접한지 확인
     */
    private fun isClose(a: Rect, b: Rect): Boolean {
        // y축 근접/겹침 && x축 동일 측(좌/우) 근사
        val verticalNear = !(a.bottom < b.top - 20 || b.bottom < a.top - 20)
        val horizontalNear = (kotlin.math.abs(a.centerX() - b.centerX()) < a.width()) ||
                (overlap(a.left, a.right, b.left, b.right) > 0)
        return verticalNear && horizontalNear
    }

    /**
     * 두 구간의 겹침 길이 계산
     */
    private fun overlap(a1: Int, a2: Int, b1: Int, b2: Int): Int = max(0, min(a2, b2) - max(a1, b1))

    /**
     * 메시지 추가 (즉각 병합은 생략하고 후처리 mergeAdjacent에서 일괄 처리)
     */
    private fun mergeOrAdd(list: MutableList<ChatMessage>, msg: ChatMessage) {
        list += msg
    }
    
    /**
     * 이전 라인이 상대방 이름과 근접한지 확인
     */
    private fun isNearPreviousLine(currentBox: android.graphics.Rect, sortedLines: List<Pair<Text.Line, Int>>, currentIndex: Int, otherPersonName: String): Boolean {
        if (currentIndex == 0 || otherPersonName.isEmpty()) return false
        
        val previousLine = sortedLines[currentIndex - 1]
        val prevBox = previousLine.first.boundingBox ?: return false
        val prevText = previousLine.first.text.trim()
        
        // 이전 라인이 상대방 이름이고 수직으로 근접한 경우
        return prevText == otherPersonName && 
               kotlin.math.abs(currentBox.top - prevBox.bottom) < 50 // 50px 이내
    }
    
    /**
     * Y 위치 기반 상대방 영역 확인 (위쪽부터 순서 고려)
     */
    private fun isInOtherPersonArea(currentBox: android.graphics.Rect, canvasHeight: Int, otherPersonName: String, sortedLines: List<Pair<Text.Line, Int>>, currentIndex: Int): Boolean {
        if (otherPersonName.isEmpty()) return false
        
        // 상대방 이름의 Y 위치 찾기 (현재 인덱스 이전에서만)
        var otherPersonY = -1
        var otherPersonIndex = -1
        for (i in 0 until currentIndex) {
            val line = sortedLines[i]
            val text = line.first.text.trim()
            if (text == otherPersonName) {
                otherPersonY = line.first.boundingBox?.top ?: -1
                otherPersonIndex = i
                break
            }
        }
        
        if (otherPersonY == -1) return false
        
        // 상대방 이름과 현재 텍스트 사이의 거리 계산
        val yDistance = kotlin.math.abs(currentBox.top - otherPersonY)
        
        // 상대방 이름보다 아래쪽에 있는지 확인
        val isBelowOtherPerson = currentBox.top > otherPersonY
        
        // 상대방 이름과의 인덱스 차이 (위쪽부터 순서 고려)
        val indexDistance = currentIndex - otherPersonIndex
        
        // 상대방 이름 근처의 Y 영역에 있는지 확인 (더 엄격한 조건)
        val isNearOtherPerson = yDistance < 150 && indexDistance <= 3 // 150px 이내, 인덱스 차이 3 이내
        
        android.util.Log.d("OCR_CLASSIFIER", "  → 상대방 Y 위치: $otherPersonY (인덱스: $otherPersonIndex), 현재 Y: ${currentBox.top} (인덱스: $currentIndex)")
        android.util.Log.d("OCR_CLASSIFIER", "  → Y 거리: $yDistance, 인덱스 거리: $indexDistance")
        android.util.Log.d("OCR_CLASSIFIER", "  → 근접 여부: $isNearOtherPerson, 아래쪽 여부: $isBelowOtherPerson")
        
        return isNearOtherPerson && isBelowOtherPerson
    }
    
    /**
     * Y 위치 기반 내 영역 확인 (위쪽부터 순서 고려)
     */
    private fun isInMyArea(currentBox: android.graphics.Rect, unusedCanvasHeight: Int, sortedLines: List<Pair<Text.Line, Int>>, currentIndex: Int): Boolean {
        // 화면 하단 40% 영역에 있는지 확인
        val bottomAreaThreshold = unusedCanvasHeight * 0.6f
        val isInBottomArea = currentBox.top > bottomAreaThreshold
        
        // 날짜와 연관된 메시지인지 확인 (위쪽부터 순서 고려)
        val isNearDate = isNearDateMessage(currentBox, sortedLines, currentIndex)
        
        // 화면 중앙 아래쪽에 있는지 확인
        val middleY = unusedCanvasHeight / 2
        val isBelowMiddle = currentBox.top > middleY
        
        // 상대방 이름이 현재 텍스트보다 위에 있는지 확인 (순서 보장)
        val hasOtherPersonAbove = hasOtherPersonAbove(currentIndex, sortedLines)
        
        android.util.Log.d("OCR_CLASSIFIER", "  → 하단 영역: $isInBottomArea (임계값: $bottomAreaThreshold), 날짜 근접: $isNearDate")
        android.util.Log.d("OCR_CLASSIFIER", "  → 중앙 아래: $isBelowMiddle, 상대방 위에 있음: $hasOtherPersonAbove")
        
        return (isInBottomArea || isNearDate) && isBelowMiddle && hasOtherPersonAbove
    }
    
    /**
     * 날짜와 연관된 메시지인지 확인
     */
    private fun isNearDateMessage(currentBox: android.graphics.Rect, sortedLines: List<Pair<Text.Line, Int>>, currentIndex: Int): Boolean {
        // 이전 라인들에서 날짜를 찾아서 근접한지 확인
        for (i in (currentIndex - 1) downTo maxOf(0, currentIndex - 3)) {
            val line = sortedLines[i]
            val box = line.first.boundingBox ?: continue
            val text = line.first.text.trim()
            
            if (looksLikeDate(text)) {
                val yDistance = kotlin.math.abs(currentBox.top - box.top)
                val isNearDate = yDistance < 100 // 100px 이내
                
                android.util.Log.d("OCR_CLASSIFIER", "  → 날짜 확인: '$text', Y 거리: $yDistance, 근접: $isNearDate")
                return isNearDate
            }
        }
        
        return false
    }
    
    /**
     * 상대방 이름이 현재 텍스트보다 위에 있는지 확인 (위쪽부터 순서 보장)
     */
    private fun hasOtherPersonAbove(currentIndex: Int, sortedLines: List<Pair<Text.Line, Int>>): Boolean {
        // 현재 인덱스 이전에서 상대방 이름이 있는지 확인
        for (i in 0 until currentIndex) {
            val line = sortedLines[i]
            val text = line.first.text.trim()
            
            // 상대방 이름으로 추정되는 텍스트가 있는지 확인
            if (text.length in 2..10 && 
                !looksLikeTimestamp(text) &&
                !text.contains("오전") && !text.contains("오후") &&
                !text.contains(":") && !text.contains(".") &&
                !text.contains("년") && !text.contains("월") && !text.contains("일")) {
                
                android.util.Log.d("OCR_CLASSIFIER", "  → 상대방 이름 위에 있음: '$text' (인덱스: $i)")
                return true
            }
        }
        
        android.util.Log.d("OCR_CLASSIFIER", "  → 상대방 이름 위에 없음")
        return false
    }
    
    /**
     * 날짜 패턴인지 확인
     */
    private fun looksLikeDate(text: String): Boolean {
        val datePatterns = listOf(
            Regex("\\d{4}년\\s*\\d{1,2}월\\s*\\d{1,2}일"),
            Regex("\\d{1,2}월\\s*\\d{1,2}일"),
            Regex("\\d{1,2}/\\d{1,2}"),
            Regex("\\d{4}\\.\\d{1,2}\\.\\d{1,2}"),
            Regex("\\d{1,2}\\.\\d{1,2}"),
            Regex("오늘"),
            Regex("어제"),
            Regex("내일")
        )
        
        return datePatterns.any { it.containsMatchIn(text) }
    }
    
    /**
     * 두 박스를 병합
     */
    private fun mergeBoxes(box1: android.graphics.Rect?, box2: android.graphics.Rect): android.graphics.Rect {
        if (box1 == null) return box2
        
        return android.graphics.Rect(
            min(box1.left, box2.left),
            min(box1.top, box2.top),
            max(box1.right, box2.right),
            max(box1.bottom, box2.bottom)
        )
    }
    
    /**
     * 프로필 사진 밑 이름 추출
     */
    private fun extractOtherPersonName(sortedLines: List<Pair<Text.Line, Int>>, canvasHeight: Int): String {
        val topAreaThreshold = canvasHeight * 0.25f // 화면 상단 25% 영역
        
        for ((line, unusedBlockIndex) in sortedLines) {
            val box = line.boundingBox ?: continue
            val txt = line.text.trim()
            
            // 상단 영역에서 이름으로 추정되는 텍스트
            if (box.top < topAreaThreshold && 
                txt.length in 2..15 && 
                !looksLikeTimestamp(txt) &&
                !txt.contains("오전") && !txt.contains("오후") &&
                !txt.contains(":") && !txt.contains(".") &&
                !txt.contains("년") && !txt.contains("월") && !txt.contains("일")) {
                
                val extractedName = extractNameFromText(txt)
                if (extractedName.isNotEmpty()) {
                    android.util.Log.d("OCR_CLASSIFIER", "🎯 상대방 이름 추출: '$extractedName' (원본: '$txt', 위치: top=${box.top})")
                    return extractedName
                }
            }
        }
        
        android.util.Log.d("OCR_CLASSIFIER", "❌ 상대방 이름 추출 실패")
        return ""
    }
    
    /**
     * 라운딩 기반 메시지 그룹 식별 (이름 인식 개선)
     */
    private fun identifyMessageGroups(sortedLines: List<Pair<Text.Line, Int>>, canvasWidth: Int, canvasHeight: Int, otherPersonName: String): List<MessageGroup> {
        val groups = mutableListOf<MessageGroup>()
        val minW = (canvasWidth * 0.12).toInt()
        val minH = (canvasHeight * 0.015).toInt()
        
        // 원본 OCR 텍스트를 좌표와 함께 시각적으로 표시
        android.util.Log.d("OCR_CLASSIFIER", "=== 원본 OCR 텍스트 (좌표 + 폰트 크기 포함) ===")
        android.util.Log.d("OCR_CLASSIFIER", "┌${"─".repeat(45)}┐")
        for (lineIndex in sortedLines.indices) {
            val (line, unusedBlockIndex) = sortedLines[lineIndex]
            val box = line.boundingBox
            val txt = line.text.trim()
            
            if (box != null && txt.isNotEmpty()) {
                val y = box.top
                val x = box.left
                val width = box.width()
                val height = box.height()
                
                // 폰트 크기 추정 개선 (높이 기준, 더 정확한 계산)
                val estimatedFontSize = calculateFontSize(height, txt.length)
                
                // 텍스트 길이에 따른 표시 조정
                val displayText = if (txt.length > 25) txt.take(25) + "..." else txt
                val padding = 45 - displayText.length
                
                android.util.Log.d("OCR_CLASSIFIER", "│ $displayText${" ".repeat(padding)}│  ← Y: $y, X: $x, 폰트: ${estimatedFontSize}px (${width}x${height})")
            }
        }
        android.util.Log.d("OCR_CLASSIFIER", "└${"─".repeat(45)}┘")
        
        var currentGroupLines = mutableListOf<Pair<Text.Line, Int>>()
        var currentSender = Sender.UNKNOWN
        var currentClassificationReason = ""
        var lastOtherPersonIndex = -1  // 마지막 상대방 이름의 인덱스 추적
        var isInOtherPersonSequence = false  // 상대방 대화 연속 구간 여부
        var otherPersonXRange = Pair(0, 0)  // 상대방 X좌표 범위 추적
        
        for (lineIndex in sortedLines.indices) {
            val (line, unusedBlockIndex) = sortedLines[lineIndex]
            val box = line.boundingBox ?: continue
            val txt = line.text.trim()
            
            // 이름 패턴 확인 (크기 필터링 전에 먼저 확인)
            val isNamePattern = isNamePattern(txt, box, canvasHeight)
            val extractedName = if (isNamePattern) extractNameFromText(txt) else ""
            val isOtherPersonName = extractedName == otherPersonName || txt == otherPersonName
            
            if (isOtherPersonName) {
                lastOtherPersonIndex = lineIndex
                isInOtherPersonSequence = true  // 상대방 대화 연속 구간 시작
                android.util.Log.d("OCR_CLASSIFIER", "라인 $lineIndex: '$txt' → 🎯 상대방 이름 감지: '$extractedName' (연속구간 시작)")
                // 상대방 이름은 별도 그룹으로 생성하지 않고 스킵
                continue
            }
            
            // 소형(시간/이모지) 필터링 (이름과 상대방 연속구간은 예외)
            if (!isNamePattern && !isInOtherPersonSequence && (box.width() < minW || box.height() < minH)) {
                android.util.Log.d("OCR_CLASSIFIER", "라인 $lineIndex: '$txt' → ❌ 너무 작음 (필터링됨)")
                continue
            }

            if (txt.isBlank()) {
                android.util.Log.d("OCR_CLASSIFIER", "라인 $lineIndex: '$txt' → ❌ 빈 텍스트 (스킵)")
                continue
            }
            
            if (looksLikeTimestamp(txt)) {
                android.util.Log.d("OCR_CLASSIFIER", "라인 $lineIndex: '$txt' → ❌ 시간 패턴 (필터링됨)")
                continue
            }
            
            // 라운딩 기반 발신자 분류 (이름 근접성 고려)
            val sender = classifyByRoundingWithNameContext(txt, box, canvasWidth, canvasHeight, otherPersonName, sortedLines, lineIndex, lastOtherPersonIndex, isInOtherPersonSequence, otherPersonXRange)
            val senderLabel = when (sender) {
                Sender.ME -> "나"
                Sender.OTHER -> "상대방"
                Sender.UNKNOWN -> "미분류"
            }
            
            // 상대방 X좌표 범위 업데이트
            if (sender == Sender.OTHER) {
                if (otherPersonXRange.first == 0 && otherPersonXRange.second == 0) {
                    otherPersonXRange = Pair(box.left, box.left)
                } else {
                    otherPersonXRange = Pair(
                        kotlin.math.min(otherPersonXRange.first, box.left),
                        kotlin.math.max(otherPersonXRange.second, box.left)
                    )
                }
            }
            
            // 내 대화가 감지되면 상대방 연속구간 종료
            if (sender == Sender.ME) {
                isInOtherPersonSequence = false
                android.util.Log.d("OCR_CLASSIFIER", "라인 $lineIndex: '$txt' → 🔄 내 대화 감지, 상대방 연속구간 종료")
            }
            
            android.util.Log.d("OCR_CLASSIFIER", "라인 $lineIndex: '$txt' → ✅ 분류됨: $senderLabel")
            
            // 새로운 그룹 시작 또는 기존 그룹에 추가
            if (currentGroupLines.isEmpty() || currentSender == sender) {
                // 기존 그룹에 추가
                currentGroupLines.add(Pair(line, 0))
                currentSender = sender
                currentClassificationReason = getClassificationReason(sender, txt, box, canvasWidth, canvasHeight, otherPersonName)
            } else {
                // 이전 그룹 저장하고 새 그룹 시작
                if (currentGroupLines.isNotEmpty()) {
                    val group = createMessageGroup(currentGroupLines, currentSender, currentClassificationReason)
                    groups.add(group)
                }
                
                currentGroupLines = mutableListOf(Pair(line, 0))
                currentSender = sender
                currentClassificationReason = getClassificationReason(sender, txt, box, canvasWidth, canvasHeight, otherPersonName)
            }
        }
        
        // 마지막 그룹 저장
        if (currentGroupLines.isNotEmpty()) {
            val group = createMessageGroup(currentGroupLines, currentSender, currentClassificationReason)
            groups.add(group)
        }
        
        return groups
    }
    
    /**
     * 폰트 크기 계산 (더 정확한 추정)
     */
    private fun calculateFontSize(boxHeight: Int, textLength: Int): Int {
        // 기본적으로 박스 높이를 사용하되, 텍스트 길이에 따른 보정 적용
        val fontSize = when {
            textLength <= 5 -> {
                // 매우 짧은 텍스트 (이름, 시간 등)
                (boxHeight * 0.8).toInt()
            }
            textLength <= 15 -> {
                // 짧은 텍스트
                (boxHeight * 0.9).toInt()
            }
            textLength <= 30 -> {
                // 중간 길이 텍스트
                boxHeight
            }
            else -> {
                // 긴 텍스트
                (boxHeight * 1.1).toInt()
            }
        }
        
        // 최소/최대 폰트 크기 제한
        return kotlin.math.max(10, kotlin.math.min(200, fontSize))
    }
    
    /**
     * 이름 패턴인지 확인
     */
    private fun isNamePattern(text: String, box: android.graphics.Rect, canvasHeight: Int): Boolean {
        val txt = text.trim()
        
        // 상단 영역에 있는 텍스트
        val isInTopArea = box.top < canvasHeight * 0.3f
        
        // 이름으로 추정되는 길이
        val isNameLength = txt.length in 2..15
        
        // 시간 패턴이 아님
        val isNotTimestamp = !looksLikeTimestamp(txt) &&
                !txt.contains("오전") && !txt.contains("오후") &&
                !txt.contains(":") && !txt.contains(".") &&
                !txt.contains("년") && !txt.contains("월") && !txt.contains("일")
        
        // 화살표나 특수문자로 시작하는 경우 (UI 요소 포함)
        val hasUIElements = txt.contains("←") || txt.contains("→") || txt.contains("•") || txt.contains("·")
        
        android.util.Log.d("OCR_CLASSIFIER", "    → 이름 패턴 확인: '$txt' - 상단영역=$isInTopArea, 이름길이=$isNameLength, 시간아님=$isNotTimestamp, UI요소=$hasUIElements")
        
        return isInTopArea && isNameLength && isNotTimestamp
    }
    
    /**
     * 이름 컨텍스트를 고려한 라운딩 기반 분류 (연속구간 지원)
     */
    private fun classifyByRoundingWithNameContext(text: String, box: android.graphics.Rect, canvasWidth: Int, canvasHeight: Int, otherPersonName: String, sortedLines: List<Pair<Text.Line, Int>>, currentIndex: Int, lastOtherPersonIndex: Int, isInOtherPersonSequence: Boolean, otherPersonXRange: Pair<Int, Int>): Sender {
        // 상대방 이름과 일치
        if (text == otherPersonName) {
            android.util.Log.d("OCR_CLASSIFIER", "  → 상대방 이름 매칭: '$text'")
            return Sender.OTHER
        }
        
        // 상대방 연속구간 중이면 X좌표 기반으로 판단
        if (isInOtherPersonSequence) {
            val isMyMessage = checkIfMyMessageByXPosition(box, sortedLines, currentIndex, lastOtherPersonIndex, canvasWidth, otherPersonXRange)
            if (isMyMessage) {
                android.util.Log.d("OCR_CLASSIFIER", "  → X좌표 기반 내 대화 감지: left=${box.left}")
                return Sender.ME
            } else {
                android.util.Log.d("OCR_CLASSIFIER", "  → 상대방 연속구간 중: 상대방 대화로 인식")
                return Sender.OTHER
            }
        }
        
        // 상대방 이름 근처에 있는지 확인 (이름 다음의 대화 내용)
        if (lastOtherPersonIndex != -1 && currentIndex > lastOtherPersonIndex) {
            val nameLine = sortedLines[lastOtherPersonIndex]
            val nameBox = nameLine.first.boundingBox ?: box
            val yDistance = kotlin.math.abs(box.top - nameBox.top)
            
            // 상대방 이름과의 거리가 가까우면 상대방 대화로 인식
            if (yDistance < 300) { // 300px 이내
                android.util.Log.d("OCR_CLASSIFIER", "  → 상대방 이름 근처 대화: 거리=$yDistance")
                return Sender.OTHER
            }
        }
        
        // 기존 라운딩 기반 분류
        return classifyByRounding(text, box, canvasWidth, canvasHeight, otherPersonName, sortedLines, currentIndex)
    }
    
    /**
     * 자동 임계값 보정을 위한 데이터 클래스
     */
    data class OcrEntry(
        val text: String,
        val x: Int,
        val y: Int
    )
    
    data class Thresholds(
        val LEFT_MAX_X: Int,
        val RIGHT_MIN_X: Int
    )
    
    /**
     * 1D k-means를 사용한 자동 임계값 보정
     */
    private fun autoCalibrateThresholds(
        entries: List<OcrEntry>,
        stepPx: Int = 30,
        marginRatio: Double = 0.10,
        minGapPx: Int = 60,
        fallback: Thresholds = Thresholds(300, 400)
    ): Thresholds {
        val xs = entries.map { it.x }.filter { it >= 0 }
        if (xs.size < 4) return fallback

        // 초기 중심: minX, maxX
        var c1 = xs.minOrNull()!!.toDouble()
        var c2 = xs.maxOrNull()!!.toDouble()
        if (c1 == c2) return fallback

        // k-means 반복 (최대 10회)
        repeat(10) {
            val leftCluster = mutableListOf<Int>()
            val rightCluster = mutableListOf<Int>()
            for (x in xs) {
                if (kotlin.math.abs(x - c1) <= kotlin.math.abs(x - c2)) {
                    leftCluster.add(x)
                } else {
                    rightCluster.add(x)
                }
            }
            val newC1 = if (leftCluster.isNotEmpty()) leftCluster.average() else c1
            val newC2 = if (rightCluster.isNotEmpty()) rightCluster.average() else c2
            
            // 수렴 체크(변화 미미)
            if (kotlin.math.abs(newC1 - c1) < 0.5 && kotlin.math.abs(newC2 - c2) < 0.5) {
                c1 = newC1; c2 = newC2; return@repeat
            }
            c1 = newC1; c2 = newC2
        }

        // 왼/오 정렬
        val leftCenter = kotlin.math.min(c1, c2)
        val rightCenter = kotlin.math.max(c1, c2)
        val gap = rightCenter - leftCenter
        if (gap < 30) return fallback // 너무 가까우면 신뢰 낮음 → 기본값

        val mid = (leftCenter + rightCenter) / 2.0
        val margin = kotlin.math.max(gap * marginRatio, 20.0) // 최소 20px 여유

        var leftMax = mid - margin
        var rightMin = mid + margin

        // 스냅(20~50px대 권장)
        leftMax = roundToStep(leftMax, stepPx).toDouble()
        rightMin = roundToStep(rightMin, stepPx).toDouble()

        // 최소 간격 보장
        if (rightMin - leftMax < minGapPx) {
            val adjust = (minGapPx - (rightMin - leftMax)) / 2.0
            leftMax = kotlin.math.max(0.0, leftMax - adjust)
            rightMin += adjust
            // 다시 스냅
            leftMax = roundToStep(leftMax, stepPx).toDouble()
            rightMin = roundToStep(rightMin, stepPx).toDouble()
        }

        android.util.Log.d("OCR_CLASSIFIER", "자동 임계값 보정 완료: LEFT_MAX_X=$leftMax, RIGHT_MIN_X=$rightMin")
        return Thresholds(leftMax.toInt(), rightMin.toInt())
    }
    
    /**
     * 스텝 단위로 반올림
     */
    private fun roundToStep(value: Double, step: Int): Int {
        return (kotlin.math.round(value / step).toInt() * step).coerceAtLeast(0)
    }
    
    /**
     * 이름 라인인지 확인
     */
    private fun isNameLine(text: String): Boolean {
        if (text.length !in 1..6) return false
        return Regex("""^[\p{L}\p{N}]+$""").matches(text)
    }
    
    /**
     * 개선된 X좌표 기반 분류 (자동 임계값 보정 사용)
     */
    private fun checkIfMyMessageByXPosition(currentBox: android.graphics.Rect, sortedLines: List<Pair<Text.Line, Int>>, currentIndex: Int, unusedLastOtherPersonIndex: Int, unusedCanvasWidth: Int, unusedOtherPersonXRange: Pair<Int, Int>): Boolean {
        val currentX = currentBox.left
        val currentText = sortedLines.getOrNull(currentIndex)?.first?.text?.trim() ?: ""
        
        // OCR 엔트리 생성
        val entries = sortedLines.mapNotNull { (line, unusedBlockIndex) ->
            val box = line.boundingBox ?: return@mapNotNull null
            val text = line.text.trim()
            if (text.isBlank()) return@mapNotNull null
            OcrEntry(text, box.left, box.top)
        }
        
        // 자동 임계값 보정
        val thresholds = autoCalibrateThresholds(entries)
        
        android.util.Log.d("OCR_CLASSIFIER", "    → 자동 임계값: LEFT_MAX_X=${thresholds.LEFT_MAX_X}, RIGHT_MIN_X=${thresholds.RIGHT_MIN_X}")
        android.util.Log.d("OCR_CLASSIFIER", "    → 현재 X좌표: $currentX")
        
        // 분류 로직
        val isLeftSide = currentX <= thresholds.LEFT_MAX_X
        val isRightSide = currentX >= thresholds.RIGHT_MIN_X
        
        when {
            isRightSide -> {
                android.util.Log.d("OCR_CLASSIFIER", "    → 오른쪽 영역, 내 대화로 판단")
                return true
            }
            isLeftSide -> {
                android.util.Log.d("OCR_CLASSIFIER", "    → 왼쪽 영역, 상대방 대화로 판단")
                return false
            }
            else -> {
                // 중간 영역 - 이름 라인이면 상대방, 아니면 이전 발화자 따라감
                if (isNameLine(currentText)) {
                    android.util.Log.d("OCR_CLASSIFIER", "    → 중간 영역 + 이름 라인, 상대방 대화로 판단")
                    return false
                } else {
                    // 이전 발화자 따라감 (기본값은 상대방)
                    android.util.Log.d("OCR_CLASSIFIER", "    → 중간 영역, 이전 발화자 따라감 (기본: 상대방)")
                    return false
                }
            }
        }
    }
    
    /**
     * 라운딩 기반 발신자 분류 (개선된 말풍선 감지)
     */
    private fun classifyByRounding(text: String, box: android.graphics.Rect, canvasWidth: Int, canvasHeight: Int, otherPersonName: String, sortedLines: List<Pair<Text.Line, Int>>, currentIndex: Int): Sender {
        // 상대방 이름과 일치
        if (text == otherPersonName) {
            android.util.Log.d("OCR_CLASSIFIER", "  → 상대방 이름 매칭: '$text'")
            return Sender.OTHER
        }
        
        // 말풍선 위치 분석
        val centerX = box.centerX()
        val left = box.left
        val right = box.right
        val width = box.width()
        
        // 카카오톡 말풍선 특징 분석
        val isLeftBubble = detectLeftBubble(box, canvasWidth, canvasHeight)
        val isRightBubble = detectRightBubble(box, canvasWidth, canvasHeight)
        
        android.util.Log.d("OCR_CLASSIFIER", "  → 말풍선 분석: centerX=$centerX, left=$left, right=$right, width=$width")
        android.util.Log.d("OCR_CLASSIFIER", "  → 왼쪽 말풍선: $isLeftBubble, 오른쪽 말풍선: $isRightBubble")
        
        return when {
            isLeftBubble -> {
                android.util.Log.d("OCR_CLASSIFIER", "  → 왼쪽 말풍선 감지 (상대방)")
                Sender.OTHER
            }
            isRightBubble -> {
                android.util.Log.d("OCR_CLASSIFIER", "  → 오른쪽 말풍선 감지 (나)")
                Sender.ME
            }
            // 말풍선 감지 실패 시 위치 기반 분류
            else -> {
                android.util.Log.d("OCR_CLASSIFIER", "  → 말풍선 감지 실패, 위치 기반 분류")
                classifyByPosition(text, box, canvasWidth, canvasHeight, otherPersonName, sortedLines, currentIndex)
            }
        }
    }
    
    /**
     * 왼쪽 말풍선 감지
     */
    private fun detectLeftBubble(box: android.graphics.Rect, canvasWidth: Int, unusedCanvasHeight: Int): Boolean {
        val centerX = box.centerX()
        val left = box.left
        val width = box.width()
        
        // 왼쪽 말풍선 특징
        val isLeftSide = centerX < canvasWidth * 0.4f  // 화면 왼쪽 40% 이내
        val isNotTooWide = width < canvasWidth * 0.6f  // 너무 넓지 않음
        val isNotAtEdge = left > canvasWidth * 0.05f  // 화면 가장자리가 아님
        
        android.util.Log.d("OCR_CLASSIFIER", "    → 왼쪽 말풍선 조건: 왼쪽=$isLeftSide, 너비적절=$isNotTooWide, 가장자리아님=$isNotAtEdge")
        
        return isLeftSide && isNotTooWide && isNotAtEdge
    }
    
    /**
     * 오른쪽 말풍선 감지
     */
    private fun detectRightBubble(box: android.graphics.Rect, canvasWidth: Int, unusedCanvasHeight: Int): Boolean {
        val centerX = box.centerX()
        val right = box.right
        val width = box.width()
        
        // 오른쪽 말풍선 특징
        val isRightSide = centerX > canvasWidth * 0.6f  // 화면 오른쪽 60% 이내
        val isNotTooWide = width < canvasWidth * 0.6f   // 너무 넓지 않음
        val isNotAtEdge = right < canvasWidth * 0.95f   // 화면 가장자리가 아님
        
        android.util.Log.d("OCR_CLASSIFIER", "    → 오른쪽 말풍선 조건: 오른쪽=$isRightSide, 너비적절=$isNotTooWide, 가장자리아님=$isNotAtEdge")
        
        return isRightSide && isNotTooWide && isNotAtEdge
    }
    
    /**
     * 위치 기반 분류 (말풍선 감지 실패 시)
     */
    private fun classifyByPosition(unusedText: String, box: android.graphics.Rect, canvasWidth: Int, canvasHeight: Int, otherPersonName: String, sortedLines: List<Pair<Text.Line, Int>>, currentIndex: Int): Sender {
        val centerX = box.centerX()
        
        // 더 엄격한 임계값 사용
        val leftThreshold = canvasWidth * 0.25f
        val rightThreshold = canvasWidth * 0.75f
        
        android.util.Log.d("OCR_CLASSIFIER", "    → 위치 기반: centerX=$centerX, 왼쪽임계값=$leftThreshold, 오른쪽임계값=$rightThreshold")
        
        return when {
            centerX < leftThreshold -> {
                android.util.Log.d("OCR_CLASSIFIER", "    → 왼쪽 영역 (상대방)")
                Sender.OTHER
            }
            centerX > rightThreshold -> {
                android.util.Log.d("OCR_CLASSIFIER", "    → 오른쪽 영역 (나)")
                Sender.ME
            }
            else -> {
                android.util.Log.d("OCR_CLASSIFIER", "    → 중앙 영역, 컨텍스트 분석")
                classifyByContext(unusedText, box, canvasHeight, otherPersonName, sortedLines, currentIndex)
            }
        }
    }
    
    /**
     * 컨텍스트 기반 분류 (중앙 영역)
     */
    private fun classifyByContext(text: String, box: android.graphics.Rect, canvasHeight: Int, otherPersonName: String, sortedLines: List<Pair<Text.Line, Int>>, currentIndex: Int): Sender {
        // 상대방 이름 근처인지 확인
        if (isNearOtherPersonName(box, otherPersonName, sortedLines, currentIndex)) {
            android.util.Log.d("OCR_CLASSIFIER", "  → 상대방 이름 근처")
            return Sender.OTHER
        }
        
        // 화면 하단 영역인지 확인
        val bottomThreshold = canvasHeight * 0.7f
        if (box.top > bottomThreshold) {
            android.util.Log.d("OCR_CLASSIFIER", "  → 화면 하단 영역")
            return Sender.ME
        }
        
        android.util.Log.d("OCR_CLASSIFIER", "  → 컨텍스트 분석 실패 - 미분류")
        return Sender.UNKNOWN
    }
    
    /**
     * 상대방 이름 근처인지 확인
     */
    private fun isNearOtherPersonName(currentBox: android.graphics.Rect, otherPersonName: String, sortedLines: List<Pair<Text.Line, Int>>, currentIndex: Int): Boolean {
        if (otherPersonName.isEmpty()) return false
        
        // 상대방 이름의 위치 찾기
        for (i in 0 until currentIndex) {
            val line = sortedLines[i]
            val text = line.first.text.trim()
            if (text == otherPersonName) {
                val nameBox = line.first.boundingBox ?: continue
                val yDistance = kotlin.math.abs(currentBox.top - nameBox.top)
                val isNear = yDistance < 200 // 200px 이내
                
                android.util.Log.d("OCR_CLASSIFIER", "  → 상대방 이름 거리: $yDistance, 근접: $isNear")
                return isNear
            }
        }
        
        return false
    }
    
    /**
     * 분류 근거 생성 (연속구간 반영)
     */
    private fun getClassificationReason(sender: Sender, text: String, box: android.graphics.Rect, canvasWidth: Int, canvasHeight: Int, otherPersonName: String): String {
        return when (sender) {
            Sender.OTHER -> {
                when {
                    text == otherPersonName -> "상대방 이름 매칭"
                    detectLeftBubble(box, canvasWidth, canvasHeight) -> "왼쪽 말풍선 감지"
                    box.centerX() < canvasWidth * 0.25f -> "왼쪽 영역 (상대방)"
                    else -> "상대방 범위 내"
                }
            }
            Sender.ME -> {
                when {
                    detectRightBubble(box, canvasWidth, canvasHeight) -> "오른쪽 말풍선 감지"
                    box.centerX() > canvasWidth * 0.75f -> "오른쪽 영역 (나)"
                    box.top > canvasHeight * 0.7f -> "화면 하단 영역"
                    else -> "상대방 범위 벗어남 (나)"
                }
            }
            Sender.UNKNOWN -> "분류 실패"
        }
    }
    
    /**
     * 메시지 그룹 생성 (텍스트 정리 포함)
     */
    private fun createMessageGroup(lines: List<Pair<Text.Line, Int>>, sender: Sender, classificationReason: String): MessageGroup {
        val rawText = lines.joinToString("\n") { it.first.text.trim() }
        val cleanedText = cleanMessageText(rawText, sender)
        val box = mergeBoxes(lines.map { it.first.boundingBox ?: android.graphics.Rect() })
        
        android.util.Log.d("OCR_CLASSIFIER", "메시지 그룹 생성: [${sender}] '$cleanedText'")
        
        return MessageGroup(cleanedText, sender, box, classificationReason)
    }
    
    /**
     * 메시지 텍스트 정리 (불필요한 요소 제거)
     */
    private fun cleanMessageText(text: String, unusedSender: Sender): String {
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val cleanedLines = mutableListOf<String>()
        var isFirstLine = true
        
        for (line in lines) {
            // 첫 번째 라인에서 왼쪽 상단 이름 텍스트 제거 (← 태용 등)
            if (isFirstLine && isTopLeftNamePattern(line)) {
                android.util.Log.d("OCR_CLASSIFIER", "  → 첫 번째 왼쪽 상단 이름 제거: '$line'")
                isFirstLine = false
                continue
            }
            
            // 날짜/요일 패턴 제거 (2025년 10월 22일 수요일 >)
            if (isDatePattern(line)) {
                android.util.Log.d("OCR_CLASSIFIER", "  → 날짜 패턴 제거: '$line'")
                continue
            }
            
            // 빈 라인이나 의미없는 라인 제거
            if (isMeaninglessLine(line)) {
                android.util.Log.d("OCR_CLASSIFIER", "  → 의미없는 라인 제거: '$line'")
                continue
            }
            
            // 나머지 라인은 유지
            cleanedLines.add(line)
            isFirstLine = false
        }
        
        return cleanedLines.joinToString("\n").trim()
    }
    
    /**
     * 왼쪽 상단 이름 패턴인지 확인 (← 태용 등, 화살표가 포함된 경우만)
     */
    private fun isTopLeftNamePattern(line: String): Boolean {
        val trimmed = line.trim()
        
        // 화살표로 시작하는 이름 패턴만 제거 (← 태용, → 태용 등)
        if (trimmed.matches(Regex("^[←→↑↓◀▶▲▼]\\s*[가-힣a-zA-Z0-9]+\\s*$"))) {
            return true
        }
        
        // 단순한 이름은 제거하지 않음 (수현, 태용 등)
        return false
    }
    
    /**
     * 이름만 있는 라인인지 확인 (← 태용, 태용 등)
     */
    private fun isNameOnlyLine(line: String): Boolean {
        val trimmed = line.trim()
        
        // 화살표로 시작하는 이름 라인 (← 태용, → 태용 등)
        if (trimmed.matches(Regex("^[←→↑↓◀▶▲▼]\\s*[가-힣a-zA-Z0-9]+\\s*$"))) {
            return true
        }
        
        // 단순한 이름만 있는 라인 (2-10자, 한글/영문/숫자만, 공백 없음)
        if (trimmed.matches(Regex("^[가-힣a-zA-Z0-9]{2,10}$")) && 
            !trimmed.contains(" ") && 
            !trimmed.contains(".") && 
            !trimmed.contains(":") &&
            !trimmed.contains("년") &&
            !trimmed.contains("월") &&
            !trimmed.contains("일")) {
            return true
        }
        
        return false
    }
    
    /**
     * 날짜 패턴인지 확인 (2025년 10월 22일 수요일 >)
     */
    private fun isDatePattern(line: String): Boolean {
        val trimmed = line.trim()
        
        // 년월일 요일 패턴 (년, 월, 일, 요일이 모두 포함된 경우)
        val fullDatePatterns = listOf(
            Regex(".*\\d{4}년\\s*\\d{1,2}월\\s*\\d{1,2}일\\s*[월화수목금토일]요일\\s*>.*"),
            Regex(".*\\d{4}년\\s*\\d{1,2}월\\s*\\d{1,2}일\\s*[월화수목금토일]요일.*"),
            Regex(".*\\d{4}년\\s*\\d{1,2}월\\s*\\d{1,2}일\\s*>.*"),
            Regex(".*\\d{1,2}월\\s*\\d{1,2}일\\s*[월화수목금토일]요일\\s*>.*")
        )
        
        // 년, 월, 일, 요일, > 가 모두 포함된 패턴
        if (fullDatePatterns.any { it.matches(trimmed) }) {
            return true
        }
        
        // 년, 월, 일, 요일이 모두 포함되고 끝에 > 기호가 있는 경우
        if (trimmed.contains("년") && trimmed.contains("월") && trimmed.contains("일") && 
            trimmed.contains("요일") && trimmed.endsWith(">")) {
            return true
        }
        
        return false
    }
    
    /**
     * 의미없는 라인인지 확인
     */
    private fun isMeaninglessLine(line: String): Boolean {
        val trimmed = line.trim()
        
        // 빈 라인
        if (trimmed.isEmpty()) return true
        
        // 단일 기호나 특수문자만 있는 라인
        if (trimmed.matches(Regex("^[\\s\\-_=+*•·←→↑↓◀▶▲▼<>]+$"))) return true
        
        // "메시지 입력" 같은 UI 텍스트
        if (trimmed.contains("메시지 입력") || trimmed.contains("메시지") || trimmed.contains("입력")) return true
        
        // 시간만 있는 라인 (오전/오후 + 시간)
        if (trimmed.matches(Regex("^[오전오후]\\s*\\d{1,2}:\\d{2}$"))) return true
        
        return false
    }
    
    /**
     * 여러 박스를 병합
     */
    private fun mergeBoxes(boxes: List<android.graphics.Rect>): android.graphics.Rect {
        if (boxes.isEmpty()) return android.graphics.Rect()
        
        var minLeft = boxes[0].left
        var minTop = boxes[0].top
        var maxRight = boxes[0].right
        var maxBottom = boxes[0].bottom
        
        for (box in boxes) {
            minLeft = min(minLeft, box.left)
            minTop = min(minTop, box.top)
            maxRight = max(maxRight, box.right)
            maxBottom = max(maxBottom, box.bottom)
        }
        
        return android.graphics.Rect(minLeft, minTop, maxRight, maxBottom)
    }
    
    /**
     * 텍스트에서 이름 부분만 추출
     * "← 태용" -> "태용"
     * "← 김철수" -> "김철수"
     * "태용" -> "태용" (그대로)
     */
    private fun extractNameFromText(text: String): String {
        val trimmed = text.trim()
        
        // 화살표나 특수문자로 시작하는 경우 제거
        val patterns = listOf(
            Regex("^[←→↑↓◀▶▲▼]\\s*(.+)"),  // 화살표 + 공백 + 이름
            Regex("^[<>=]\\s*(.+)"),        // 기호 + 공백 + 이름
            Regex("^[•·]\\s*(.+)"),         // 불릿 + 공백 + 이름
            Regex("^[\\-\\*]\\s*(.+)"),     // 대시/별표 + 공백 + 이름
            Regex("^\\s*(.+)\\s*$")         // 앞뒤 공백 제거
        )
        
        for (pattern in patterns) {
            val match = pattern.find(trimmed)
            if (match != null) {
                val extracted = match.groupValues[1].trim()
                if (extracted.isNotEmpty() && extracted.length in 2..10) {
                    android.util.Log.d("OCR_CLASSIFIER", "  → 이름 추출: '$trimmed' -> '$extracted'")
                    return extracted
                }
            }
        }
        
        // 패턴에 맞지 않으면 원본 반환 (이미 적절한 길이인 경우)
        if (trimmed.length in 2..10) {
            android.util.Log.d("OCR_CLASSIFIER", "  → 이름 그대로: '$trimmed'")
            return trimmed
        }
        
        // 너무 길거나 짧으면 빈 문자열
        android.util.Log.d("OCR_CLASSIFIER", "  → 이름 추출 실패: '$trimmed' (길이: ${trimmed.length})")
        return ""
    }
}
