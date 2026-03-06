package com.viralclipai.app.analytics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.viralclipai.app.R
import kotlinx.coroutines.launch

class AnalyticsDashboardActivity : AppCompatActivity() {

    private lateinit var viewModel: AnalyticsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics)

        viewModel = ViewModelProvider(this)[AnalyticsViewModel::class.java]

        val rvInsights = findViewById<RecyclerView>(R.id.rv_insights)
        rvInsights.layoutManager = LinearLayoutManager(this)

        val rvVideos = findViewById<RecyclerView>(R.id.rv_videos)
        rvVideos.layoutManager = LinearLayoutManager(this)

        findViewById<View>(R.id.btn_refresh).setOnClickListener {
            viewModel.loadData()
        }

        lifecycleScope.launch {
            viewModel.state.collect { state ->
                // Loading
                findViewById<TextView>(R.id.tv_loading).visibility =
                    if (state.isLoading) View.VISIBLE else View.GONE

                // Error
                val tvError = findViewById<TextView>(R.id.tv_error)
                if (state.error != null) {
                    tvError.text = state.error
                    tvError.visibility = View.VISIBLE
                } else {
                    tvError.visibility = View.GONE
                }

                // Stats
                findViewById<TextView>(R.id.tv_total_views).text = formatNumber(state.totalViews)
                findViewById<TextView>(R.id.tv_total_likes).text = formatNumber(state.totalLikes)
                findViewById<TextView>(R.id.tv_avg_engagement).text = "${(state.avgEngagement * 100).toInt()}%"

                // Overall score
                findViewById<ProgressBar>(R.id.progress_overall).progress = state.overallScore
                findViewById<TextView>(R.id.tv_overall_score).text = "${state.overallScore}/100"

                // Insights
                rvInsights.adapter = InsightAdapter(state.insights)

                // Videos
                rvVideos.adapter = VideoStatAdapter(state.videos)
            }
        }
    }

    private fun formatNumber(n: Long): String = when {
        n >= 1_000_000 -> "${n / 1_000_000}.${(n % 1_000_000) / 100_000}M"
        n >= 1_000 -> "${n / 1_000}.${(n % 1_000) / 100}K"
        else -> n.toString()
    }

    // --- Insight Adapter ---
    class InsightAdapter(private val items: List<AnalyticsViewModel.InsightItem>) :
        RecyclerView.Adapter<InsightAdapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvCategory: TextView = view.findViewById(R.id.tv_insight_category)
            val tvScore: TextView = view.findViewById(R.id.tv_insight_score)
            val progress: ProgressBar = view.findViewById(R.id.progress_insight_score)
            val tvRec: TextView = view.findViewById(R.id.tv_insight_recommendation)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_insight, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvCategory.text = item.category
            holder.tvScore.text = "${(item.score * 100).toInt()}%"
            holder.progress.progress = (item.score * 100).toInt()
            holder.tvRec.text = item.recommendation
        }

        override fun getItemCount() = items.size
    }

    // --- Video Stats Adapter ---
    class VideoStatAdapter(private val items: List<AnalyticsViewModel.VideoStat>) :
        RecyclerView.Adapter<VideoStatAdapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvVideoId: TextView = view.findViewById(R.id.tv_video_id)
            val tvPlatform: TextView = view.findViewById(R.id.tv_platform)
            val tvViews: TextView = view.findViewById(R.id.tv_views)
            val tvLikes: TextView = view.findViewById(R.id.tv_likes)
            val tvEngagement: TextView = view.findViewById(R.id.tv_engagement)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_video_stat, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvVideoId.text = item.videoId
            holder.tvPlatform.text = if (item.platform == "youtube") "\uD83D\uDCFA YouTube" else "\uD83C\uDFB5 TikTok"
            holder.tvViews.text = "\uD83D\uDC41 ${item.views}"
            holder.tvLikes.text = "\u2764 ${item.likes}"
            holder.tvEngagement.text = "${(item.engagementRate * 100).toInt()}%"
        }

        override fun getItemCount() = items.size
    }
}
