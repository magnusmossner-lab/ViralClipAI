package com.viralclipai.app.viewmodel

import android.app.Application
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.viralclipai.app.data.api.ApiClient
import com.viralclipai.app.data.models.*
import com.viralclipai.app.data.repository.ClipRepository
import com.viralclipai.app.util.isYouTubeUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = ClipRepository()
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState
    private val _clips = MutableStateFlow<List<ClipData>>(emptyList())
    val clips: StateFlow<List<ClipData>> = _clips
    private val _serverConnected = MutableStateFlow(false)
    val serverConnected: StateFlow<Boolean> = _serverConnected

    companion object {
        private const val TAG = "MainViewModel"
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 2000L
    }

    data class UiState(
        val isProcessing: Boolean = false,
        val progress: Int = 0,
        val statusText: String = "Bereit",
        val error: String? = null,
        val currentJobId: String? = null,
        val downloadingClipId: String? = null,
        val downloadSuccess: String? = null,
        val minDuration: Int = 30,
        val maxDuration: Int = 180,
        val brollEnabled: Boolean = true,
        val autoCaptions: Boolean = true,
        val autoSubtitles: Boolean = true
    )

    init {
        connectToServer()
    }

    fun connectToServer() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(statusText = "Verbinde mit Server...")
            val connected = retryWithBackoff { repo.checkServer() }
            _serverConnected.value = connected
            _uiState.value = _uiState.value.copy(
                statusText = if (connected) "Server verbunden" else "Server nicht erreichbar",
                error = if (!connected) "Server nicht erreichbar. Bitte Backend starten." else null
            )
        }
    }

    fun setServerUrl(url: String) {
        ApiClient.setBaseUrl(url)
        connectToServer()
    }

    fun updateSettings(
        minDuration: Int? = null, maxDuration: Int? = null,
        broll: Boolean? = null, captions: Boolean? = null, subtitles: Boolean? = null
    ) {
        val s = _uiState.value
        _uiState.value = s.copy(
            minDuration = minDuration ?: s.minDuration,
            maxDuration = maxDuration ?: s.maxDuration,
            brollEnabled = broll ?: s.brollEnabled,
            autoCaptions = captions ?: s.autoCaptions,
            autoSubtitles = subtitles ?: s.autoSubtitles
        )
    }

    fun processVideo(url: String) {
        _uiState.value = _uiState.value.copy(error = null, downloadSuccess = null)

        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Bitte eine URL eingeben")
            return
        }
        if (!url.isYouTubeUrl()) {
            _uiState.value = _uiState.value.copy(error = "Keine gueltige YouTube-URL")
            return
        }

        viewModelScope.launch {
            try {
                val s = _uiState.value
                _uiState.value = s.copy(isProcessing = true, progress = 0, statusText = "Starte...")

                val response = repo.processVideo(
                    url = url,
                    minDuration = s.minDuration,
                    maxDuration = s.maxDuration,
                    brollEnabled = s.brollEnabled,
                    autoCaptions = s.autoCaptions,
                    autoSubtitles = s.autoSubtitles
                )
                val jobId = response.jobId
                _uiState.value = _uiState.value.copy(currentJobId = jobId, statusText = "Job gestartet...")

                // Poll for status
                var consecutiveErrors = 0
                while (true) {
                    delay(1500)
                    try {
                        val status = repo.getJobStatus(jobId)
                        _uiState.value = _uiState.value.copy(
                            progress = status.progress,
                            statusText = translateStatus(status.status)
                        )
                        consecutiveErrors = 0 // Reset on success

                        when (status.status) {
                            "done" -> {
                                _clips.value = status.clips
                                _uiState.value = _uiState.value.copy(
                                    isProcessing = false, progress = 100,
                                    statusText = "${status.clips.size} Clips erstellt!"
                                )
                                break
                            }
                            "error" -> {
                                _uiState.value = _uiState.value.copy(
                                    isProcessing = false,
                                    error = status.error ?: "Unbekannter Fehler"
                                )
                                break
                            }
                        }
                    } catch (e: Exception) {
                        consecutiveErrors++
                        if (consecutiveErrors >= 5) {
                            _uiState.value = _uiState.value.copy(
                                isProcessing = false,
                                error = "Verbindung verloren: ${e.message}"
                            )
                            break
                        }
                        Log.w(TAG, "Poll error #$consecutiveErrors: ${e.message}")
                        delay(RETRY_DELAY_MS)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Process error", e)
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    error = "Fehler: ${e.message}"
                )
            }
        }
    }

    fun downloadClip(clipId: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(downloadingClipId = clipId, downloadSuccess = null, error = null)
                val bytes = repo.downloadClip(clipId)

                if (bytes.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        downloadingClipId = null,
                        error = "Download fehlgeschlagen: Leere Datei"
                    )
                    return@launch
                }

                val filename = "ViralClip_${clipId}.mp4"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = getApplication<Application>().contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ViralClipAI")
                    }
                    val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    uri?.let {
                        resolver.openOutputStream(it)?.use { out -> out.write(bytes) }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "ViralClipAI")
                    dir.mkdirs()
                    File(dir, filename).writeBytes(bytes)
                }

                _uiState.value = _uiState.value.copy(
                    downloadingClipId = null,
                    downloadSuccess = "\u2705 $filename gespeichert!"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Download error", e)
                _uiState.value = _uiState.value.copy(
                    downloadingClipId = null,
                    error = "Download fehlgeschlagen: ${e.message}"
                )
            }
        }
    }

    fun rateClip(clipId: String, rating: Int) {
        viewModelScope.launch {
            try {
                repo.sendFeedback(clipId, rating, true)
                Log.i(TAG, "Feedback sent: clip=$clipId, rating=$rating")
            } catch (e: Exception) {
                Log.w(TAG, "Feedback failed: ${e.message}")
            }
        }
    }

    private fun translateStatus(status: String): String = when (status) {
        "queued" -> "In der Warteschlange..."
        "downloading" -> "Video wird heruntergeladen..."
        "analyzing" -> "KI analysiert Video..."
        "cutting" -> "Clips werden geschnitten..."
        "subtitling" -> "Untertitel werden erstellt..."
        "captioning" -> "Captions werden generiert..."
        "broll" -> "B-Roll Effekte..."
        "done" -> "Fertig!"
        "error" -> "Fehler aufgetreten"
        else -> status.replaceFirstChar { it.uppercase() }
    }

    private suspend fun <T> retryWithBackoff(
        maxRetries: Int = MAX_RETRIES,
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null
        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Retry ${attempt + 1}/$maxRetries: ${e.message}")
                delay(RETRY_DELAY_MS * (attempt + 1))
            }
        }
        throw lastException ?: Exception("Max retries reached")
    }
}
