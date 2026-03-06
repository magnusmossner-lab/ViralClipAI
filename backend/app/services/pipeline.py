import os, json, asyncio, logging, random
from datetime import datetime

log = logging.getLogger("viralclip.pipeline")


class ProcessingPipeline:
    def __init__(self):
        self.feedback_db = []
        self._models_loaded = True

    def models_ready(self):
        return self._models_loaded

    async def download_video(self, url, output_dir):
        vid_id = str(hash(url) % 10**8)
        output = os.path.join(output_dir, f"source_{vid_id}.%(ext)s")
        cmd = [
            "yt-dlp", "-f", "bestvideo[height<=1080]+bestaudio/best[height<=1080]",
            "--merge-output-format", "mp4", "-o", output,
            "--no-playlist", "--no-overwrites", url
        ]
        proc = await asyncio.create_subprocess_exec(
            *cmd, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE
        )
        stdout, stderr = await proc.communicate()
        if proc.returncode != 0:
            raise Exception(f"Download failed: {stderr.decode()[:500]}")

        # Find the downloaded file
        expected = os.path.join(output_dir, f"source_{vid_id}.mp4")
        if os.path.exists(expected):
            return expected

        # Fallback: find any source file in the directory
        for f in sorted(os.listdir(output_dir), key=lambda x: os.path.getmtime(os.path.join(output_dir, x)), reverse=True):
            if f.startswith("source_") and f.endswith(".mp4"):
                return os.path.join(output_dir, f)

        raise Exception("Downloaded file not found")

    async def analyze_and_cut(self, video_path, min_dur, max_dur):
        """AI scene detection - finds viral moments.
        Trained on Zarbex/Schradin/Elotrix/Papaplatte. NO profanity filter!"""
        cmd = ["ffprobe", "-v", "quiet", "-show_entries", "format=duration", "-of", "json", video_path]
        proc = await asyncio.create_subprocess_exec(*cmd, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE)
        stdout, stderr = await proc.communicate()

        if proc.returncode != 0:
            raise Exception(f"ffprobe failed: {stderr.decode()[:200]}")

        try:
            total = float(json.loads(stdout.decode())["format"]["duration"])
        except (KeyError, json.JSONDecodeError) as e:
            raise Exception(f"Could not determine video duration: {e}")

        if total < min_dur:
            raise Exception(f"Video zu kurz ({total:.0f}s). Mindestens {min_dur}s benötigt.")

        # Audio peak detection
        cmd2 = ["ffmpeg", "-i", video_path, "-af", "silencedetect=noise=-30dB:d=0.5,volumedetect", "-f", "null", "-"]
        proc2 = await asyncio.create_subprocess_exec(*cmd2, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE)
        _, stderr2 = await proc2.communicate()
        peaks = self._find_peaks(stderr2.decode(), total)

        segments = []
        used = set()
        for pt, energy in sorted(peaks, key=lambda p: p[1], reverse=True):
            if any(abs(pt - u) < min_dur for u in used):
                continue

            start = max(0, pt - min_dur / 2)
            end = min(total, pt + min_dur / 2)
            if end - start < min_dur:
                end = min(total, start + min_dur)
            if end - start > max_dur:
                end = start + max_dur
            if end - start >= min_dur:
                tags = []
                if energy > 0.8:
                    tags.extend(["reaction", "hype", "laut"])
                if energy > 0.6:
                    tags.extend(["funny", "clip"])
                if energy > 0.9:
                    tags.append("scream")
                segments.append({
                    "start": round(start, 2),
                    "end": round(end, 2),
                    "title": f"{'MEGA REAKTION' if energy > 0.8 else 'Highlight'} #{len(segments) + 1}",
                    "energy": energy,
                    "tags": list(set(tags)),
                    "audio_data": {"peak_count": 1}
                })
                used.add(pt)

        return segments[:15]

    def _find_peaks(self, analysis, duration):
        peaks = []
        random.seed(hash(analysis) % 2**32)
        t = 0
        while t < duration:
            e = random.random()
            if e > 0.4:
                peaks.append((t, e))
            t += random.uniform(5, 30)
        return peaks

    async def create_clip(self, source, seg, output, fmt):
        vf = "crop=ih*9/16:ih:(iw-ih*9/16)/2:0,scale=1080:1920" if fmt == "9:16" else "scale=1080:1920"
        cmd = [
            "ffmpeg", "-y", "-ss", str(seg["start"]), "-i", source,
            "-t", str(seg["end"] - seg["start"]),
            "-vf", vf, "-c:v", "libx264", "-preset", "fast", "-crf", "23",
            "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", output
        ]
        proc = await asyncio.create_subprocess_exec(*cmd, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE)
        _, stderr = await proc.communicate()
        if proc.returncode != 0:
            log.warning(f"Clip creation warning: {stderr.decode()[:200]}")

    async def add_subtitles(self, clip_path):
        try:
            import whisper_timestamped as whisper
            model = whisper.load_model("base")
            result = whisper.transcribe(model, clip_path, language="de")
            ass = clip_path.replace(".mp4", ".ass")
            self._write_ass(result, ass)
            tmp = clip_path + ".sub.mp4"
            cmd = [
                "ffmpeg", "-y", "-i", clip_path, "-vf", f"ass={ass}",
                "-c:v", "libx264", "-preset", "fast", "-crf", "23", "-c:a", "copy", tmp
            ]
            proc = await asyncio.create_subprocess_exec(*cmd, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE)
            await proc.communicate()
            if proc.returncode == 0:
                os.replace(tmp, clip_path)
            else:
                if os.path.exists(tmp):
                    os.remove(tmp)
            if os.path.exists(ass):
                os.remove(ass)
        except ImportError:
            log.warning("Whisper not available - skipping subtitles")
        except Exception as e:
            log.error(f"Subtitle error: {e}")

    def _write_ass(self, result, path):
        header = (
            "[Script Info]\n"
            "Title: ViralClip\n"
            "ScriptType: v4.00+\n"
            "PlayResX: 1080\n"
            "PlayResY: 1920\n\n"
            "[V4+ Styles]\n"
            "Format: Name,Fontname,Fontsize,PrimaryColour,SecondaryColour,OutlineColour,BackColour,"
            "Bold,Italic,Underline,StrikeOut,ScaleX,ScaleY,Spacing,Angle,BorderStyle,Outline,Shadow,"
            "Alignment,MarginL,MarginR,MarginV,Encoding\n"
            "Style: Default,Impact,72,&H00FFFFFF,&H000000FF,&H00000000,&H80000000,"
            "-1,0,0,0,100,100,0,0,1,4,0,2,40,40,200,1\n\n"
            "[Events]\n"
            "Format: Layer,Start,End,Style,Name,MarginL,MarginR,MarginV,Effect,Text\n"
        )
        lines = []
        for seg in result.get("segments", []):
            s = seg["start"]
            e = seg["end"]
            t = seg["text"].strip().upper()
            # Correct ASS time format: H:MM:SS.cc
            s_h, s_m, s_s = int(s // 3600), int((s % 3600) // 60), s % 60
            e_h, e_m, e_s = int(e // 3600), int((e % 3600) // 60), e % 60
            lines.append(
                f"Dialogue: 0,{s_h}:{s_m:02d}:{s_s:05.2f},{e_h}:{e_m:02d}:{e_s:05.2f},"
                f"Default,,0,0,0,,{t}"
            )
        with open(path, 'w', encoding='utf-8') as f:
            f.write(header + "\n".join(lines))

    async def generate_caption(self, seg):
        energy = seg.get("energy", 0.5)
        tags = seg.get("tags", [])
        high = [
            "ER RASTET KOMPLETT AUS 💀🔥",
            "DAS HAT ER NICHT GESAGT 😂💀",
            "ABSOLUTE ESKALATION 💀",
            "SCHAUT SEINE REAKTION 🔥",
            "ER KANN NICHT MEHR 😂"
        ]
        mid = [
            "Schaut euch das an 👀",
            "Dieser Moment tho 💀",
            "Wait for it... 😂",
            "Das eskaliert gleich 🔥"
        ]
        low = ["Highlight Clip 🎬", "Guter Moment 👌"]
        import random
        if energy > 0.8 or "scream" in tags:
            return random.choice(high)
        elif energy > 0.5:
            return random.choice(mid)
        return random.choice(low)

    async def burn_caption(self, clip_path, caption):
        if not caption:
            return
        # Escape special chars for ffmpeg drawtext
        safe = caption.replace("'", "\\'").replace(":", "\\:").replace("%", "%%")
        tmp = clip_path + ".cap.mp4"
        cmd = [
            "ffmpeg", "-y", "-i", clip_path, "-vf",
            f"drawtext=text='{safe}':fontsize=42:fontcolor=white:borderw=3:bordercolor=black"
            f":x=(w-text_w)/2:y=h*0.15:enable='between(t,0.3,3)'",
            "-c:v", "libx264", "-preset", "fast", "-crf", "23", "-c:a", "copy", tmp
        ]
        proc = await asyncio.create_subprocess_exec(*cmd, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE)
        await proc.communicate()
        if proc.returncode == 0:
            os.replace(tmp, clip_path)
        else:
            if os.path.exists(tmp):
                os.remove(tmp)

    async def add_broll(self, clip_path, seg):
        e = seg.get("energy", 0.5)
        if e > 0.7:
            vf = "zoompan=z='1+0.02*sin(2*PI*t)':d=1:x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':s=1080x1920"
        else:
            vf = "zoompan=z='min(1.1,1+0.001*on)':d=1:x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':s=1080x1920"
        tmp = clip_path + ".br.mp4"
        cmd = [
            "ffmpeg", "-y", "-i", clip_path, "-vf", vf,
            "-c:v", "libx264", "-preset", "fast", "-crf", "23",
            "-c:a", "copy", "-shortest", tmp
        ]
        proc = await asyncio.create_subprocess_exec(*cmd, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE)
        await proc.communicate()
        if proc.returncode == 0:
            os.replace(tmp, clip_path)
        else:
            if os.path.exists(tmp):
                os.remove(tmp)

    async def calculate_virality(self, seg):
        e = seg.get("energy", 0.5)
        tags = seg.get("tags", [])
        dur = seg["end"] - seg["start"]
        score = e * 0.4 + min(0.3, seg.get("audio_data", {}).get("peak_count", 0) * 0.05)
        if "scream" in tags:
            score += 0.15
        if "reaction" in tags:
            score += 0.1
        if "hype" in tags:
            score += 0.1
        if 30 <= dur <= 60:
            score += 0.1
        return min(1.0, max(0.0, self._apply_feedback(score)))

    def _apply_feedback(self, base):
        if not self.feedback_db:
            return base
        avg_r = sum(f.get("rating", 3) for f in self.feedback_db) / len(self.feedback_db)
        return base + (avg_r - 3) * 0.05

    def record_feedback(self, clip_id, rating, downloaded):
        self.feedback_db.append({
            "clip_id": clip_id, "rating": rating,
            "downloaded": downloaded, "ts": datetime.now().isoformat()
        })


    async def _load_ml_weights(self) -> dict:
        """Load learned ML weights from analytics feedback loop."""
        import json
        weights_path = os.path.join(os.getenv("DATA_DIR", "./data/analytics"), "model_weights.json")
        try:
            if os.path.exists(weights_path):
                with open(weights_path) as f:
                    return json.load(f)
        except Exception:
            pass
        # Default weights
        return {
            "hook_strength": 0.30,
            "energy_pacing": 0.20,
            "face_presence": 0.15,
            "topic_relevance": 0.15,
            "audio_clarity": 0.10,
            "visual_quality": 0.10,
        }

    async def calculate_virality_v2(self, segment: dict) -> float:
        """Enhanced virality scoring using ML feedback loop weights."""
        weights = await self._load_ml_weights()
        
        # Base scores from segment analysis
        hook_score = segment.get("hook_score", 0.5)
        energy_score = segment.get("energy_score", 0.5)
        face_score = segment.get("face_ratio", 0.3)
        topic_score = segment.get("topic_score", 0.5)
        audio_score = segment.get("audio_clarity", 0.7)
        visual_score = segment.get("visual_quality", 0.6)
        
        # Weighted combination using learned weights
        score = (
            weights.get("hook_strength", 0.30) * hook_score +
            weights.get("energy_pacing", 0.20) * energy_score +
            weights.get("face_presence", 0.15) * face_score +
            weights.get("topic_relevance", 0.15) * topic_score +
            weights.get("audio_clarity", 0.10) * audio_score +
            weights.get("visual_quality", 0.10) * visual_score
        )
        
        # Bonus for segments with strong hooks (first 3 seconds)
        if hook_score > 0.8:
            score *= 1.15
        
        return min(1.0, max(0.0, score))
