import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
}

val localProperties =
  Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) propertiesFile.inputStream().use(::load)
  }
val tmdbApiKey = localProperties.getProperty("tmdb.api.key", System.getenv("TMDB_API_KEY") ?: "")
val escapedTmdbApiKey = tmdbApiKey.replace("\\", "\\\\").replace("\"", "\\\"")
val updateRepository =
  localProperties.getProperty(
    "update.github.repository",
    System.getenv("UPDATE_GITHUB_REPOSITORY") ?: "",
  )
val updateManifestUrl =
  localProperties.getProperty("update.manifest.url")
    ?: updateRepository.takeIf(String::isNotBlank)?.let {
      "https://github.com/$it/releases/latest/download/update.json"
    }.orEmpty()
val escapedUpdateManifestUrl = updateManifestUrl.replace("\\", "\\\\").replace("\"", "\\\"")

val releaseKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH") ?: ""
val releaseKeystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD") ?: ""
val releaseKeyAlias = System.getenv("ANDROID_KEY_ALIAS") ?: ""
val releaseKeyPassword = System.getenv("ANDROID_KEY_PASSWORD") ?: ""
val hasReleaseSigning =
  listOf(
      releaseKeystorePath,
      releaseKeystorePassword,
      releaseKeyAlias,
      releaseKeyPassword,
    )
    .all(String::isNotBlank)

android {
    namespace = "com.giztv.tv"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.giztv.tv"
        minSdk = 23
        targetSdk = 36
        versionCode = 127
        versionName = "1.67.0"
        buildConfigField("String", "TMDB_API_KEY", "\"$escapedTmdbApiKey\"")
        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"$escapedUpdateManifestUrl\"")
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(releaseKeystorePath)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            } else {
                storeFile = file("giztv.keystore")
                storePassword = "giztv123456"
                keyAlias = "giztv_release"
                keyPassword = "giztv123456"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.appcompat)

  // Background update checks that outlive the app being open
  implementation(libs.androidx.work.runtime)
  implementation(libs.androidx.startup.runtime)

  // A media session standing in for the television, so a watch and a lock screen can drive it
  implementation(libs.androidx.media)

  // Continue watching where the app is not: the phone home screen, and the television's own
  implementation(libs.androidx.glance.appwidget)
  implementation(libs.androidx.tvprovider)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.tv.material)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  // Image loading
  implementation(libs.coil.compose)
  implementation(libs.coil.network.okhttp)
  implementation(libs.coil.core)

  // Native adaptive HLS playback
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.exoplayer.dash)
  implementation(libs.androidx.media3.exoplayer.hls)
  implementation(libs.androidx.media3.ui)
  implementation(libs.androidx.media3.cast)
  implementation(libs.androidx.media3.session)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Unit tests: everything that needs no device, so the loop is seconds rather than an emulator
  testImplementation(libs.junit)
  testImplementation(libs.json)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

}
