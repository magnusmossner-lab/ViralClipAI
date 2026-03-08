# 🔥 ViralClip AI v4.2
**YouTube → Virale 9:16 Clips mit Karaoke-Untertiteln & Content-Filtern**

## Was ist neu in v4.2? 
- 🎤 **Karaoke-Untertitel** – Wort-für-Wort-Highlighting (wie OpusClip/Zarbex)
- 💬 **Hook-Captions** – Weißer Kasten oben (erste 3 Sekunden)
- 🎯 **Content-Filter** – Sprache, Schlüsselwörter, Thema, Viral-Empfindlichkeit
- 🎨 **Untertitel-Anpassung** – 6 Schriftarten, 8 Farben, 6 Highlight-Farben, 4 Größen, 5 Stile
- 📱 **Social Media Upload** – Direkt auf TikTok/YouTube/Instagram unter jedem Clip
- 🔊 **Video-Vorschau mit Ton** – ExoPlayer mit Audio
- 🎬 **Auto-Cut** – Face-Zoom bei Reaktionen

## Features
| Feature | Beschreibung |
|---------|-------------|
| 🎤 Karaoke-Untertitel | Aktuelles Wort hervorgehoben (Grün/Gelb/Custom) |
| 💬 Hook-Caption | Weißer Kasten, schwarze Schrift, erste 3s |
| 🎯 Content-Filter | Sprache, Keywords, Themen (Kontrovers, Drama, Realtalk...) |
| ⚡ Viral-Empfindlichkeit | Wenig (1-3), Mittel (3-6), Viel (6-12) Clips |
| 📱 Social Upload | TikTok, YouTube, Instagram direkt aus der App |
| 🔊 Vorschau mit Ton | ExoPlayer Video-Preview |
| 🤖 Auto-Cut | Gesichts-Zoom bei Reaktionen |
| 📐 9:16 Format | Für TikTok/Reels/Shorts |
| 🧠 Selbstlernende KI | Verbessert sich durch Feedback |
| 🩹 Self-Healing | Automatische Fehlerbehebung |

### Untertitel-Optionen
| Kategorie | Optionen |
|-----------|----------|
| **Schriftarten** | Anton, Bebas Neue, Montserrat, Oswald, Poppins, Bangers |
| **Textfarben** | Weiß, Gelb, Cyan, Grün, Rot, Orange, Pink, Lila |
| **Highlight** | Neon-Grün, Gelb, Cyan, Rot, Orange, Hot Pink |
| **Größen** | Klein (36px), Mittel (48px), Groß (64px), XL (80px) |
| **Stile** | 🎤 Karaoke, 📝 Klassisch, ✨ Neon Glow, 🔲 Box, 🔤 Outline |

### Content-Filter
| Filter | Optionen |
|--------|----------|
| **Sprache** | Auto, Deutsch, English, Türkçe, العربية, Español, Français |
| **Thema** | Alle, Kontrovers, Emotional, Lustig, Realtalk, Drama, Motivation, Skandal |
| **Keywords** | Eigene + KI-Vorschläge passend zum Thema |
| **Empfindlichkeit** | Wenig (1-3), Mittel (3-6), Viel (6-12) |

### Trainiert auf: Zarbex, Schradin, Elotrix, Papaplatte
### Kein Schimpfwort-Filter!

## Setup

### 1. Backend starten
```bash
cd backend
pip install -r requirements.txt
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
# Oder via Docker:
docker-compose up -d
```

### 2. App builden
```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

### 3. Benutzen
1. App öffnen → verbindet automatisch mit Server
2. YouTube-Link einfügen
3. Content-Filter einstellen (Sprache, Keywords, Thema)
4. Untertitel-Stil wählen (Schrift, Farbe, Größe)
5. "CLIPS ERSTELLEN" drücken
6. Clips ansehen (mit Ton!) und direkt uploaden

## Anforderungen
- Android 8.0+ (API 26+)
- Python 3.11+, FFmpeg, ~4GB RAM
- whisper-timestamped oder faster-whisper
