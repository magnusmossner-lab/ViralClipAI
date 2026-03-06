"""Analytics API endpoints for tracking uploads, syncing stats, and ML insights."""

import json
import os
from datetime import datetime
from pathlib import Path
from typing import Optional

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from ..models.analytics import (
    UploadRecord,
    VideoPerformance,
    PerformanceInsight,
    SyncRequest,
    TrackUploadRequest,
)

router = APIRouter(prefix="/api/v1/analytics", tags=["analytics"])

# Simple JSON-file based storage (replace with real DB in production)
DATA_DIR = Path(os.getenv("DATA_DIR", "./data/analytics"))
DATA_DIR.mkdir(parents=True, exist_ok=True)

UPLOADS_FILE = DATA_DIR / "uploads.json"
PERFORMANCE_FILE = DATA_DIR / "performance.json"
MODEL_WEIGHTS_FILE = DATA_DIR / "model_weights.json"


def _load_json(path: Path) -> list:
    if path.exists():
        with open(path) as f:
            return json.load(f)
    return []


def _save_json(path: Path, data: list):
    with open(path, "w") as f:
        json.dump(data, f, indent=2, default=str)


# ─── Track Upload ─────────────────────────────────────────────
@router.post("/track-upload")
async def track_upload(req: TrackUploadRequest):
    """Called by the app after a successful upload to track it for analytics."""
    uploads = _load_json(UPLOADS_FILE)

    # Avoid duplicates
    if any(u["video_id"] == req.video_id and u["platform"] == req.platform for u in uploads):
        return {"status": "already_tracked"}

    record = {
        "video_id": req.video_id,
        "platform": req.platform,
        "title": req.title,
        "tags": req.tags,
        "uploaded_at": req.uploaded_at or datetime.utcnow().isoformat(),
    }
    uploads.append(record)
    _save_json(UPLOADS_FILE, uploads)

    return {"status": "tracked", "video_id": req.video_id}


# ─── Get Tracked Videos ──────────────────────────────────────
@router.get("/tracked-videos")
async def get_tracked_videos():
    """Returns all tracked uploaded videos."""
    uploads = _load_json(UPLOADS_FILE)
    return {"videos": uploads}


# ─── Sync Performance Stats ──────────────────────────────────
@router.post("/sync")
async def sync_performance(req: SyncRequest):
    """Receives fresh performance data from the app and triggers ML learning."""
    performance_data = _load_json(PERFORMANCE_FILE)

    for stat in req.stats:
        # Update or insert
        found = False
        for i, existing in enumerate(performance_data):
            if existing["video_id"] == stat.video_id and existing["platform"] == stat.platform:
                performance_data[i] = stat.dict()
                performance_data[i]["fetched_at"] = datetime.utcnow().isoformat()
                found = True
                break
        if not found:
            entry = stat.dict()
            entry["fetched_at"] = datetime.utcnow().isoformat()
            performance_data.append(entry)

    _save_json(PERFORMANCE_FILE, performance_data)

    # Trigger ML model update
    _update_ml_model(performance_data)

    return {"status": "synced", "videos_updated": len(req.stats)}


# ─── Get ML Insights ─────────────────────────────────────────
@router.get("/insights")
async def get_insights():
    """Returns AI-generated insights based on performance data."""
    performance_data = _load_json(PERFORMANCE_FILE)
    uploads = _load_json(UPLOADS_FILE)
    weights = _load_model_weights()

    insights = _generate_insights(performance_data, uploads, weights)
    return {"insights": [i.dict() for i in insights]}


# ─── Get Performance Summary ─────────────────────────────────
@router.get("/summary")
async def get_summary():
    """Returns aggregated performance metrics."""
    performance_data = _load_json(PERFORMANCE_FILE)

    if not performance_data:
        return {
            "total_videos": 0,
            "total_views": 0,
            "total_likes": 0,
            "avg_engagement": 0.0,
            "best_video": None,
            "platform_breakdown": {},
        }

    total_views = sum(p.get("views", 0) for p in performance_data)
    total_likes = sum(p.get("likes", 0) for p in performance_data)
    avg_engagement = (
        sum(p.get("engagement_rate", 0) for p in performance_data) / len(performance_data)
        if performance_data
        else 0.0
    )
    best = max(performance_data, key=lambda p: p.get("views", 0))

    # Platform breakdown
    platforms = {}
    for p in performance_data:
        plat = p.get("platform", "unknown")
        if plat not in platforms:
            platforms[plat] = {"videos": 0, "views": 0, "likes": 0}
        platforms[plat]["videos"] += 1
        platforms[plat]["views"] += p.get("views", 0)
        platforms[plat]["likes"] += p.get("likes", 0)

    return {
        "total_videos": len(performance_data),
        "total_views": total_views,
        "total_likes": total_likes,
        "avg_engagement": round(avg_engagement, 4),
        "best_video": best,
        "platform_breakdown": platforms,
    }


# ═══════════════════════════════════════════════════════════════
# ML FEEDBACK LOOP
# ═══════════════════════════════════════════════════════════════

def _load_model_weights() -> dict:
    """Load learned weights for clip scoring."""
    if MODEL_WEIGHTS_FILE.exists():
        with open(MODEL_WEIGHTS_FILE) as f:
            return json.load(f)

    # Default weights (initial priors)
    return {
        "hook_strength": 0.30,
        "energy_pacing": 0.20,
        "face_presence": 0.15,
        "topic_relevance": 0.15,
        "audio_clarity": 0.10,
        "visual_quality": 0.10,
        "learning_rate": 0.05,
        "total_samples": 0,
        "version": 1,
    }


def _update_ml_model(performance_data: list):
    """
    Self-improving ML feedback loop:
    Analyzes which clips performed best and adjusts scoring weights
    so future clips are generated with better parameters.
    """
    if len(performance_data) < 3:
        return  # Need minimum data for meaningful learning

    weights = _load_model_weights()
    lr = weights.get("learning_rate", 0.05)

    # Separate high-performing and low-performing videos
    sorted_by_engagement = sorted(
        performance_data,
        key=lambda p: p.get("engagement_rate", 0),
        reverse=True
    )

    n = len(sorted_by_engagement)
    top_quartile = sorted_by_engagement[:max(1, n // 4)]
    bottom_quartile = sorted_by_engagement[max(1, n - n // 4):]

    # Analyze patterns in top performers
    top_avg_views = sum(p.get("views", 0) for p in top_quartile) / len(top_quartile)
    bottom_avg_views = sum(p.get("views", 0) for p in bottom_quartile) / len(bottom_quartile)

    if top_avg_views <= 0:
        return

    # View ratio indicates how much better top performers do
    view_ratio = top_avg_views / max(bottom_avg_views, 1)

    # Adjust weights based on correlation analysis
    # Videos with high engagement -> their characteristics should be weighted higher
    top_avg_watch = sum(p.get("average_view_duration", 0) for p in top_quartile) / len(top_quartile)

    # If top videos have high average view duration -> hook_strength matters more
    if top_avg_watch > 15:  # Seconds
        weights["hook_strength"] = min(0.50, weights["hook_strength"] + lr * 0.5)
        weights["energy_pacing"] = min(0.35, weights["energy_pacing"] + lr * 0.3)

    # If top videos have high share rate -> topic_relevance matters more
    top_share_rate = sum(p.get("shares", 0) for p in top_quartile) / max(sum(p.get("views", 1) for p in top_quartile), 1)
    if top_share_rate > 0.01:  # 1% share rate is very good
        weights["topic_relevance"] = min(0.35, weights["topic_relevance"] + lr * 0.4)

    # If engagement is heavily like-driven -> visual_quality and face_presence matter
    top_like_rate = sum(p.get("likes", 0) for p in top_quartile) / max(sum(p.get("views", 1) for p in top_quartile), 1)
    if top_like_rate > 0.05:  # 5% like rate
        weights["face_presence"] = min(0.30, weights["face_presence"] + lr * 0.3)
        weights["visual_quality"] = min(0.25, weights["visual_quality"] + lr * 0.2)

    # Normalize weights to sum to 1.0
    scoring_keys = ["hook_strength", "energy_pacing", "face_presence", "topic_relevance", "audio_clarity", "visual_quality"]
    total = sum(weights[k] for k in scoring_keys)
    for k in scoring_keys:
        weights[k] = round(weights[k] / total, 4)

    # Decay learning rate over time (slower learning as model matures)
    weights["total_samples"] = len(performance_data)
    weights["learning_rate"] = max(0.005, lr * 0.98)
    weights["version"] = weights.get("version", 1) + 1
    weights["last_updated"] = datetime.utcnow().isoformat()

    with open(MODEL_WEIGHTS_FILE, "w") as f:
        json.dump(weights, f, indent=2)


def _generate_insights(performance_data: list, uploads: list, weights: dict) -> list[PerformanceInsight]:
    """Generate actionable insights from performance data."""
    insights = []

    if not performance_data:
        return [
            PerformanceInsight(
                category="Erste Schritte",
                score=0.0,
                recommendation="Lade deinen ersten Clip hoch, um personalisierte Empfehlungen zu erhalten!"
            )
        ]

    # 1. Hook Strength Analysis
    avg_watch = sum(p.get("average_view_duration", 0) for p in performance_data) / len(performance_data)
    hook_score = min(1.0, avg_watch / 30.0)  # 30s = perfect score
    hook_rec = (
        "Deine Hooks sind stark! Zuschauer bleiben durchschnittlich {:.0f}s dran.".format(avg_watch)
        if hook_score > 0.6
        else "Verbessere die ersten 3 Sekunden deiner Clips. Nutze eine provokante Frage oder einen visuellen Überraschungsmoment."
    )
    insights.append(PerformanceInsight(category="🪝 Hook-Stärke", score=round(hook_score, 2), recommendation=hook_rec))

    # 2. Engagement Analysis
    avg_eng = sum(p.get("engagement_rate", 0) for p in performance_data) / len(performance_data)
    eng_score = min(1.0, avg_eng / 0.10)  # 10% = perfect
    eng_rec = (
        "Exzellente Engagement-Rate! Deine Community interagiert stark."
        if eng_score > 0.7
        else "Füge Call-to-Actions ein: 'Kommentiere wenn...', 'Teile das mit...' um Engagement zu steigern."
    )
    insights.append(PerformanceInsight(category="💬 Engagement", score=round(eng_score, 2), recommendation=eng_rec))

    # 3. Virality Potential
    total_shares = sum(p.get("shares", 0) for p in performance_data)
    total_views = sum(p.get("views", 0) for p in performance_data)
    share_rate = total_shares / max(total_views, 1)
    viral_score = min(1.0, share_rate / 0.02)  # 2% share rate = viral
    viral_rec = (
        "Deine Clips werden aktiv geteilt – viral-potential ist hoch!"
        if viral_score > 0.5
        else "Um die Share-Rate zu erhöhen: Erstelle Clips, die Emotionen auslösen oder 'Das musst du sehen!'-Momente haben."
    )
    insights.append(PerformanceInsight(category="🚀 Viralitäts-Potenzial", score=round(viral_score, 2), recommendation=viral_rec))

    # 4. Platform Performance
    platform_data = {}
    for p in performance_data:
        plat = p.get("platform", "unknown")
        if plat not in platform_data:
            platform_data[plat] = []
        platform_data[plat].append(p)

    if len(platform_data) > 1:
        platform_scores = {}
        for plat, data in platform_data.items():
            avg_views = sum(d.get("views", 0) for d in data) / len(data)
            platform_scores[plat] = avg_views

        best_platform = max(platform_scores, key=platform_scores.get)
        plat_score = 0.8 if platform_scores[best_platform] > 1000 else 0.4
        insights.append(PerformanceInsight(
            category="📊 Plattform-Vergleich",
            score=plat_score,
            recommendation=f"Deine Clips performen am besten auf {best_platform.title()} "
                           f"(Ø {platform_scores[best_platform]:.0f} Views). Fokussiere dich darauf!"
        ))

    # 5. AI Model Confidence
    model_version = weights.get("version", 1)
    total_samples = weights.get("total_samples", 0)
    confidence = min(1.0, total_samples / 50)  # 50 videos = full confidence
    insights.append(PerformanceInsight(
        category="🧠 KI-Lernfortschritt",
        score=round(confidence, 2),
        recommendation=f"Das AI-Modell hat {total_samples} Videos analysiert (Version {model_version}). "
                       + ("Es ist gut kalibriert!" if confidence > 0.6 else f"Noch {50 - total_samples} Videos bis zur vollen Genauigkeit.")
    ))

    return insights


# ─── Endpoint: Get current model weights (for clip generator) ─
@router.get("/model-weights")
async def get_model_weights():
    """Returns current ML model weights for the clip scoring algorithm."""
    return _load_model_weights()
