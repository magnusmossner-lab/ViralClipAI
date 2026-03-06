package com.viralclipai.app.analytics

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

class AnalyticsSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "AnalyticsSyncWorker"
        private const val WORK_NAME = "analytics_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val work = PeriodicWorkRequestBuilder<AnalyticsSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                work
            )
            Log.i(TAG, "Analytics sync work scheduled (every 6h)")
        }
    }

    override suspend fun doWork(): Result {
        return try {
            Log.i(TAG, "Starting analytics sync...")
            val client = AnalyticsClient(applicationContext)
            client.syncAllPlatforms()
            Log.i(TAG, "Analytics sync completed")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Analytics sync failed: ${e.message}", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
