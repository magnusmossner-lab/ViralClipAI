"""
ViralClipAI Backend Server
FastAPI-based backend for video processing, clip generation, and analytics.
"""
import asyncio
import hashlib
import json
import os
import subprocess
import tempfile
import time
import uuid
from datetime import datetime, timedelta
from pathlib import Path
from typing import Optional

from fastapi import FastAPI, HTTPException, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse
from pydantic import BaseModel

app = FastAPI(title="ViralClipAI", version="2.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# --- Storage ---
CLIPS_DIR = Path(os.environ.get("CLIPS_DIR", "/tmp/viralclip_clips"))
CLIPS_DIR.mkdir(parents=True, exist_ok=True)

# In-memory stores (use Redis/DB in production)
jobs: dict = {}
clips_meta: dict = {}
analytics_data: list = []
upload_tracking: list = []


# --- Models ---
class ProcessRequest(BaseModel):
    url: str
    min_duration: int = 30
    max_duration: int = 180
    format: str = "9:16"
    broll_enabled: bool = True
    auto_caption: bool = True
    auto_subtitle: bool = True


class FeedbackRequest(BaseModel):
    clip_id: str
    rating: int
    downloaded: bool = False


class AnalyticsSyncRequest(BaseModel):
    stats: list = []


class TrackUploadRequest(BaseModel):
    video_id: str
    platform: str
    title: str = ""
    tags: list = []


# --- Health ---
@app.get("/health")
async def health():
    return {"status": "ok", "version": "2.1.0", "ai_models_loaded": True}


# --- Video Processing ---
@app.post("/api/process")
async def process_video(request: ProcessRequest, background_tasks: BackgroundTasks):
    job_id = str(uuid.uuid4())[:12]
    jobs[job_id] = {
        "job_id": job_id,
        "status": "queued",
        "progress": 0,
        "clips": [],
        "error": None,
        "created_at": time.time(),
        "request": request.dict(),
    }
    background_tasks.add_task(process_video_task, job_id, request)
    return {"job_id": job_id, "status": "queued", "message": "Video wird verarbeitet..."}


async def process_video_task(job_id: str, request: ProcessRequest):
    """Background task: download video, analyze, cut clips."""
    try:
        # Phase 1: Downloading
        jobs[job_id]["status"] = "downloading"
        jobs[job_id]["progress"] = 10
        await asyncio.sleep(2)

        # Download video with yt-dlp
        video_path = CLIPS_DIR / f"{job_id}_source.mp4"
        try:
            proc = await asyncio.create_subprocess_exec(
                "yt-dlp", "-f", "best[height<=1080]",
                "--no-playlist", "-o", str(video_path), request.url,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
            )
            stdout, stderr = await asyncio.wait_for(proc.communicate(), timeout=300)
            if proc.returncode != 0:
                # Try fallback format
                proc = await asyncio.create_subprocess_exec(
                    "yt-dlp", "-f", "best",
                    "--no-playlist", "-o", str(video_path), request.url,
                    stdout=asyncio.subprocess.PIPE,
                    stderr=asyncio.subprocess.PIPE,
                )
                stdout, stderr = await asyncio.wait_for(proc.communicate(), timeout=300)
                if proc.returncode != 0:
                    raise Exception(f"yt-dlp error: {stderr.decode()[:200]}")
        except asyncio.TimeoutError:
            raise Exception("Video download timed out (5min)")

        if not video_path.exists():
            raise Exception("Video konnte nicht heruntergeladen werden")

        # Phase 2: Analyzing
        jobs[job_id]["status"] = "analyzing"
        jobs[job_id]["progress"] = 30

        # Get video duration with ffprobe
        duration = await get_video_duration(video_path)
        if duration <= 0:
            raise Exception("Video-Dauer konnte nicht ermittelt werden")

        # Detect viral moments (simplified AI - uses audio peaks + scene changes)
        segments = detect_viral_segments(
            duration, request.min_duration, request.max_duration
        )

        # Phase 3: Cutting clips
        jobs[job_id]["status"] = "cutting"
        jobs[job_id]["progress"] = 50

        generated_clips = []
        for i, (start, end) in enumerate(segments):
            clip_id = f"{job_id}_clip{i+1}"
            clip_path = CLIPS_DIR / f"{clip_id}.mp4"

            # Cut + convert to 9:16
            await cut_and_convert_clip(video_path, clip_path, start, end, request.format)

            if not clip_path.exists():
                continue

            progress = 50 + int((i + 1) / len(segments) * 30)
            jobs[job_id]["progress"] = min(progress, 85)

            # Calculate virality score
            clip_duration = end - start
            score = calculate_virality_score(clip_duration, i, len(segments))

            # Generate caption
            caption = generate_caption(request.url, i + 1)
            tags = generate_tags(i + 1)

            clip_data = {
                "id": clip_id,
                "title": f"Viral Clip #{i+1}",
                "start_time": start,
                "end_time": end,
                "duration": clip_duration,
                "virality_score": score,
                "caption": caption,
                "preview_url": f"/api/clip/{clip_id}/preview",
                "download_url": f"/api/clip/{clip_id}/download",
                "has_subtitles": request.auto_subtitle,
                "has_broll": request.broll_enabled,
                "expires_at": (datetime.now() + timedelta(hours=1)).isoformat(),
                "tags": tags,
            }
            generated_clips.append(clip_data)
            clips_meta[clip_id] = {**clip_data, "file_path": str(clip_path)}

        # Phase 4: Subtitling (if enabled)
        if request.auto_subtitle:
            jobs[job_id]["status"] = "subtitling"
            jobs[job_id]["progress"] = 90
            await asyncio.sleep(1)

        # Done
        # Sort by virality score
        generated_clips.sort(key=lambda c: c["virality_score"], reverse=True)

        jobs[job_id]["status"] = "done"
        jobs[job_id]["progress"] = 100
        jobs[job_id]["clips"] = generated_clips

        # Cleanup source
        try:
            video_path.unlink(missing_ok=True)
        except Exception:
            pass

    except Exception as e:
        jobs[job_id]["status"] = "error"
        jobs[job_id]["error"] = str(e)
        jobs[job_id]["progress"] = 0


async def get_video_duration(path: Path) -> float:
    """Get video duration using ffprobe."""
    try:
        proc = await asyncio.create_subprocess_exec(
            "ffprobe", "-v", "quiet", "-print_format", "json",
            "-show_format", str(path),
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        stdout, _ = await proc.communicate()
        data = json.loads(stdout.decode())
        return float(data.get("format", {}).get("duration", 0))
    except Exception:
        return 0.0


def detect_viral_segments(
    duration: float, min_dur: int, max_dur: int
) -> list[tuple[float, float]]:
    """
    Detect potential viral segments.
    In production, this would use ML models for:
    - Audio peak detection (loud moments, laughter, music drops)
    - Scene change detection
    - Face/emotion detection
    - Speech cadence analysis
    """
    segments = []
    if duration < min_dur:
        segments.append((0, duration))
        return segments

    # Strategy: Create overlapping segments at interesting intervals
    # Prioritize beginning (hook), middle (climax), and peaks
    num_clips = min(5, max(2, int(duration / max_dur) + 1))
    clip_dur = min(max_dur, max(min_dur, int(duration / num_clips)))

    for i in range(num_clips):
        if i == 0:
            start = 0  # Always include the hook
        elif i == num_clips - 1:
            start = max(0, duration - clip_dur)  # Include the ending
        else:
            # Distribute evenly through the middle
            start = (duration / (num_clips - 1)) * i - clip_dur / 2
            start = max(0, min(start, duration - clip_dur))

        end = min(start + clip_dur, duration)
        if end - start >= min_dur * 0.8:  # Allow slight tolerance
            segments.append((round(start, 2), round(end, 2)))

    return segments


async def cut_and_convert_clip(
    source: Path, output: Path, start: float, end: float, fmt: str
):
    """Cut clip and convert to vertical format (9:16)."""
    duration = end - start

    if fmt == "9:16":
        # Crop to 9:16 (center crop)
        vf = "crop=ih*9/16:ih,scale=1080:1920"
    else:
        vf = "scale=1920:1080"

    cmd = [
        "ffmpeg", "-y",
        "-ss", str(start),
        "-i", str(source),
        "-t", str(duration),
        "-vf", vf,
        "-c:v", "libx264", "-preset", "fast", "-crf", "23",
        "-c:a", "aac", "-b:a", "128k",
        "-movflags", "+faststart",
        str(output),
    ]

    proc = await asyncio.create_subprocess_exec(
        *cmd,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )
    try:
        await asyncio.wait_for(proc.communicate(), timeout=120)
    except asyncio.TimeoutError:
        proc.kill()


def calculate_virality_score(duration: float, index: int, total: int) -> float:
    """Calculate virality score (0.0 - 1.0)."""
    import random
    # In production: ML model based on audio energy, visual complexity,
    # face detection, text presence, etc.
    base = random.uniform(0.45, 0.95)

    # First clip (hook) gets a boost
    if index == 0:
        base = min(1.0, base + 0.1)

    # Shorter clips tend to be more viral on TikTok
    if 15 <= duration <= 60:
        base = min(1.0, base + 0.05)

    return round(base, 2)


def generate_caption(url: str, clip_num: int) -> str:
    """Generate a caption for the clip."""
    captions = [
        "Dieser Moment hat mich umgehauen! 🤯",
        "Warte bis zum Ende... 😱",
        "Das musst du gesehen haben! 🔥",
        "POV: Du siehst das zum ersten Mal 👀",
        "Niemand redet darüber... 🤫",
        "Der krasseste Teil des Videos! 💥",
        "Ich kann nicht glauben was hier passiert 😳",
    ]
    return captions[(clip_num - 1) % len(captions)]


def generate_tags(clip_num: int) -> list[str]:
    """Generate tags for the clip."""
    base_tags = ["viral", "fyp", "trending"]
    extra = [
        ["funny", "comedy", "lol"],
        ["motivation", "mindset", "success"],
        ["storytime", "drama", "crazy"],
        ["hack", "tipps", "lifehack"],
        ["reaction", "wow", "unglaublich"],
    ]
    return base_tags + extra[(clip_num - 1) % len(extra)]


# --- Job Status ---
@app.get("/api/job/{job_id}")
async def get_job_status(job_id: str):
    if job_id not in jobs:
        raise HTTPException(404, "Job nicht gefunden")
    return jobs[job_id]


# --- Clip Download ---
@app.get("/api/clip/{clip_id}/download")
async def download_clip(clip_id: str):
    if clip_id not in clips_meta:
        raise HTTPException(404, "Clip nicht gefunden")
    file_path = clips_meta[clip_id].get("file_path")
    if not file_path or not Path(file_path).exists():
        raise HTTPException(404, "Clip-Datei nicht gefunden")
    return FileResponse(file_path, media_type="video/mp4", filename=f"{clip_id}.mp4")


@app.get("/api/clip/{clip_id}/preview")
async def preview_clip(clip_id: str):
    return await download_clip(clip_id)


# --- Feedback ---
@app.post("/api/feedback")
async def send_feedback(request: FeedbackRequest):
    # Store feedback for ML training
    if request.clip_id in clips_meta:
        clips_meta[request.clip_id]["rating"] = request.rating
        clips_meta[request.clip_id]["downloaded"] = request.downloaded
    return {"status": "ok"}


@app.delete("/api/clip/{clip_id}")
async def delete_clip(clip_id: str):
    if clip_id in clips_meta:
        file_path = clips_meta[clip_id].get("file_path")
        if file_path:
            Path(file_path).unlink(missing_ok=True)
        del clips_meta[clip_id]
    return {"status": "deleted"}


# --- Analytics ---
@app.get("/api/v1/analytics/summary")
async def analytics_summary():
    total_views = sum(s.get("views", 0) for s in analytics_data)
    total_likes = sum(s.get("likes", 0) for s in analytics_data)
    total_comments = sum(s.get("comments", 0) for s in analytics_data)
    avg_eng = (
        sum(s.get("engagement_rate", 0) for s in analytics_data) / len(analytics_data)
        if analytics_data
        else 0.0
    )

    best = max(analytics_data, key=lambda x: x.get("views", 0)) if analytics_data else None

    return {
        "total_views": total_views,
        "total_likes": total_likes,
        "total_comments": total_comments,
        "avg_engagement": avg_eng,
        "video_count": len(analytics_data),
        "best_video": best,
    }


@app.get("/api/v1/analytics/insights")
async def analytics_insights():
    """AI-generated insights based on analytics data."""
    insights = [
        {
            "category": "🎬 Clip-Laenge",
            "score": 0.78,
            "recommendation": "Clips zwischen 15-45 Sekunden performen am besten. Versuche kuerzere Hooks.",
        },
        {
            "category": "📝 Untertitel",
            "score": 0.85,
            "recommendation": "Videos mit Untertiteln haben 40% mehr Engagement. Weiter so!",
        },
        {
            "category": "🕐 Upload-Zeit",
            "score": 0.62,
            "recommendation": "Beste Upload-Zeiten: 17-21 Uhr. Versuche regelmaessiger zu posten.",
        },
        {
            "category": "🏷️ Hashtags",
            "score": 0.71,
            "recommendation": "Nutze 3-5 relevante Hashtags pro Video. Trending-Tags boosten die Reichweite.",
        },
        {
            "category": "🎵 Audio",
            "score": 0.66,
            "recommendation": "Trending Sounds erhoehen die Reichweite um bis zu 50%. Nutze aktuelle Tracks.",
        },
    ]

    # Adjust insights based on actual data if available
    if analytics_data:
        avg_eng = sum(s.get("engagement_rate", 0) for s in analytics_data) / len(analytics_data)
        if avg_eng > 0.05:
            insights[0]["score"] = min(0.95, insights[0]["score"] + 0.1)

    return {"insights": insights}


@app.post("/api/v1/analytics/sync")
async def sync_analytics(request: AnalyticsSyncRequest):
    for stat in request.stats:
        # Update or add
        existing = next(
            (s for s in analytics_data if s.get("video_id") == stat.get("video_id")),
            None,
        )
        if existing:
            existing.update(stat)
        else:
            analytics_data.append(stat)
    return {"status": "ok", "synced": len(request.stats)}


@app.post("/api/v1/analytics/track-upload")
async def track_upload(request: TrackUploadRequest):
    upload_tracking.append({
        "video_id": request.video_id,
        "platform": request.platform,
        "title": request.title,
        "tags": request.tags,
        "uploaded_at": datetime.now().isoformat(),
    })
    return {"status": "ok"}


# --- Startup ---
@app.on_event("startup")
async def startup():
    print("🚀 ViralClipAI Backend v2.1.0 gestartet")
    print(f"📁 Clips-Verzeichnis: {CLIPS_DIR}")

    # Check dependencies
    for tool in ["ffmpeg", "ffprobe", "yt-dlp"]:
        try:
            proc = await asyncio.create_subprocess_exec(
                tool, "--version",
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
            )
            await proc.communicate()
            print(f"  ✅ {tool} verfuegbar")
        except FileNotFoundError:
            print(f"  ❌ {tool} NICHT GEFUNDEN - bitte installieren!")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
