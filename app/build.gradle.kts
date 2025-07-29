plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-parcelize")
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.google.firebase.crashlytics)
}

android {
    namespace = "com.example.kenyanradiostations"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.kenyanradiostations"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Media3 for audio playback (replaces ExoPlayer)
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")
    // For HLS streams (.m3u8)
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    // For DASH streams (.mpd)
    implementation("androidx.media3:media3-exoplayer-dash:1.3.1")
    // For SmoothStreaming streams (.ism)
    implementation("androidx.media3:media3-exoplayer-smoothstreaming:1.3.1")

    // Google Cast Framework
    implementation("com.google.android.gms:play-services-cast-framework:21.5.0")

    // Jsoup for HTML Parsing/Scraping
    implementation("org.jsoup:jsoup:1.17.2")

    // Coil for modern image loading
    implementation("io.coil-kt:coil:2.6.0")

    // Coroutines for background tasks
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    implementation("androidx.media:media:1.7.0")

    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.3")
    implementation("androidx.navigation:navigation-ui-ktx:2.9.2")

    // splash screen
    implementation("androidx.core:core-splashscreen:1.0.1")

    implementation("androidx.viewpager2:viewpager2:1.1.0")
}