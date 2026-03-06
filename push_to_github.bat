@echo off
echo ================================================
echo   ViralClipAI v2.1 - GitHub Auto-Push
echo ================================================
echo.

where git >nul 2>nul
if %errorlevel% neq 0 (
    echo Git ist nicht installiert!
    echo Installiere es: https://git-scm.com/downloads
    pause
    exit /b 1
)

cd /d "%~dp0"
echo Arbeite in: %cd%
echo.

if exist ".git" (
    echo Git-Repository existiert bereits, aktualisiere...
    git add -A
    git commit -m "feat: ViralClipAI v2.1 - Complete self-healing AI system"
    git push origin main --force
) else (
    echo Erstelle neues Git-Repository...
    git init
    git add -A
    git commit -m "feat: ViralClipAI v2.1 - Complete self-healing AI system"
    git branch -M main
    git remote add origin https://github.com/magnusmossner-lab/ViralClipAI.git
    git push -u origin main --force
)

echo.
echo ================================================
echo   FERTIG! Code ist auf GitHub!
echo   https://github.com/magnusmossner-lab/ViralClipAI
echo   GitHub Actions baut jetzt die APK...
echo ================================================
pause
