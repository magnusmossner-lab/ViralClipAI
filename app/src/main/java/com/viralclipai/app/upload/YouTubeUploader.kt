package com.viralclipai.app.upload

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class YouTubeUploader {

    companion object {
        private const val TAG = "YouTubeUploader"
        private const val UPLOAD_URL = "https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status"
        private const val CHUNK_SIZE = 2 * 1024 * 1024 // 2MB chunks
    }

    suspend fun upload(
        accessToken: String,
        file: File,
        title: String,
        description: String,
        tags: List<String>,
        onProgress: (Int) -> Unit
    ): String = withContext(Dispatchers.IO) {
        // Step 1: Initiate resumable upload
        val metadata = JSONObject().apply {
            put("snippet", JSONObject().apply {
                put("title", title)
                put("description", description)
                put("tags", JSONArray(tags))
                put("categoryId", "22") // People & Blogs
            })
            put("status", JSONObject().apply {
                put("privacyStatus", "public")
                put("selfDeclaredMadeForKids", false)
                put("madeForKids", false)
            })
        }

        val initConn = URL(UPLOAD_URL).openConnection() as HttpURLConnection
        initConn.requestMethod = "POST"
        initConn.setRequestProperty("Authorization", "Bearer $accessToken")
        initConn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        initConn.setRequestProperty("X-Upload-Content-Type", "video/mp4")
        initConn.setRequestProperty("X-Upload-Content-Length", file.length().toString())
        initConn.doOutput = true
        initConn.outputStream.write(metadata.toString().toByteArray())

        if (initConn.responseCode != 200) {
            val errorBody = initConn.errorStream?.bufferedReader()?.readText() ?: "HTTP ${initConn.responseCode}"
            initConn.disconnect()
            throw Exception("Upload init failed: $errorBody")
        }

        val uploadUri = initConn.getHeaderField("Location")
            ?: throw Exception("No upload URI returned")
        initConn.disconnect()

        // Step 2: Upload file in chunks
        val fileSize = file.length()
        var uploaded = 0L
        val buffer = ByteArray(CHUNK_SIZE)

        file.inputStream().use { input ->
            while (uploaded < fileSize) {
                val bytesRead = input.read(buffer)
                if (bytesRead == -1) break

                val chunkConn = URL(uploadUri).openConnection() as HttpURLConnection
                chunkConn.requestMethod = "PUT"
                chunkConn.setRequestProperty("Content-Type", "video/mp4")
                chunkConn.setRequestProperty(
                    "Content-Range",
                    "bytes $uploaded-${uploaded + bytesRead - 1}/$fileSize"
                )
                chunkConn.doOutput = true
                chunkConn.fixedLengthStreamingMode = bytesRead
                chunkConn.outputStream.write(buffer, 0, bytesRead)

                val code = chunkConn.responseCode
                if (code == 200 || code == 201) {
                    // Upload complete
                    val responseBody = chunkConn.inputStream.bufferedReader().readText()
                    chunkConn.disconnect()
                    onProgress(100)
                    val json = JSONObject(responseBody)
                    return@withContext json.getString("id")
                } else if (code == 308) {
                    // Resume incomplete - continue
                    uploaded += bytesRead
                    onProgress((uploaded * 100 / fileSize).toInt())
                    chunkConn.disconnect()
                } else {
                    val errorBody = chunkConn.errorStream?.bufferedReader()?.readText() ?: ""
                    chunkConn.disconnect()
                    throw Exception("Upload chunk failed ($code): $errorBody")
                }
            }
        }

        throw Exception("Upload completed without response")
    }
}
