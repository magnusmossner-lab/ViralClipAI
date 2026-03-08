package com.viralclipai.app.data.api

import com.viralclipai.app.data.models.*
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
}
