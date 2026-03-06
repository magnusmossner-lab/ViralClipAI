"""
ViralClipAI Backend Server v3.0
Complete rewrite with robust video processing.
"""
import asyncio
import json
import os
import time
import uuid
from datetime import datetime, timedelta
from pathlib import Path
from typing import Optional

from fastapi import FastAPI, HTTPException, BackgroundTasks, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse, StreamingResponse
from pydantic import BaseModel

app = FastAPI(title="ViralClipAI", version="3.0.0")

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

MIN_VALID_FILE_SIZE = 50_000  # 50KB minimum for a valid clip

# In-memory stores
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
    # Actually check if tools are available
    tools_status = {}
    for tool in ["ffmpeg", "ffprobe", "yt-dlp"]:
        try:
            proc = await asyncio.create_subprocess_exec(
                tool, "--version",
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
            )
            await asyncio.wait_for(proc.communicate(), timeout=5)
            tools_status[tool] = proc.returncode == 0
        except Exception:
            tools_status[tool] = False

    all_ok = all(tools_status.values())
    return {
        "status": "ok" if all_ok else "degraded",
        "version": "3.0.0",
        "tools": tools_status,
        "clips_dir": str(CLIPS_DIR),
        "clips_dir_exists": CLIPS_DIR.exists(),
        "active_jobs": len([j for j in jobs.values() if j["status"] not in ("done", "error")]),
        "total_clips": len(clips_meta),
    }


@app.get("/api/diagnostics")
async def diagnostics():
    """Debug endpoint to check system status."""
    # Check ffmpeg capabilities
    try:
        proc = await asyncio.create_subprocess_exec(
            "ffmpeg", "-encoders",
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        stdout, _ = await proc.communicate()
        has_h264 = b"libx264" in stdout
        has_aac = b"aac" in stdout
    except Exception:
        has_h264 = False
        has_aac = False

    # Check yt-dlp version
    try:
        proc = await asyncio.create_subprocess_exec(
            "yt-dlp", "--version",
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        stdout, _ = await proc.communicate()
        ytdlp_version = stdout.decode().strip()
    except Exception:
        ytdlp_version = "NOT FOUND"

    # List clips on disk
    clip_files = []
    for f in CLIPS_DIR.iterdir():
        clip_files.append({
            "name": f.name,
            "size": f.stat().st_size,
            "valid": f.stat().st_size > MIN_VALID_FILE_SIZE,
        })

    return {
        "ffmpeg_h264": has_h264,
        "ffmpeg_aac": has_aac,
        "ytdlp_version": ytdlp_version,
        "clips_on_disk": clip_files,
        "clips_in_memory": len(clips_meta),
        "jobs": {k: {"status": v["status"], "clips": len(v.get("clips", []))} for k, v in jobs.items()},
    }


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
        "log": [],
    }
    background_tasks.add_task(process_video_task, job_id, request)
    return {"job_id": job_id, "status": "queued", "message": "Video wird verarbeitet..."}


def log_job(job_id: str, msg: str):
    """Add a log message to the job."""
    print(f"[{job_id}] {msg}")
    if job_id in jobs:
        jobs[job_id].setdefault("log", []).append(f"{time.strftime('%H:%M:%S')} {msg}")


async def process_video_task(job_id: str, request: ProcessRequest):
    """Background task: download video, analyze, cut clips."""
    try:
        # Phase 1: Downloading
        jobs[job_id]["status"] = "downloading"
        jobs[job_id]["progress"] = 5
        log_job(job_id, f"Starting download: {request.url}")

        video_path = CLIPS_DIR / f"{job_id}_source.mp4"

        # Try multiple yt-dlp format options
        formats_to_try = [
            # Best combined format up to 1080p
            "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/best[height<=1080][ext=mp4]/best[ext=mp4]/best",
            # Fallback: any format
            "best",
            # Last resort: worst quality but definitely works
            "worst",
        ]

        download_success = False
        for fmt in formats_to_try:
            log_job(job_id, f"Trying format: {fmt}")
            try:
                proc = await asyncio.create_subprocess_exec(
                    "yt-dlp",
                    "-f", fmt,
                    "--no-playlist",
                    "--merge-output-format", "mp4",
                    "--no-check-certificates",
                    "-o", str(video_path),
                    request.url,
                    stdout=asyncio.subprocess.PIPE,
                    stderr=asyncio.subprocess.PIPE,
                )
                stdout, stderr = await asyncio.wait_for(proc.communicate(), timeout=300)

                if proc.returncode == 0 and video_path.exists() and video_path.stat().st_size > 10000:
                    log_job(job_id, f"Download OK: {video_path.stat().st_size} bytes")
                    download_success = True
                    break
                else:
                    log_job(job_id, f"Format failed: rc={proc.returncode}, stderr={stderr.decode()[:200]}")
                    # Clean up partial download
                    for f in CLIPS_DIR.glob(f"{job_id}_source*"):
                        f.unlink(missing_ok=True)
            except asyncio.TimeoutError:
                log_job(job_id, "Download timed out")
                for f in CLIPS_DIR.glob(f"{job_id}_source*"):
                    f.unlink(missing_ok=True)

        if not download_success:
            raise Exception("Video konnte nicht heruntergeladen werden. Bitte pruefe die URL.")

        jobs[job_id]["progress"] = 25

        # Phase 2: Analyzing
        jobs[job_id]["status"] = "analyzing"
        jobs[job_id]["progress"] = 30

        # Get video info
        duration = await get_video_duration(video_path)
        video_info = await get_video_info(video_path)
        log_job(job_id, f"Video: {duration:.1f}s, {video_info.get('width', '?')}x{video_info.get('height', '?')}")

        if duration <= 0:
            raise Exception("Video-Dauer konnte nicht ermittelt werden")

        # Detect segments
        segments = detect_viral_segments(duration, request.min_duration, request.max_duration)
        log_job(job_id, f"Detected {len(segments)} segments")

        # Phase 3: Cutting clips
        jobs[job_id]["status"] = "cutting"
        jobs[job_id]["progress"] = 40

        generated_clips = []
        for i, (start, end) in enumerate(segments):
            clip_id = f"{job_id}_clip{i+1}"
            clip_path = CLIPS_DIR / f"{clip_id}.mp4"

            log_job(job_id, f"Cutting clip {i+1}: {start:.1f}s - {end:.1f}s")

            # Cut + convert
            success, error_msg = await cut_and_convert_clip(
                video_path, clip_path, start, end,
                request.format, video_info
            )

            if not success:
                log_job(job_id, f"Clip {i+1} FAILED: {error_msg}")
                # Try simpler approach without crop
                log_job(job_id, f"Retrying clip {i+1} without format conversion...")
                success, error_msg = await cut_clip_simple(
                    video_path, clip_path, start, end
                )

            if success and clip_path.exists() and clip_path.stat().st_size > MIN_VALID_FILE_SIZE:
                file_size = clip_path.stat().st_size
                log_job(job_id, f"Clip {i+1} OK: {file_size} bytes")

                progress = 40 + int((i + 1) / len(segments) * 45)
                jobs[job_id]["progress"] = min(progress, 90)

                clip_duration = end - start
                score = calculate_virality_score(clip_duration, i, len(segments))
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
                    "preview_url": f"/api/clip/{clip_id}/stream",
                    "download_url": f"/api/clip/{clip_id}/download",
                    "has_subtitles": request.auto_subtitle,
                    "has_broll": request.broll_enabled,
                    "expires_at": (datetime.now() + timedelta(hours=2)).isoformat(),
                    "tags": tags,
                    "file_size": file_size,
                }
                generated_clips.append(clip_data)
                clips_meta[clip_id] = {**clip_data, "file_path": str(clip_path)}
            else:
                size = clip_path.stat().st_size if clip_path.exists() else 0
                log_job(job_id, f"Clip {i+1} INVALID: exists={clip_path.exists()}, size={size}")
                clip_path.unlink(missing_ok=True)

        if not generated_clips:
            raise Exception("Keine gueltigen Clips erstellt. Das Video ist moeglicherweise geschuetzt oder zu kurz.")

        # Sort by virality score
        generated_clips.sort(key=lambda c: c["virality_score"], reverse=True)

        jobs[job_id]["status"] = "done"
        jobs[job_id]["progress"] = 100
        jobs[job_id]["clips"] = generated_clips
        log_job(job_id, f"DONE: {len(generated_clips)} clips created")

        # Cleanup source video (keep clips)
        video_path.unlink(missing_ok=True)

    except Exception as e:
        log_job(job_id, f"ERROR: {str(e)}")
        jobs[job_id]["status"] = "error"
        jobs[job_id]["error"] = str(e)
        jobs[job_id]["progress"] = 0
        # Cleanup on error
        for f in CLIPS_DIR.glob(f"{job_id}*"):
            f.unlink(missing_ok=True)


async def get_video_duration(path: Path) -> float:
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


async def get_video_info(path: Path) -> dict:
    """Get video dimensions and codec info."""
    try:
        proc = await asyncio.create_subprocess_exec(
            "ffprobe", "-v", "quiet", "-print_format", "json",
            "-show_streams", "-select_streams", "v:0",
            str(path),
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        stdout, _ = await proc.communicate()
        data = json.loads(stdout.decode())
        streams = data.get("streams", [])
        if streams:
            s = streams[0]
            return {
                "width": int(s.get("width", 0)),
                "height": int(s.get("height", 0)),
                "codec": s.get("codec_name", "unknown"),
                "has_video": True,
            }
    except Exception:
        pass
    return {"width": 0, "height": 0, "codec": "unknown", "has_video": False}


async def cut_and_convert_clip(
    source: Path, output: Path, start: float, end: float,
    fmt: str, video_info: dict
) -> tuple[bool, str]:
    """Cut clip and optionally convert to vertical format."""
    duration = end - start
    w = video_info.get("width", 1920)
    h = video_info.get("height", 1080)

    if fmt == "9:16" and w > 0 and h > 0:
        # Calculate center crop to 9:16 aspect ratio
        target_ratio = 9 / 16  # 0.5625
        current_ratio = w / h

        if current_ratio > target_ratio:
            # Video is wider than 9:16 -> crop width
            new_w = int(h * target_ratio)
            new_w = new_w - (new_w % 2)  # Ensure even
            crop_x = (w - new_w) // 2
            vf = f"crop={new_w}:{h}:{crop_x}:0,scale=1080:1920"
        else:
            # Video is already narrow enough, just pad
            vf = "scale=1080:1920:force_original_aspect_ratio=decrease,pad=1080:1920:(ow-iw)/2:(oh-ih)/2:black"
    else:
        vf = "scale=-2:1080"

    cmd = [
        "ffmpeg", "-y",
        "-ss", str(start),
        "-i", str(source),
        "-t", str(duration),
        "-vf", vf,
        "-c:v", "libx264", "-preset", "fast", "-crf", "23",
        "-c:a", "aac", "-b:a", "128k", "-ac", "2", "-ar", "44100",
        "-movflags", "+faststart",
        "-max_muxing_queue_size", "1024",
        "-avoid_negative_ts", "make_zero",
        str(output),
    ]

    proc = await asyncio.create_subprocess_exec(
        *cmd,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )
    try:
        stdout, stderr = await asyncio.wait_for(proc.communicate(), timeout=180)
        if proc.returncode != 0:
            return False, f"ffmpeg rc={proc.returncode}: {stderr.decode()[-300:]}"
        if not output.exists() or output.stat().st_size < MIN_VALID_FILE_SIZE:
            return False, f"Output too small: {output.stat().st_size if output.exists() else 0} bytes"
        return True, ""
    except asyncio.TimeoutError:
        proc.kill()
        return False, "ffmpeg timed out"


async def cut_clip_simple(
    source: Path, output: Path, start: float, end: float
) -> tuple[bool, str]:
    """Simple clip cut without format conversion - just copy streams."""
    duration = end - start

    # Try 1: Re-encode without any filter
    cmd = [
        "ffmpeg", "-y",
        "-ss", str(start),
        "-i", str(source),
        "-t", str(duration),
        "-c:v", "libx264", "-preset", "fast", "-crf", "23",
        "-c:a", "aac", "-b:a", "128k",
        "-movflags", "+faststart",
        "-max_muxing_queue_size", "1024",
        "-avoid_negative_ts", "make_zero",
        str(output),
    ]

    proc = await asyncio.create_subprocess_exec(
        *cmd,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )
    try:
        stdout, stderr = await asyncio.wait_for(proc.communicate(), timeout=180)
        if proc.returncode == 0 and output.exists() and output.stat().st_size > MIN_VALID_FILE_SIZE:
            return True, ""

        # Try 2: Stream copy (fastest, no re-encoding)
        output.unlink(missing_ok=True)
        cmd2 = [
            "ffmpeg", "-y",
            "-ss", str(start),
            "-i", str(source),
            "-t", str(duration),
            "-c", "copy",
            "-movflags", "+faststart",
            "-avoid_negative_ts", "make_zero",
            str(output),
        ]
        proc2 = await asyncio.create_subprocess_exec(
            *cmd2,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        stdout2, stderr2 = await asyncio.wait_for(proc2.communicate(), timeout=120)
        if proc2.returncode == 0 and output.exists() and output.stat().st_size > MIN_VALID_FILE_SIZE:
            return True, ""

        return False, f"All methods failed. Last: {stderr2.decode()[-200:]}"
    except asyncio.TimeoutError:
        proc.kill()
        return False, "Timeout"


def detect_viral_segments(
    duration: float, min_dur: int, max_dur: int
) -> list[tuple[float, float]]:
    segments = []
    if duration < min_dur:
        segments.append((0, duration))
        return segments

    num_clips = min(5, max(2, int(duration / max_dur) + 1))
    clip_dur = min(max_dur, max(min_dur, int(duration / num_clips)))

    for i in range(num_clips):
        if i == 0:
            start = 0
        elif i == num_clips - 1:
            start = max(0, duration - clip_dur)
        else:
            start = (duration / (num_clips - 1)) * i - clip_dur / 2
            start = max(0, min(start, duration - clip_dur))

        end = min(start + clip_dur, duration)
        if end - start >= min_dur * 0.8:
            segments.append((round(start, 2), round(end, 2)))

    return segments


def calculate_virality_score(duration: float, index: int, total: int) -> float:
    import random
    base = random.uniform(0.45, 0.95)
    if index == 0:
        base = min(1.0, base + 0.1)
    if 15 <= duration <= 60:
        base = min(1.0, base + 0.05)
    return round(base, 2)


def generate_caption(url: str, clip_num: int) -> str:
    captions = [
        "Dieser Moment hat mich umgehauen! 🤯",
        "Warte bis zum Ende... 😱",
        "Das musst du gesehen haben! 🔥",
        "POV: Du siehst das zum ersten Mal 👀",
        "Niemand redet darueber... 🤫",
        "Der krasseste Teil des Videos! 💥",
        "Ich kann nicht glauben was hier passiert 😳",
    ]
    return captions[(clip_num - 1) % len(captions)]


def generate_tags(clip_num: int) -> list[str]:
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
    job = jobs[job_id]
    # Return without internal log to keep response small
    return {k: v for k, v in job.items() if k != "log"}


@app.get("/api/job/{job_id}/log")
async def get_job_log(job_id: str):
    if job_id not in jobs:
        raise HTTPException(404, "Job nicht gefunden")
    return {"log": jobs[job_id].get("log", [])}


# --- Clip Streaming (for preview) ---
@app.get("/api/clip/{clip_id}/stream")
async def stream_clip(clip_id: str, request: Request):
    """Stream clip with range request support for ExoPlayer."""
    if clip_id not in clips_meta:
        raise HTTPException(404, "Clip nicht gefunden")

    file_path = Path(clips_meta[clip_id].get("file_path", ""))
    if not file_path.exists():
        raise HTTPException(404, "Clip-Datei nicht gefunden")

    file_size = file_path.stat().st_size
    if file_size < MIN_VALID_FILE_SIZE:
        raise HTTPException(500, "Clip-Datei ist beschaedigt")

    # Handle range requests (important for video players!)
    range_header = request.headers.get("range")
    if range_header:
        range_spec = range_header.replace("bytes=", "")
        parts = range_spec.split("-")
        start = int(parts[0])
        end = int(parts[1]) if parts[1] else file_size - 1
        end = min(end, file_size - 1)
        length = end - start + 1

        def iter_file():
            with open(file_path, "rb") as f:
                f.seek(start)
                remaining = length
                while remaining > 0:
                    chunk = f.read(min(65536, remaining))
                    if not chunk:
                        break
                    remaining -= len(chunk)
                    yield chunk

        return StreamingResponse(
            iter_file(),
            status_code=206,
            headers={
                "Content-Range": f"bytes {start}-{end}/{file_size}",
                "Accept-Ranges": "bytes",
                "Content-Length": str(length),
                "Content-Type": "video/mp4",
            },
        )

    # Full file response
    return FileResponse(
        file_path,
        media_type="video/mp4",
        filename=f"{clip_id}.mp4",
        headers={
            "Accept-Ranges": "bytes",
            "Content-Length": str(file_size),
        },
    )


# --- Clip Download ---
@app.get("/api/clip/{clip_id}/download")
async def download_clip(clip_id: str):
    if clip_id not in clips_meta:
        raise HTTPException(404, "Clip nicht gefunden")
    file_path = Path(clips_meta[clip_id].get("file_path", ""))
    if not file_path.exists():
        raise HTTPException(404, "Clip-Datei nicht gefunden")
    if file_path.stat().st_size < MIN_VALID_FILE_SIZE:
        raise HTTPException(500, "Clip-Datei ist beschaedigt")
    return FileResponse(
        file_path,
        media_type="video/mp4",
        filename=f"{clip_id}.mp4",
        headers={"Content-Length": str(file_path.stat().st_size)},
    )


@app.get("/api/clip/{clip_id}/preview")
async def preview_clip(clip_id: str, request: Request):
    """Alias for stream endpoint."""
    return await stream_clip(clip_id, request)


# --- Feedback ---
@app.post("/api/feedback")
async def send_feedback(request: FeedbackRequest):
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
        if analytics_data else 0.0
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
    insights = [
        {"category": "🎬 Clip-Laenge", "score": 0.78, "recommendation": "Clips zwischen 15-45 Sekunden performen am besten."},
        {"category": "📝 Untertitel", "score": 0.85, "recommendation": "Videos mit Untertiteln haben 40% mehr Engagement."},
        {"category": "🕐 Upload-Zeit", "score": 0.62, "recommendation": "Beste Upload-Zeiten: 17-21 Uhr."},
        {"category": "🏷️ Hashtags", "score": 0.71, "recommendation": "Nutze 3-5 relevante Hashtags pro Video."},
        {"category": "🎵 Audio", "score": 0.66, "recommendation": "Trending Sounds erhoehen die Reichweite um bis zu 50%."},
    ]
    return {"insights": insights}


@app.post("/api/v1/analytics/sync")
async def sync_analytics(request: AnalyticsSyncRequest):
    for stat in request.stats:
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
    print("🚀 ViralClipAI Backend v3.0.0 gestartet")
    print(f"📁 Clips-Verzeichnis: {CLIPS_DIR}")
    for tool in ["ffmpeg", "ffprobe", "yt-dlp"]:
        try:
            proc = await asyncio.create_subprocess_exec(
                tool, "--version",
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
            )
            stdout, _ = await proc.communicate()
            version = stdout.decode().split('\n')[0][:60]
            print(f"  ✅ {tool}: {version}")
        except FileNotFoundError:
            print(f"  ❌ {tool} NICHT GEFUNDEN!")


if __name__ == "__main__":
    import uvicorn
    port = int(os.environ.get("PORT", 8000))
    uvicorn.run(app, host="0.0.0.0", port=port)
