# 🔥 ViralClip AI v2.0 - Setup Guide

## Neue Features in v2.0
- ✅ **Direkter Upload zu YouTube Shorts & TikTok**
- ✅ **Analytics Dashboard** mit Performance-Daten
- ✅ **KI-Feedback-Loop** - die App lernt aus deinen Videos
- ✅ **Automatische Installation** via install.sh / install.bat

---

## 🚀 Schnell-Installation

### Linux / Mac
```bash
unzip ViralClipAI_v2.zip
cd ViralClipAI
chmod +x install.sh
./install.sh
```

### Windows
```
Entpacke ViralClipAI_v2.zip
Doppelklick auf install.bat
```

Das Skript:
1. Prüft Java, Android SDK, ADB
2. Baut die APK automatisch
3. Installiert die App auf deinem verbundenen Gerät

---

## 🔑 YouTube API einrichten

1. Gehe zu [Google Cloud Console](https://console.cloud.google.com/)
2. Erstelle ein neues Projekt "ViralClipAI"
3. Aktiviere APIs:
   - **YouTube Data API v3**
   - **YouTube Analytics API**
4. Erstelle OAuth 2.0 Credentials:
   - Typ: **Android**
   - Package Name: `com.viralclipai.app`
   - SHA-1: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`
5. Trage Client ID + Secret in `app/src/main/assets/oauth_config.json` ein

---

## 🔑 TikTok API einrichten

1. Gehe zu [TikTok for Developers](https://developers.tiktok.com/)
2. Erstelle eine App
3. Aktiviere Scopes:
   - `user.info.basic`
   - `video.publish`
   - `video.upload`
   - `video.list`
4. Setze Redirect URI: `com.viralclipai.app://oauth/callback`
5. Trage Client Key + Secret in `oauth_config.json` ein

---

## 🧠 Wie der KI-Feedback-Loop funktioniert

```
┌─────────────┐     ┌──────────────┐     ┌──────────────┐
│  Clip wird   │────▶│  Upload zu   │────▶│ Performance  │
│  generiert   │     │  YT / TikTok │     │  wird getracked │
└─────────────┘     └──────────────┘     └──────┬───────┘
       ▲                                         │
       │                                         ▼
┌──────┴───────┐                        ┌──────────────┐
│  Verbesserte │◀───────────────────────│ ML analysiert │
│  Gewichtung  │   Weights werden       │ was viral ging│
└──────────────┘   angepasst            └──────────────┘
```

### Was die KI lernt:
- **Hook-Stärke**: Wie wichtig sind die ersten 3 Sekunden?
- **Energy/Pacing**: Wie schnell sollte geschnitten werden?
- **Face-Presence**: Wie wichtig sind Gesichter im Clip?
- **Topic-Relevance**: Welche Themen performen besser?
- **Audio-Qualität**: Wie stark beeinflusst Audio die Performance?
- **Visual-Qualität**: Wie wichtig ist die Bildqualität?

Die Gewichtungen starten mit Standardwerten und werden mit jedem
hochgeladenen Video angepasst. Nach ~50 Videos ist das Modell
gut kalibriert auf deinen Content-Stil.

---

## 📊 Analytics Dashboard

Das Dashboard zeigt:
- 👁️ **Gesamt-Views** über alle Plattformen
- ❤️ **Gesamt-Likes**
- 🔥 **Engagement-Rate**
- 🏆 **AI Performance Score** (0-100)
- 🧠 **KI-Empfehlungen** pro Kategorie
- 🎬 **Video-Übersicht** mit Einzelstatistiken

Der Analytics-Sync läuft automatisch alle 6 Stunden im Hintergrund.

---

## 🐳 Backend starten

```bash
cd backend
docker-compose up -d
```

Oder ohne Docker:
```bash
cd backend
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

---

## 📁 Projektstruktur

```
ViralClipAI/
├── app/                           # Android App
│   └── src/main/
│       ├── java/.../
│       │   ├── auth/              # 🔑 OAuth2 Manager
│       │   │   └── OAuthManager.kt
│       │   ├── upload/            # 🚀 Upload System
│       │   │   ├── YouTubeUploader.kt
│       │   │   ├── TikTokUploader.kt
│       │   │   ├── UploadManager.kt
│       │   │   ├── UploadViewModel.kt
│       │   │   └── UploadActivity.kt
│       │   ├── analytics/         # 📊 Analytics & Feedback
│       │   │   ├── AnalyticsClient.kt
│       │   │   ├── AnalyticsViewModel.kt
│       │   │   ├── AnalyticsDashboardActivity.kt
│       │   │   └── AnalyticsSyncWorker.kt
│       │   └── ...
│       ├── assets/
│       │   └── oauth_config.json  # 🔑 API Keys hier eintragen
│       └── res/layout/
│           ├── activity_upload.xml
│           └── activity_analytics.xml
├── backend/
│   └── app/
│       ├── routers/
│       │   └── analytics.py       # 📊 Analytics API
│       └── models/
│           └── analytics.py        # 📊 Datenmodelle
├── install.sh                      # 🐧 Linux/Mac Installer
├── install.bat                     # 🪟 Windows Installer
└── SETUP_GUIDE.md                  # 📖 Diese Datei
```
