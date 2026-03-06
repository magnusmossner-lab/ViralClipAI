package com.viralclipai.app.upload

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viralclipai.app.auth.OAuthManager
import com.viralclipai.app.auth.Platform
import com.viralclipai.app.data.api.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class UploadViewModel : ViewModel() {

    companion object {
        private const val TAG = "UploadViewModel"
    }

    data class UploadState(
        val isUploading: Boolean = false,
        val statusText: String = "Bereit",
        val error: String? = null,
        val youtubeProgress: Int = 0,
        val tiktokProgress: Int = 0,
        val results: List<String> = emptyList()
    )

    private val _uploadState = MutableStateFlow(UploadState())
    val uploadState: StateFlow<UploadState> = _uploadState

    fun uploadClip(
        context: Context,
        filePath: String,
        title: String,
        description: String,
        tags: List<String>,
        uploadToYouTube: Boolean,
        uploadToTikTok: Boolean
    ) {
        viewModelScope.launch {
            val oAuth = OAuthManager(context)
            val file = File(filePath)
            if (!file.exists()) {
                _uploadState.value = _uploadState.value.copy(
                    error = "Datei nicht gefunden: $filePath"
                )
                return@launch
            }

            _uploadState.value = UploadState(isUploading = true, statusText = "Upload wird vorbereitet...")
            val results = mutableListOf<String>()

            // YouTube Upload
            if (uploadToYouTube) {
                try {
                    val ytTokens = oAuth.refreshTokenIfNeeded(Platform.YOUTUBE)
                    if (ytTokens != null) {
                        _uploadState.value = _uploadState.value.copy(statusText = "YouTube Upload...")
                        val uploader = YouTubeUploader()
                        val videoId = uploader.upload(
                            accessToken = ytTokens.accessToken,
                            file = file,
                            title = title,
                            description = description,
                            tags = tags
                        ) { progress ->
                            _uploadState.value = _uploadState.value.copy(youtubeProgress = progress)
                        }
                        results.add("\u2705 YouTube: https://youtube.com/shorts/$videoId")

                        // Track upload for analytics
                        trackUpload(context, videoId, "youtube", title, tags)
                    } else {
                        results.add("\u274C YouTube: Nicht eingeloggt")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "YouTube upload failed", e)
                    results.add("\u274C YouTube: ${e.message}")
                }
            }

            // TikTok Upload
            if (uploadToTikTok) {
                try {
                    val ttTokens = oAuth.refreshTokenIfNeeded(Platform.TIKTOK)
                    if (ttTokens != null) {
                        _uploadState.value = _uploadState.value.copy(statusText = "TikTok Upload...")
                        val uploader = TikTokUploader()
                        val videoId = uploader.upload(
                            accessToken = ttTokens.accessToken,
                            file = file,
                            title = title,
                            tags = tags
                        ) { progress ->
                            _uploadState.value = _uploadState.value.copy(tiktokProgress = progress)
                        }
                        results.add("\u2705 TikTok: Upload erfolgreich ($videoId)")
                        trackUpload(context, videoId, "tiktok", title, tags)
                    } else {
                        results.add("\u274C TikTok: Nicht eingeloggt")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "TikTok upload failed", e)
                    results.add("\u274C TikTok: ${e.message}")
                }
            }

            _uploadState.value = UploadState(
                isUploading = false,
                statusText = "Upload abgeschlossen",
                results = results
            )
        }
    }

    private suspend fun trackUpload(
        context: Context, videoId: String, platform: String,
        title: String, tags: List<String>
    ) {
        try {
            val body = org.json.JSONObject().apply {
                put("video_id", videoId)
                put("platform", platform)
                put("title", title)
                put("tags", org.json.JSONArray(tags))
            }
            val baseUrl = ApiClient.getBaseUrl()
            val url = java.net.URL("${baseUrl}api/v1/analytics/track-upload")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.outputStream.write(body.toString().toByteArray())
            conn.responseCode // Trigger request
            conn.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "Track upload failed: ${e.message}")
        }
    }
}
