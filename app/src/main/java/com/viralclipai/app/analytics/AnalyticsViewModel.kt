package com.viralclipai.app.analytics

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AnalyticsViewModel : ViewModel() {

    companion object {
        private const val TAG = "AnalyticsVM"
        private const val BASE_URL = "http://10.0.2.2:8000"
    }

    data class AnalyticsState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val totalViews: Long = 0,
        val totalLikes: Long = 0,
        val avgEngagement: Double = 0.0,
        val overallScore: Int = 0,
        val insights: List<InsightItem> = emptyList(),
        val videos: List<VideoStat> = emptyList()
    )

    data class InsightItem(
        val category: String,
        val score: Float,
        val recommendation: String
    )

    data class VideoStat(
        val videoId: String,
        val platform: String,
        val views: Long,
        val likes: Long,
        val engagementRate: Double
    )

    private val _state = MutableStateFlow(AnalyticsState())
    val state: StateFlow<AnalyticsState> = _state

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val summary = fetchJson("$BASE_URL/api/v1/analytics/summary")
                val insightsResponse = fetchJson("$BASE_URL/api/v1/analytics/insights")

                val insights = mutableListOf<InsightItem>()
                val insightsArray = insightsResponse.optJSONArray("insights")
                if (insightsArray != null) {
                    for (i in 0 until insightsArray.length()) {
                        val obj = insightsArray.getJSONObject(i)
                        insights.add(InsightItem(
                            category = obj.getString("category"),
                            score = obj.getDouble("score").toFloat(),
                            recommendation = obj.getString("recommendation")
                        ))
                    }
                }

                val totalViews = summary.optLong("total_views", 0)
                val totalLikes = summary.optLong("total_likes", 0)
                val avgEng = summary.optDouble("avg_engagement", 0.0)
                val avgScore = if (insights.isNotEmpty())
                    (insights.sumOf { it.score.toDouble() } / insights.size * 100).toInt()
                else 0

                // Parse videos from bestVideo
                val videos = mutableListOf<VideoStat>()
                val best = summary.optJSONObject("best_video")
                if (best != null) {
                    videos.add(VideoStat(
                        videoId = best.optString("video_id", "?"),
                        platform = best.optString("platform", "?"),
                        views = best.optLong("views", 0),
                        likes = best.optLong("likes", 0),
                        engagementRate = best.optDouble("engagement_rate", 0.0)
                    ))
                }

                _state.value = AnalyticsState(
                    isLoading = false,
                    totalViews = totalViews,
                    totalLikes = totalLikes,
                    avgEngagement = avgEng,
                    overallScore = avgScore,
                    insights = insights,
                    videos = videos
                )
            } catch (e: Exception) {
                Log.e(TAG, "Load analytics failed", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Daten konnten nicht geladen werden: ${e.message}"
                )
            }
        }
    }

    private suspend fun fetchJson(urlStr: String): JSONObject = withContext(Dispatchers.IO) {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        try {
            if (conn.responseCode == 200) {
                JSONObject(conn.inputStream.bufferedReader().readText())
            } else {
                throw Exception("HTTP ${conn.responseCode}")
            }
        } finally {
            conn.disconnect()
        }
    }
}
