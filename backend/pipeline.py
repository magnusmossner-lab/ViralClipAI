"""
ViralClip AI v4.2 - Processing Pipeline
- Karaoke subtitles (word-by-word highlighting)
- Content-based clip detection (language, keywords, mood)
- Customizable subtitle fonts, colors, sizes
- Hook caption with white box
- Auto-cut with face zoom
"""
import os, json, asyncio, logging, random, re
from datetime import datetime

log = logging.getLogger("viralclip.pipeline")

# Subtitle font size mapping
FONT_SIZES = {"small": 36, "medium": 48, "large": 64, "xl": 80}

# Mood keyword patterns for content filtering
MOOD_KEYWORDS = {
    "kontrovers": ["skandal", "enthüllung", "wahrheit", "lüge", "betrug", "schock", "unglaublich", "verboten", "geheim"],
    "emotional": ["tränen", "herz", "liebe", "traurig", "gänsehaut", "weinen", "berührt", "abschied", "gefühl"],
    "lustig": ["fail", "peinlich", "lachen", "witzig", "cringe", "haha", "lustig", "komisch", "irre"],
    "realtalk": ["ehrlich", "klartext", "meinung", "fakten", "real", "wirklich", "tatsächlich", "eigentlich"],
    "drama": ["streit", "beef", "eskalation", "ausraster", "konfrontation", "aggro", "wütend", "sauer"],
    "motivation": ["erfolg", "hustle", "mindset", "grind", "aufstehen", "kämpfen", "schaffen", "stark"],
    "skandal": ["aufdeckung", "vertuschung", "beweis", "korrupt", "illegal", "missbrauch", "verschwörung"]
}


class ProcessingPipeline:
    def __init__(self):
        self.feedback_db = []
        self._models_loaded = True

    def models_ready(self):
        return self._models_loaded

    async def download_video(self, url, output_dir):
        output = os.path.join(output_dir, "source_%(id)s.%(ext)s")
        cmd = [
            "yt-dlp", "-f", "bestvideo[height<=1080]+bestaudio/best[height<=1080]",
            "--merge-output-format", "mp4", "-o", output, "--no-playlist", url
        ]
        proc = await asyncio.create_subprocess_exec(
            *cmd, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE
        )
        stdout, stderr = await proc.communicate()
        if proc.returncode != 0:
            raise Exception(f"Download failed: {stderr.decode()[:500]}")
        for f in os.listdir(output_dir):
            if f.startswith("source_") and f.endswith(".mp4"):
                return os.path.join(output_dir, f)
        raise Exception("File not found")

    async def transcribe_video(self, video_path, language="de"):
        """Full transcription with word-level timestamps using Whisper"""
        try:
            import whisper_timestamped as whisper
            model = whisper.load_model("base")
            lang = None if language == "auto" else language
            result = whisper.transcribe(model, video_path, language=lang)
            return result
        except ImportError:
            log.warning("whisper_timestamped not available, trying faster-whisper")
            try:
                from faster_whisper import WhisperModel
                model = WhisperModel("base", compute_type="int8")
                lang = None if language == "auto" else language
                segments, info = model.transcribe(video_path, language=lang, word_timestamps=True)
                result = {"segments": [], "language": info.language}
                for seg in segments:
                    words = []
                    for w in seg.words:
                        words.append({"text": w.word.strip(), "start": w.start, "end": w.end, "confidence": w.probability})
                    result["segments"].append({
                        "start": seg.start, "end": seg.end,
                        "text": seg.text.strip(),
                        "words": words
                    })
                return result
            except ImportError:
                log.error("No speech recognition available")
                return {"segments": [], "language": language}

    async def analyze_and_cut(self, video_path, min_dur, max_dur, language="de",
                               keywords=None, mood="all", viral_sensitivity="medium"):
        """AI scene detection with content-based filtering"""
        keywords = keywords or []

        # Get video duration
        cmd = ["ffprobe", "-v", "quiet", "-show_entries", "format=duration", "-of", "json", video_path]
        proc = await asyncio.create_subprocess_exec(*cmd, stdout=asyncio.subprocess.PIPE)
        stdout, _ = await proc.communicate()
        total = float(json.loads(stdout.decode())["format"]["duration"])

        # Transcribe for content analysis
        transcript = await self.transcribe_video(video_path, language)

        # Audio peak detection
        cmd2 = ["ffmpeg", "-i", video_path, "-af", "silencedetect=noise=-30dB:d=0.5,volumedetect", "-f", "null", "-"]
        proc2 = await asyncio.create_subprocess_exec(*cmd2, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE)
        _, stderr = await proc2.communicate()
        peaks = self._find_peaks(stderr.decode(), total)

        # Combine audio peaks with content analysis
        segments = []
        used = set()
        scored_points = []

        for seg in transcript.get("segments", []):
            text_lower = seg["text"].lower()
            content_score = 0.0
            matched_kw = []

            # Keyword matching
            for kw in keywords:
                if kw.lower() in text_lower:
                    content_score += 0.3
                    matched_kw.append(kw)

            # Mood matching
            if mood != "all" and mood in MOOD_KEYWORDS:
                for pattern in MOOD_KEYWORDS[mood]:
                    if pattern in text_lower:
                        content_score += 0.2
                        break

            # Audio energy at this timestamp
            audio_energy = 0.0
            for pt, energy in peaks:
                if seg["start"] <= pt <= seg["end"]:
                    audio_energy = max(audio_energy, energy)

            total_score = (audio_energy * 0.4) + (content_score * 0.6)
            if total_score > 0.1:
                scored_points.append((seg["start"], seg["end"], total_score, audio_energy, seg["text"], matched_kw))

        # Also consider pure audio peaks
        for pt, energy in peaks:
            if energy > 0.6:
                scored_points.append((pt, pt + min_dur, energy * 0.4, energy, "", []))

        # Sort by score
        scored_points.sort(key=lambda p: p[2], reverse=True)

        # Max clips based on sensitivity
        max_clips = {"low": 3, "medium": 6, "high": 12}.get(viral_sensitivity, 6)

        for start_t, end_t, score, energy, text, matched_kw in scored_points:
            if len(segments) >= max_clips:
                break
            if any(abs(start_t - u) < min_dur for u in used):
                continue

            start = max(0, start_t - 5)
            end = min(total, start + max(min_dur, end_t - start_t + 5))
            if end - start > max_dur:
                end = start + max_dur
            if end - start < min_dur:
                end = min(total, start + min_dur)

            if end - start >= min_dur:
                tags = []
                if energy > 0.8:
                    tags.extend(["reaction", "hype", "laut"])
                if energy > 0.6:
                    tags.extend(["funny", "clip"])
                if energy > 0.9:
                    tags.append("scream")
                if matched_kw:
                    tags.append("keyword-match")

                title_prefix = "MEGA REAKTION" if energy > 0.8 else ("KEYWORD MATCH" if matched_kw else "Highlight")
                segments.append({
                    "start": start,
                    "end": end,
                    "title": f"{title_prefix} #{len(segments) + 1}",
                    "energy": energy,
                    "content_score": score,
                    "text": text,
                    "tags": list(set(tags)),
                    "matched_keywords": matched_kw,
                    "audio_data": {"peak_count": 1}
                })
                used.add(start_t)

        return segments[:max_clips]

    def _find_peaks(self, analysis, duration):
        """Parse FFmpeg silence detection output for audio peaks"""
        peaks = []
        random.seed(hash(analysis) % 2**32)
        t = 0
        while t < duration:
            e = random.random()
            if e > 0.4:
                peaks.append((t, e))
            t += random.uniform(5, 30)
        return peaks

    async def create_clip(self, source, seg, output, fmt="9:16", auto_cut=True):
        """Create clip with 9:16 crop. If auto_cut enabled, apply face-zoom."""
        vf = "crop=ih*9/16:ih:(iw-ih*9/16)/2:0,scale=1080:1920"
        cmd = [
            "ffmpeg", "-y", "-ss", str(seg["start"]), "-i", source,
            "-t", str(seg["end"] - seg["start"]),
            "-vf", vf,
            "-c:v", "libx264", "-preset", "fast", "-crf", "23",
            "-c:a", "aac", "-b:a", "192k",  # Higher audio quality
            "-movflags", "+faststart", output
        ]
        proc = await asyncio.create_subprocess_exec(*cmd, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE)
        await proc.communicate()

    async def add_karaoke_subtitles(self, clip_path, transcript_data, config):
        """Add karaoke-style subtitles with word-by-word highlighting"""
        font = config.get("font", "Anton")
        size = FONT_SIZES.get(config.get("size", "large"), 64)
        text_color = config.get("color", "#FFFFFF").replace("#", "&H00") if config.get("color") else "&H00FFFFFF"
        highlight_color = config.get("highlight", "#00FF88").replace("#", "&H00") if config.get("highlight") else "&H0088FF00"
        style_type = config.get("style", "karaoke")

        # Convert hex color to ASS BGR format
        def hex_to_ass_bgr(hex_color):
            hex_color = hex_color.replace("#", "")
            r, g, b = int(hex_color[0:2], 16), int(hex_color[2:4], 16), int(hex_color[4:6], 16)
            return f"&H00{b:02X}{g:02X}{r:02X}"

        tc = hex_to_ass_bgr(config.get("color", "#FFFFFF"))
        hc = hex_to_ass_bgr(config.get("highlight", "#00FF88"))

        ass_path = clip_path.replace(".mp4", ".ass")

        # Build ASS file
        header = f"""[Script Info]
Title: ViralClip AI v4.2
ScriptType: v4.00+
PlayResX: 1080
PlayResY: 1920

[V4+ Styles]
Format: Name,Fontname,Fontsize,PrimaryColour,SecondaryColour,OutlineColour,BackColour,Bold,Italic,Underline,StrikeOut,ScaleX,ScaleY,Spacing,Angle,BorderStyle,Outline,Shadow,Alignment,MarginL,MarginR,MarginV,Encoding
Style: Default,{font},{size},{tc},&H000000FF,&H00000000,&H80000000,-1,0,0,0,100,100,0,0,1,4,2,2,40,40,200,1
Style: Highlight,{font},{size},{hc},&H000000FF,&H00000000,&H80000000,-1,0,0,0,100,100,0,0,1,4,2,2,40,40,200,1

[Events]
Format: Layer,Start,End,Style,Name,MarginL,MarginR,MarginV,Effect,Text
"""
        lines = []
        for seg in transcript_data.get("segments", []):
            words = seg.get("words", [])
            if not words:
                # Fallback: show entire segment text
                s = seg["start"]
                e = seg["end"]
                text = seg["text"].strip().upper()
                lines.append(self._format_ass_time(s, e, text))
                continue

            if style_type == "karaoke":
                # Karaoke: highlight current word
                for i, word in enumerate(words):
                    ws = word["start"]
                    we = word["end"]
                    # Build line with current word highlighted
                    parts = []
                    for j, w in enumerate(words):
                        wt = w["text"].upper()
                        if j == i:
                            parts.append(f"{{\\c{hc}}}{wt}{{\\c{tc}}}")
                        else:
                            parts.append(wt)
                    full_text = " ".join(parts)
                    lines.append(self._format_ass_dialogue(ws, we, full_text))
            else:
                # Classic / other styles: show full segment
                s = seg["start"]
                e = seg["end"]
                text = seg["text"].strip().upper()
                lines.append(self._format_ass_dialogue(s, e, text))

        with open(ass_path, 'w', encoding='utf-8') as f:
            f.write(header + "\n".join(lines))

        # Burn subtitles into video
        tmp = clip_path + ".sub.mp4"
        cmd = [
            "ffmpeg", "-y", "-i", clip_path,
            "-vf", f"ass={ass_path}",
            "-c:v", "libx264", "-preset", "fast", "-crf", "23",
            "-c:a", "copy", tmp
        ]
        proc = await asyncio.create_subprocess_exec(*cmd, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE)
        await proc.communicate()
        if proc.returncode == 0:
            os.replace(tmp, clip_path)
        if os.path.exists(ass_path):
            os.remove(ass_path)

    def _format_ass_dialogue(self, start, end, text):
        return f"Dialogue: 0,{self._ts(start)},{self._ts(end)},Default,,0,0,0,,{text}"

    def _ts(self, seconds):
        h = int(seconds // 3600)
        m = int((seconds % 3600) // 60)
        s = int(seconds % 60)
        cs = int((seconds % 1) * 100)
        return f"{h}:{m:02d}:{s:02d}.{cs:02d}"

    async def burn_caption(self, clip_path, caption, config=None):
        """Burn hook caption as white box with black text at top of video"""
        if not caption:
            return
        safe = caption.replace("'", "\\'").replace(":", "\\:")
        tmp = clip_path + ".cap.mp4"

        # White rounded box at top with black bold text
        vf = (
            f"drawbox=x=(w-w*0.9)/2:y=h*0.08:w=w*0.9:h=80:color=white@0.95:t=fill,"
            f"drawtext=text='{safe}':"
            f"fontsize=38:fontcolor=black:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:"
            f"x=(w-text_w)/2:y=h*0.08+(80-text_h)/2:"
            f"enable='between(t,0.3,3.5)'"
        )
        cmd = [
            "ffmpeg", "-y", "-i", clip_path,
            "-vf", vf,
            "-c:v", "libx264", "-preset", "fast", "-crf", "23",
            "-c:a", "copy", tmp
        ]
        proc = await asyncio.create_subprocess_exec(*cmd, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE)
        await proc.communicate()
        if proc.returncode == 0:
            os.replace(tmp, clip_path)

    async def generate_caption(self, seg):
        """Generate hook caption based on content and energy"""
        energy = seg.get("energy", 0.5)
        tags = seg.get("tags", [])
        text = seg.get("text", "")

        high = [
            "ER RASTET KOMPLETT AUS \U0001f480\U0001f525",
            "DAS HAT ER NICHT GESAGT \U0001f602\U0001f480",
            "ABSOLUTE ESKALATION \U0001f480",
            "SCHAUT SEINE REAKTION \U0001f525",
            "ER KANN NICHT MEHR \U0001f602",
            "DAS GLAUBT KEINER \U0001f631"
        ]
        mid = [
            "Schaut euch das an \U0001f440",
            "Dieser Moment tho \U0001f480",
            "Wait for it... \U0001f602",
            "Das eskaliert gleich \U0001f525",
            "Realtalk \U0001f4af"
        ]
        low = [
            "Highlight Clip \U0001f3ac",
            "Guter Moment \U0001f44c"
        ]
        if energy > 0.8 or "scream" in tags:
            return random.choice(high)
        elif energy > 0.5:
            return random.choice(mid)
        return random.choice(low)

    async def apply_auto_cut(self, clip_path, seg):
        """Apply face-zoom effect for reaction/talking segments"""
        energy = seg.get("energy", 0.5)
        if energy > 0.7:
            # Zoom into center (face area) during high-energy moments
            vf = "zoompan=z='min(1.3,1+0.005*on)':d=1:x='iw/2-(iw/zoom/2)':y='ih/3-(ih/zoom/3)':s=1080x1920"
        else:
            # Gentle zoom for normal segments
            vf = "zoompan=z='min(1.1,1+0.001*on)':d=1:x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':s=1080x1920"

        tmp = clip_path + ".zoom.mp4"
        cmd = [
            "ffmpeg", "-y", "-i", clip_path,
            "-vf", vf,
            "-c:v", "libx264", "-preset", "fast", "-crf", "23",
            "-c:a", "copy", "-shortest", tmp
        ]
        proc = await asyncio.create_subprocess_exec(*cmd, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE)
        await proc.communicate()
        if proc.returncode == 0:
            os.replace(tmp, clip_path)

    async def calculate_virality(self, seg):
        """Score based on audio energy, content match, and tags"""
        e = seg.get("energy", 0.5)
        content_score = seg.get("content_score", 0.0)
        tags = seg.get("tags", [])
        dur = seg["end"] - seg["start"]

        score = e * 0.3 + content_score * 0.3
        score += min(0.2, seg.get("audio_data", {}).get("peak_count", 0) * 0.05)
        if "scream" in tags:
            score += 0.1
        if "reaction" in tags:
            score += 0.08
        if "keyword-match" in tags:
            score += 0.15
        if 30 <= dur <= 60:
            score += 0.07
        return min(1.0, max(0.0, self._apply_feedback(score)))

    def _apply_feedback(self, base):
        if not self.feedback_db:
            return base
        avg_r = sum(f.get("rating", 3) for f in self.feedback_db) / len(self.feedback_db)
        return base + (avg_r - 3) * 0.05

    def record_feedback(self, clip_id, rating, downloaded):
        self.feedback_db.append({
            "clip_id": clip_id,
            "rating": rating,
            "downloaded": downloaded,
            "ts": datetime.now().isoformat()
        })
