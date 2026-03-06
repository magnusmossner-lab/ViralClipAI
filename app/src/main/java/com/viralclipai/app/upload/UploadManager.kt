package com.viralclipai.app.upload

import android.content.Context
import android.util.Log
import com.viralclipai.app.auth.OAuthManager
import com.viralclipai.app.auth.Platform
import java.io.File

/**
 * Central upload manager that coordinates uploads to multiple platforms.
 */
class UploadManager(private val context: Context) {

    companion object {
        private const val TAG = "UploadManager"
    }

    private val oAuthManager = OAuthManager(context)

    fun isConnected(platform: Platform): Boolean = oAuthManager.isLoggedIn(platform)

    fun getConnectedPlatforms(): List<Platform> =
        Platform.entries.filter { oAuthManager.isLoggedIn(it) }

    suspend fun uploadToAll(
        file: File,
        title: String,
        description: String = "",
        tags: List<String> = emptyList(),
        onProgress: (Platform, Int) -> Unit
    ): Map<Platform, Result<String>> {
        val results = mutableMapOf<Platform, Result<String>>()

        for (platform in getConnectedPlatforms()) {
            try {
                val tokens = oAuthManager.refreshTokenIfNeeded(platform)
                    ?: throw Exception("Nicht eingeloggt bei ${platform.displayName}")

                val videoId = when (platform) {
                    Platform.YOUTUBE -> {
                        YouTubeUploader().upload(
                            accessToken = tokens.accessToken,
                            file = file,
                            title = title,
                            description = description,
                            tags = tags
                        ) { onProgress(platform, it) }
                    }
                    Platform.TIKTOK -> {
                        TikTokUploader().upload(
                            accessToken = tokens.accessToken,
                            file = file,
                            title = title,
                            tags = tags
                        ) { onProgress(platform, it) }
                    }
                }
                results[platform] = Result.success(videoId)
            } catch (e: Exception) {
                Log.e(TAG, "${platform.displayName} upload failed", e)
                results[platform] = Result.failure(e)
            }
        }

        return results
    }
}
