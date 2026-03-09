package com.viralclipai.app.data.api

import com.viralclipai.app.data.models.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.*

interface ViralClipApiService {
    @GET("health")
    suspend fun healthCheck(): HealthResponse

    /** Lightweight keep-alive - returns instantly, prevents Railway from sleeping */
    @GET("ping")
    suspend fun ping(): Map<String, Any>

    @POST("api/process")
    suspend fun processVideo(@Body request: Map<String, @JvmSuppressWildcards Any>): ProcessResponse

    @GET("api/job/{jobId}")
    suspend fun getJobStatus(@Path("jobId") jobId: String): JobStatus

    /** @Streaming = don't buffer entire video in RAM - stream directly to disk */
    @Streaming
    @GET("api/clip/{clipId}/download")
    suspend fun downloadClip(
        @Path("clipId") clipId: String,
        @Header("Range") range: String? = null
    ): ResponseBody

    @POST("api/feedback")
    suspend fun sendFeedback(@Body request: FeedbackRequest): FeedbackResponse

    @DELETE("api/clip/{clipId}")
    suspend fun deleteClip(@Path("clipId") clipId: String): Map<String, String>

    // ─── v5.4.0: Gallery Upload ───
    @Multipart
    @POST("api/upload")
    suspend fun uploadVideo(
        @Part file: MultipartBody.Part,
        @Part("min_duration") minDuration: RequestBody,
        @Part("max_duration") maxDuration: RequestBody,
        @Part("format") format: RequestBody,
        @Part("auto_cut") autoCut: RequestBody,
        @Part("auto_caption") autoCaption: RequestBody,
        @Part("auto_subtitle") autoSubtitle: RequestBody,
        @Part("language") language: RequestBody,
        @Part("keywords") keywords: RequestBody,
        @Part("mood") mood: RequestBody,
        @Part("viral_sensitivity") viralSensitivity: RequestBody,
        @Part("subtitle_font") subtitleFont: RequestBody,
        @Part("subtitle_size") subtitleSize: RequestBody,
        @Part("subtitle_color") subtitleColor: RequestBody,
        @Part("subtitle_highlight") subtitleHighlight: RequestBody,
        @Part("subtitle_style") subtitleStyle: RequestBody,
        @Part("caption_text") captionText: RequestBody
    ): ProcessResponse

    // ─── v5.4.0: Auto-Update ───
    @GET("api/app/latest")
    suspend fun checkUpdate(): UpdateInfo
}
