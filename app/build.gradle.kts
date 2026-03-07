plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.viralclipai.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.viralclipai.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 7
        versionName = "5.0.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.4" }
}
dependencies {
    val bom = platform("androidx.compose:compose-bom:2023.10.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.navigation:navigation-compose:2.7.5")
    // Network
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    // Video Player (ExoPlayer for preview with audio)
    implementation("androidx.media3:media3-exoplayer:1.2.0")
    implementation("androidx.media3:media3-ui:1.2.0")
    // Utils
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    // Google Fonts for subtitle customization
    implementation("androidx.compose.ui:ui-text-google-fonts:1.5.4")
    // WorkManager (for AnalyticsSyncWorker)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    // Security Crypto (for OAuthManager encrypted prefs)
    implementation("androidx.security:security-crypto:1.0.0")
    // AppCompat (for UploadActivity, AnalyticsDashboardActivity)
    implementation("androidx.appcompat:appcompat:1.6.1")
    // RecyclerView (for AnalyticsDashboardActivity)
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
