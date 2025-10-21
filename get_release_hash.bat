@echo off
echo ========================================
echo       릴리즈 카카오 해시코드 생성
echo ========================================
echo.

REM 키스토어 정보
set KEYSTORE_NAME=mindvrFloatingApp.keystore
set KEY_ALIAS=mindvrFloatingApp

echo 현재 설정:
echo - 키스토어: %KEYSTORE_NAME%
echo - 별칭: %KEY_ALIAS%
echo.

REM 키스토어 파일 존재 확인
if not exist "%KEYSTORE_NAME%" (
    echo [오류] 키스토어 파일을 찾을 수 없습니다: %KEYSTORE_NAME%
    echo.
    echo 먼저 create_release_keystore.bat을 실행하여 키스토어를 생성하세요.
    echo.
    pause
    exit /b 1
)

echo 키스토어 파일을 찾았습니다.
echo.
echo 해시코드를 생성합니다...
echo 패스워드를 입력하세요:
echo.

keytool -exportcert -alias "%KEY_ALIAS%" -keystore "%KEYSTORE_NAME%" | openssl sha1 -binary | openssl base64

echo.
echo ========================================
echo 생성된 해시코드를 카카오 개발자 콘솔에 등록하세요:
echo.
echo 1. https://developers.kakao.com/console/app 접속
echo 2. 앱 선택
echo 3. 플랫폼 설정 > Android
echo 4. 키 해시에 위의 해시코드 추가
echo.
echo 주의: 디버그 해시코드와 릴리즈 해시코드 모두 등록해야 합니다!
echo ========================================
echo.
pause
