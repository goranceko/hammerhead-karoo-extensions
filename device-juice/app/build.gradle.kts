plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.kotlin.compose)
}

android {
    namespace = "io.github.goranceko.devicejuice"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.goranceko.devicejuice"
        minSdk = 23
        targetSdk = 34
        // Keep in step with manifest.json (latestVersion / latestVersionCode); CI checks the tag against it.
        versionCode = 10
        versionName = "0.9.1"
    }

    // Shared debug key, committed on purpose, so local and CI builds can update each other on the Karoo.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Private release key, only when CI (or a local shell) provides it; otherwise releases use the shared key.
        System.getenv("RELEASE_STORE_FILE")?.takeIf { file(it).exists() }?.let { path ->
            create("release") {
                storeFile = file(path)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Milestone 0 device spike: raw SavedDevices / stream logging to logcat + file.
            buildConfigField("boolean", "SPIKE_LOGGING", "true")
        }
        release {
            buildConfigField("boolean", "SPIKE_LOGGING", "false")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation(libs.karoo.ext)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
