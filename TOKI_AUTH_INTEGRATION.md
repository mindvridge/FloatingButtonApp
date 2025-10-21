# Toki Auth & Gemini API 통합 가이드

## 📋 개요

Toki Auth 서버와 Gemini API 서버를 연동한 로그인 시스템입니다.

### 🏗️ 아키텍처

```
1️⃣ 로그인
   안드로이드 앱 → Toki Auth (Google/Kakao 인증)
   ← JWT 토큰 (access + refresh)

2️⃣ Gemini API 호출
   안드로이드 앱 → Gemini API (Authorization: Bearer {token})
   
3️⃣ 토큰 검증
   Gemini API → Toki Auth (/api/v1/auth/me)
   ← 사용자 정보
   
4️⃣ 응답
   Gemini API → 안드로이드 앱 (답변)
```

---

## 📁 생성된 파일

### 1. API 인터페이스

#### `app/src/main/java/com/mv/toki/api/AuthApi.kt`
- Toki Auth 서버 API 정의
- 엔드포인트:
  - `POST /api/v1/auth/google/callback` - 구글 로그인
  - `POST /api/v1/auth/kakao/callback` - 카카오 로그인
  - `POST /api/v1/auth/refresh` - 토큰 갱신
  - `GET /api/v1/auth/me` - 사용자 프로필 조회

#### `app/src/main/java/com/mv/toki/api/GeminiApi.kt`
- Gemini API 서버 API 정의
- 엔드포인트:
  - `POST /v1/reply` - AI 답변 생성 (Authorization 헤더 자동 추가)
  - `POST /api/chat` - 채팅 메시지 전송
  - `GET /api/history` - 채팅 히스토리 조회
  - `POST /api/analyze` - 이미지 분석

### 2. 인증 관리

#### `app/src/main/java/com/mv/toki/auth/TokenManager.kt`
- JWT 토큰 저장 및 관리
- EncryptedSharedPreferences 사용 (보안)
- 주요 기능:
  - `saveTokens()` - 액세스/리프레시 토큰 저장
  - `getAccessToken()` - 액세스 토큰 조회
  - `getAuthorizationHeader()` - "Bearer {token}" 형식 반환
  - `isTokenExpired()` - 토큰 만료 여부 확인
  - `hasValidToken()` - 유효한 토큰 존재 여부
  - `clearTokens()` - 로그아웃 시 토큰 삭제

#### `app/src/main/java/com/mv/toki/auth/AuthInterceptor.kt`
- OkHttp 인터셉터
- 모든 Gemini API 요청에 Authorization 헤더 자동 추가
- 토큰 만료 시 자동 갱신
- 401 Unauthorized 응답 시 재시도

### 3. API 클라이언트

#### `app/src/main/java/com/mv/floatingbuttonapp/api/ApiClient.kt` (수정)
- 두 개의 Retrofit 인스턴스 관리:
  - `authApi` - Toki Auth (인증 불필요)
  - `geminiApi` - Gemini API (AuthInterceptor 포함)
- 초기화: `ApiClient.init(context)` 필요

---

## 🔄 로그인 플로우

### 카카오 로그인

```kotlin
// 1. 사용자가 카카오 로그인 버튼 클릭
MainActivity.loginWithKakao()

// 2. 카카오 SDK로 로그인
KakaoLoginManager.loginWithKakao()
  ├── UserApiClient.loginWithKakaoTalk() or loginWithKakaoAccount()
  └── 카카오 액세스 토큰 발급

// 3. 카카오 사용자 정보 조회
UserApiClient.instance.me()

// 4. Toki Auth 서버에 로그인 요청
ApiClient.authApi.loginWithKakao(KakaoAuthRequest)
  └── POST https://toki-auth-.../api/v1/auth/kakao/callback

// 5. JWT 토큰 수신 및 저장
TokenManager.saveTokens(accessToken, refreshToken, expiresIn)

// 6. 로컬 사용자 정보 저장
SharedPreferences에 사용자 정보 저장

// 7. 권한 화면으로 이동
currentScreen = AppScreen.PERMISSION_OVERLAY
```

### 구글 로그인

```kotlin
// 1. 사용자가 구글 로그인 버튼 클릭
MainActivity.loginWithGoogle()

// 2. Google Sign-In Intent 실행
GoogleLoginManager.getSignInIntent()

// 3. 구글 계정 선택 및 ID 토큰 발급
GoogleSignIn.getSignedInAccountFromIntent()

// 4. Firebase Auth로 인증
FirebaseAuth.signInWithCredential(GoogleAuthProvider)

// 5. Toki Auth 서버에 로그인 요청
ApiClient.authApi.loginWithGoogle(GoogleAuthRequest)
  └── POST https://toki-auth-.../api/v1/auth/google/callback

// 6. JWT 토큰 수신 및 저장
TokenManager.saveTokens(accessToken, refreshToken, expiresIn)

// 7. 로컬 사용자 정보 저장
SharedPreferences에 사용자 정보 저장

// 8. 권한 화면으로 이동
currentScreen = AppScreen.PERMISSION_OVERLAY
```

### 자동 로그인

```kotlin
// 1. 앱 실행 시
MainActivity.onCreate()
  └── checkAutoLogin()

// 2. JWT 토큰 존재 여부 확인
TokenManager.hasValidToken()
  ├── 토큰 없음 → 로그인 화면
  └── 토큰 있음 → 자동 로그인 시도

// 3. 소셜 로그인 자동 로그인
KakaoLoginManager.checkAutoLogin() or GoogleLoginManager.checkAutoLogin()
  ├── 성공 → 권한 화면으로 이동
  └── 실패 → JWT 토큰 삭제 → 로그인 화면
```

---

## 🔐 인증 처리

### Gemini API 호출 시

```kotlin
// 1. 사용자가 "답변 추천받기" 버튼 클릭
generateResponses()

// 2. API 요청 생성
val request = ReplyRequest(대상자, 답변길이, 대화내용, 추가지침)

// 3. Gemini API 호출 (AuthInterceptor가 자동으로 작동)
ApiClient.geminiApi.getReplies(request)

// 4. AuthInterceptor 동작
AuthInterceptor.intercept()
  ├── 토큰 만료 확인
  │   ├── 만료되지 않음 → Authorization 헤더 추가
  │   └── 만료됨 → 토큰 갱신 후 헤더 추가
  └── 요청 전송

// 5. 서버 응답
  ├── 200 OK → 답변 반환
  └── 401 Unauthorized → 토큰 갱신 후 재시도
```

### 토큰 갱신

```kotlin
// AuthInterceptor에서 자동으로 처리
AuthInterceptor.refreshAccessToken()
  ├── 리프레시 토큰으로 새 토큰 요청
  ├── POST /api/v1/auth/refresh
  ├── 새 토큰 저장
  └── Authorization 헤더에 새 토큰 추가
```

---

## 📊 데이터 클래스

### 요청 데이터

```kotlin
// 카카오 로그인
data class KakaoAuthRequest(
    val accessToken: String,
    val userId: Long,
    val email: String?,
    val nickname: String?,
    val profileImageUrl: String?
)

// 구글 로그인
data class GoogleAuthRequest(
    val idToken: String,
    val email: String?,
    val name: String?,
    val picture: String?
)

// 토큰 갱신
data class RefreshTokenRequest(
    val refreshToken: String
)

// AI 답변 요청
data class ReplyRequest(
    val 대상자: String,
    val 답변길이: String,
    val 대화내용: String,
    val 추가지침: String,
    val 모델: String = "gemini-2.5-flash"
)
```

### 응답 데이터

```kotlin
// 토큰 응답
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val tokenType: String = "Bearer"
)

// 사용자 프로필
data class UserProfileResponse(
    val id: String,
    val provider: String,
    val providerId: String,
    val email: String?,
    val name: String?,
    val nickname: String?,
    val picture: String?,
    val createdAt: String?,
    val updatedAt: String?
)

// AI 답변 응답
data class ReplyResponse(
    val model: String?,
    val count: Int?,
    val answers: List<Answer>?,
    val message: String?,
    val error: String?
)
```

---

## 🧪 테스트 방법

### 1. 카카오 로그인 테스트

```kotlin
1. 앱 실행
2. "카카오 로그인" 버튼 클릭
3. 카카오톡 앱 또는 웹뷰로 로그인
4. Logcat 확인:
   - "Toki Auth 서버 로그인 요청"
   - "카카오 로그인 완료 (서버 연동 성공)"
   - "JWT 토큰 저장 완료"
```

### 2. 구글 로그인 테스트

```kotlin
1. 앱 실행
2. "구글 로그인" 버튼 클릭 (구현 필요)
3. 구글 계정 선택
4. Logcat 확인:
   - "Toki Auth 서버 로그인 요청"
   - "구글 로그인 완료 (서버 연동 성공)"
   - "JWT 토큰 저장 완료"
```

### 3. 자동 로그인 테스트

```kotlin
1. 로그인 완료 후 앱 종료
2. 앱 재실행
3. Logcat 확인:
   - "JWT 토큰 존재 - 자동 로그인 시도"
   - "카카오 자동 로그인 성공" or "구글 자동 로그인 성공"
   - 권한 화면 또는 서비스 화면으로 자동 이동
```

### 4. AI 답변 생성 테스트

```kotlin
1. 로그인 완료
2. OCR 텍스트 인식
3. "답변 추천받기" 버튼 클릭
4. Logcat 확인:
   - "Authorization 헤더 추가"
   - "API 호출 시작"
   - "API 호출 완료"
   - 답변 표시
```

### 5. 토큰 갱신 테스트

```kotlin
1. 로그인 후 토큰 만료 시간까지 대기 (또는 수동으로 만료 시간 조작)
2. AI 답변 생성 요청
3. Logcat 확인:
   - "토큰 만료 - 갱신 시도"
   - "토큰 갱신 성공"
   - "갱신된 토큰으로 재시도"
```

---

## 🔍 디버깅

### Logcat 필터

```
태그 필터:
- API_DEBUG: API 요청/응답 로그
- TokenManager: 토큰 관리 로그
- AuthInterceptor: 인증 인터셉터 로그
- KakaoLoginManager: 카카오 로그인 로그
- GoogleLoginManager: 구글 로그인 로그
- MainActivity: 메인 액티비티 로그
```

### 토큰 정보 확인

```kotlin
val tokenManager = TokenManager.getInstance(context)
tokenManager.logTokenInfo()

// 출력:
// === 토큰 정보 ===
// 액세스 토큰 존재: true
// 리프레시 토큰 존재: true
// 토큰 만료까지 남은 시간: 3600초
// 토큰 유효 여부: true
```

---

## ⚠️ 주의사항

1. **ApiClient 초기화 필수**
   - `FloatingButtonApplication.onCreate()`에서 `ApiClient.init(this)` 호출
   - Context가 없으면 AuthInterceptor가 작동하지 않음

2. **토큰 보안**
   - EncryptedSharedPreferences 사용
   - 프로덕션 환경에서는 추가 보안 조치 필요

3. **네트워크 오류 처리**
   - 토큰 갱신 실패 시 자동 로그아웃
   - 사용자에게 재로그인 유도

4. **서버 URL**
   - 개발/프로덕션 환경별로 BASE_URL 변경 필요
   - BuildConfig 사용 권장

---

## 📝 변경 내역

### 신규 파일
- ✅ `api/AuthApi.kt` - Toki Auth API 인터페이스
- ✅ `api/GeminiApi.kt` - Gemini API 인터페이스  
- ✅ `auth/TokenManager.kt` - JWT 토큰 관리
- ✅ `auth/AuthInterceptor.kt` - 자동 인증 헤더 추가

### 수정 파일
- ✅ `api/ApiClient.kt` - Toki Auth/Gemini API 분리
- ✅ `KakaoLoginManager.kt` - 서버 연동 추가
- ✅ `GoogleLoginManager.kt` - 서버 연동 추가
- ✅ `MainActivity.kt` - JWT 토큰 확인 추가
- ✅ `OcrBottomSheetActivity.kt` - geminiApi 사용
- ✅ `FloatingButtonApplication.kt` - ApiClient 초기화
- ✅ `build.gradle.kts` - security-crypto 의존성 추가

---

## 🚀 다음 단계

1. **서버 API 구현 확인**
   - Toki Auth 엔드포인트 동작 확인
   - Gemini API 토큰 검증 로직 확인

2. **UI 개선**
   - 로그인 중 로딩 표시
   - 토큰 갱신 중 로딩 표시
   - 오류 메시지 개선

3. **기능 추가**
   - 사용자 프로필 조회 기능
   - 로그인 이력 관리
   - 다중 디바이스 지원

4. **보안 강화**
   - Certificate Pinning
   - Biometric 인증
   - 세션 타임아웃

---

## 📞 문의

구현 관련 문의사항이 있으시면 개발팀에 연락주세요.

Copyright © 2025 (주)마인드브이알 | 마브 All rights reserved.

