"""
ViralClip AI v5.4.0 - Backend Server
- Content-based clip detection (language, keywords, mood)
- Karaoke subtitles with customization
- Hook captions with white box
- Auto-cut with face zoom
- Social media ready (9:16)
"""
import uuid, os, time, asyncio, logging
from datetime import datetime, timedelta
from fastapi import FastAPI, BackgroundTasks, HTTPException, UploadFile, File, Form, Request
from fastapi.responses import FileResponse, StreamingResponse
from pydantic import BaseModel
from typing import Optional, List
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from app.services.pipeline import ProcessingPipeline
from app.selfheal.engine import SelfHealingEngine

logging.basicConfig(level=logging.INFO)
log = logging.getLogger("viralclip")
app = FastAPI(title="ViralClip AI Server", version="5.4.0")
pipeline = ProcessingPipeline()
healer = SelfHealingEngine()
jobs = {}
CLIP_DIR = "/tmp/viralclip_clips"
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
    healer.start()

@app.get("/health")
async def health():
    return {"status": "ok", "version": "5.4.0", "ai_models_loaded": pipeline.models_ready()}

# ─── Keep-alive ping ───
@app.get("/ping")
async def ping():
    return {"pong": True, "ts": time.time()}

@app.post("/api/process")
async def process_video(req: ProcessRequest, bg: BackgroundTasks):
    job_id = str(uuid.uuid4())[:8]
    jobs[job_id] = {"job_id": job_id, "status": "queued", "progress": 0, "clips": [], "error": None}
    bg.add_task(run_pipeline, job_id, req)
    return {"job_id": job_id, "status": "queued", "message": "Verarbeitung gestartet"}

async def run_pipeline(job_id, req):
    try:
        # 1. Download
        jobs[job_id].update(status="downloading", progress=5)
        video_path = await pipeline.download_video(req.url, CLIP_DIR)

        # 2. Transcribe + Analyze with content filters
        jobs[job_id].update(status="analyzing", progress=15)
        transcript = await pipeline.transcribe_video(video_path, req.language)

        # 3. Find viral segments
        jobs[job_id].update(status="analyzing", progress=25)
        segments = await pipeline.analyze_and_cut(
            video_path, req.min_duration, req.max_duration,
            language=req.language, keywords=req.keywords,
            mood=req.mood, viral_sensitivity=req.viral_sensitivity
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
            jobs[job_id].update(progress=pct)
            clip_id = f"{job_id}_{i}"
            clip_path = os.path.join(CLIP_DIR, f"{clip_id}.mp4")

            # 4. Create clip (9:16 crop + audio)
            jobs[job_id]["status"] = "cutting"
            await pipeline.create_clip(video_path, seg, clip_path, req.format, req.auto_cut)

            # 5. Add karaoke subtitles
            caption = ""
            if req.auto_subtitle:
                jobs[job_id]["status"] = "subtitling"
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

            # 6. Add hook caption
            if req.auto_caption:
                jobs[job_id]["status"] = "captioning"
                caption = req.caption_text if req.caption_text else await pipeline.generate_caption(seg)
                await pipeline.burn_caption(clip_path, caption)

            # 7. Auto-cut face zoom
            if req.auto_cut:
                jobs[job_id]["status"] = "auto_cut"
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
        jobs[job_id].update(status="done", progress=100, clips=clips)

    except Exception as e:
        log.error(f"Pipeline error: {e}")
        healer.report_error("pipeline", str(e))
        fix = await healer.attempt_fix("pipeline", str(e))
        if fix.get("retry"):
            await run_pipeline(job_id, req)
        else:
            jobs[job_id].update(status="error", error=str(e))

# ─── v5.4.0: Gallery Video Upload ───
# FIX: Added BackgroundTasks param, initialized jobs[job_id], replaced
#      non-existent pipeline.start_local_job() with run_pipeline_local()
@app.post("/api/upload")
async def upload_video(
    bg: BackgroundTasks,
    file: UploadFile = File(...),
    min_duration: int = Form(30),
    max_duration: int = Form(180),
    format: str = Form("9:16"),
    auto_cut: bool = Form(True),
    auto_caption: bool = Form(True),
    auto_subtitle: bool = Form(True),
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
    """Process an uploaded video file (from gallery) instead of YouTube URL."""
    job_id = str(uuid.uuid4())[:8]

    # Save uploaded file
    upload_path = os.path.join(CLIP_DIR, f"upload_{job_id}.mp4")
    content = await file.read()
    with open(upload_path, "wb") as f:
        f.write(content)

    # FIX: Initialize job BEFORE starting background task so polling works immediately
    jobs[job_id] = {"job_id": job_id, "status": "queued", "progress": 0, "clips": [], "error": None}

    kw_list = [k.strip() for k in keywords.split(",") if k.strip()]

    # FIX: Use proper background task instead of non-existent pipeline.start_local_job()
    bg.add_task(run_pipeline_local, job_id, upload_path, {
        "min_duration": min_duration,
        "max_duration": max_duration,
        "format": format,
        "auto_cut": auto_cut,
        "auto_caption": auto_caption,
        "auto_subtitle": auto_subtitle,
        "language": language,
        "keywords": kw_list,
        "mood": mood,
        "viral_sensitivity": viral_sensitivity,
        "subtitle_font": subtitle_font,
        "subtitle_size": subtitle_size,
        "subtitle_color": subtitle_color,
        "subtitle_highlight": subtitle_highlight,
        "subtitle_style": subtitle_style,
        "caption_text": caption_text
    })

    return {"job_id": job_id, "status": "queued", "message": "Video wird verarbeitet..."}


async def run_pipeline_local(job_id: str, video_path: str, settings: dict):
    """Process an already-uploaded local video file (gallery import) – skips download step."""
    try:
        # Skip download – file already exists
        jobs[job_id].update(status="analyzing", progress=10)
        transcript = await pipeline.transcribe_video(video_path, settings["language"])

        jobs[job_id].update(status="analyzing", progress=25)
        segments = await pipeline.analyze_and_cut(
            video_path,
            settings["min_duration"],
            settings["max_duration"],
            language=settings["language"],
            keywords=settings["keywords"],
            mood=settings["mood"],
            viral_sensitivity=settings["viral_sensitivity"]
        )

        subtitle_config = {
            "font": settings["subtitle_font"],
            "size": settings["subtitle_size"],
            "color": settings["subtitle_color"],
            "highlight": settings["subtitle_highlight"],
            "style": settings["subtitle_style"]
        }

        clips = []
        for i, seg in enumerate(segments):
            pct = 30 + int(60 * (i + 1) / max(1, len(segments)))
            jobs[job_id].update(progress=pct)
            clip_id = f"{job_id}_{i}"
            clip_path = os.path.join(CLIP_DIR, f"{clip_id}.mp4")

            jobs[job_id]["status"] = "cutting"
            await pipeline.create_clip(video_path, seg, clip_path, settings["format"], settings["auto_cut"])

            caption = ""
            if settings["auto_subtitle"]:
                jobs[job_id]["status"] = "subtitling"
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

            if settings["auto_caption"]:
                jobs[job_id]["status"] = "captioning"
                caption = settings["caption_text"] if settings["caption_text"] else await pipeline.generate_caption(seg)
                await pipeline.burn_caption(clip_path, caption)

            if settings["auto_cut"]:
                jobs[job_id]["status"] = "auto_cut"
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
                "has_subtitles": settings["auto_subtitle"],
                "has_caption": settings["auto_caption"],
                "transcript": seg.get("text", ""),
                "expires_at": (datetime.now() + timedelta(hours=1)).isoformat(),
                "tags": seg.get("tags", []),
                "matched_keywords": seg.get("matched_keywords", [])
            })

        clips.sort(key=lambda c: c["virality_score"], reverse=True)
        jobs[job_id].update(status="done", progress=100, clips=clips)

    except Exception as e:
        log.error(f"Local pipeline error for job {job_id}: {e}")
        jobs[job_id].update(status="error", error=str(e))
    finally:
        # Cleanup uploaded source file
        if os.path.exists(video_path):
            try:
                os.remove(video_path)
            except Exception:
                pass


@app.get("/api/job/{job_id}")
async def job_status(job_id: str):
    if job_id not in jobs:
        raise HTTPException(404)
    return jobs[job_id]

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

# ─── v5.4.0: Auto-Update Endpoint ───
LATEST_APP_VERSION = os.getenv("LATEST_APP_VERSION", "5.4.0")
LATEST_APP_VERSION_CODE = int(os.getenv("LATEST_APP_VERSION_CODE", "11"))
LATEST_APK_URL = os.getenv("LATEST_APK_URL", "")
LATEST_CHANGELOG = os.getenv("LATEST_CHANGELOG", "Gallery Import & Auto-Update")

@app.get("/api/app/latest")
async def app_latest():
    return {
        "latest_version": LATEST_APP_VERSION,
        "latest_version_code": LATEST_APP_VERSION_CODE,
        "download_url": LATEST_APK_URL,
        "changelog": LATEST_CHANGELOG,
        "force_update": False,
        "min_supported_version": 7
    }
