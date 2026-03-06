@echo off
title ViralClipAI - Automatischer Build & Install
color 0A
echo.
echo  ╔══════════════════════════════════════════════╗
echo  ║     ViralClipAI - Auto Build ^& Install      ║
echo  ║                  v2.0                         ║
echo  ╚══════════════════════════════════════════════╝
echo.

:: Check for Java
where java >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [!] Java nicht gefunden. Installiere JDK 17...
    echo [*] Oeffne Download-Seite...
    start https://adoptium.net/de/temurin/releases/?version=17
    echo.
    echo Bitte installiere Java 17 und starte dieses Skript erneut.
    pause
    exit /b 1
)
echo [OK] Java gefunden

:: Check for ANDROID_HOME
if "%ANDROID_HOME%"=="" (
    if exist "%LOCALAPPDATA%\Android\Sdk" (
        set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
        echo [OK] Android SDK gefunden: %LOCALAPPDATA%\Android\Sdk
    ) else (
        echo [!] Android SDK nicht gefunden.
        echo [*] Option 1: Installiere Android Studio: https://developer.android.com/studio
        echo [*] Option 2: Oder nur die Command Line Tools:
        echo              https://developer.android.com/studio#command-line-tools-only
        echo.
        echo Nach Installation: Setze ANDROID_HOME Umgebungsvariable
        echo und starte dieses Skript erneut.
        pause
        exit /b 1
    )
)
echo [OK] ANDROID_HOME: %ANDROID_HOME%

:: Build
echo.
echo [*] Starte Build...
echo ================================================
call gradlew.bat assembleDebug
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [FEHLER] Build fehlgeschlagen!
    echo Pruefe die Fehlermeldungen oben.
    pause
    exit /b 1
)

echo.
echo [OK] APK erfolgreich gebaut!
echo.

:: Find the APK
set "APK_PATH=app\build\outputs\apk\debug\app-debug.apk"
if not exist "%APK_PATH%" (
    echo [!] APK nicht gefunden unter %APK_PATH%
    pause
    exit /b 1
)

echo [*] APK: %APK_PATH%
echo.

:: Check for connected device
echo [*] Suche verbundenes Android-Geraet...
"%ANDROID_HOME%\platform-tools\adb.exe" devices | findstr /R "device$" >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [!] Kein Android-Geraet gefunden!
    echo     1. Verbinde dein Handy per USB
    echo     2. Aktiviere USB-Debugging in den Entwickleroptionen
    echo     3. Bestaetie die Verbindung auf deinem Handy
    echo.
    echo [*] Die APK liegt hier: %APK_PATH%
    echo     Du kannst sie auch manuell auf dein Handy kopieren.
    echo.
    set /p WAIT="Geraet verbunden? Druecke ENTER zum Installieren oder STRG+C zum Abbrechen: "
)

:: Install
echo [*] Installiere auf Geraet...
"%ANDROID_HOME%\platform-tools\adb.exe" install -r "%APK_PATH%"
if %ERRORLEVEL% EQU 0 (
    echo.
    echo  ╔══════════════════════════════════════════════╗
    echo  ║   ViralClipAI wurde erfolgreich installiert! ║
    echo  ║   Oeffne die App auf deinem Handy.           ║
    echo  ╚══════════════════════════════════════════════╝
    echo.
    :: Launch the app
    "%ANDROID_HOME%\platform-tools\adb.exe" shell am start -n com.viralclipai.app/.MainActivity
) else (
    echo [FEHLER] Installation fehlgeschlagen.
    echo Die APK liegt hier: %APK_PATH%
    echo Kopiere sie manuell auf dein Handy.
)
echo.
pause
