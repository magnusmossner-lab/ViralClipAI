package com.viralclipai.app.data.repository

import com.viralclipai.app.data.api.ApiClient
import com.viralclipai.app.data.models.*

class ClipRepository {
    private val api get() = ApiClient.getService()

    suspend fun checkServer(): Boolean {
        return try {
            val response = api.healthCheck()
            response.status == "ok"
        } catch (e: Exception) {
            false
        }
    }

    suspend fun processVideo(
        url: String,
        minDuration: Int,
        maxDuration: Int,
        brollEnabled: Boolean,
        autoCaptions: Boolean,
        autoSubtitles: Boolean
    ): ProcessResponse {
        val request = mapOf(
            "url" to url,
            "min_duration" to minDuration,
            "max_duration" to maxDuration,
            "format" to "9:16",
            "broll_enabled" to brollEnabled,
            "auto_caption" to autoCaptions,
            "auto_subtitle" to autoSubtitles
        )
        return api.processVideo(request)
    }

    suspend fun getJobStatus(jobId: String): JobStatus {
        return api.getJobStatus(jobId)
    }

    suspend fun downloadClip(clipId: String): ByteArray {
        val responseBody = api.downloadClip(clipId)
        return responseBody.bytes()
    }

    suspend fun sendFeedback(clipId: String, rating: Int, downloaded: Boolean) {
        api.sendFeedback(FeedbackRequest(clipId, rating, downloaded))
    }

    suspend fun deleteClip(clipId: String) {
        api.deleteClip(clipId)
    }
}
