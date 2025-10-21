@echo off
echo ========================================
echo       릴리즈 키스토어 생성
echo ========================================
echo.

REM 키스토어 정보 설정
set KEYSTORE_NAME=mindvrFloatingApp.keystore
set KEY_ALIAS=mindvrFloatingApp
set VALIDITY_DAYS=36500

echo 키스토어 생성 정보:
echo - 파일명: %KEYSTORE_NAME%
echo - 별칭: %KEY_ALIAS%
echo - 유효기간: %VALIDITY_DAYS%일 (약 100년)
echo.

echo 키스토어를 생성합니다...
echo 패스워드를 입력하세요 (최소 6자 이상):
echo.

keytool -genkey -v -keystore %KEYSTORE_NAME% -alias %KEY_ALIAS% -keyalg RSA -keysize 2048 -validity %VALIDITY_DAYS%

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo 키스토어가 성공적으로 생성되었습니다!
    echo 파일: %KEYSTORE_NAME%
    echo.
    echo 다음 단계:
    echo 1. get_release_hash.bat 실행하여 해시코드 생성
    echo 2. 카카오 개발자 콘솔에 해시코드 등록
    echo ========================================
) else (
    echo.
    echo 키스토어 생성에 실패했습니다.
)

echo.
pause
