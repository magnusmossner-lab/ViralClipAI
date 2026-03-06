package com.viralclipai.app.upload

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.viralclipai.app.R
import com.viralclipai.app.auth.OAuthManager
import com.viralclipai.app.auth.Platform
import kotlinx.coroutines.launch

class UploadActivity : AppCompatActivity() {

    private lateinit var oAuthManager: OAuthManager
    private lateinit var viewModel: UploadViewModel
    private var clipFilePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upload)

        oAuthManager = OAuthManager(this)
        viewModel = ViewModelProvider(this)[UploadViewModel::class.java]

        clipFilePath = intent.getStringExtra("clip_path")
        findViewById<TextView>(R.id.tv_file_path).text = clipFilePath ?: "Keine Datei ausgewaehlt"

        setupConnectButtons()
        setupUploadButton()
        observeViewModel()
        updateConnectionStatus()
    }

    private fun setupConnectButtons() {
        findViewById<Button>(R.id.btn_connect_youtube).setOnClickListener {
            val tokens = oAuthManager.getStoredTokens(Platform.YOUTUBE)
            if (tokens != null) {
                oAuthManager.clearTokens(Platform.YOUTUBE)
                updateConnectionStatus()
            } else {
                startActivity(oAuthManager.getAuthIntent(Platform.YOUTUBE))
            }
        }

        findViewById<Button>(R.id.btn_connect_tiktok).setOnClickListener {
            val tokens = oAuthManager.getStoredTokens(Platform.TIKTOK)
            if (tokens != null) {
                oAuthManager.clearTokens(Platform.TIKTOK)
                updateConnectionStatus()
            } else {
                startActivity(oAuthManager.getAuthIntent(Platform.TIKTOK))
            }
        }
    }

    private fun setupUploadButton() {
        findViewById<Button>(R.id.btn_upload).setOnClickListener {
            val title = findViewById<EditText>(R.id.et_title).text.toString()
            val description = findViewById<EditText>(R.id.et_description).text.toString()
            val tags = findViewById<EditText>(R.id.et_tags).text.toString()
                .split(",").map { it.trim() }.filter { it.isNotBlank() }
            val uploadYT = findViewById<CheckBox>(R.id.cb_youtube).isChecked
            val uploadTT = findViewById<CheckBox>(R.id.cb_tiktok).isChecked

            if (title.isBlank()) {
                showStatus("Bitte einen Titel eingeben", isError = true)
                return@setOnClickListener
            }

            val filePath = clipFilePath
            if (filePath == null) {
                showStatus("Keine Datei ausgewaehlt", isError = true)
                return@setOnClickListener
            }

            lifecycleScope.launch {
                viewModel.uploadClip(
                    context = this@UploadActivity,
                    filePath = filePath,
                    title = title,
                    description = description,
                    tags = tags,
                    uploadToYouTube = uploadYT,
                    uploadToTikTok = uploadTT
                )
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uploadState.collect { state ->
                showStatus(state.statusText, isError = state.error != null)

                // Progress bars
                val ytProgress = findViewById<ProgressBar>(R.id.progress_youtube)
                val ttProgress = findViewById<ProgressBar>(R.id.progress_tiktok)
                ytProgress.visibility = if (state.isUploading) View.VISIBLE else View.GONE
                ttProgress.visibility = if (state.isUploading) View.VISIBLE else View.GONE
                ytProgress.progress = state.youtubeProgress
                ttProgress.progress = state.tiktokProgress

                // Results
                val layoutResults = findViewById<LinearLayout>(R.id.layout_results)
                val tvResults = findViewById<TextView>(R.id.tv_results)
                if (state.results.isNotEmpty()) {
                    layoutResults.visibility = View.VISIBLE
                    tvResults.text = state.results.joinToString("\n")
                }
            }
        }
    }

    private fun updateConnectionStatus() {
        val ytTokens = oAuthManager.getStoredTokens(Platform.YOUTUBE)
        val ttTokens = oAuthManager.getStoredTokens(Platform.TIKTOK)

        val ytStatus = findViewById<TextView>(R.id.tv_youtube_status)
        val ttStatus = findViewById<TextView>(R.id.tv_tiktok_status)
        val btnYt = findViewById<Button>(R.id.btn_connect_youtube)
        val btnTt = findViewById<Button>(R.id.btn_connect_tiktok)
        val cbYt = findViewById<CheckBox>(R.id.cb_youtube)
        val cbTt = findViewById<CheckBox>(R.id.cb_tiktok)

        if (ytTokens != null) {
            ytStatus.text = "\u2705 Verbunden"
            btnYt.text = "Trennen"
            cbYt.isEnabled = true
        } else {
            ytStatus.text = "\u274C Nicht verbunden"
            btnYt.text = "Verbinden"
            cbYt.isEnabled = false
            cbYt.isChecked = false
        }

        if (ttTokens != null) {
            ttStatus.text = "\u2705 Verbunden"
            btnTt.text = "Trennen"
            cbTt.isEnabled = true
        } else {
            ttStatus.text = "\u274C Nicht verbunden"
            btnTt.text = "Verbinden"
            cbTt.isEnabled = false
            cbTt.isChecked = false
        }
    }

    private fun showStatus(text: String, isError: Boolean = false) {
        val tv = findViewById<TextView>(R.id.tv_status)
        tv.text = text
        tv.setTextColor(if (isError) 0xFFFF6B6B.toInt() else 0xFF6C63FF.toInt())
    }

    override fun onResume() {
        super.onResume()
        // Handle OAuth callback
        intent?.data?.let { uri ->
            handleOAuthCallback(uri)
        }
        updateConnectionStatus()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.data?.let { uri ->
            handleOAuthCallback(uri)
        }
    }

    private fun handleOAuthCallback(uri: Uri) {
        val code = uri.getQueryParameter("code") ?: return
        val state = uri.getQueryParameter("state")

        val platform = when {
            state?.contains("youtube", ignoreCase = true) == true -> Platform.YOUTUBE
            state?.contains("tiktok", ignoreCase = true) == true -> Platform.TIKTOK
            uri.toString().contains("youtube") -> Platform.YOUTUBE
            else -> Platform.TIKTOK
        }

        lifecycleScope.launch {
            try {
                oAuthManager.exchangeCode(platform, code)
                updateConnectionStatus()
                showStatus("${platform.displayName} verbunden!")
            } catch (e: Exception) {
                showStatus("Auth fehlgeschlagen: ${e.message}", isError = true)
            }
        }
    }
}
