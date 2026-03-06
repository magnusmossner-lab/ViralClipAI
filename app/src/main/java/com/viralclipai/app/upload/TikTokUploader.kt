package com.viralclipai.app.upload

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class TikTokUploader {

    companion object {
        private const val TAG = "TikTokUploader"
        private const val INIT_URL = "https://open.tiktokapis.com/v2/post/publish/inbox/video/init/"
        private const val CHUNK_SIZE = 5 * 1024 * 1024 // 5MB chunks
    }

    suspend fun upload(
        accessToken: String,
        file: File,
        title: String,
        tags: List<String>,
        onProgress: (Int) -> Unit
    ): String = withContext(Dispatchers.IO) {
        // Step 1: Init upload
        val initBody = JSONObject().apply {
            put("source_info", JSONObject().apply {
                put("source", "FILE_UPLOAD")
                put("video_size", file.length())
                put("chunk_size", CHUNK_SIZE)
                put("total_chunk_count", (file.length() + CHUNK_SIZE - 1) / CHUNK_SIZE)
            })
        }

        val initConn = URL(INIT_URL).openConnection() as HttpURLConnection
        initConn.requestMethod = "POST"
        initConn.setRequestProperty("Authorization", "Bearer $accessToken")
        initConn.setRequestProperty("Content-Type", "application/json")
        initConn.doOutput = true
        initConn.outputStream.write(initBody.toString().toByteArray())

        if (initConn.responseCode != 200) {
            val errorBody = initConn.errorStream?.bufferedReader()?.readText() ?: "HTTP ${initConn.responseCode}"
            initConn.disconnect()
            throw Exception("TikTok init failed: $errorBody")
        }

        val initResponse = JSONObject(initConn.inputStream.bufferedReader().readText())
        initConn.disconnect()

        val data = initResponse.getJSONObject("data")
        val publishId = data.getString("publish_id")
        val uploadUrl = data.getString("upload_url")

        // Step 2: Upload chunks
        val fileSize = file.length()
        var uploaded = 0L
        val buffer = ByteArray(CHUNK_SIZE)

        file.inputStream().use { input ->
            while (uploaded < fileSize) {
                val bytesRead = input.read(buffer)
                if (bytesRead == -1) break

                val chunkConn = URL(uploadUrl).openConnection() as HttpURLConnection
                chunkConn.requestMethod = "PUT"
                chunkConn.setRequestProperty("Content-Type", "video/mp4")
                chunkConn.setRequestProperty(
                    "Content-Range",
                    "bytes $uploaded-${uploaded + bytesRead - 1}/$fileSize"
                )
                chunkConn.doOutput = true
                chunkConn.setFixedLengthStreamingMode(bytesRead)
                chunkConn.outputStream.write(buffer, 0, bytesRead)

                val code = chunkConn.responseCode
                chunkConn.disconnect()

                if (code !in 200..299) {
                    throw Exception("TikTok chunk upload failed: HTTP $code")
                }

                uploaded += bytesRead
                onProgress((uploaded * 100 / fileSize).toInt())
            }
        }

        onProgress(100)
        Log.i(TAG, "TikTok upload complete: $publishId")
        publishId
    }
}
