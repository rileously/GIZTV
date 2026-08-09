import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
    kotlin("jvm")
}

kotlin {
    sourceSets {
        main {
            kotlin {
                srcDir("../app/src/main/java")
                exclude("**/MainActivity.kt")
                exclude("**/home/ContinueWatching.kt")
                exclude("**/home/ContinueWatchingChannel.kt")
                exclude("**/home/ContinueWatchingWidget.kt")
                exclude("**/home/WidgetArtwork.kt")
                exclude("**/data/HttpCache.kt")
                exclude("**/link/LinkHost.kt")
                exclude("**/link/LinkClient.kt")
                exclude("**/link/LinkServer.kt")
                exclude("**/link/PhoneLink.kt")
                exclude("**/link/RemoteMediaService.kt")
                exclude("**/link/RemoteControl.kt")
                exclude("**/ui/link/RemoteScreen.kt")
                exclude("**/ui/link/PairingCodeOverlay.kt")
                exclude("**/ui/player/PictureInPicture.kt")
                exclude("**/ui/player/OffsetSubtitleRendererFactory.kt")
                exclude("**/ui/player/MediaControls.kt")
                exclude("**/ui/player/HlsPlayerScreen.kt")
                exclude("**/ui/player/InAppMiniPlayer.kt")
                exclude("**/ui/player/PauseCastTrivia.kt")
                exclude("**/ui/player/CastMediaProxy.kt")
                exclude("**/ui/player/CastSubtitleMediaItemConverter.kt")
                exclude("**/ui/player/CastPlaybackSupport.kt")
                exclude("**/ui/browser/BrowserScreen.kt")
                exclude("**/ui/browser/StreamPrefetcher.kt")
                exclude("**/ui/browser/AdBlockingWebViewClient.kt")
                exclude("**/ui/browser/AdRequestEvaluator.kt")
                exclude("**/ui/browser/BookmarkStore.kt")
                exclude("**/ui/browser/StreamCacheStore.kt")
                exclude("**/ui/update/AppUpdateController.kt")
                exclude("**/update/AppUpdateService.kt")
                exclude("**/update/UpdateCheckWorker.kt")
                exclude("**/update/UpdateDownloadWorker.kt")
                exclude("**/update/UpdateNotifications.kt")
                exclude("**/update/UpdateNotificationStore.kt")
            }
        }
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.animation)
    implementation(libs.vlcj)
    implementation("org.json:json:20250517")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

}

compose.desktop {
    application {
        mainClass = "com.example.auroratv.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "AuroraTV"
            packageVersion = "1.48.0"
            description = "AuroraTV Desktop App for Windows"
            copyright = "© 2026 AuroraTV Team"
            vendor = "AuroraTV"

            windows {
                menu = true
                upgradeUuid = "68c18776-921d-4eb8-b997-8c3aa8901234"
                dirChooser = true
                perUserInstall = true
            }
        }
    }
}
