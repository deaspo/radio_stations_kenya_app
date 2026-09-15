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
        // Play rejects com.example.*, and the id can never change after the
        // first upload. Nothing has been uploaded yet, so it is free to change
        // now - but see the note in UPGRADE_PROPOSAL.md: google-services.json
        // must list this same package or the build fails.
        applicationId = "io.github.deaspo.radiodiaspora"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.1.3"

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

        /*
         * Same R8 pipeline as release, but signed with the debug key so it can
         * actually be installed and smoke-tested without a release keystore.
         *
         * isDebuggable stays true on purpose: it keeps the Crashlytics plugin
         * from trying to upload a mapping file, which would need credentials.
         * R8 still runs, which is the point - this variant exists to prove the
         * keep rules are right before a real release is cut.
         *
         *     ./gradlew installStaging
         */
        create("staging") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = true
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
    testOptions {
        unitTests.isReturnDefaultValues = true
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

    // Media3 for audio playback. Only HLS, AAC and MP3 are ever resolved by the
    // stream API, so the DASH and SmoothStreaming artifacts were dead weight.
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    // media3-ui is deliberately absent. The only thing that used it was the
    // PlayerControlView in the mini player, which was built for video and
    // brought a seek bar that means nothing for a live stream; the mini player
    // now has an explicit play/pause button instead.

    // Google Cast (brings androidx.mediarouter with it)
    implementation("com.google.android.gms:play-services-cast-framework:21.5.0")

    // Scraping the station list
    implementation("org.jsoup:jsoup:1.17.2")

    // Image loading
    implementation("io.coil-kt:coil:2.6.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")

    testImplementation(libs.junit)
    // A real org.json on the unit-test classpath; the android.jar stub throws
    // "not mocked" for every call.
    testImplementation("org.json:json:20231013")

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
