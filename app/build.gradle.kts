// DeepLoader — App Module Build Configuration
// Developer: Sonu Verma
// All dependencies are free and open-source

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.sonuverma.deeploader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sonuverma.deeploader"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }

        // Embed developer identity
        buildConfigField("String", "DEVELOPER_NAME", "\"Sonu Verma\"")
        buildConfigField("String", "APP_NAME", "\"DeepLoader\"")
        buildConfigField("String", "GITHUB_REPO", "\"https://github.com/SONUVERMA11/DeepLoader\"")
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
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.media3.common.util.UnstableApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
        }
    }

    // Support multiple ABIs for native libs (libtorrent4j)
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64") // x86_64 for emulators
            isUniversalApk = true // Fallback universal APK
        }
    }
}

dependencies {
    // ─── Compose BOM (manages all Compose versions) ───
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // ─── Compose UI ───
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // ─── Core Android ───
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // ─── Navigation ───
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // ─── Hilt (Dependency Injection) ───
    implementation("com.google.dagger:hilt-android:2.53.1")
    ksp("com.google.dagger:hilt-android-compiler:2.53.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // ─── Room (Local Database) ───
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // ─── WorkManager (Background Tasks) ───
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // ─── Network ───
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // ─── Image Loading ───
    implementation("io.coil-kt:coil-compose:2.7.0")

    // ─── ExoPlayer / Media3 (Video Playback) ───
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.5.1")

    // ─── NewPipe Extractor (YouTube + multi-site extraction) ───
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.24.8")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    // ─── JSON Serialization ───
    implementation("com.google.code.gson:gson:2.11.0")

    // ─── Biometric Authentication ───
    implementation("androidx.biometric:biometric:1.1.0")

    // ─── DataStore (Modern SharedPreferences) ───
    implementation("androidx.datastore:datastore-preferences:1.1.2")

    // ─── Jsoup (HTML Parsing for torrent search) ───
    implementation("org.jsoup:jsoup:1.18.3")

    // ─── libtorrent4j (BitTorrent engine) ───
    implementation("org.libtorrent4j:libtorrent4j:2.1.0-31")
    implementation("org.libtorrent4j:libtorrent4j-android-arm64:2.1.0-31")
    implementation("org.libtorrent4j:libtorrent4j-android-arm:2.1.0-31")

    // ─── Coroutines ───
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // ─── Testing ───
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
