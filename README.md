# 🎬 ViralClipAI

> YouTube-Videos automatisch in virale 9:16 Clips schneiden & auf YouTube Shorts + TikTok hochladen.

## Features

- 🤖 **KI-gestützte Clip-Erkennung** – Erkennt automatisch die viralsten Momente
- 📐 **9:16 Konvertierung** – Automatische Anpassung für Shorts/TikTok
- 📝 **Auto-Untertitel** – Generiert automatisch Untertitel
- 🎬 **B-Roll Effekte** – Optionale visuelle Effekte
- 🚀 **Multi-Platform Upload** – YouTube Shorts + TikTok
- 📊 **Analytics Dashboard** – Verfolge Views, Likes, Engagement
- 🧠 **ML Feedback Loop** – Die KI lernt aus deinen Upload-Daten

## Architektur

```
┌──────────────────┐     ┌──────────────────┐
│  Android App     │────▶│  Python Backend   │
│  (Kotlin/Compose)│◀────│  (FastAPI)        │
└──────────────────┘     └──────────────────┘
        │                         │
        ▼                         ▼
  YouTube API              ffmpeg + yt-dlp
  TikTok API               Video Processing
```

## Quick Start

### 1. Backend starten

```bash
cd backend
docker-compose up -d
```

Oder ohne Docker:
```bash
cd backend
pip install -r requirements.txt
# ffmpeg und yt-dlp müssen installiert sein
python main.py
```

Backend läuft dann auf `http://localhost:8000`

### 2. App installieren

```bash
# APK von GitHub Actions herunterladen oder lokal bauen:
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 3. Server-URL konfigurieren

In der App unter **Einstellungen** die IP deines Servers eingeben:
- Emulator: `http://10.0.2.2:8000`
- Lokales Netzwerk: `http://DEINE-IP:8000`

### 4. API-Keys einrichten (für Upload-Funktion)

#### YouTube / Google OAuth2
1. [Google Cloud Console](https://console.cloud.google.com/) → Neues Projekt
2. **YouTube Data API v3** aktivieren
3. OAuth 2.0 Client ID erstellen (Android-App)
4. Package Name: `com.viralclipai.app`
5. `client_id` und `client_secret` in `app/src/main/assets/oauth_config.json` eintragen

#### TikTok
1. [TikTok Developer Portal](https://developers.tiktok.com/) → App registrieren
2. **Content Posting API** beantragen
3. Callback URL: `com.viralclipai.app://oauth/callback`
4. `client_key` in `oauth_config.json` eintragen

## API Endpoints

| Endpoint | Methode | Beschreibung |
|----------|---------|-------------|
| `/health` | GET | Server-Status |
| `/api/process` | POST | Video verarbeiten |
| `/api/job/{id}` | GET | Job-Status |
| `/api/clip/{id}/download` | GET | Clip herunterladen |
| `/api/feedback` | POST | Clip bewerten |
| `/api/v1/analytics/summary` | GET | Analytics-Übersicht |
| `/api/v1/analytics/insights` | GET | KI-Empfehlungen |

## Tech Stack

**Android App:**
- Kotlin 1.9.20
- Jetpack Compose (Material3)
- Retrofit + OkHttp
- WorkManager
- EncryptedSharedPreferences

**Backend:**
- Python 3.12
- FastAPI
- ffmpeg
- yt-dlp

## Lizenz

Private Nutzung – © 2026 magnusmossner-lab
