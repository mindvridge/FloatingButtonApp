package com.mv.toki.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mv.toki.api.AppUpdateCheckResponse
import android.util.Log

/**
 * 앱 업데이트 알림 다이얼로그
 */
@Composable
fun UpdateDialog(
    updateInfo: AppUpdateCheckResponse,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Log.d("UpdateDialog", "=== UpdateDialog 렌더링 시작 ===")
    Log.d("UpdateDialog", "업데이트 정보:")
    Log.d("UpdateDialog", "  - needs_update: ${updateInfo.needsUpdate}")
    Log.d("UpdateDialog", "  - latest_version: ${updateInfo.latestVersion}")
    Log.d("UpdateDialog", "  - current_version: ${updateInfo.currentVersion}")
    Log.d("UpdateDialog", "  - force_update: ${updateInfo.forceUpdate}")
    Log.d("UpdateDialog", "  - update_message: ${updateInfo.updateMessage}")
    Log.d("UpdateDialog", "  - store_url: ${updateInfo.storeUrl}")
    Log.d("UpdateDialog", "  - release_notes: ${updateInfo.releaseNotes}")
    
    AlertDialog(
        onDismissRequest = if (updateInfo.forceUpdate) {
            { /* 강제 업데이트인 경우 닫기 버튼 비활성화 */ }
        } else {
            onDismiss
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Update,
                    contentDescription = "업데이트",
                    tint = Color(0xFFF27B13)
                )
                Text(
                    text = if (updateInfo.forceUpdate) {
                        "필수 업데이트"
                    } else {
                        "업데이트 알림"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (updateInfo.forceUpdate) Color(0xFFE53935) else Color(0xFFF27B13)
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    // 업데이트 메시지
                    Text(
                        text = updateInfo.updateMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 버전 정보
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "버전 정보",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "현재 버전:",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = updateInfo.currentVersion,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "최신 버전:",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = updateInfo.latestVersion,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF27B13)
                                )
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "업데이트 타입:",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = when (updateInfo.updateType) {
                                        "major" -> "주요 업데이트"
                                        "minor" -> "부가 업데이트"
                                        "patch" -> "패치 업데이트"
                                        else -> updateInfo.updateType
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                
                // 릴리즈 노트
                if (updateInfo.releaseNotes.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "업데이트 내용",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                updateInfo.releaseNotes.forEach { note ->
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "•",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color(0xFFF27B13),
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = note,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // 강제 업데이트 안내
                if (updateInfo.forceUpdate) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFE53935).copy(alpha = 0.1f)
                            )
                        ) {
                            Text(
                                text = "⚠️ 이 업데이트는 필수입니다. 앱을 계속 사용하려면 업데이트가 필요합니다.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFE53935),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(12.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdate,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF27B13)
                )
            ) {
                Text(
                    text = if (updateInfo.forceUpdate) "지금 업데이트" else "업데이트",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            if (!updateInfo.forceUpdate) {
                TextButton(onClick = onDismiss) {
                    Text("나중에")
                }
            }
        },
        modifier = modifier
    )
}
