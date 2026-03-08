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
            // Auto-retry if connection was reused but server already closed it (Railway cold-start fix)
            .retryOnConnectionFailure(true)
            // Keep connections alive for 3 minutes max (within Railway's 5-min sleep window)
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

    @Volatile private var retrofit: Retrofit? = null
    @Volatile private var apiService: ViralClipApiService? = null
    @Volatile private var downloadRetrofit: Retrofit? = null
    @Volatile private var downloadService: ViralClipApiService? = null

    fun setBaseUrl(url: String) {
        val normalizedUrl = if (url.endsWith("/")) url else "$url/"
        if (normalizedUrl != baseUrl) {
            baseUrl = normalizedUrl
            synchronized(this) {
                retrofit = null
                apiService = null
                downloadRetrofit = null
                downloadService = null
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

    /** Download service with extended timeout – use for clip downloads only */
    fun getDownloadService(): ViralClipApiService {
        return downloadService ?: synchronized(this) {
            downloadService ?: buildDownloadService().also { downloadService = it }
        }
    }

    private fun buildService(client: OkHttpClient): ViralClipApiService {
        val r = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit = r
        return r.create(ViralClipApiService::class.java)
    }

    private fun buildDownloadService(): ViralClipApiService {
        val r = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(downloadClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        downloadRetrofit = r
        return r.create(ViralClipApiService::class.java)
    }
}
