package com.viralclipai.app.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private var baseUrl = "https://viralclipai-backend-production.up.railway.app/"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    @Volatile
    private var retrofit: Retrofit? = null

    @Volatile
    private var apiService: ViralClipApiService? = null

    fun setBaseUrl(url: String) {
        val normalizedUrl = if (url.endsWith("/")) url else "$url/"
        if (normalizedUrl != baseUrl) {
            baseUrl = normalizedUrl
            synchronized(this) {
                retrofit = null
                apiService = null
            }
        }
    }

    fun getBaseUrl(): String = baseUrl

    fun getService(): ViralClipApiService {
        return apiService ?: synchronized(this) {
            apiService ?: buildService().also { apiService = it }
        }
    }

    private fun buildService(): ViralClipApiService {
        val r = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit = r
        return r.create(ViralClipApiService::class.java)
    }
}
