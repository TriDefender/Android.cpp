@echo off
REM Generate a release keystore for signing the APK.
REM Run this once, then fill in keystore.properties with the password you choose.

set KEYSTORE=release.keystore
set ALIAS=release
set VALIDITY=10000

if exist "%KEYSTORE%" (
    echo Keystore %KEYSTORE% already exists. Delete it first if you want to regenerate.
    exit /b 1
)

echo Generating keystore: %KEYSTORE%
echo Key alias: %ALIAS%
echo Validity: %VALIDITY% days
echo.

keytool -genkeypair ^
    -v ^
    -keystore "%KEYSTORE%" ^
    -alias "%ALIAS%" ^
    -keyalg RSA ^
    -keysize 2048 ^
    -validity %VALIDITY%

echo.
echo Keystore created. Now:
echo   1. Copy keystore.properties.example to keystore.properties
echo   2. Fill in the password you just set
echo.
echo   copy keystore.properties.example keystore.properties
echo   REM Edit keystore.properties with your editor
