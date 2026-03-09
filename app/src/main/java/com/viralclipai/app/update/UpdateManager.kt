package com.viralclipai.app.update

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.viralclipai.app.data.api.ApiClient
import kotlinx.coroutines.*
import java.io.File
import java.net.URL

object UpdateManager {
    private const val TAG = "UpdateManager"
    private const val PREF_NAME = "update_prefs"
    private const val KEY_LAST_CHECK = "last_check_time"
    private const val KEY_SKIPPED_VERSION = "skipped_version"
    private const val CHECK_INTERVAL_MS = 4 * 60 * 60 * 1000L  // 4 hours

    fun checkOnStart(activity: Activity, currentVersionCode: Int) {
        val prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0)
        val now = System.currentTimeMillis()

        if (now - lastCheck < CHECK_INTERVAL_MS) {
            Log.d(TAG, "Skipping update check - last check was ${(now - lastCheck) / 1000}s ago")
            return
        }

        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
        performCheck(activity, currentVersionCode, silent = true)
    }

    fun forceCheck(activity: Activity, currentVersionCode: Int) {
        performCheck(activity, currentVersionCode, silent = false)
    }

    private fun performCheck(activity: Activity, currentVersionCode: Int, silent: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.getService().checkUpdate()
                val latestCode = response.latestVersionCode
                val latestVersion = response.latestVersion
                val downloadUrl = response.downloadUrl
                val changelog = response.changelog
                val forceUpdate = response.forceUpdate

                if (latestCode <= currentVersionCode) {
                    if (!silent) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(activity, "\u2705 Du hast die neueste Version (v$latestVersion)", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return@launch
                }

                // Check if user skipped this version
                val prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                val skippedVersion = prefs.getInt(KEY_SKIPPED_VERSION, 0)
                if (silent && !forceUpdate && skippedVersion == latestCode) {
                    Log.d(TAG, "User skipped version $latestCode")
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    showUpdateDialog(activity, latestVersion, latestCode, downloadUrl, changelog, forceUpdate)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed: ${e.message}")
                if (!silent) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(activity, "\u26A0\uFE0F Update-Check fehlgeschlagen: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showUpdateDialog(
        activity: Activity,
        version: String,
        versionCode: Int,
        downloadUrl: String,
        changelog: String,
        forceUpdate: Boolean
    ) {
        val builder = AlertDialog.Builder(activity)
            .setTitle("\uD83D\uDE80 Update v$version verfuegbar!")
            .setMessage("$changelog\n\nJetzt updaten?")
            .setPositiveButton("Updaten") { _, _ ->
                downloadAndInstall(activity, downloadUrl, version)
            }

        if (!forceUpdate) {
            builder.setNegativeButton("Spaeter", null)
            builder.setNeutralButton("Ueberspringen") { _, _ ->
                activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit().putInt(KEY_SKIPPED_VERSION, versionCode).apply()
                Toast.makeText(activity, "Version $version wird uebersprungen", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setCancelable(!forceUpdate)
        builder.show()
    }

    private fun downloadAndInstall(activity: Activity, downloadUrl: String, version: String) {
        Toast.makeText(activity, "\u2B07\uFE0F Update wird heruntergeladen...", Toast.LENGTH_LONG).show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val updateDir = File(activity.getExternalFilesDir(null), "updates")
                if (!updateDir.exists()) updateDir.mkdirs()
                val apkFile = File(updateDir, "ViralClipAI-v$version.apk")

                URL(downloadUrl).openStream().use { input ->
                    apkFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                withContext(Dispatchers.Main) {
                    installApk(activity, apkFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(activity, "\u274C Download fehlgeschlagen: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun installApk(activity: Activity, apkFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!activity.packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(activity, "Bitte erlaube die Installation aus unbekannten Quellen", Toast.LENGTH_LONG).show()
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .setData(android.net.Uri.parse("package:${activity.packageName}"))
                    activity.startActivity(intent)
                    return
                }
            }

            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Install failed: ${e.message}")
            Toast.makeText(activity, "\u274C Installation fehlgeschlagen: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
