#!/bin/bash
set -e
clear

echo "╔══════════════════════════════════════════════════╗"
echo "║       🔥 ViralClip AI - Auto-Installer 🔥       ║"
echo "║     YouTube → Virale 9:16 Clips in Sekunden     ║"
echo "╚══════════════════════════════════════════════════╝"
echo ""

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'
CHECK="${GREEN}✓${NC}"
CROSS="${RED}✗${NC}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# ─── 1. Check prerequisites ─────────────────────────────────
echo "━━━ Voraussetzungen prüfen ━━━"

# Java
if command -v java &>/dev/null; then
    JAVA_VER=$(java -version 2>&1 | head -1 | awk -F'"' '{print $2}')
    echo -e " ${CHECK} Java: ${JAVA_VER}"
else
    echo -e " ${CROSS} Java nicht gefunden!"
    echo "    → Installiere JDK 17+: https://adoptium.net/"
    exit 1
fi

# Android SDK / ANDROID_HOME
if [ -n "$ANDROID_HOME" ] && [ -d "$ANDROID_HOME" ]; then
    echo -e " ${CHECK} Android SDK: ${ANDROID_HOME}"
elif [ -n "$ANDROID_SDK_ROOT" ] && [ -d "$ANDROID_SDK_ROOT" ]; then
    export ANDROID_HOME="$ANDROID_SDK_ROOT"
    echo -e " ${CHECK} Android SDK: ${ANDROID_HOME}"
elif [ -d "$HOME/Android/Sdk" ]; then
    export ANDROID_HOME="$HOME/Android/Sdk"
    echo -e " ${CHECK} Android SDK: ${ANDROID_HOME} (auto-detected)"
elif [ -d "$HOME/Library/Android/sdk" ]; then
    export ANDROID_HOME="$HOME/Library/Android/sdk"
    echo -e " ${CHECK} Android SDK: ${ANDROID_HOME} (auto-detected)"
else
    echo -e " ${CROSS} Android SDK nicht gefunden!"
    echo "    → Installiere Android Studio: https://developer.android.com/studio"
    echo "    → Oder setze ANDROID_HOME Umgebungsvariable"
    exit 1
fi

# ADB
ADB="$ANDROID_HOME/platform-tools/adb"
if [ ! -f "$ADB" ]; then
    if command -v adb &>/dev/null; then
        ADB="adb"
    fi
fi
if command -v "$ADB" &>/dev/null || [ -f "$ADB" ]; then
    echo -e " ${CHECK} ADB gefunden"
else
    echo -e " ${YELLOW}⚠ ADB nicht gefunden - APK wird gebaut aber nicht installiert${NC}"
    ADB=""
fi

echo ""

# ─── 2. Create local.properties ─────────────────────────────
echo "sdk.dir=${ANDROID_HOME}" > local.properties
echo -e " ${CHECK} local.properties erstellt"

# ─── 3. Make gradlew executable / generate if missing ────────
if [ ! -f "gradlew" ]; then
    echo " → Gradle Wrapper wird erstellt..."
    if command -v gradle &>/dev/null; then
        gradle wrapper --gradle-version 8.4 2>/dev/null
    else
        # Download gradle wrapper jar manually
        mkdir -p gradle/wrapper
        WRAPPER_URL="https://services.gradle.org/distributions/gradle-8.4-bin.zip"
        cat > gradle/wrapper/gradle-wrapper.properties << GWPROP
distributionUrl=https\://services.gradle.org/distributions/gradle-8.4-bin.zip
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
zipStorePath=wrapper/dists
zipStoreBase=GRADLE_USER_HOME
GWPROP
        # Download gradlew script
        curl -sL "https://raw.githubusercontent.com/gradle/gradle/v8.4.0/gradlew" -o gradlew 2>/dev/null || {
            # Fallback: write gradlew manually
            cat > gradlew << 'GRADLEW_SCRIPT'
#!/bin/sh
# Gradle Wrapper bootstrap
APP_NAME="Gradle"
CLASSPATH="$APP_BASE_NAME/gradle/wrapper/gradle-wrapper.jar"
exec java -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
GRADLEW_SCRIPT
        }
    fi
fi
chmod +x gradlew
echo -e " ${CHECK} gradlew bereit"

# ─── 4. Build APK ───────────────────────────────────────────
echo ""
echo "━━━ APK wird gebaut ━━━"
echo " Das kann einige Minuten dauern..."
echo ""

./gradlew assembleDebug --no-daemon 2>&1 | while IFS= read -r line; do
    if [[ "$line" == *"BUILD SUCCESSFUL"* ]]; then
        echo -e " ${CHECK} BUILD SUCCESSFUL"
    elif [[ "$line" == *"BUILD FAILED"* ]]; then
        echo -e " ${CROSS} BUILD FAILED"
    elif [[ "$line" == *"> Task"* ]]; then
        echo -ne "\r 🔨 $line                    "
    fi
done

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK_PATH" ]; then
    echo ""
    echo -e " ${CROSS} APK nicht gefunden! Build fehlgeschlagen."
    echo " Führe aus: ./gradlew assembleDebug --stacktrace"
    exit 1
fi

APK_SIZE=$(du -h "$APK_PATH" | awk '{print $1}')
echo ""
echo -e " ${CHECK} APK erstellt: ${APK_PATH} (${APK_SIZE})"

# ─── 5. Install on device ───────────────────────────────────
if [ -n "$ADB" ]; then
    echo ""
    echo "━━━ App wird installiert ━━━"

    # Check for connected device
    DEVICES=$("$ADB" devices 2>/dev/null | grep -w "device" | grep -v "List")
    if [ -z "$DEVICES" ]; then
        echo -e " ${YELLOW}⚠ Kein Android-Gerät verbunden!${NC}"
        echo "    → USB-Debugging aktivieren und Gerät anschließen"
        echo "    → Oder APK manuell installieren: ${APK_PATH}"
    else
        DEVICE_NAME=$("$ADB" devices -l 2>/dev/null | grep "device " | head -1 | awk '{print $4}' | sed 's/model://')
        echo -e " ${CHECK} Gerät gefunden: ${DEVICE_NAME:-Unbekannt}"
        echo " → Installiere ViralClip AI..."

        "$ADB" install -r "$APK_PATH" 2>&1

        if [ $? -eq 0 ]; then
            echo ""
            echo -e " ${CHECK} ${GREEN}ViralClip AI erfolgreich installiert!${NC}"

            # Ask to launch
            echo ""
            read -p " App jetzt starten? (j/n): " -n 1 -r
            echo ""
            if [[ $REPLY =~ ^[Jj]$ ]]; then
                "$ADB" shell am start -n com.viralclipai.app/.MainActivity 2>/dev/null
                echo -e " ${CHECK} App gestartet!"
            fi
        else
            echo -e " ${CROSS} Installation fehlgeschlagen"
            echo "    → APK manuell installieren: ${APK_PATH}"
        fi
    fi
else
    echo ""
    echo "━━━ APK bereit ━━━"
    echo " → Übertrage ${APK_PATH} auf dein Android-Gerät"
fi

# ─── 6. Backend setup hint ───────────────────────────────────
echo ""
echo "╔══════════════════════════════════════════════════╗"
echo "║                   🎉 Fertig!                     ║"
echo "╠══════════════════════════════════════════════════╣"
echo "║  Backend starten:                                ║"
echo "║    cd backend                                    ║"
echo "║    docker-compose up -d                          ║"
echo "║  Oder:                                           ║"
echo "║    pip install -r requirements.txt               ║"
echo "║    ./start.sh                                    ║"
echo "╚══════════════════════════════════════════════════╝"
