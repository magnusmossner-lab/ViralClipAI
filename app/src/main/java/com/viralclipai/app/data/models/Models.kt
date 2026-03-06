package com.viralclipai.app.data.models

import com.google.gson.annotations.SerializedName

data class ProcessResponse(
    @SerializedName("job_id") val jobId: String,
    val status: String,
    val message: String
)

data class JobStatus(
    @SerializedName("job_id") val jobId: String,
    val status: String,
    val progress: Int = 0,
    val clips: List<ClipData> = emptyList(),
    val error: String? = null
)

data class ClipData(
    val id: String,
    val title: String = "",
    @SerializedName("start_time") val startTime: Double = 0.0,
    @SerializedName("end_time") val endTime: Double = 0.0,
    val duration: Double = 0.0,
    @SerializedName("virality_score") val viralityScore: Float = 0f,
    val caption: String = "",
    @SerializedName("preview_url") val previewUrl: String = "",
    @SerializedName("download_url") val downloadUrl: String = "",
    @SerializedName("has_subtitles") val hasSubtitles: Boolean = false,
    @SerializedName("has_broll") val hasBroll: Boolean = false,
    @SerializedName("expires_at") val expiresAt: String = "",
    val tags: List<String> = emptyList()
)

data class HealthResponse(
    val status: String,
    val version: String = "",
    @SerializedName("ai_models_loaded") val aiModelsLoaded: Boolean = false
)

data class FeedbackRequest(
    @SerializedName("clip_id") val clipId: String,
    val rating: Int,
    val downloaded: Boolean = false
)

data class FeedbackResponse(
    val status: String
)
