import java.util.Properties

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
        versionCode = 14
        versionName = "0.9.5"
    }

    // Private release key from CI secrets (env) or this Mac's local.properties (never committed):
    // release.storeFile, release.storePassword, release.keyAlias, release.keyPassword.
    // When present it signs debug builds too, so local builds and releases update each other on the Karoo.
    // Without it (a fresh clone) builds fall back to the shared debug key committed here, which can't update
    // an install signed with the private key.
    val local = Properties().apply {
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    fun secret(env: String, prop: String): String? = System.getenv(env) ?: local.getProperty(prop)
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        secret("RELEASE_STORE_FILE", "release.storeFile")?.takeIf { file(it).exists() }?.let { path ->
            create("release") {
                storeFile = file(path)
                storePassword = secret("RELEASE_STORE_PASSWORD", "release.storePassword")
                keyAlias = secret("RELEASE_KEY_ALIAS", "release.keyAlias")
                keyPassword = secret("RELEASE_KEY_PASSWORD", "release.keyPassword")
            }
        }
    }
    val signing = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")

    buildTypes {
        debug {
            // Milestone 0 device spike: raw SavedDevices / stream logging to logcat + file.
            buildConfigField("boolean", "SPIKE_LOGGING", "true")
            signingConfig = signing
        }
        release {
            buildConfigField("boolean", "SPIKE_LOGGING", "false")
            signingConfig = signing
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
