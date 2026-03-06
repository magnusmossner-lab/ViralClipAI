# ViralClipAI ProGuard Rules

# Keep data models for Gson serialization
-keepclassmembers class com.viralclipai.app.data.models.** { *; }

# Keep Retrofit interfaces
-keep interface com.viralclipai.app.data.api.ViralClipApiService { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Retrofit
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Gson
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# WorkManager
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# Compose
-keep class androidx.compose.** { *; }
