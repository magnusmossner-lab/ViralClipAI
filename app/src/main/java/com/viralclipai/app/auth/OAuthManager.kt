package com.viralclipai.app.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class Platform(val displayName: String) {
    YOUTUBE("YouTube"),
    TIKTOK("TikTok")
}

data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Long,
    val platform: Platform
) {
    val isExpired: Boolean get() = System.currentTimeMillis() >= expiresAt - 60_000
}

class OAuthManager(private val context: Context) {

    companion object {
        private const val TAG = "OAuthManager"
        private const val YT_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val YT_TOKEN_URL = "https://oauth2.googleapis.com/token"
        private const val YT_SCOPES = "https://www.googleapis.com/auth/youtube.upload https://www.googleapis.com/auth/youtube.readonly https://www.googleapis.com/auth/yt-analytics.readonly"
        private const val TT_AUTH_URL = "https://www.tiktok.com/v2/auth/authorize/"
        private const val TT_TOKEN_URL = "https://open.tiktokapis.com/v2/oauth/token/"
        private const val TT_SCOPES = "user.info.basic,video.publish,video.upload,video.list"
        private const val REDIRECT_URI = "com.viralclipai.app://oauth/callback"
        private const val PREFS_NAME = "viralclip_oauth_tokens"
    }

    private val prefs by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context, PREFS_NAME, masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "EncryptedPrefs failed, falling back to regular prefs", e)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private fun getConfig(): JSONObject {
        return try {
            val stream = context.assets.open("oauth_config.json")
            JSONObject(stream.bufferedReader().readText())
        } catch (e: Exception) {
            Log.e(TAG, "Could not read oauth_config.json", e)
            JSONObject()
        }
    }

    fun getAuthIntent(platform: Platform): Intent {
        val config = getConfig().optJSONObject(platform.name.lowercase()) ?: JSONObject()
        val clientId = config.optString("client_id", "")

        val uri = when (platform) {
            Platform.YOUTUBE -> Uri.parse(YT_AUTH_URL).buildUpon()
                .appendQueryParameter("client_id", clientId)
                .appendQueryParameter("redirect_uri", REDIRECT_URI)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("scope", YT_SCOPES)
                .appendQueryParameter("access_type", "offline")
                .appendQueryParameter("prompt", "consent")
                .appendQueryParameter("state", "youtube")
                .build()

            Platform.TIKTOK -> Uri.parse(TT_AUTH_URL).buildUpon()
                .appendQueryParameter("client_key", clientId)
                .appendQueryParameter("redirect_uri", REDIRECT_URI)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("scope", TT_SCOPES)
                .appendQueryParameter("state", "tiktok")
                .build()
        }

        return Intent(Intent.ACTION_VIEW, uri)
    }

    suspend fun exchangeCode(platform: Platform, code: String): OAuthTokens =
        withContext(Dispatchers.IO) {
            val config = getConfig().optJSONObject(platform.name.lowercase()) ?: JSONObject()
            val clientId = config.optString("client_id", "")
            val clientSecret = config.optString("client_secret", "")

            val tokenUrl = when (platform) {
                Platform.YOUTUBE -> YT_TOKEN_URL
                Platform.TIKTOK -> TT_TOKEN_URL
            }

            val params = when (platform) {
                Platform.YOUTUBE -> mapOf(
                    "code" to code, "client_id" to clientId, "client_secret" to clientSecret,
                    "redirect_uri" to REDIRECT_URI, "grant_type" to "authorization_code"
                )
                Platform.TIKTOK -> mapOf(
                    "client_key" to clientId, "client_secret" to clientSecret,
                    "code" to code, "grant_type" to "authorization_code", "redirect_uri" to REDIRECT_URI
                )
            }

            val body = params.entries.joinToString("&") {
                "${it.key}=${java.net.URLEncoder.encode(it.value, "UTF-8")}"
            }
            val conn = URL(tokenUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.outputStream.write(body.toByteArray())

            val responseCode = conn.responseCode
            val responseBody = try {
                if (responseCode in 200..299) conn.inputStream.bufferedReader().readText()
                else conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $responseCode"
            } catch (e: Exception) { "Error: ${e.message}" }
            conn.disconnect()

            if (responseCode !in 200..299) {
                throw Exception("Token exchange failed ($responseCode): $responseBody")
            }

            val json = JSONObject(responseBody)
            val accessToken = json.optString("access_token", "")
            val refreshToken = json.optString("refresh_token")
            val expiresIn = json.optLong("expires_in", 3600)

            if (accessToken.isBlank()) {
                throw Exception("No access_token in response")
            }

            val tokens = OAuthTokens(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAt = System.currentTimeMillis() + (expiresIn * 1000),
                platform = platform
            )
            storeTokens(tokens)
            tokens
        }

    fun storeTokens(tokens: OAuthTokens) {
        val key = tokens.platform.name.lowercase()
        prefs.edit()
            .putString("${key}_access_token", tokens.accessToken)
            .putString("${key}_refresh_token", tokens.refreshToken)
            .putLong("${key}_expires_at", tokens.expiresAt)
            .apply()
    }

    fun getStoredTokens(platform: Platform): OAuthTokens? {
        val key = platform.name.lowercase()
        val accessToken = prefs.getString("${key}_access_token", null) ?: return null
        return OAuthTokens(
            accessToken = accessToken,
            refreshToken = prefs.getString("${key}_refresh_token", null),
            expiresAt = prefs.getLong("${key}_expires_at", 0),
            platform = platform
        )
    }

    fun clearTokens(platform: Platform) {
        val key = platform.name.lowercase()
        prefs.edit()
            .remove("${key}_access_token")
            .remove("${key}_refresh_token")
            .remove("${key}_expires_at")
            .apply()
    }

    fun isLoggedIn(platform: Platform): Boolean {
        val tokens = getStoredTokens(platform)
        return tokens != null && !tokens.isExpired
    }

    suspend fun refreshTokenIfNeeded(platform: Platform): OAuthTokens? {
        val tokens = getStoredTokens(platform) ?: return null
        if (!tokens.isExpired) return tokens

        val refreshToken = tokens.refreshToken ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val config = getConfig().optJSONObject(platform.name.lowercase()) ?: return@withContext null
                val clientId = config.optString("client_id", "")
                val clientSecret = config.optString("client_secret", "")

                val tokenUrl = when (platform) {
                    Platform.YOUTUBE -> YT_TOKEN_URL
                    Platform.TIKTOK -> TT_TOKEN_URL
                }

                val params = when (platform) {
                    Platform.YOUTUBE -> "client_id=$clientId&client_secret=$clientSecret&refresh_token=$refreshToken&grant_type=refresh_token"
                    Platform.TIKTOK -> "client_key=$clientId&client_secret=$clientSecret&refresh_token=$refreshToken&grant_type=refresh_token"
                }

                val conn = URL(tokenUrl).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.doOutput = true
                conn.outputStream.write(params.toByteArray())

                if (conn.responseCode == 200) {
                    val json = JSONObject(conn.inputStream.bufferedReader().readText())
                    val newTokens = OAuthTokens(
                        accessToken = json.getString("access_token"),
                        refreshToken = json.optString("refresh_token", refreshToken),
                        expiresAt = System.currentTimeMillis() + (json.optLong("expires_in", 3600) * 1000),
                        platform = platform
                    )
                    storeTokens(newTokens)
                    conn.disconnect()
                    newTokens
                } else {
                    conn.disconnect()
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Token refresh failed: ${e.message}")
                null
            }
        }
    }
}
