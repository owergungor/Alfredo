import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingProps = Properties().apply {
    val homeProps = File(System.getProperty("user.home"), ".android/alfredo_signing/signing.properties")
    if (homeProps.exists()) homeProps.inputStream().use { load(it) }
    val localProps = rootProject.file("local.properties")
    if (localProps.exists()) localProps.inputStream().use { load(it) }
}

val releaseKeystoreFile: String? = System.getenv("KEYSTORE_FILE") ?: signingProps.getProperty("KEYSTORE_FILE")
val releaseKeystorePassword: String? = System.getenv("KEYSTORE_PASSWORD") ?: signingProps.getProperty("KEYSTORE_PASSWORD")
val releaseKeyAlias: String? = System.getenv("KEY_ALIAS") ?: signingProps.getProperty("KEY_ALIAS")
val releaseKeyPassword: String? = System.getenv("KEY_PASSWORD") ?: signingProps.getProperty("KEY_PASSWORD")

val hasReleaseSigning = !releaseKeystoreFile.isNullOrBlank() && File(releaseKeystoreFile).exists()

android {
    namespace = "com.glasslauncher.tv"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.glasslauncher.tv.devtest"
        minSdk = 26
        targetSdk = 35
        versionCode = 32
        versionName = "1.1"
        val traktClientId = System.getenv("TRAKT_CLIENT_ID") ?: signingProps.getProperty("TRAKT_CLIENT_ID", "")
        buildConfigField("String", "TRAKT_CLIENT_ID", "\"$traktClientId\"")
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = File(releaseKeystoreFile!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                signingConfig = null
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

gradle.taskGraph.whenReady {
    val isReleaseTask = allTasks.any { 
        (it.name.contains("Release", ignoreCase = true) || it.name == "bundleRelease") && 
        !it.name.contains("Lint", ignoreCase = true) &&
        !it.name.contains("Test", ignoreCase = true)
    }
    if (isReleaseTask && !hasReleaseSigning) {
        throw GradleException(
            "Release build rejected: Production signing credentials not found. " +
            "Release builds must not fall back to debug signing. Configure KEYSTORE_FILE, " +
            "KEYSTORE_PASSWORD, KEY_ALIAS, and KEY_PASSWORD via environment variables or " +
            "~/.android/alfredo_signing/signing.properties."
        )
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-ui:1.8.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("dev.mobile:dadb:1.2.10")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}