package com.viralclipai.app.data.repository

import com.viralclipai.app.data.api.ApiClient
import com.viralclipai.app.data.api.CountingRequestBody
import com.viralclipai.app.data.models.*
import com.viralclipai.app.network.ConnectionManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class ClipRepository {
    private val api get() = ApiClient.getService()
    // Separate service with 10-minute timeout for large video downloads
    private val downloadApi get() = ApiClient.getDownloadService()
    // FIX: Separate service with 10-minute WRITE timeout for large gallery uploads
    private val uploadApi get() = ApiClient.getUploadService()

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

    // ─── v5.4.0: Upload video from gallery ───
    // FIX: Use uploadApi (long write timeout) + CountingRequestBody for real upload progress
    suspend fun uploadVideo(
        videoFile: File,
        request: ProcessRequest,
        onProgress: (Int) -> Unit = {}
    ): Result<ProcessResponse> {
        return ConnectionManager.executeWithRetry(maxAttempts = 2, operation = "Video hochladen") {
            val rawBody = videoFile.asRequestBody("video/*".toMediaType())
            val countingBody = CountingRequestBody(rawBody) { bytesWritten, contentLength ->
                if (contentLength > 0) {
                    // FIX v5.9.0: Expanded range 13% → 60% so user sees real upload progress
                    val uploadPct = 13 + (bytesWritten * 47 / contentLength).toInt()
                    onProgress(uploadPct.coerceIn(13, 60))
                }
            }
            val filePart = MultipartBody.Part.createFormData(
                "file",
                videoFile.name,
                countingBody
            )
            fun str(s: String) = s.toRequestBody("text/plain".toMediaType())
            fun str(i: Int) = i.toString().toRequestBody("text/plain".toMediaType())
            fun str(b: Boolean) = b.toString().toRequestBody("text/plain".toMediaType())

            // FIX: Use uploadApi instead of api to avoid 60s write timeout for large files
            uploadApi.uploadVideo(
                file = filePart,
                minDuration = str(request.minDuration),
                maxDuration = str(request.maxDuration),
                format = str(request.format),
                autoCut = str(request.autoCut),
                autoCaption = str(request.autoCaption),
                autoSubtitle = str(request.autoSubtitle),
                language = str(request.language),
                keywords = str(request.keywords.joinToString(",")),
                mood = str(request.mood),
                viralSensitivity = str(request.viralSensitivity),
                subtitleFont = str(request.subtitleFont),
                subtitleSize = str(request.subtitleSize),
                subtitleColor = str(request.subtitleColor),
                subtitleHighlight = str(request.subtitleHighlight),
                subtitleStyle = str(request.subtitleStyle),
                captionText = str(request.captionText)
            )
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
        return ConnectionManager.executeWithRetry(maxAttempts = 2, operation = "Clip loeschen") {
            api.deleteClip(clipId)
        }
    }
}
