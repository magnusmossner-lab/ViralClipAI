@echo off
chcp 65001 >nul 2>&1
title ViralClip AI - Auto-Installer
cls

echo ╔══════════════════════════════════════════════════╗
echo ║       🔥 ViralClip AI - Auto-Installer 🔥       ║
echo ║     YouTube → Virale 9:16 Clips in Sekunden     ║
echo ╚══════════════════════════════════════════════════╝
echo.

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

:: ─── 1. Check Java ──────────────────────────────────────────
echo ━━━ Voraussetzungen prüfen ━━━
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo  ✗ Java nicht gefunden!
    echo    → Installiere JDK 17+: https://adoptium.net/
    pause
    exit /b 1
)
for /f "tokens=3 delims= " %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set JAVA_VER=%%v
echo  ✓ Java: %JAVA_VER%

:: ─── 2. Check Android SDK ───────────────────────────────────
if defined ANDROID_HOME (
    if exist "%ANDROID_HOME%" (
        echo  ✓ Android SDK: %ANDROID_HOME%
        goto :sdk_ok
    )
)
if defined ANDROID_SDK_ROOT (
    if exist "%ANDROID_SDK_ROOT%" (
        set "ANDROID_HOME=%ANDROID_SDK_ROOT%"
        echo  ✓ Android SDK: %ANDROID_HOME%
        goto :sdk_ok
    )
)
if exist "%LOCALAPPDATA%\Android\Sdk" (
    set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
    echo  ✓ Android SDK: %ANDROID_HOME% (auto-detected)
    goto :sdk_ok
)
echo  ✗ Android SDK nicht gefunden!
echo    → Installiere Android Studio: https://developer.android.com/studio
echo    → Oder setze ANDROID_HOME Umgebungsvariable
pause
exit /b 1

:sdk_ok

:: ─── 3. Check ADB ───────────────────────────────────────────
set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
if not exist "%ADB%" (
    where adb >nul 2>&1
    if %errorlevel% equ 0 (
        set "ADB=adb"
    ) else (
        echo  ⚠ ADB nicht gefunden - APK wird gebaut aber nicht installiert
        set "ADB="
    )
)
if defined ADB echo  ✓ ADB gefunden

:: ─── 4. Create local.properties ─────────────────────────────
echo sdk.dir=%ANDROID_HOME:\=/%> local.properties
echo  ✓ local.properties erstellt
echo.

:: ─── 5. Build APK ───────────────────────────────────────────
echo ━━━ APK wird gebaut ━━━
echo  Das kann einige Minuten dauern...
echo.

if exist gradlew.bat (
    call gradlew.bat assembleDebug --no-daemon
) else (
    echo  ✗ gradlew.bat nicht gefunden!
    echo    → Öffne das Projekt in Android Studio und baue es dort
    pause
    exit /b 1
)

set "APK_PATH=app\build\outputs\apk\debug\app-debug.apk"
if not exist "%APK_PATH%" (
    echo.
    echo  ✗ APK nicht gefunden! Build fehlgeschlagen.
    echo    Führe aus: gradlew.bat assembleDebug --stacktrace
    pause
    exit /b 1
)
echo.
echo  ✓ APK erstellt: %APK_PATH%

:: ─── 6. Install on device ───────────────────────────────────
if not defined ADB goto :no_adb
echo.
echo ━━━ App wird installiert ━━━

"%ADB%" devices | findstr /r "device$" >nul 2>&1
if %errorlevel% neq 0 (
    echo  ⚠ Kein Android-Gerät verbunden!
    echo    → USB-Debugging aktivieren und Gerät anschließen
    echo    → Oder APK manuell installieren: %APK_PATH%
    goto :done
)

echo  ✓ Gerät gefunden
echo  → Installiere ViralClip AI...
"%ADB%" install -r "%APK_PATH%"

if %errorlevel% equ 0 (
    echo.
    echo  ✓ ViralClip AI erfolgreich installiert!
    echo.
    set /p "LAUNCH= App jetzt starten? (j/n): "
    if /i "%LAUNCH%"=="j" (
        "%ADB%" shell am start -n com.viralclipai.app/.MainActivity
        echo  ✓ App gestartet!
    )
) else (
    echo  ✗ Installation fehlgeschlagen
    echo    → APK manuell installieren: %APK_PATH%
)
goto :done

:no_adb
echo.
echo ━━━ APK bereit ━━━
echo  → Übertrage %APK_PATH% auf dein Android-Gerät

:done
echo.
echo ╔══════════════════════════════════════════════════╗
echo ║                   🎉 Fertig!                     ║
echo ╠══════════════════════════════════════════════════╣
echo ║  Backend starten:                                ║
echo ║    cd backend                                    ║
echo ║    docker-compose up -d                          ║
echo ║  Oder:                                           ║
echo ║    pip install -r requirements.txt               ║
echo ║    python -m uvicorn app.main:app --port 8000    ║
echo ╚══════════════════════════════════════════════════╝
echo.
pause
