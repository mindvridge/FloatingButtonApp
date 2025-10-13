package com.mv.floatingbuttonapp

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
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
    // 흰색 배경
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 상단: 단계 표시
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(60.dp))
                
                // 단계 표시 (1단계, 2단계, 3단계) - per1.png 이미지 사용
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(horizontal = 40.dp)
                ) {
                    // 배경 이미지
                    Image(
                        painter = painterResource(id = R.drawable.per1),
                        contentDescription = "단계 표시",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    
                    // 텍스트 오버레이
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1단계 (활성) - 주황색 배경에 흰색 텍스트 (왼쪽으로 이동)
                        Text(
                            text = "1단계",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.offset(x = (-30).dp)
                        )
                        
                        // 2단계 (비활성) - 회색 배경에 회색 텍스트
                        Text(
                            text = "2단계",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF666666)
                        )
                        
                        // 3단계 (비활성) - 회색 배경에 회색 텍스트 (오른쪽으로 이동)
                        Text(
                            text = "3단계",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF666666),
                            modifier = Modifier.offset(x = 30.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(80.dp))
                
                // 중앙 일러스트레이션 - Per1Logo.png 사용
                Image(
                    painter = painterResource(id = R.drawable.per1logo),
                    contentDescription = "권한 설정 일러스트레이션",
                    modifier = Modifier
                        .size(300.dp)
                        .padding(20.dp),
                    contentScale = ContentScale.Fit
                )
                
                Spacer(modifier = Modifier.height(60.dp))
            }
            
            // 하단: 텍스트와 버튼
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(60.dp))
                
                // 제목
                Text(
                    text = "다른 앱 위에 그리기 권한",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 설명
                Text(
                    text = "플로팅 버튼을 화면에 표시하기 위해\n다른 앱 위에 그리기 권한이 필요합니다.",
                    fontSize = 16.sp,
                    color = Color(0xFF666666),
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )
                
                Spacer(modifier = Modifier.height(40.dp))
                
                // 다음 단계로 버튼 - UIButtons.png 사용 (가로 길이의 1/2 사이즈)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f) // 가로 길이의 1/2
                            .aspectRatio(3.2f) // 원본 비율 유지
                            .clickable { onNextClick() }
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.uibuttons),
                            contentDescription = "다음 단계로 버튼",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit // 비율 유지
                        )
                        
                        // 버튼 위에 텍스트 오버레이 - 완전 중앙 정렬
                        Text(
                            text = "다음 단계로",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(100.dp))
            }
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
    // 배경 색상 (임시로 단색 배경 사용)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE8F5E8))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                            .background(Color(0xFF4CAF50), shape = CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(2.dp)
                            .background(Color(0xFF4CAF50))
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
                    color = Color(0xFF333333)
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
                    color = Color(0xFF333333),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 설명
                Text(
                    text = "키보드 입력을 감지하고 화면을 캡처하기 위해\n접근성 서비스 권한이 필요합니다.",
                    fontSize = 16.sp,
                    color = Color(0xFF666666),
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
                        containerColor = Color.White.copy(alpha = 0.8f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (hasPermission) Icons.Default.Check else Icons.Default.Close,
                                contentDescription = "권한 상태",
                                modifier = Modifier.size(24.dp),
                                tint = if (hasPermission) Color(0xFF4CAF50) else Color(0xFFF44336)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (hasPermission) "권한 허용됨" else "권한 필요",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF333333)
                            )
                        }
                        
                        if (!hasPermission) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "권한을 허용하면 키보드 입력 감지 기능을 사용할 수 있습니다.",
                                fontSize = 14.sp,
                                color = Color(0xFF666666),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            
            // 하단: 버튼들
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 권한 요청 버튼 (권한이 없을 때만 표시)
                if (!hasPermission) {
                    Button(
                        onClick = onRequestPermission,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6200EE)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "권한 설정하기",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                // 다음 단계로 버튼
                Button(
                    onClick = onNextClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hasPermission) Color(0xFF4CAF50) else Color(0xFF6200EE)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (hasPermission) "다음 단계로" else "건너뛰기",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 건너뛰기 버튼 (권한이 있을 때만 표시)
                if (hasPermission) {
                    TextButton(
                        onClick = onSkipClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "건너뛰기",
                            color = Color(0xFF666666),
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * 서비스 제어 화면
 * 사용자가 서비스를 시작/중지할 수 있는 메인 화면
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
    // 배경 색상 (임시로 단색 배경 사용)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE3F2FD))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 사용자 정보 표시 (중앙 정렬)
            if (currentUser != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "사용자",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "안녕하세요!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentUser.nickname ?: "사용자",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // 권한 설정 섹션
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "권한 상태",
                        style = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            shadow = Shadow(
                                color = Color.Black,
                                offset = Offset(1f, 1f),
                                blurRadius = 2f
                            )
                        ),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // 오버레이 권한
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (hasOverlayPermission) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = "오버레이 권한",
                            modifier = Modifier.size(20.dp),
                            tint = if (hasOverlayPermission) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "다른 앱 위에 그리기",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        if (!hasOverlayPermission) {
                            Button(
                                onClick = onOverlayPermissionClick,
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    text = "설정",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    
                    // 접근성 권한
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (hasAccessibilityPermission) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = "접근성 권한",
                            modifier = Modifier.size(20.dp),
                            tint = if (hasAccessibilityPermission) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "접근성 서비스",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        if (!hasAccessibilityPermission) {
                            Button(
                                onClick = onAccessibilityPermissionClick,
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    text = "설정",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            // 서비스 상태 표시
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isServiceRunning) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isServiceRunning) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = "서비스 상태",
                        modifier = Modifier.size(24.dp),
                        tint = if (isServiceRunning) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (isServiceRunning) "서비스 실행 중" else "서비스 중지됨",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isServiceRunning) 
                            MaterialTheme.colorScheme.onPrimaryContainer 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 서비스 제어 버튼들
            if (isServiceRunning) {
                Button(
                    onClick = onStopServiceClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        text = "서비스 중지",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            } else {
                Button(
                    onClick = onStartServiceClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFA500)
                    )
                ) {
                    Text(
                        text = "서비스 시작",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF2C3E50)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 로그아웃 버튼
            OutlinedButton(
                onClick = onLogoutClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = "로그아웃",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "로그아웃",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
