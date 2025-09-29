@echo off
echo 키 해시 생성 중...
echo.

REM Debug 키스토어에서 키 해시 생성
keytool -exportcert -alias androiddebugkey -keystore "%USERPROFILE%\.android\debug.keystore" -storepass android -keypass android | openssl sha1 -binary | openssl base64

echo.
echo 위의 키 해시를 카카오 개발자 콘솔에 등록하세요!
echo.
pause
