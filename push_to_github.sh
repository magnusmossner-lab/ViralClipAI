#!/bin/bash
echo "================================================"
echo "  ViralClipAI v2.1 - GitHub Auto-Push"
echo "================================================"
echo ""

# Check if git is installed
if ! command -v git &> /dev/null; then
    echo "❌ Git ist nicht installiert!"
    echo "   Installiere es: https://git-scm.com/downloads"
    exit 1
fi

# Find the ViralClipAI folder (same directory as this script)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "📁 Arbeite in: $SCRIPT_DIR"
echo ""

# Check if we're already in a git repo
if [ -d ".git" ]; then
    echo "🔄 Git-Repository existiert bereits, aktualisiere..."
    git add -A
    git commit -m "feat: ViralClipAI v2.1 - Complete self-healing AI system"
    git push origin main --force
else
    echo "🆕 Erstelle neues Git-Repository..."
    git init
    git add -A
    git commit -m "feat: ViralClipAI v2.1 - Complete self-healing AI system with YouTube/TikTok upload & analytics"
    git branch -M main
    git remote add origin https://github.com/magnusmossner-lab/ViralClipAI.git
    git push -u origin main --force
fi

echo ""
echo "================================================"
echo "  ✅ FERTIG! Code ist auf GitHub!"
echo "  🔗 https://github.com/magnusmossner-lab/ViralClipAI"
echo "  ⏳ GitHub Actions baut jetzt die APK..."
echo "  📦 Check: Actions → build-apk → Artifacts"
echo "================================================"
