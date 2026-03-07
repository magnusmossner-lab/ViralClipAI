package com.viralclipai.app.viewmodel

import android.app.Application
import android.content.Intent
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.viralclipai.app.data.api.ApiClient
import com.viralclipai.app.data.models.*
import com.viralclipai.app.data.repository.ClipRepository
import com.viralclipai.app.network.ConnectionManager
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
    private val _contentFilter = MutableStateFlow(ContentFilter())
    val contentFilter: StateFlow<ContentFilter> = _contentFilter
    private val _subtitleConfig = MutableStateFlow(SubtitleConfig())
    val subtitleConfig: StateFlow<SubtitleConfig> = _subtitleConfig
    private val _captionConfig = MutableStateFlow(CaptionConfig())
    val captionConfig: StateFlow<CaptionConfig> = _captionConfig
    private val _socialAccounts = MutableStateFlow<List<SocialAccount>>(emptyList())
    val socialAccounts: StateFlow<List<SocialAccount>> = _socialAccounts

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
                error = ConnectionManager.connectionInfo.value.statusMessage
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, progress = 0, statusText = "Video wird analysiert...", error = null)
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
                    _uiState.value = _uiState.value.copy(isProcessing = false, error = "Fehler: ${it.message}", statusText = "Fehler")
                }
            )
        }
    }

    private fun pollJob(jobId: String) {
        viewModelScope.launch {
            var consecutiveErrors = 0
            while (true) {
                delay(2000)
                repo.getJobStatus(jobId).fold(
                    onSuccess = { st ->
                        consecutiveErrors = 0
                        _uiState.value = _uiState.value.copy(
                            progress = st.progress,
                            statusText = when (st.status) {
                                "downloading" -> "Video wird heruntergeladen... ${st.progress}%"
                                "analyzing" -> "KI analysiert Sprache & Keywords... ${st.progress}%"
                                "cutting" -> "Clips werden geschnitten... ${st.progress}%"
                                "subtitling" -> "Karaoke-Untertitel... ${st.progress}%"
                                "captioning" -> "Hook-Captions... ${st.progress}%"
                                "auto_cut" -> "Auto-Cut Gesichtserkennung... ${st.progress}%"
                                "ranking" -> "Viral-Ranking... ${st.progress}%"
                                "done" -> "Fertig! ${st.clips.size} Clips"
                                "error" -> "Fehler: ${st.error}"
                                else -> "Verarbeitung... ${st.progress}%"
                            }
                        )
                        if (st.status == "done") {
                            _clips.value = st.clips.sortedByDescending { it.viralityScore }
                            _uiState.value = _uiState.value.copy(isProcessing = false)
                            return@launch
                        }
                        if (st.status == "error") {
                            _uiState.value = _uiState.value.copy(isProcessing = false, error = st.error)
                            return@launch
                        }
                    },
                    onFailure = {
                        consecutiveErrors++
                        if (consecutiveErrors >= 5) {
                            _uiState.value = _uiState.value.copy(
                                isProcessing = false,
                                error = "Verbindung verloren – tippe auf Reconnect"
                            )
                            return@launch
                        }
                        // Kurz warten, ConnectionManager versucht reconnect
                        _uiState.value = _uiState.value.copy(
                            statusText = "Verbindungsproblem... wird wiederhergestellt"
                        )
                        delay(3000)
                    }
                )
            }
        }
    }

    // ─── Download ───
    fun downloadClip(clip: ClipData) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(downloadingClipId = clip.id, downloadSuccess = null)
            repo.downloadClip(clip.id).fold(
                onSuccess = { bytes ->
                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val file = File(dir, "ViralClip_${clip.id}.mp4")
                    file.writeBytes(bytes)
                    repo.sendFeedback(clip.id, 5, true)
                    _uiState.value = _uiState.value.copy(downloadingClipId = null, downloadSuccess = "${clip.title} gespeichert")
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

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, android.net.Uri.fromFile(file))
                    putExtra(Intent.EXTRA_TEXT, clip.caption)
                    when (platform) {
                        "tiktok" -> setPackage("com.zhiliaoapp.musically")
                        "youtube" -> setPackage("com.google.android.youtube")
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
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, android.net.Uri.fromFile(file))
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
}
