package com.viralclipai.app.data.api

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private var baseUrl = "https://viralclipai-backend-production.up.railway.app/"

    /** Standard client for short requests (health, process, status) */
    private val standardClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionPool(ConnectionPool(5, 3, TimeUnit.MINUTES))
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    /** Separate download client with very long read timeout for large video files */
    private val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(600, TimeUnit.SECONDS)   // 10 minutes for large video downloads
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionPool(ConnectionPool(3, 3, TimeUnit.MINUTES))
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    // FIX: Separate upload client with long WRITE timeout for large video gallery uploads
    /** Separate upload client with very long write timeout for large video uploads */
    private val uploadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(600, TimeUnit.SECONDS)  // 10 minutes for large video uploads
            .retryOnConnectionFailure(false)       // Don't retry uploads (could double-send)
            .connectionPool(ConnectionPool(3, 3, TimeUnit.MINUTES))
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    @Volatile private var retrofit: Retrofit? = null
    @Volatile private var apiService: ViralClipApiService? = null
    @Volatile private var downloadRetrofit: Retrofit? = null
    @Volatile private var downloadService: ViralClipApiService? = null
    @Volatile private var uploadRetrofit: Retrofit? = null  // FIX
    @Volatile private var uploadService: ViralClipApiService? = null  // FIX

    fun setBaseUrl(url: String) {
        val normalizedUrl = if (url.endsWith("/")) url else "$url/"
        if (normalizedUrl != baseUrl) {
            baseUrl = normalizedUrl
            synchronized(this) {
                retrofit = null
                apiService = null
                downloadRetrofit = null
                downloadService = null
                uploadRetrofit = null   // FIX
                uploadService = null    // FIX
            }
        }
    }

    fun getBaseUrl(): String = baseUrl

    /** Standard service for API calls */
    fun getService(): ViralClipApiService {
        return apiService ?: synchronized(this) {
            apiService ?: buildService(standardClient).also { apiService = it }
        }
    }

    /** Download service with extended read timeout – use for clip downloads only */
    fun getDownloadService(): ViralClipApiService {
        return downloadService ?: synchronized(this) {
            downloadService ?: buildService(downloadClient).also {
                downloadRetrofit = buildRetrofit(downloadClient)
                downloadService = it
            }
        }
    }

    // FIX: New upload service with extended write timeout
    /** Upload service with extended write timeout – use for gallery uploads only */
    fun getUploadService(): ViralClipApiService {
        return uploadService ?: synchronized(this) {
            uploadService ?: buildService(uploadClient).also {
                uploadRetrofit = buildRetrofit(uploadClient)
                uploadService = it
            }
        }
    }

    private fun buildRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun buildService(client: OkHttpClient): ViralClipApiService {
        val r = buildRetrofit(client)
        retrofit = r
        return r.create(ViralClipApiService::class.java)
    }
}
