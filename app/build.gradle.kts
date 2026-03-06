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
        versionCode = 4
        versionName = "2.2.0"
        manifestPlaceholders["appAuthRedirectScheme"] = "com.viralclipai.app"
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
    composeOptions { kotlinCompilerExtensionVersion = "1.5.5" }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
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
    implementation("androidx.navigation:navigation-compose:2.7.5")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Media
    implementation("androidx.media3:media3-exoplayer:1.2.0")
    implementation("androidx.media3:media3-ui:1.2.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("com.google.android.material:material:1.11.0")

    // Security (OAuth tokens)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // AppCompat (XML-based activities)
    implementation("androidx.appcompat:appcompat:1.6.1")

    // RecyclerView (Analytics dashboard)
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // WorkManager (periodic sync)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}
