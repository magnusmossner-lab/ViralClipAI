package com.viralclipai.app

import android.app.Application
import android.util.Log
import com.viralclipai.app.analytics.AnalyticsSyncWorker

class ViralClipApp : Application() {

    companion object {
        private const val TAG = "ViralClipApp"
        lateinit var instance: ViralClipApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Global crash handler - self-healing: log & restart
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception in ${thread.name}", throwable)
            try {
                // Store crash info for self-healing
                getSharedPreferences("crash_log", MODE_PRIVATE).edit()
                    .putString("last_crash", throwable.stackTraceToString())
                    .putLong("last_crash_time", System.currentTimeMillis())
                    .apply()
            } catch (_: Exception) { }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Schedule periodic analytics sync (every 6h)
        try {
            AnalyticsSyncWorker.schedule(this)
            Log.i(TAG, "Analytics sync scheduled")
        } catch (e: Exception) {
            Log.w(TAG, "Could not schedule analytics sync: ${e.message}")
        }
    }
}
