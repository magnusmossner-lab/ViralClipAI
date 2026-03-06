# 🔥 ViralClip AI v2.1

**YouTube → Virale 9:16 Clips in Sekunden**

KI-gestützte App die YouTube-Videos automatisch in virale TikTok/Shorts Clips schneidet.
Mit Self-Healing Engine, ML-Feedback-Loop und direktem Upload zu YouTube & TikTok.

---

## ✨ Features

| Feature | Beschreibung |
|---------|-------------|
| 🎬 Auto-Cut | KI analysiert Audio-Peaks und findet die besten Momente |
| 📐 9:16 Format | Automatische Konvertierung ins Shorts/TikTok-Format |
| 📝 Untertitel | Whisper-basierte deutsche Untertitel (Impact-Style) |
| 🔥 Virality Score | KI bewertet wie viral ein Clip werden kann |
| 🚀 Upload | Direkt zu YouTube Shorts & TikTok hochladen |
| 📊 Analytics | Live-Dashboard mit Views, Likes, Engagement |
| 🧠 ML Feedback Loop | Die KI lernt aus deinen besten Videos und wird besser |
| 🛡️ Self-Healing | Automatische Fehlerbehebung (yt-dlp Update, Disk Cleanup, etc.) |
| 💥 Crash Recovery | Globaler Crash-Handler mit automatischem Neustart |

---

## 🚀 Installation

### Option A: GitHub Actions (empfohlen)

1. Push das Projekt auf GitHub
2. Die APK wird automatisch gebaut unter **Actions → Artifacts**
3. APK herunterladen und auf dem Handy installieren

```bash
git init
git add .
git commit -m "Initial commit"
git remote add origin https://github.com/DEIN_USER/ViralClipAI.git
git push -u origin main
```

### Option B: Lokal bauen

**Voraussetzungen:** JDK 17+, Android SDK

```bash
# Linux/Mac
chmod +x install.sh
./install.sh

# Windows
install.bat
```

---

## ⚙️ Backend starten

```bash
cd backend

# Option 1: Docker (empfohlen)
docker-compose up -d

# Option 2: Direkt
pip install -r requirements.txt
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

---

## 🔑 YouTube & TikTok API Keys

Siehe [SETUP_GUIDE.md](SETUP_GUIDE.md) für eine Schritt-für-Schritt Anleitung.

Trage deine Keys ein in: `app/src/main/assets/oauth_config.json`

---

## 📁 Projektstruktur

```
ViralClipAI/
├── app/                          # Android App (Kotlin + Compose)
│   └── src/main/java/.../
│       ├── MainActivity.kt       # Haupteinstieg (Compose)
│       ├── ViralClipApp.kt       # Application (Crash Handler + WorkManager)
│       ├── viewmodel/            # ViewModels (MVVM)
│       ├── data/                 # API, Models, Repository
│       ├── ui/                   # Screens + Theme
│       ├── auth/                 # OAuth2 Manager
│       ├── upload/               # YouTube/TikTok Upload
│       ├── analytics/            # Dashboard + Sync Worker
│       ├── service/              # Foreground Service
│       └── util/                 # Extensions
├── backend/                      # Python FastAPI Backend
│   ├── app/main.py              # FastAPI Server
│   ├── app/services/pipeline.py # Video Processing Pipeline
│   ├── app/selfheal/engine.py   # Self-Healing Engine
│   ├── app/routers/analytics.py # Analytics API
│   ├── app/ai/trend_analyzer.py # Trend Analysis
│   ├── Dockerfile               # Docker Build
│   └── docker-compose.yml       # Docker Compose
├── .github/workflows/           # GitHub Actions CI/CD
├── install.sh                   # Linux/Mac Auto-Installer
├── install.bat                  # Windows Auto-Installer
└── README.md                    # Diese Datei
```

---

## 🧠 Wie der ML-Feedback-Loop funktioniert

```
┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
│  Video    │───▶│  KI      │───▶│  Upload  │───▶│ Analytics│
│  Input    │    │  Schnitt │    │  YT/TT   │    │  Tracking│
└──────────┘    └──────────┘    └──────────┘    └────┬─────┘
                     ▲                                │
                     │          ┌──────────┐          │
                     └──────────│  ML      │◀─────────┘
                                │  Weights │
                                │  Update  │
                                └──────────┘
```

Die KI analysiert welche Clips die meisten Views/Likes bekommen und passt
automatisch die Gewichtung an (Hook-Stärke, Energie, Gesichter, etc.).

---

## 📄 Lizenz

MIT License - Frei nutzbar.
