import uuid, os, time, asyncio, logging
from datetime import datetime, timedelta
from contextlib import asynccontextmanager
from fastapi import FastAPI, BackgroundTasks, HTTPException
from fastapi.responses import FileResponse
from pydantic import BaseModel
from typing import Optional
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from app.services.pipeline import ProcessingPipeline
from app.selfheal.engine import SelfHealingEngine
from app.routers.analytics import router as analytics_router

logging.basicConfig(level=logging.INFO)
log = logging.getLogger("viralclip")

pipeline = ProcessingPipeline()
healer = SelfHealingEngine()
jobs = {}
CLIP_DIR = "/tmp/viralclip_clips"
CLIP_TTL = 3600
os.makedirs(CLIP_DIR, exist_ok=True)

MAX_RETRY_DEPTH = 2  # Prevent infinite recursion from self-healing


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


def cleanup_old_clips():
    now = time.time()
    for f in os.listdir(CLIP_DIR):
        fp = os.path.join(CLIP_DIR, f)
        if os.path.isfile(fp) and now - os.path.getmtime(fp) > CLIP_TTL:
            try:
                os.remove(fp)
                log.info(f"Cleaned up expired clip: {f}")
            except OSError as e:
                log.warning(f"Could not remove {f}: {e}")


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup
    scheduler = AsyncIOScheduler()
    scheduler.add_job(cleanup_old_clips, "interval", minutes=5)
    scheduler.start()
    healer.start()
    log.info("ViralClip AI Server started")
    yield
    # Shutdown
    scheduler.shutdown()
    log.info("ViralClip AI Server stopped")


app = FastAPI(title="ViralClip AI Server", version="2.0.0", lifespan=lifespan)

# Analytics & ML Feedback Loop
app.include_router(analytics_router)


@app.get("/health")
async def health():
    return {"status": "ok", "version": "2.0.0", "ai_models_loaded": pipeline.models_ready()}


@app.post("/api/process")
async def process_video(req: ProcessRequest, bg: BackgroundTasks):
    job_id = str(uuid.uuid4())[:8]
    jobs[job_id] = {"job_id": job_id, "status": "queued", "progress": 0, "clips": [], "error": None}
    bg.add_task(run_pipeline, job_id, req, 0)
    return {"job_id": job_id, "status": "queued", "message": "Verarbeitung gestartet"}


async def run_pipeline(job_id: str, req: ProcessRequest, retry_depth: int = 0):
    try:
        # Create job-specific subdirectory to avoid file conflicts
        job_dir = os.path.join(CLIP_DIR, job_id)
        os.makedirs(job_dir, exist_ok=True)

        jobs[job_id].update(status="downloading", progress=5)
        video_path = await pipeline.download_video(req.url, job_dir)

        jobs[job_id].update(status="analyzing", progress=20)
        segments = await pipeline.analyze_and_cut(video_path, req.min_duration, req.max_duration)

        if not segments:
            jobs[job_id].update(status="error", error="Keine passenden Clips gefunden")
            return

        clips = []
        for i, seg in enumerate(segments):
            pct = 40 + int(50 * (i + 1) / max(1, len(segments)))
            jobs[job_id].update(status="cutting", progress=pct)
            clip_id = f"{job_id}_{i}"
            clip_path = os.path.join(CLIP_DIR, f"{clip_id}.mp4")

            await pipeline.create_clip(video_path, seg, clip_path, req.format)

            caption = ""
            if req.auto_subtitle:
                jobs[job_id]["status"] = "subtitling"
                await pipeline.add_subtitles(clip_path)

            if req.auto_caption:
                jobs[job_id]["status"] = "captioning"
                caption = await pipeline.generate_caption(seg)
                await pipeline.burn_caption(clip_path, caption)

            if req.broll_enabled:
                jobs[job_id]["status"] = "broll"
                await pipeline.add_broll(clip_path, seg)

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
                "has_broll": req.broll_enabled,
                "expires_at": (datetime.now() + timedelta(hours=1)).isoformat(),
                "tags": seg.get("tags", [])
            })

        clips.sort(key=lambda c: c["virality_score"], reverse=True)
        jobs[job_id].update(status="done", progress=100, clips=clips)
        log.info(f"Job {job_id} completed: {len(clips)} clips")

    except Exception as e:
        log.error(f"Pipeline error for job {job_id}: {e}")
        healer.report_error("pipeline", str(e))

        if retry_depth < MAX_RETRY_DEPTH:
            fix = await healer.attempt_fix("pipeline", str(e))
            if fix.get("retry"):
                log.info(f"Self-heal retry #{retry_depth + 1} for job {job_id}")
                await run_pipeline(job_id, req, retry_depth + 1)
                return

        jobs[job_id].update(status="error", error=str(e))


@app.get("/api/job/{job_id}")
async def job_status(job_id: str):
    if job_id not in jobs:
        raise HTTPException(404, "Job not found")
    return jobs[job_id]


@app.get("/api/clip/{clip_id}/download")
async def download_clip(clip_id: str):
    path = os.path.join(CLIP_DIR, f"{clip_id}.mp4")
    if not os.path.exists(path):
        raise HTTPException(404, "Clip expired or not found")
    return FileResponse(path, filename=f"ViralClip_{clip_id}.mp4", media_type="video/mp4")


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


@app.get("/api/health/detailed")
async def detailed_health():
    """Detailed health check including self-healing status."""
    return {
        "status": "ok",
        "version": "2.1.0",
        "ai_models_loaded": pipeline.models_ready(),
        "self_healing": healer.get_health_report(),
        "active_jobs": len([j for j in jobs.values() if j["status"] not in ("done", "error")]),
        "total_jobs": len(jobs),
    }
