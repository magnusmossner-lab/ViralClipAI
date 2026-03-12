"""
ViralClip AI v5.3.0 - Backend Server
- Content-based clip detection (language, keywords, mood)
- Karaoke subtitles with customization
- Hook captions with white box
- Auto-cut with face zoom
- Social media ready (9:16)
- Node.js runtime for yt-dlp YouTube support
- v5.3.0: Stable connections, ping endpoint, streaming downloads
"""
import uuid, os, time, asyncio, logging, shutil, subprocess
from datetime import datetime, timedelta
from fastapi import FastAPI, BackgroundTasks, HTTPException, Request, UploadFile, File, Form
from fastapi.responses import FileResponse, StreamingResponse, Response
from pydantic import BaseModel
from typing import Optional, List
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from pipeline import ProcessingPipeline

logging.basicConfig(level=logging.INFO)
log = logging.getLogger("viralclip")
app = FastAPI(title="ViralClip AI Server", version="5.10.0")
pipeline = ProcessingPipeline()
CLIP_DIR = "/tmp/viralclip_clips"
JOBS_DIR = "/tmp/viralclip_jobs"
os.makedirs(JOBS_DIR, exist_ok=True)

def _job_path(job_id: str) -> str:
    return os.path.join(JOBS_DIR, f"{job_id}.json")

def jobs_get(job_id: str):
    """Read job from persistent file store."""
    p = _job_path(job_id)
    try:
        with open(p) as f:
            return json.load(f)
    except Exception:
        return None

def jobs_set(job_id: str, data: dict):
    """Write job to persistent file store."""
    try:
        with open(_job_path(job_id), "w") as f:
            json.dump(data, f)
    except Exception as e:
        log.error(f"Failed to persist job {job_id}: {e}")

def jobs_update(job_id: str, **kwargs):
    """Update fields in a persisted job."""
    data = jobs_get(job_id) or {}
    data.update(kwargs)
    jobs_set(job_id, data)

def jobs_contains(job_id: str) -> bool:
    return os.path.exists(_job_path(job_id))
CLIP_TTL = 3600
os.makedirs(CLIP_DIR, exist_ok=True)


class ProcessRequest(BaseModel):
    url: str
    min_duration: int = 30
    max_duration: int = 180
    format: str = "9:16"
    auto_cut: bool = True
    auto_caption: bool = True
    auto_subtitle: bool = True
    # Content Filters
    language: str = "de"
    keywords: List[str] = []
    mood: str = "all"
    viral_sensitivity: str = "medium"
    # Subtitle Config
    subtitle_font: str = "Anton"
    subtitle_size: str = "large"
    subtitle_color: str = "#FFFFFF"
    subtitle_highlight: str = "#00FF88"
    subtitle_style: str = "karaoke"
    # Caption
    caption_text: str = ""


class FeedbackRequest(BaseModel):
    clip_id: str
    rating: int
    downloaded: bool = False


def cleanup_old_clips():
    now = time.time()
    for f in os.listdir(CLIP_DIR):
        fp = os.path.join(CLIP_DIR, f)
        if os.path.isfile(fp) and now - os.path.getmtime(fp) > CLIP_TTL:
            os.remove(fp)


@app.on_event("startup")
async def startup():
    s = AsyncIOScheduler()
    s.add_job(cleanup_old_clips, "interval", minutes=5)
    s.start()
    # Log system info
    node_path = shutil.which("node")
    ytdlp_path = shutil.which("yt-dlp")
    log.info(f"ViralClip AI v5.3.0 started")
    log.info(f"Node.js: {node_path or 'NOT FOUND'}")
    log.info(f"yt-dlp: {ytdlp_path or 'NOT FOUND'}")
    if node_path:
        try:
            ver = subprocess.check_output([node_path, "--version"], timeout=5).decode().strip()
            log.info(f"Node.js version: {ver}")
        except Exception:
            pass


@app.get("/health")
async def health():
    node_available = shutil.which("node") is not None
    return {
        "status": "ok",
        "version": "5.10.0",
        "ai_models_loaded": pipeline.models_ready(),
        "node_js": node_available
    }


@app.get("/ping")
async def ping():
    """Lightweight keep-alive endpoint. Returns instantly to prevent Railway from sleeping."""
    return {"pong": True, "ts": int(time.time())}


@app.post("/api/process")
async def process_video(req: ProcessRequest, bg: BackgroundTasks):
    job_id = str(uuid.uuid4())[:8]
    jobs_set(job_id, {"job_id": job_id, "status": "queued", "progress": 0, "clips": [], "error": None})
    bg.add_task(run_pipeline, job_id, req)
    return {"job_id": job_id, "status": "queued", "message": "Verarbeitung gestartet"}


async def run_pipeline(job_id, req):
    try:
        # 1. Download
        jobs_update(job_id, status="downloading", progress=5)
        video_path = await pipeline.download_video(req.url, CLIP_DIR)

        # 2. Transcribe + Analyze with content filters
        jobs_update(job_id, status="analyzing", progress=15)
        transcript = await pipeline.transcribe_video(video_path, req.language)

        # 3. Find viral segments
        jobs_update(job_id, status="analyzing", progress=25)
        segments = await pipeline.analyze_and_cut(
            video_path, req.min_duration, req.max_duration,
            language=req.language, keywords=req.keywords,
            mood=req.mood, viral_sensitivity=req.viral_sensitivity,
            transcript=transcript  # FIX: reuse transcript, avoid double transcription
        )

        subtitle_config = {
            "font": req.subtitle_font,
            "size": req.subtitle_size,
            "color": req.subtitle_color,
            "highlight": req.subtitle_highlight,
            "style": req.subtitle_style
        }

        clips = []
        for i, seg in enumerate(segments):
            pct = 30 + int(60 * (i + 1) / max(1, len(segments)))
            jobs_update(job_id, progress=pct)
            clip_id = f"{job_id}_{i}"
            clip_path = os.path.join(CLIP_DIR, f"{clip_id}.mp4")

            # 4. Create clip (9:16 crop + audio)
            jobs_update(job_id, status="cutting")
            await pipeline.create_clip(video_path, seg, clip_path, req.format, req.auto_cut)

            # 5. Add karaoke subtitles
            caption = ""
            if req.auto_subtitle:
                jobs_update(job_id, status="subtitling")
                # Get transcript for this specific segment
                seg_transcript = {"segments": []}
                for ts in transcript.get("segments", []):
                    if ts["start"] >= seg["start"] and ts["end"] <= seg["end"]:
                        # Adjust timestamps relative to clip start
                        adjusted = dict(ts)
                        adjusted["start"] -= seg["start"]
                        adjusted["end"] -= seg["start"]
                        if "words" in ts:
                            adjusted["words"] = []
                            for w in ts["words"]:
                                aw = dict(w)
                                aw["start"] -= seg["start"]
                                aw["end"] -= seg["start"]
                                adjusted["words"].append(aw)
                        seg_transcript["segments"].append(adjusted)
                await pipeline.add_karaoke_subtitles(clip_path, seg_transcript, subtitle_config)

            # 6. Add hook caption
            if req.auto_caption:
                jobs_update(job_id, status="captioning")
                caption = req.caption_text if req.caption_text else await pipeline.generate_caption(seg)
                await pipeline.burn_caption(clip_path, caption)

            # 7. Auto-cut face zoom
            if req.auto_cut:
                jobs_update(job_id, status="auto_cut")
                await pipeline.apply_auto_cut(clip_path, seg)

            # 8. Calculate virality score
            score = await pipeline.calculate_virality(seg)

            clips.append({
                "id": clip_id,
                "title": seg.get("title", f"Clip {i + 1}"),
                "start_time": seg["start"],
                "end_time": seg["end"],
                "duration": seg["end"] - seg["start"],
                "virality_score": score,
                "caption": caption,
                "preview_url": f"/api/clip/{clip_id}/preview",
                "download_url": f"/api/clip/{clip_id}/download",
                "has_subtitles": req.auto_subtitle,
                "has_caption": req.auto_caption,
                "transcript": seg.get("text", ""),
                "expires_at": (datetime.now() + timedelta(hours=1)).isoformat(),
                "tags": seg.get("tags", []),
                "matched_keywords": seg.get("matched_keywords", [])
            })

        clips.sort(key=lambda c: c["virality_score"], reverse=True)
        jobs_update(job_id, status="done", progress=100, clips=clips)

    except Exception as e:
        log.error(f"Pipeline error: {e}")
        jobs_update(job_id, status="error", error=str(e))


@app.get("/api/job/{job_id}")
async def job_status(job_id: str):
    if not jobs_contains(job_id):
        raise HTTPException(404, "Job nicht gefunden")
    return jobs_get(job_id)


@app.get("/api/clip/{clip_id}/preview")
async def preview_clip(clip_id: str):
    """Stream clip for preview (with audio!)"""
    path = os.path.join(CLIP_DIR, f"{clip_id}.mp4")
    if not os.path.exists(path):
        raise HTTPException(404, "Clip expired")
    return FileResponse(path, media_type="video/mp4")


@app.get("/api/clip/{clip_id}/download")
async def download_clip(clip_id: str, request: Request):
    """Download clip with Range support for resumable downloads."""
    path = os.path.join(CLIP_DIR, f"{clip_id}.mp4")
    if not os.path.exists(path):
        raise HTTPException(404, "Clip expired or not found")

    file_size = os.path.getsize(path)
    range_header = request.headers.get("Range")

    if range_header:
        # Parse range: "bytes=start-end"
        try:
            range_val = range_header.replace("bytes=", "")
            parts = range_val.split("-")
            start = int(parts[0]) if parts[0] else 0
            end = int(parts[1]) if parts[1] else file_size - 1
            end = min(end, file_size - 1)
            chunk_size = end - start + 1

            def file_iterator(filepath, offset, length, chunk=1024 * 1024):
                with open(filepath, "rb") as f:
                    f.seek(offset)
                    remaining = length
                    while remaining > 0:
                        read_size = min(chunk, remaining)
                        data = f.read(read_size)
                        if not data:
                            break
                        remaining -= len(data)
                        yield data

            headers = {
                "Content-Range": f"bytes {start}-{end}/{file_size}",
                "Accept-Ranges": "bytes",
                "Content-Length": str(chunk_size),
                "Content-Disposition": f'attachment; filename="ViralClip_{clip_id}.mp4"',
            }
            return StreamingResponse(
                file_iterator(path, start, chunk_size),
                status_code=206,
                headers=headers,
                media_type="video/mp4"
            )
        except Exception as e:
            log.warning(f"Range parse error: {e}, falling back to full response")

    # Full file response with streaming (1MB chunks)
    def full_file_iterator(filepath, chunk=1024 * 1024):
        with open(filepath, "rb") as f:
            while True:
                data = f.read(chunk)
                if not data:
                    break
                yield data

    headers = {
        "Accept-Ranges": "bytes",
        "Content-Length": str(file_size),
        "Content-Disposition": f'attachment; filename="ViralClip_{clip_id}.mp4"',
    }
    return StreamingResponse(
        full_file_iterator(path),
        headers=headers,
        media_type="video/mp4"
    )


@app.post("/api/feedback")
async def feedback(req: FeedbackRequest):
    pipeline.record_feedback(req.clip_id, req.rating, req.downloaded)
    return {"status": "ok"}


@app.delete("/api/clip/{clip_id}")
async def delete_clip(clip_id: str):
    p = os.path.join(CLIP_DIR, f"{clip_id}.mp4")
    if os.path.exists(p):
        os.remove(p)
    return {"status": "deleted"}


@app.post("/api/upload")
async def upload_video(
    bg: BackgroundTasks,
    file: UploadFile = File(...),
    min_duration: int = Form(30),
    max_duration: int = Form(180),
    format: str = Form("9:16"),
    auto_cut: str = Form("true"),
    auto_caption: str = Form("true"),
    auto_subtitle: str = Form("true"),
    language: str = Form("de"),
    keywords: str = Form(""),
    mood: str = Form("all"),
    viral_sensitivity: str = Form("medium"),
    subtitle_font: str = Form("Anton"),
    subtitle_size: str = Form("large"),
    subtitle_color: str = Form("#FFFFFF"),
    subtitle_highlight: str = Form("#00FF88"),
    subtitle_style: str = Form("karaoke"),
    caption_text: str = Form("")
):
    """Accept uploaded video file from gallery and process it."""
    job_id = str(uuid.uuid4())[:8]
    
    # Stream uploaded file to disk in 1MB chunks (avoids OOM for large files)
    upload_path = os.path.join(CLIP_DIR, f"upload_{job_id}.mp4")
    MAX_SIZE = 500 * 1024 * 1024  # 500 MB limit
    file_size = 0
    with open(upload_path, "wb") as out:
        while True:
            chunk = await file.read(1024 * 1024)  # 1MB chunks
            if not chunk:
                break
            file_size += len(chunk)
            if file_size > MAX_SIZE:
                out.close()
                os.remove(upload_path)
                raise HTTPException(413, "Video zu groß. Maximale Dateigröße: 500MB. Bitte ein kürzeres Video verwenden.")
            out.write(chunk)
    
    # Parse parameters
    kw_list = [k.strip() for k in keywords.split(",") if k.strip()]
    
    jobs_set(job_id, {"job_id": job_id, "status": "queued", "progress": 0, "clips": [], "error": None})
    
    req = ProcessRequest(
        url="",
        min_duration=min_duration,
        max_duration=max_duration,
        format=format,
        auto_cut=auto_cut.lower() == "true",
        auto_caption=auto_caption.lower() == "true",
        auto_subtitle=auto_subtitle.lower() == "true",
        language=language,
        keywords=kw_list,
        mood=mood,
        viral_sensitivity=viral_sensitivity,
        subtitle_font=subtitle_font,
        subtitle_size=subtitle_size,
        subtitle_color=subtitle_color,
        subtitle_highlight=subtitle_highlight,
        subtitle_style=subtitle_style,
        caption_text=caption_text
    )
    
    bg.add_task(run_pipeline_from_file, job_id, upload_path, req)
    return {"job_id": job_id, "status": "queued", "message": "Upload empfangen, Verarbeitung gestartet"}


async def run_pipeline_from_file(job_id, video_path, req):
    """Same as run_pipeline but skips download step (file already on disk)."""
    try:
        # 1. Transcribe + Analyze
        jobs_update(job_id, status="analyzing", progress=10)
        transcript = await pipeline.transcribe_video(video_path, req.language)

        # 2. Find viral segments
        jobs_update(job_id, status="analyzing", progress=25)
        segments = await pipeline.analyze_and_cut(
            video_path, req.min_duration, req.max_duration,
            language=req.language, keywords=req.keywords,
            mood=req.mood, viral_sensitivity=req.viral_sensitivity,
            transcript=transcript  # FIX: reuse transcript, avoid double transcription
        )

        subtitle_config = {
            "font": req.subtitle_font,
            "size": req.subtitle_size,
            "color": req.subtitle_color,
            "highlight": req.subtitle_highlight,
            "style": req.subtitle_style
        }

        clips = []
        for i, seg in enumerate(segments):
            pct = 30 + int(60 * (i + 1) / max(1, len(segments)))
            jobs_update(job_id, progress=pct)
            clip_id = f"{job_id}_{i}"
            clip_path = os.path.join(CLIP_DIR, f"{clip_id}.mp4")

            jobs_update(job_id, status="cutting")
            await pipeline.create_clip(video_path, seg, clip_path, req.format, req.auto_cut)

            caption = ""
            if req.auto_subtitle:
                jobs_update(job_id, status="subtitling")
                seg_transcript = {"segments": []}
                for ts in transcript.get("segments", []):
                    if ts["start"] >= seg["start"] and ts["end"] <= seg["end"]:
                        adjusted = dict(ts)
                        adjusted["start"] -= seg["start"]
                        adjusted["end"] -= seg["start"]
                        if "words" in ts:
                            adjusted["words"] = []
                            for w in ts["words"]:
                                aw = dict(w)
                                aw["start"] -= seg["start"]
                                aw["end"] -= seg["start"]
                                adjusted["words"].append(aw)
                        seg_transcript["segments"].append(adjusted)
                await pipeline.add_karaoke_subtitles(clip_path, seg_transcript, subtitle_config)

            if req.auto_caption:
                jobs_update(job_id, status="captioning")
                caption = req.caption_text if req.caption_text else await pipeline.generate_caption(seg)
                await pipeline.burn_caption(clip_path, caption)

            if req.auto_cut:
                jobs_update(job_id, status="auto_cut")
                await pipeline.apply_auto_cut(clip_path, seg)

            score = await pipeline.calculate_virality(seg)

            clips.append({
                "id": clip_id,
                "title": seg.get("title", f"Clip {i + 1}"),
                "start_time": seg["start"],
                "end_time": seg["end"],
                "duration": seg["end"] - seg["start"],
                "virality_score": score,
                "caption": caption,
                "preview_url": f"/api/clip/{clip_id}/preview",
                "download_url": f"/api/clip/{clip_id}/download",
                "has_subtitles": req.auto_subtitle,
                "has_caption": req.auto_caption,
                "transcript": seg.get("text", ""),
                "expires_at": (datetime.now() + timedelta(hours=1)).isoformat(),
                "tags": seg.get("tags", []),
                "matched_keywords": seg.get("matched_keywords", [])
            })

        clips.sort(key=lambda c: c["virality_score"], reverse=True)
        jobs_update(job_id, status="done", progress=100, clips=clips)

    except Exception as e:
        log.error(f"Pipeline error (upload): {e}")
        jobs_update(job_id, status="error", error=str(e))
    finally:
        # Clean up uploaded source file
        if os.path.exists(video_path):
            os.remove(video_path)


@app.get("/api/app/latest")
async def app_latest():
    """Returns the latest APK version info for auto-update."""
    return {
        "version": "5.9.0",
        "version_code": 590,
        "download_url": "",
        "release_notes": "YouTube-Downloads repariert, Galerie-Upload funktioniert jetzt",
        "force_update": False
    }
