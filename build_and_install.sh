#!/bin/bash
set -e

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}"
echo "╔══════════════════════════════════════════════╗"
echo "║     ViralClipAI - Auto Build & Install       ║"
echo "║                  v2.0                         ║"
echo "╚══════════════════════════════════════════════╝"
echo -e "${NC}"

# Check Java
if ! command -v java &> /dev/null; then
    echo -e "${RED}[!] Java nicht gefunden!${NC}"
    echo "Installiere mit:"
    if [[ "$OSTYPE" == "darwin"* ]]; then
        echo "  brew install openjdk@17"
    else
        echo "  sudo apt install openjdk-17-jdk   (Ubuntu/Debian)"
        echo "  sudo dnf install java-17-openjdk   (Fedora)"
    fi
    exit 1
fi
echo -e "${GREEN}[OK]${NC} Java gefunden: $(java -version 2>&1 | head -1)"

# Check/Setup ANDROID_HOME
if [ -z "$ANDROID_HOME" ]; then
    POSSIBLE_PATHS=(
        "$HOME/Android/Sdk"
        "$HOME/Library/Android/sdk"
        "/usr/local/lib/android/sdk"
    )
    for p in "${POSSIBLE_PATHS[@]}"; do
        if [ -d "$p" ]; then
            export ANDROID_HOME="$p"
            break
        fi
    done
fi

if [ -z "$ANDROID_HOME" ]; then
    echo -e "${YELLOW}[!] Android SDK nicht gefunden. Installiere automatisch...${NC}"
    
    SDK_DIR="$HOME/Android/Sdk"
    mkdir -p "$SDK_DIR/cmdline-tools"
    
    # Download command line tools
    if [[ "$OSTYPE" == "darwin"* ]]; then
        TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip"
    else
        TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
    fi
    
    echo "[*] Lade Android SDK herunter..."
    curl -L -o /tmp/cmdline-tools.zip "$TOOLS_URL"
    unzip -q /tmp/cmdline-tools.zip -d "$SDK_DIR/cmdline-tools/"
    mv "$SDK_DIR/cmdline-tools/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
    rm /tmp/cmdline-tools.zip
    
    export ANDROID_HOME="$SDK_DIR"
    export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
    
    # Install required packages
    echo "[*] Installiere SDK-Pakete..."
    yes | sdkmanager --licenses > /dev/null 2>&1
    sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"
    
    echo -e "${GREEN}[OK]${NC} Android SDK installiert: $SDK_DIR"
    echo ""
    echo "Fuege dies zu deiner ~/.bashrc oder ~/.zshrc hinzu:"
    echo "  export ANDROID_HOME=$SDK_DIR"
    echo ""
fi

echo -e "${GREEN}[OK]${NC} ANDROID_HOME: $ANDROID_HOME"

# Make gradlew executable
chmod +x gradlew

# Build
echo ""
echo -e "${BLUE}[*] Starte Build...${NC}"
echo "================================================"
./gradlew assembleDebug

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

if [ ! -f "$APK_PATH" ]; then
    echo -e "${RED}[FEHLER] APK nicht gefunden!${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}[OK] APK erfolgreich gebaut!${NC}"
echo "    $APK_PATH"
echo ""

# Try to install on device
ADB="$ANDROID_HOME/platform-tools/adb"
if [ -x "$ADB" ]; then
    DEVICES=$("$ADB" devices | grep -c "device$" || true)
    if [ "$DEVICES" -gt 0 ]; then
        echo -e "${BLUE}[*] Installiere auf Geraet...${NC}"
        "$ADB" install -r "$APK_PATH"
        echo ""
        echo -e "${GREEN}╔══════════════════════════════════════════════╗${NC}"
        echo -e "${GREEN}║  ViralClipAI wurde erfolgreich installiert!  ║${NC}"
        echo -e "${GREEN}╚══════════════════════════════════════════════╝${NC}"
        "$ADB" shell am start -n com.viralclipai.app/.MainActivity
    else
        echo -e "${YELLOW}[!] Kein Android-Geraet gefunden.${NC}"
        echo "    Verbinde dein Handy per USB mit aktiviertem USB-Debugging"
        echo "    und fuehre aus: $ADB install -r $APK_PATH"
        echo ""
        echo "    Oder kopiere die APK manuell auf dein Handy:"
        echo "    $APK_PATH"
    fi
else
    echo "Die APK liegt hier: $APK_PATH"
    echo "Kopiere sie auf dein Handy und tippe sie an."
fi
