package com.viralclipai.app.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.viralclipai.app.data.api.ApiClient
import com.viralclipai.app.data.models.*
import com.viralclipai.app.data.repository.ClipRepository
import com.viralclipai.app.network.ConnectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = ClipRepository()
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState
    private val _clips = MutableStateFlow<List<ClipData>>(emptyList())
    val clips: StateFlow<List<ClipData>> = _clips
    private val _serverConnected = MutableStateFlow(false)
    val serverConnected: StateFlow<Boolean> = _serverConnected
    private val _contentFilter = MutableStateFlow(ContentFilter())
    val contentFilter: StateFlow<ContentFilter> = _contentFilter
    private val _subtitleConfig = MutableStateFlow(SubtitleConfig())
    val subtitleConfig: StateFlow<SubtitleConfig> = _subtitleConfig
    private val _captionConfig = MutableStateFlow(CaptionConfig())
    val captionConfig: StateFlow<CaptionConfig> = _captionConfig
    private val _socialAccounts = MutableStateFlow<List<SocialAccount>>(emptyList())
    val socialAccounts: StateFlow<List<SocialAccount>> = _socialAccounts

    /** Active keep-alive job – cancelled when processing finishes */
    private var keepAliveJob: Job? = null

    // Expose ConnectionManager state to UI
    val connectionInfo: StateFlow<ConnectionManager.ConnectionInfo> = ConnectionManager.connectionInfo

    data class UiState(
        val isProcessing: Boolean = false,
        val progress: Int = 0,
        val statusText: String = "Bereit",
        val error: String? = null,
        val currentJobId: String? = null,
        val downloadingClipId: String? = null,
        val downloadSuccess: String? = null,
        val uploadingClipId: String? = null,
        val uploadPlatform: String? = null,
        val minDuration: Int = 30,
        val maxDuration: Int = 180,
        val autoCut: Boolean = true,
        val autoCaptions: Boolean = true,
        val autoSubtitles: Boolean = true
    )

    init {
        connectToServer()
        // Observe ConnectionManager state
        viewModelScope.launch {
            ConnectionManager.connectionInfo.collect { info ->
                _serverConnected.value = ConnectionManager.isConnected
            }
        }
    }

    // ─── Keep-Alive: Ping every 30s to prevent Railway from sleeping ───
    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = viewModelScope.launch {
            while (true) {
                delay(30_000L) // Every 30 seconds
                try {
                    ApiClient.getService().ping()
                } catch (e: Exception) {
                    // Ping failed – that's ok, polling will handle reconnection
                }
            }
        }
    }

    private fun stopKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = null
    }

    fun connectToServer() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(statusText = "Verbinde mit Server...")
            val c = repo.checkServer()
            _serverConnected.value = c
            _uiState.value = _uiState.value.copy(
                statusText = if (c) "Server verbunden" else "Server nicht erreichbar",
                error = if (!c) "Server nicht erreichbar. Bitte Backend starten." else null
            )
        }
    }

    fun setServerUrl(url: String) {
        ApiClient.setBaseUrl(url)
        ConnectionManager.onServerUrlChanged()
        connectToServer()
    }

    fun reconnectServer() {
        ConnectionManager.reconnect()
    }

    // ─── Content Filter ───
    fun updateContentFilter(
        language: String? = null,
        keywords: List<String>? = null,
        mood: String? = null,
        viralSensitivity: String? = null
    ) {
        val f = _contentFilter.value
        _contentFilter.value = f.copy(
            language = language ?: f.language,
            keywords = keywords ?: f.keywords,
            mood = mood ?: f.mood,
            viralSensitivity = viralSensitivity ?: f.viralSensitivity
        )
    }

    fun addKeyword(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isNotEmpty()) {
            val current = _contentFilter.value.keywords.toMutableList()
            if (!current.contains(trimmed)) {
                current.add(trimmed)
                _contentFilter.value = _contentFilter.value.copy(keywords = current)
            }
        }
    }

    fun removeKeyword(keyword: String) {
        val current = _contentFilter.value.keywords.toMutableList()
        current.remove(keyword)
        _contentFilter.value = _contentFilter.value.copy(keywords = current)
    }

    // ─── Subtitle Config ───
    fun updateSubtitleConfig(
        fontFamily: String? = null,
        fontSize: String? = null,
        textColor: String? = null,
        highlightColor: String? = null,
        style: String? = null
    ) {
        val s = _subtitleConfig.value
        _subtitleConfig.value = s.copy(
            fontFamily = fontFamily ?: s.fontFamily,
            fontSize = fontSize ?: s.fontSize,
            textColor = textColor ?: s.textColor,
            highlightColor = highlightColor ?: s.highlightColor,
            style = style ?: s.style
        )
    }

    // ─── Caption Config ───
    fun updateCaptionConfig(enabled: Boolean? = null, text: String? = null) {
        val c = _captionConfig.value
        _captionConfig.value = c.copy(
            enabled = enabled ?: c.enabled,
            text = text ?: c.text
        )
    }

    // ─── Settings ───
    fun updateSettings(
        minDuration: Int? = null,
        maxDuration: Int? = null,
        autoCut: Boolean? = null,
        captions: Boolean? = null,
        subtitles: Boolean? = null
    ) {
        val s = _uiState.value
        _uiState.value = s.copy(
            minDuration = minDuration ?: s.minDuration,
            maxDuration = maxDuration ?: s.maxDuration,
            autoCut = autoCut ?: s.autoCut,
            autoCaptions = captions ?: s.autoCaptions,
            autoSubtitles = subtitles ?: s.autoSubtitles
        )
    }

    // ─── Process Video ───
    fun processVideo(url: String) {
        if (url.isBlank()) { _uiState.value = _uiState.value.copy(error = "Bitte YouTube-Link eingeben"); return }

        // Check connection before starting
        if (!ConnectionManager.isConnected) {
            _uiState.value = _uiState.value.copy(
                error = ConnectionManager.connectionInfo.value.statusText
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProcessing = true, progress = 0,
                statusText = "Server wird aufgeweckt...", error = null
            )

            // Step 1: Ping server to wake it up (Railway can sleep after inactivity)
            var wakeAttempts = 0
            var serverReady = false
            while (wakeAttempts < 3 && !serverReady) {
                try {
                    ApiClient.getService().ping()
                    serverReady = true
                } catch (e: Exception) {
                    wakeAttempts++
                    if (wakeAttempts < 3) {
                        _uiState.value = _uiState.value.copy(
                            statusText = "Server startet... (${wakeAttempts}/3)"
                        )
                        delay(3000L)
                    }
                }
            }

            // Start keep-alive to prevent server from sleeping during long processing
            startKeepAlive()

            _uiState.value = _uiState.value.copy(statusText = "Video wird analysiert...")

            val s = _uiState.value
            val cf = _contentFilter.value
            val sc = _subtitleConfig.value
            val cc = _captionConfig.value

            val req = ProcessRequest(
                url = url,
                minDuration = s.minDuration,
                maxDuration = s.maxDuration,
                autoCut = s.autoCut,
                autoCaption = s.autoCaptions && cc.enabled,
                autoSubtitle = s.autoSubtitles,
                language = cf.language,
                keywords = cf.keywords,
                mood = cf.mood,
                viralSensitivity = cf.viralSensitivity,
                subtitleFont = sc.fontFamily,
                subtitleSize = sc.fontSize,
                subtitleColor = sc.textColor,
                subtitleHighlight = sc.highlightColor,
                subtitleStyle = sc.style,
                captionText = cc.text
            )
            repo.processVideo(req).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(currentJobId = it.jobId, statusText = "KI schneidet Clips...")
                    pollJob(it.jobId)
                },
                onFailure = {
                    stopKeepAlive()
                    _uiState.value = _uiState.value.copy(isProcessing = false, error = "Fehler: ${it.message}", statusText = "Fehler")
                }
            )
        }
    }

    private fun pollJob(jobId: String) {
        viewModelScope.launch {
            var consecutiveErrors = 0
            var totalElapsedMs = 0L
            val maxTotalMs = 20 * 60 * 1000L  // 20 minutes max (video processing can take long)

            while (totalElapsedMs < maxTotalMs) {
                val pollDelay = when {
                    consecutiveErrors == 0 -> 2000L   // Normal: check every 2s
                    consecutiveErrors <= 3  -> 5000L  // Minor issue: check every 5s
                    consecutiveErrors <= 6  -> 15000L // Bigger issue: check every 15s
                    else                   -> 30000L  // Persistent: check every 30s
                }
                delay(pollDelay)
                totalElapsedMs += pollDelay

                repo.getJobStatus(jobId).fold(
                    onSuccess = { st ->
                        consecutiveErrors = 0  // Reset on success
                        _uiState.value = _uiState.value.copy(
                            progress = st.progress,
                            statusText = when (st.status) {
                                "downloading"  -> "Video wird heruntergeladen... ${st.progress}%"
                                "analyzing"    -> "KI analysiert Sprache & Keywords... ${st.progress}%"
                                "cutting"      -> "Clips werden geschnitten... ${st.progress}%"
                                "subtitling"   -> "Karaoke-Untertitel... ${st.progress}%"
                                "captioning"   -> "Hook-Captions... ${st.progress}%"
                                "auto_cut"     -> "Auto-Cut Gesichtserkennung... ${st.progress}%"
                                "ranking"      -> "Viral-Ranking... ${st.progress}%"
                                "done"         -> "Fertig! ${st.clips.size} Clips"
                                "error"        -> "Fehler: ${st.error}"
                                else           -> "Verarbeitung... ${st.progress}%"
                            }
                        )
                        if (st.status == "done") {
                            stopKeepAlive()
                            _clips.value = st.clips.sortedByDescending { it.viralityScore }
                            _uiState.value = _uiState.value.copy(isProcessing = false)
                            return@launch
                        }
                        if (st.status == "error") {
                            stopKeepAlive()
                            _uiState.value = _uiState.value.copy(isProcessing = false, error = st.error)
                            return@launch
                        }
                    },
                    onFailure = { error ->
                        consecutiveErrors++
                        // Never give up during active processing – show friendly message and keep trying
                        _uiState.value = _uiState.value.copy(
                            statusText = when {
                                consecutiveErrors <= 3 -> "Verbindung kurz unterbrochen – wird wiederhergestellt..."
                                consecutiveErrors <= 6 -> "Server antwortet nicht – warte auf Reconnect (${consecutiveErrors}x)"
                                else -> "Verbindungsprobleme – versuche es weiter... (${consecutiveErrors}x)"
                            }
                        )
                        // Trigger a reconnect attempt via ConnectionManager
                        if (consecutiveErrors == 3) {
                            ConnectionManager.reconnect()
                        }
                    }
                )
            }

            // Timeout after 20 minutes
            stopKeepAlive()
            if (_uiState.value.isProcessing) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    error = "Zeitüberschreitung – der Server hat zu lange gebraucht. Bitte erneut versuchen."
                )
            }
        }
    }

    // ─── Helper: Get content URI via FileProvider (fixes FileUriExposedException) ───
    private fun getFileUri(file: File): android.net.Uri {
        return FileProvider.getUriForFile(
            getApplication(),
            "${getApplication<Application>().packageName}.fileprovider",
            file
        )
    }

    // ─── Save video to Gallery via MediaStore (works on all Android versions) ───
    private suspend fun saveVideoToGallery(bytes: ByteArray, fileName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+ - Use MediaStore (no WRITE_EXTERNAL_STORAGE needed)
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ViralClipAI")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }

                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)

                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(bytes)
                        }
                        // Mark as not pending so it appears in gallery
                        contentValues.clear()
                        contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                        true
                    } else {
                        false
                    }
                } else {
                    // Android 9 and below - Save to Movies directory
                    val moviesDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                        "ViralClipAI"
                    )
                    if (!moviesDir.exists()) moviesDir.mkdirs()
                    val file = File(moviesDir, fileName)
                    file.writeBytes(bytes)

                    // Notify media scanner so it appears in gallery
                    val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                    mediaScanIntent.data = android.net.Uri.fromFile(file)
                    context.sendBroadcast(mediaScanIntent)
                    true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    // ─── Download to Gallery ───
    fun downloadClip(clip: ClipData) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(downloadingClipId = clip.id, downloadSuccess = null)
            repo.downloadClip(clip.id).fold(
                onSuccess = { bytes ->
                    val fileName = "ViralClip_${clip.id}.mp4"
                    val saved = saveVideoToGallery(bytes, fileName)
                    if (saved) {
                        repo.sendFeedback(clip.id, 5, true)
                        _uiState.value = _uiState.value.copy(
                            downloadingClipId = null,
                            downloadSuccess = "\u2705 ${clip.title} in Galerie gespeichert!"
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            downloadingClipId = null,
                            error = "Speichern in Galerie fehlgeschlagen. Bitte Berechtigungen pruefen."
                        )
                    }
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(downloadingClipId = null, error = "Download fehlgeschlagen: ${it.message}")
                }
            )
        }
    }

    // ─── Social Media Upload ───
    fun uploadToSocial(clip: ClipData, platform: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(uploadingClipId = clip.id, uploadPlatform = platform)
            try {
                val bytes = repo.downloadClip(clip.id).getOrThrow()
                val dir = getApplication<Application>().cacheDir
                val file = File(dir, "upload_${clip.id}.mp4")
                file.writeBytes(bytes)

                val contentUri = getFileUri(file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_TEXT, clip.caption)
                    when (platform) {
                        "tiktok"    -> setPackage("com.zhiliaoapp.musically")
                        "youtube"   -> setPackage("com.google.android.youtube")
                        "instagram" -> setPackage("com.instagram.android")
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                getApplication<Application>().startActivity(shareIntent)
                _uiState.value = _uiState.value.copy(uploadingClipId = null, uploadPlatform = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    uploadingClipId = null,
                    uploadPlatform = null,
                    error = "Upload fehlgeschlagen: ${e.message}"
                )
            }
        }
    }

    fun shareClip(clip: ClipData) {
        viewModelScope.launch {
            try {
                val bytes = repo.downloadClip(clip.id).getOrThrow()
                val dir = getApplication<Application>().cacheDir
                val file = File(dir, "share_${clip.id}.mp4")
                file.writeBytes(bytes)
                val contentUri = getFileUri(file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_TEXT, clip.caption)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                getApplication<Application>().startActivity(
                    Intent.createChooser(shareIntent, "Clip teilen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Teilen fehlgeschlagen")
            }
        }
    }

    fun rateClip(clipId: String, rating: Int) {
        viewModelScope.launch { repo.sendFeedback(clipId, rating, false) }
    }

    // ─── v5.4.0: Process video from gallery ───
    fun processLocalVideo(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProcessing = true, progress = 0,
                statusText = "Video wird vorbereitet...",
                error = null
            )
            try {
                // Copy URI content to temp file
                val fileName = getFileNameFromUri(context, uri) ?: "gallery_video.mp4"
                val tempFile = File(context.cacheDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                } ?: throw Exception("Konnte Video nicht lesen")

                _uiState.value = _uiState.value.copy(
                    progress = 5, statusText = "Video wird hochgeladen..."
                )

                // Build request with current settings
                val filter = _contentFilter.value
                val subtitle = _subtitleConfig.value
                val caption = _captionConfig.value
                val request = ProcessRequest(
                    url = "",
                    minDuration = _uiState.value.minDuration,
                    maxDuration = _uiState.value.maxDuration,
                    format = "9:16",
                    autoCut = true,
                    autoCaption = caption.enabled,
                    autoSubtitle = true,
                    language = filter.language,
                    keywords = filter.keywords,
                    mood = filter.mood,
                    viralSensitivity = filter.viralSensitivity,
                    subtitleFont = subtitle.fontFamily,
                    subtitleSize = subtitle.fontSize,
                    subtitleColor = subtitle.textColor,
                    subtitleHighlight = subtitle.highlightColor,
                    subtitleStyle = subtitle.style,
                    captionText = caption.text
                )

                // Upload via multipart
                val response = repo.uploadVideo(tempFile, request).getOrThrow()

                _uiState.value = _uiState.value.copy(
                    progress = 15, statusText = "Server verarbeitet Video..."
                )

                // Start keep-alive heartbeat
                startKeepAlive()

                // Poll for results (same as URL processing)
                pollJob(response.jobId)

                // Cleanup temp file
                tempFile.delete()

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    error = "Upload fehlgeschlagen: ${e.message}"
                )
                stopKeepAlive()
            }
        }
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }
}
