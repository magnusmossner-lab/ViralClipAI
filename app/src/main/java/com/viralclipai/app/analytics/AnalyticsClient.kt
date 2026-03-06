package com.viralclipai.app.analytics

import android.content.Context
import android.util.Log
import com.viralclipai.app.auth.OAuthManager
import com.viralclipai.app.auth.Platform
import com.viralclipai.app.data.api.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AnalyticsClient(private val context: Context) {

    companion object {
        private const val TAG = "AnalyticsClient"
    }

    private val oAuthManager = OAuthManager(context)

    suspend fun syncAllPlatforms() {
        val stats = mutableListOf<JSONObject>()

        // YouTube
        try {
            val ytTokens = oAuthManager.getStoredTokens(Platform.YOUTUBE)
            if (ytTokens != null) {
                val ytStats = fetchYouTubeStats(ytTokens.accessToken)
                stats.addAll(ytStats)
            }
        } catch (e: Exception) {
            Log.w(TAG, "YouTube sync failed: ${e.message}")
        }

        // TikTok
        try {
            val ttTokens = oAuthManager.getStoredTokens(Platform.TIKTOK)
            if (ttTokens != null) {
                val ttStats = fetchTikTokStats(ttTokens.accessToken)
                stats.addAll(ttStats)
            }
        } catch (e: Exception) {
            Log.w(TAG, "TikTok sync failed: ${e.message}")
        }

        // Send to backend
        if (stats.isNotEmpty()) {
            sendToBackend(stats)
        }
    }

    private suspend fun fetchYouTubeStats(accessToken: String): List<JSONObject> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<JSONObject>()
            try {
                val url = URL("https://www.googleapis.com/youtube/v3/channels?part=contentDetails&mine=true")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("Authorization", "Bearer $accessToken")
                conn.connectTimeout = 15000
                conn.readTimeout = 15000

                if (conn.responseCode == 200) {
                    val json = JSONObject(conn.inputStream.bufferedReader().readText())
                    val items = json.optJSONArray("items")
                    if (items != null && items.length() > 0) {
                        val uploadsPlaylistId = items.getJSONObject(0)
                            .getJSONObject("contentDetails")
                            .getJSONObject("relatedPlaylists")
                            .getString("uploads")

                        // Fetch recent videos
                        val videosUrl = URL("https://www.googleapis.com/youtube/v3/playlistItems?part=snippet&maxResults=50&playlistId=$uploadsPlaylistId")
                        val vConn = videosUrl.openConnection() as HttpURLConnection
                        vConn.setRequestProperty("Authorization", "Bearer $accessToken")

                        if (vConn.responseCode == 200) {
                            val vJson = JSONObject(vConn.inputStream.bufferedReader().readText())
                            val vItems = vJson.optJSONArray("items") ?: JSONArray()
                            for (i in 0 until vItems.length()) {
                                val videoId = vItems.getJSONObject(i)
                                    .getJSONObject("snippet")
                                    .getJSONObject("resourceId")
                                    .getString("videoId")

                                val statsUrl = URL("https://www.googleapis.com/youtube/v3/videos?part=statistics&id=$videoId")
                                val sConn = statsUrl.openConnection() as HttpURLConnection
                                sConn.setRequestProperty("Authorization", "Bearer $accessToken")

                                if (sConn.responseCode == 200) {
                                    val sJson = JSONObject(sConn.inputStream.bufferedReader().readText())
                                    val sItems = sJson.optJSONArray("items")
                                    if (sItems != null && sItems.length() > 0) {
                                        val stats = sItems.getJSONObject(0).getJSONObject("statistics")
                                        val views = stats.optLong("viewCount", 0)
                                        val likes = stats.optLong("likeCount", 0)
                                        val comments = stats.optLong("commentCount", 0)

                                        results.add(JSONObject().apply {
                                            put("video_id", videoId)
                                            put("platform", "youtube")
                                            put("views", views)
                                            put("likes", likes)
                                            put("comments", comments)
                                            put("shares", 0)
                                            put("engagement_rate", if (views > 0) (likes + comments).toDouble() / views else 0.0)
                                        })
                                    }
                                }
                                sConn.disconnect()
                            }
                        }
                        vConn.disconnect()
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "YouTube stats error: ${e.message}")
            }
            results
        }

    private suspend fun fetchTikTokStats(accessToken: String): List<JSONObject> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<JSONObject>()
            try {
                val url = URL("https://open.tiktokapis.com/v2/video/list/?fields=id,title,view_count,like_count,comment_count,share_count")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer $accessToken")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.outputStream.write("{\"max_count\": 20}".toByteArray())

                if (conn.responseCode == 200) {
                    val json = JSONObject(conn.inputStream.bufferedReader().readText())
                    val videos = json.optJSONObject("data")?.optJSONArray("videos") ?: JSONArray()
                    for (i in 0 until videos.length()) {
                        val v = videos.getJSONObject(i)
                        val views = v.optLong("view_count", 0)
                        val likes = v.optLong("like_count", 0)
                        val comments = v.optLong("comment_count", 0)
                        val shares = v.optLong("share_count", 0)

                        results.add(JSONObject().apply {
                            put("video_id", v.getString("id"))
                            put("platform", "tiktok")
                            put("views", views)
                            put("likes", likes)
                            put("comments", comments)
                            put("shares", shares)
                            put("engagement_rate", if (views > 0) (likes + comments + shares).toDouble() / views else 0.0)
                        })
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "TikTok stats error: ${e.message}")
            }
            results
        }

    private suspend fun sendToBackend(stats: List<JSONObject>) =
        withContext(Dispatchers.IO) {
            try {
                val service = ApiClient.getService()
                // Send via direct HTTP to analytics endpoint
                val body = JSONObject().apply {
                    put("stats", JSONArray(stats.map { it }))
                }
                val url = URL("${getBaseUrl()}api/v1/analytics/sync")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.outputStream.write(body.toString().toByteArray())
                val responseCode = conn.responseCode
                Log.i(TAG, "Backend sync response: $responseCode")
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Backend sync failed: ${e.message}")
            }
        }

    private fun getBaseUrl(): String {
        // Read from ApiClient - fallback to default
        return "http://10.0.2.2:8000/"
    }
}
