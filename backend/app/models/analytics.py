"""Database models for upload tracking & analytics."""

from datetime import datetime
from typing import Optional
from pydantic import BaseModel, Field


class UploadRecord(BaseModel):
    """Tracks a single uploaded video."""
    video_id: str
    platform: str  # "youtube" or "tiktok"
    title: str
    tags: list[str] = []
    uploaded_at: datetime = Field(default_factory=datetime.utcnow)

    # Clip generation metadata (for ML learning)
    source_video_url: Optional[str] = None
    clip_start_time: Optional[float] = None
    clip_end_time: Optional[float] = None
    hook_score: Optional[float] = None
    energy_score: Optional[float] = None
    face_ratio: Optional[float] = None


class VideoPerformance(BaseModel):
    """Performance data pulled from platform APIs."""
    video_id: str
    platform: str
    views: int = 0
    likes: int = 0
    comments: int = 0
    shares: int = 0
    watch_time_seconds: int = 0
    average_view_duration: float = 0.0
    engagement_rate: float = 0.0
    fetched_at: datetime = Field(default_factory=datetime.utcnow)


class PerformanceInsight(BaseModel):
    """AI-generated insight for clip improvement."""
    category: str
    score: float
    recommendation: str


class SyncRequest(BaseModel):
    stats: list[VideoPerformance]


class TrackUploadRequest(BaseModel):
    video_id: str
    platform: str
    title: str
    tags: list[str] = []
    uploaded_at: Optional[str] = None
