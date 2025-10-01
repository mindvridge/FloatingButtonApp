package com.mv.floatingbuttonapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 권한 설정 1: 다른 앱 위에 그리기 화면
 * 로그인 후 첫 번째 권한 설정 단계
 */
@Composable
fun PermissionOverlayScreen(
    currentUser: UserInfo?,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onNextClick: () -> Unit,
    onSkipClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 상단: 진행 상황 표시
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            
            // 진행 바
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 단계 1 (현재)
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color(0xFF6200EE), shape = CircleShape)
                )
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(2.dp)
                        .background(Color(0xFFDDDDDD))
                )
                // 단계 2
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color(0xFFDDDDDD), shape = CircleShape)
                )
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(2.dp)
                        .background(Color(0xFFDDDDDD))
                )
                // 단계 3
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color(0xFFDDDDDD), shape = CircleShape)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "1 / 3",
                fontSize = 14.sp,
                color = Color.Gray
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // 아이콘
            Icon(
                imageVector = Icons.Default.Visibility,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = Color(0xFF6200EE)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 제목
            Text(
                text = "다른 앱 위에 그리기 권한",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2C3E50),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 설명
            Text(
                text = "플로팅 버튼을 화면에 표시하기 위해\n다른 앱 위에 그리기 권한이 필요합니다.",
                fontSize = 16.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 권한 상태 카드
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (hasPermission) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (hasPermission) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        tint = if (hasPermission) Color(0xFF4CAF50) else Color(0xFFF44336),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (hasPermission) "권한이 허용되었습니다" else "권한이 필요합니다",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (hasPermission) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                }
            }
        }
        
        // 하단: 버튼들
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!hasPermission) {
                // 권한 요청 버튼
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6200EE)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "권한 설정하기",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 건너뛰기 버튼
                OutlinedButton(
                    onClick = onSkipClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6200EE))
                ) {
                    Text(
                        text = "나중에 하기",
                        fontSize = 16.sp,
                        color = Color(0xFF6200EE)
                    )
                }
            } else {
                // 다음 버튼
                Button(
                    onClick = onNextClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6200EE)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "다음 단계로",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 권한 설정 2: 접근성 서비스 화면
 * 두 번째 권한 설정 단계
 */
@Composable
fun PermissionAccessibilityScreen(
    currentUser: UserInfo?,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onNextClick: () -> Unit,
    onSkipClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 상단: 진행 상황 표시
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            
            // 진행 바
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 단계 1 (완료)
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color(0xFF6200EE), shape = CircleShape)
                )
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(2.dp)
                        .background(Color(0xFF6200EE))
                )
                // 단계 2 (현재)
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color(0xFF6200EE), shape = CircleShape)
                )
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(2.dp)
                        .background(Color(0xFFDDDDDD))
                )
                // 단계 3
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color(0xFFDDDDDD), shape = CircleShape)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "2 / 3",
                fontSize = 14.sp,
                color = Color.Gray
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // 아이콘
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = Color(0xFF6200EE)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 제목
            Text(
                text = "접근성 서비스 권한",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2C3E50),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 설명
            Text(
                text = "키보드 감지와 화면 캡처를 위해\n접근성 서비스 권한이 필요합니다.",
                fontSize = 16.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 권한 상태 카드
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (hasPermission) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (hasPermission) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        tint = if (hasPermission) Color(0xFF4CAF50) else Color(0xFFF44336),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (hasPermission) "권한이 허용되었습니다" else "권한이 필요합니다",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (hasPermission) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                }
            }
        }
        
        // 하단: 버튼들
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!hasPermission) {
                // 권한 요청 버튼
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6200EE)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "권한 설정하기",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 건너뛰기 버튼
                OutlinedButton(
                    onClick = onSkipClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6200EE))
                ) {
                    Text(
                        text = "나중에 하기",
                        fontSize = 16.sp,
                        color = Color(0xFF6200EE)
                    )
                }
            } else {
                // 다음 버튼
                Button(
                    onClick = onNextClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6200EE)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "완료하기",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 서비스 실행 화면
 * 모든 권한 설정 후 서비스를 시작/중지하는 화면
 */
@Composable
fun ServiceControlScreen(
    currentUser: UserInfo?,
    hasOverlayPermission: Boolean,
    hasAccessibilityPermission: Boolean,
    isServiceRunning: Boolean,
    onStartServiceClick: () -> Unit,
    onStopServiceClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onOverlayPermissionClick: () -> Unit,
    onAccessibilityPermissionClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA))
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        
        // 사용자 정보 카드
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color(0xFF6200EE)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = currentUser?.nickname ?: "사용자",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2C3E50)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentUser?.email ?: "",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
                IconButton(onClick = onLogoutClick) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = "로그아웃",
                        tint = Color.Gray
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 서비스 상태 카드
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isServiceRunning) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = if (isServiceRunning) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = if (isServiceRunning) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (isServiceRunning) "서비스 실행 중" else "서비스 중지됨",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isServiceRunning) Color(0xFF2E7D32) else Color(0xFFC62828)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isServiceRunning) 
                        "플로팅 버튼이 활성화되어 있습니다" 
                    else 
                        "서비스를 시작하려면 아래 버튼을 누르세요",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 서비스 제어 버튼
        if (isServiceRunning) {
            Button(
                onClick = onStopServiceClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF44336)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "서비스 중지",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Button(
                onClick = onStartServiceClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6200EE)
                ),
                shape = RoundedCornerShape(12.dp),
                enabled = hasOverlayPermission && hasAccessibilityPermission
            ) {
                Text(
                    text = "서비스 시작",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 권한 상태 섹션
        Text(
            text = "권한 상태",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2C3E50)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 오버레이 권한 카드
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !hasOverlayPermission) { onOverlayPermissionClick() },
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    tint = if (hasOverlayPermission) Color(0xFF4CAF50) else Color.Gray,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "다른 앱 위에 그리기",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF2C3E50)
                    )
                    Text(
                        text = if (hasOverlayPermission) "허용됨" else "권한 필요",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
                Icon(
                    imageVector = if (hasOverlayPermission) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (hasOverlayPermission) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 접근성 권한 카드
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !hasAccessibilityPermission) { onAccessibilityPermissionClick() },
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = if (hasAccessibilityPermission) Color(0xFF4CAF50) else Color.Gray,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "접근성 서비스",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF2C3E50)
                    )
                    Text(
                        text = if (hasAccessibilityPermission) "허용됨" else "권한 필요",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
                Icon(
                    imageVector = if (hasAccessibilityPermission) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (hasAccessibilityPermission) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

