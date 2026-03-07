package com.viralclipai.app.data.models

import com.google.gson.annotations.SerializedName

// ─── Content Filter Config ───
data class ContentFilter(
    val language: String = "de",
    val keywords: List<String> = emptyList(),
    val mood: String = "all",          // all, kontrovers, emotional, lustig, realtalk, drama, motivation, skandal
    val viralSensitivity: String = "medium"  // low, medium, high
)

// ─── Subtitle Customization ───
data class SubtitleConfig(
    val fontFamily: String = "Anton",
    val fontSize: String = "large",     // small (36), medium (48), large (64), xl (80)
    val textColor: String = "#FFFFFF",
    val highlightColor: String = "#00FF88",
    val style: String = "karaoke"       // karaoke, classic, neon, box, outline
)

// ─── Caption Config ───
data class CaptionConfig(
    val enabled: Boolean = true,
    val text: String = "",              // empty = auto-generate
    val position: String = "top"        // top, center
)

// ─── API Request/Response ───
data class ProcessRequest(
    @SerializedName("url") val url: String,
    @SerializedName("min_duration") val minDuration: Int = 30,
    @SerializedName("max_duration") val maxDuration: Int = 180,
    @SerializedName("format") val format: String = "9:16",
    @SerializedName("auto_cut") val autoCut: Boolean = true,
    @SerializedName("auto_caption") val autoCaption: Boolean = true,
    @SerializedName("auto_subtitle") val autoSubtitle: Boolean = true,
    // Content Filters
    @SerializedName("language") val language: String = "de",
    @SerializedName("keywords") val keywords: List<String> = emptyList(),
    @SerializedName("mood") val mood: String = "all",
    @SerializedName("viral_sensitivity") val viralSensitivity: String = "medium",
    // Subtitle Config
    @SerializedName("subtitle_font") val subtitleFont: String = "Anton",
    @SerializedName("subtitle_size") val subtitleSize: String = "large",
    @SerializedName("subtitle_color") val subtitleColor: String = "#FFFFFF",
    @SerializedName("subtitle_highlight") val subtitleHighlight: String = "#00FF88",
    @SerializedName("subtitle_style") val subtitleStyle: String = "karaoke",
    // Caption Config
    @SerializedName("caption_text") val captionText: String = ""
)

data class ProcessResponse(
    @SerializedName("job_id") val jobId: String,
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String
)

data class JobStatus(
    @SerializedName("job_id") val jobId: String,
    @SerializedName("status") val status: String,
    @SerializedName("progress") val progress: Int = 0,
    @SerializedName("clips") val clips: List<ClipData> = emptyList(),
    @SerializedName("error") val error: String? = null
)

data class ClipData(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("start_time") val startTime: Double,
    @SerializedName("end_time") val endTime: Double,
    @SerializedName("duration") val duration: Double,
    @SerializedName("virality_score") val viralityScore: Float,
    @SerializedName("caption") val caption: String,
    @SerializedName("preview_url") val previewUrl: String,
    @SerializedName("download_url") val downloadUrl: String,
    @SerializedName("has_subtitles") val hasSubtitles: Boolean = true,
    @SerializedName("has_caption") val hasCaption: Boolean = true,
    @SerializedName("transcript") val transcript: String = "",
    @SerializedName("expires_at") val expiresAt: String = "",
    @SerializedName("tags") val tags: List<String> = emptyList(),
    @SerializedName("matched_keywords") val matchedKeywords: List<String> = emptyList()
)

data class ServerHealth(
    @SerializedName("status") val status: String,
    @SerializedName("version") val version: String = "",
    @SerializedName("ai_models_loaded") val aiModelsLoaded: Boolean = false
)

data class FeedbackRequest(
    @SerializedName("clip_id") val clipId: String,
    @SerializedName("rating") val rating: Int,
    @SerializedName("downloaded") val downloaded: Boolean = false
)

// ─── Social Media Config ───
data class SocialAccount(
    val platform: String,   // youtube, tiktok, instagram
    val name: String,
    val connected: Boolean
)
