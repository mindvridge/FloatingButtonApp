@echo off
echo ========================================
echo       모든 카카오 해시코드 생성
echo ========================================
echo.

echo 1. 디버그 해시코드 생성:
echo ========================================
keytool -exportcert -alias androiddebugkey -keystore "%USERPROFILE%\.android\debug.keystore" | openssl sha1 -binary | openssl base64

echo.
echo.
echo 2. 릴리즈 해시코드 생성:
echo ========================================
set KEYSTORE_NAME=mindvrFloatingApp.keystore
set KEY_ALIAS=mindvrFloatingApp

if not exist "%KEYSTORE_NAME%" (
    echo [오류] 릴리즈 키스토어 파일을 찾을 수 없습니다: %KEYSTORE_NAME%
    echo 먼저 create_release_keystore.bat을 실행하세요.
    echo.
    pause
    exit /b 1
)

echo 패스워드를 입력하세요:
keytool -exportcert -alias "%KEY_ALIAS%" -keystore "%KEYSTORE_NAME%" | openssl sha1 -binary | openssl base64

echo.
echo ========================================
echo 카카오 개발자 콘솔 등록 방법:
echo.
echo 1. https://developers.kakao.com/console/app 접속
echo 2. 앱 선택
echo 3. 플랫폼 설정 > Android
echo 4. 키 해시에 위의 두 해시코드 모두 추가
echo.
echo - 첫 번째: 디버그 해시코드 (개발용)
echo - 두 번째: 릴리즈 해시코드 (배포용)
echo ========================================
echo.
pause
