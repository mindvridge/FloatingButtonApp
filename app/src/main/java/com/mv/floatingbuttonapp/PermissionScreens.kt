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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding() // 시스템 바 영역 패딩 추가
            .padding(horizontal = 24.dp)
            .padding(bottom = 80.dp), // 하단 여백 추가
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 상단 여백 (대폭 감소)
        Spacer(modifier = Modifier.height(20.dp))
        
        // 단계 표시 (1단계, 2단계, 3단계) - per1.png 이미지 사용
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp) // 높이 감소
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
                    fontSize = 12.sp, // 폰트 크기 감소
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .offset(x = (-27).dp) // 오프셋 감소
                        .align(Alignment.CenterVertically)
                )
                
                // 2단계 (비활성) - 회색 배경에 회색 텍스트
                Text(
                    text = "2단계",
                    fontSize = 12.sp, // 폰트 크기 감소
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF666666),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                
                // 3단계 (비활성) - 회색 배경에 회색 텍스트 (오른쪽으로 이동)
                Text(
                    text = "3단계",
                    fontSize = 12.sp, // 폰트 크기 감소
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF666666),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .offset(x = 27.dp) // 오프셋 감소
                        .align(Alignment.CenterVertically)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(30.dp)) // 간격 대폭 감소
        
        // 중앙 일러스트레이션 - Per1Logo.png 사용
        Image(
            painter = painterResource(id = R.drawable.per1logo),
            contentDescription = "권한 설정 일러스트레이션",
            modifier = Modifier
                .size(200.dp) // 크기 감소
                .padding(10.dp), // 패딩 감소
            contentScale = ContentScale.Fit
        )
        
        Spacer(modifier = Modifier.height(30.dp)) // 간격 대폭 감소
        
        // 제목
        Text(
            text = "다른 앱 위에 그리기 권한",
            fontSize = 22.sp, // 폰트 크기 감소
            fontWeight = FontWeight.Bold,
            color = Color(0xFF333333),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(12.dp)) // 간격 감소
        
        // 설명
        Text(
            text = "플로팅 버튼을 화면에 표시하기 위해\n다른 앱 위에 그리기 권한이 필요합니다.",
            fontSize = 14.sp, // 폰트 크기 감소
            color = Color(0xFF666666),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp // 줄 간격 감소
        )
        
        Spacer(modifier = Modifier.height(20.dp)) // 간격 대폭 감소
        
        // 권한 설정 버튼 - UIButtons.png 사용 (가로 길이의 1/2 사이즈)
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
                    .clickable { 
                        if (hasPermission) {
                            onNextClick()
                        } else {
                            onRequestPermission()
                        }
                    }
            ) {
                Image(
                    painter = painterResource(id = R.drawable.uibuttons),
                    contentDescription = if (hasPermission) "다음 단계로 버튼" else "권한 설정 버튼",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit // 비율 유지
                )
                
                // 버튼 위에 텍스트 오버레이 - 완전 중앙 정렬
                Text(
                    text = if (hasPermission) "다음 단계로" else "권한 설정하기",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center)
                )
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
    // 흰색 배경
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding() // 시스템 바 영역 패딩 추가
            .padding(horizontal = 24.dp)
            .padding(bottom = 80.dp), // 하단 여백 추가
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 상단 여백 (대폭 감소)
        Spacer(modifier = Modifier.height(20.dp))
        
        // 단계 표시 (1단계, 2단계, 3단계) - per2.png 이미지 사용
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp) // 높이 감소
                .padding(horizontal = 40.dp)
        ) {
            // 배경 이미지
            Image(
                painter = painterResource(id = R.drawable.per2),
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
                // 1단계 (완료) - 회색 배경에 회색 텍스트
                Text(
                    text = "1단계",
                    fontSize = 12.sp, // 폰트 크기 감소
                    fontWeight = FontWeight.Normal,
                    color = Color.White,
                    modifier = Modifier.offset(x = (-27).dp) // 오프셋 감소
                )
                
                // 2단계 (활성) - 주황색 배경에 흰색 텍스트
                Text(
                    text = "2단계",
                    fontSize = 12.sp, // 폰트 크기 감소
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                // 3단계 (비활성) - 회색 배경에 회색 텍스트
                Text(
                    text = "3단계",
                    fontSize = 12.sp, // 폰트 크기 감소
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF666666),
                    modifier = Modifier.offset(x = 27.dp) // 오프셋 감소
                )
            }
        }
        
        Spacer(modifier = Modifier.height(30.dp)) // 간격 대폭 감소
        
        // 중앙 일러스트레이션 - Per2Logo.png 사용
        Image(
            painter = painterResource(id = R.drawable.per2logo),
            contentDescription = "접근성 서비스 권한",
            modifier = Modifier
                .size(200.dp) // 크기 감소
                .padding(10.dp), // 패딩 감소
            contentScale = ContentScale.Fit
        )
        
        Spacer(modifier = Modifier.height(30.dp)) // 간격 대폭 감소
        
        // 제목
        Text(
            text = "접근성 서비스 권한",
            fontSize = 22.sp, // 폰트 크기 감소
            fontWeight = FontWeight.Bold,
            color = Color(0xFF333333),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(12.dp)) // 간격 감소
        
        // 설명
        Text(
            text = "키보드 입력을 감지하고 화면을 캡처하기 위해\n접근성 서비스 권한이 필요합니다.",
            fontSize = 14.sp, // 폰트 크기 감소
            color = Color(0xFF666666),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp // 줄 간격 감소
        )
        
        Spacer(modifier = Modifier.height(20.dp)) // 간격 대폭 감소
        
        // 권한 설정 버튼 - UIButtons.png 사용 (가로 길이의 1/2 사이즈)
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
                    .clickable { 
                        if (hasPermission) {
                            onNextClick()
                        } else {
                            onRequestPermission()
                        }
                    }
            ) {
                Image(
                    painter = painterResource(id = R.drawable.uibuttons),
                    contentDescription = if (hasPermission) "다음 단계로 버튼" else "권한 설정 버튼",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit // 비율 유지
                )
                
                // 버튼 위에 텍스트 오버레이 - 완전 중앙 정렬
                Text(
                    text = if (hasPermission) "다음 단계로" else "권한 설정하기",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
    }

/**
 * 설치 완료 화면
 * 모든 권한 설정이 완료된 후 표시되는 화면
 */
@Composable
fun InstallationCompleteScreen(
    currentUser: UserInfo?,
    onStartClick: () -> Unit
) {
    // 흰색 배경
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding() // 시스템 바 영역 패딩 추가
            .padding(horizontal = 24.dp)
            .padding(bottom = 80.dp), // 하단 여백 추가
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 상단 여백 (대폭 감소)
        Spacer(modifier = Modifier.height(20.dp))
        
        // 단계 표시 (1단계, 2단계, 3단계) - per3.png 이미지 사용
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp) // 높이 감소
                .padding(horizontal = 40.dp)
        ) {
            // 배경 이미지
            Image(
                painter = painterResource(id = R.drawable.per3),
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
                // 1단계 (완료) - 회색 배경에 회색 텍스트
                Text(
                    text = "1단계",
                    fontSize = 12.sp, // 폰트 크기 감소
                    fontWeight = FontWeight.Normal,
                    color = Color.White,
                    modifier = Modifier.offset(x = (-27).dp) // 오프셋 감소
                )
                
                // 2단계 (완료) - 회색 배경에 회색 텍스트
                Text(
                    text = "2단계",
                    fontSize = 12.sp, // 폰트 크기 감소
                    fontWeight = FontWeight.Normal,
                    color = Color.White,
                )
                
                // 3단계 (활성) - 주황색 배경에 흰색 텍스트
                Text(
                    text = "3단계",
                    fontSize = 12.sp, // 폰트 크기 감소
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.offset(x = 27.dp) // 오프셋 감소
                )
            }
        }
        
        Spacer(modifier = Modifier.height(30.dp)) // 간격 대폭 감소
        
        // 중앙 일러스트레이션 - Per3Logo.png 사용
        Image(
            painter = painterResource(id = R.drawable.per3logo),
            contentDescription = "설치 완료",
            modifier = Modifier
                .size(200.dp) // 크기 감소
                .padding(10.dp), // 패딩 감소
            contentScale = ContentScale.Fit
        )
        
        Spacer(modifier = Modifier.height(30.dp)) // 간격 대폭 감소
        
        // 제목
        Text(
            text = "설치 완료",
            fontSize = 22.sp, // 폰트 크기 감소
            fontWeight = FontWeight.Bold,
            color = Color(0xFF333333),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(12.dp)) // 간격 감소
        
        // 설명
        Text(
            text = "모든 설정이 완료되었습니다.\n이제 서비스를 시작할 수 있습니다.",
            fontSize = 14.sp, // 폰트 크기 감소
            color = Color(0xFF666666),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp // 줄 간격 감소
        )
        
        Spacer(modifier = Modifier.height(20.dp)) // 간격 대폭 감소
        
        // 시작하기 버튼 - UIButtons.png 사용 (가로 길이의 1/2 사이즈)
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
                    .clickable { onStartClick() }
            ) {
                Image(
                    painter = painterResource(id = R.drawable.uibuttons),
                    contentDescription = "시작하기 버튼",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit // 비율 유지
                )
                
                // 버튼 위에 텍스트 오버레이 - 완전 중앙 정렬
                Text(
                    text = "시작하기",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center)
                )
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))
        
        // 헤더 제목
        Text(
            text = "대화를 열어주는 키 🔑 토키",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF333333),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(40.dp))
        
        // 사용자 프로필
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 프로필 이미지 (회색 원 안에 사람 아이콘)
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        color = Color(0xFFE0E0E0),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "사용자 프로필",
                    modifier = Modifier.size(40.dp),
                    tint = Color(0xFF666666)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 사용자 닉네임
            Text(
                text = currentUser?.nickname ?: "닉네임123",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF333333)
            )
        }
        
        Spacer(modifier = Modifier.height(40.dp))
        
        // 서비스 제어 카드
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isServiceRunning) Color(0xFF4CAF50) else Color(0xFFFF9800)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 상단 텍스트
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isServiceRunning) "서비스 이용 중" else "서비스 중지",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = if (isServiceRunning) "토키 버튼이 활성화되어 있습니다" else "토키를 시작하려면 아래 버튼을 누르세요",
                        fontSize = 14.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
                
                // 하단 버튼
                Button(
                    onClick = if (isServiceRunning) onStopServiceClick else onStartServiceClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isServiceRunning) "비활성화" else "토키 시작하기",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isServiceRunning) Color(0xFF4CAF50) else Color(0xFFFF9800)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(40.dp))
        
        // 권한 상태 섹션
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 권한 상태 헤더
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "권한 상태",
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFF333333)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "권한 상태",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF333333)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 구분선
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFFE0E0E0))
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 다른 앱 위에 그리기 권한
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "다른 앱 위에 그리기 권한",
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFF666666)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = "다른 앱 위에 그리기 권한",
                    fontSize = 16.sp,
                    color = Color(0xFF333333),
                    modifier = Modifier.weight(1f)
                )
                
                if (hasOverlayPermission) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "허용됨",
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF4CAF50)
                        )
                        
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        Text(
                            text = "허용됨",
                            fontSize = 14.sp,
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Button(
                        onClick = onOverlayPermissionClick,
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF9800)
                        )
                    ) {
                        Text(
                            text = "설정",
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                }
            }
            
            // 접근성 서비스 권한
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = "접근성 서비스 권한",
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFF666666)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = "접근성 서비스 권한",
                    fontSize = 16.sp,
                    color = Color(0xFF333333),
                    modifier = Modifier.weight(1f)
                )
                
                if (hasAccessibilityPermission) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "허용됨",
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF4CAF50)
                        )
                        
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        Text(
                            text = "허용됨",
                            fontSize = 14.sp,
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Button(
                        onClick = onAccessibilityPermissionClick,
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF9800)
                        )
                    ) {
                        Text(
                            text = "설정",
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // 로그아웃 버튼 (하단) - uibuttons.png 사용
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp)
                .clickable { onLogoutClick() }
        ) {
            Image(
                painter = painterResource(id = R.drawable.uibuttons),
                contentDescription = "로그아웃 버튼",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                contentScale = ContentScale.FillBounds
            )
            
            // 버튼 텍스트 오버레이
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "로그아웃",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }
    }
}
