package com.viralclipai.app.data.repository

import com.viralclipai.app.data.api.ApiClient
import com.viralclipai.app.data.models.*
import com.viralclipai.app.network.ConnectionManager

class ClipRepository {
    private val api get() = ApiClient.getService()
    // Separate service with 10-minute timeout for large video downloads
    private val downloadApi get() = ApiClient.getDownloadService()

    suspend fun checkServer(): Boolean {
        val result = ConnectionManager.executeWithRetry(maxAttempts = 3, operation = "Health-Check") {
            api.healthCheck()
        }
        return result.fold(
            onSuccess = { it.status == "ok" || it.status == "degraded" },
            onFailure = { false }
        )
    }

    suspend fun processVideo(request: ProcessRequest): Result<ProcessResponse> {
        return ConnectionManager.executeWithRetry(maxAttempts = 3, operation = "Video verarbeiten") {
            val requestMap = mapOf<String, Any>(
                "url" to request.url,
                "min_duration" to request.minDuration,
                "max_duration" to request.maxDuration,
                "format" to request.format,
                "auto_cut" to request.autoCut,
                "auto_caption" to request.autoCaption,
                "auto_subtitle" to request.autoSubtitle,
                "language" to request.language,
                "keywords" to request.keywords,
                "mood" to request.mood,
                "viral_sensitivity" to request.viralSensitivity,
                "subtitle_font" to request.subtitleFont,
                "subtitle_size" to request.subtitleSize,
                "subtitle_color" to request.subtitleColor,
                "subtitle_highlight" to request.subtitleHighlight,
                "subtitle_style" to request.subtitleStyle,
                "caption_text" to request.captionText
            )
            api.processVideo(requestMap)
        }
    }

    suspend fun getJobStatus(jobId: String): Result<JobStatus> {
        return ConnectionManager.executeWithRetry(maxAttempts = 3, operation = "Job-Status") {
            api.getJobStatus(jobId)
        }
    }

    suspend fun downloadClip(clipId: String): Result<ByteArray> {
        // Use download service with 10-minute read timeout + streaming
        return ConnectionManager.executeWithRetry(maxAttempts = 3, operation = "Clip Download") {
            downloadApi.downloadClip(clipId).bytes()
        }
    }

    suspend fun sendFeedback(clipId: String, rating: Int, downloaded: Boolean) {
        ConnectionManager.executeWithRetry(maxAttempts = 1, operation = "Feedback") {
            api.sendFeedback(FeedbackRequest(clipId, rating, downloaded))
        }
    }

    suspend fun deleteClip(clipId: String): Result<Map<String, String>> {
        return ConnectionManager.executeWithRetry(maxAttempts = 2, operation = "Clip löschen") {
            api.deleteClip(clipId)
        }
    }
}
