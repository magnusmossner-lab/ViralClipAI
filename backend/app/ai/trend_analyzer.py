"""YouTube Shorts Trend Analyzer - learns from Zarbex, Schradin, Elotrix, Papaplatte"""
import logging, json, os, asyncio
from datetime import datetime
log = logging.getLogger("viralclip.trends")
CHANNELS = ["Zarbex", "Schradin", "Elotrix", "Papaplatte", "Trymacs", "MontanaBlack"]

class TrendAnalyzer:
    def __init__(self):
        self.patterns = {"avg_duration": 45, "hook_first_3s": True, "subtitle_style": "impact_center", "no_profanity_filter": True, "audio": {"screams_boost": True, "roasts_boost": True}, "visual": {"format": "9:16", "zoom_reactions": True}}

    async def analyze_trending(self):
        try:
            for ch in CHANNELS[:4]:
                cmd = ["yt-dlp", "--flat-playlist", "--dump-json", f"ytsearch10:{ch} shorts viral", "--no-download"]
                p = await asyncio.create_subprocess_exec(*cmd, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE)
                stdout, _ = await p.communicate()
                for line in (stdout.decode().strip().split("\n") if stdout else []):
                    try:
                        v = json.loads(line)
                        if v.get("view_count", 0) > 100000:
                            d = v.get("duration", 0)
                            if d: self.patterns["avg_duration"] = (self.patterns["avg_duration"] + d) / 2
                    except: pass
        except Exception as e: log.error(f"Trend analysis failed: {e}")
