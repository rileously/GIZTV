package com.example.auroratv.update

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateServiceTest {
  @Test
  fun validManifestIsParsed() {
    val update =
      parseUpdateManifest(
        """
        {
          "versionCode": 19,
          "versionName": "1.7.1",
          "apkUrl": "https://github.com/example/releases/GIZTV.apk",
          "sha256": "${"a".repeat(64)}",
          "releaseNotes": "Faster playback"
        }
        """.trimIndent()
      )

    assertEquals(19L, update.versionCode)
    assertEquals("1.7.1", update.versionName)
    assertEquals("Faster playback", update.releaseNotes)
  }

  @Test
  fun insecureApkUrlIsRejected() {
    assertThrows(IllegalArgumentException::class.java) {
      parseUpdateManifest(
        """
        {
          "versionCode": 19,
          "versionName": "1.7.1",
          "apkUrl": "http://example.com/GIZTV.apk",
          "sha256": "${"b".repeat(64)}"
        }
        """.trimIndent()
      )
    }
  }

  @Test
  fun emulatorHostHttpUrlIsAcceptedOnlyForDebugTesting() {
    val update =
      parseUpdateManifest(
        """
        {
          "versionCode": 19,
          "versionName": "1.7.1",
          "apkUrl": "http://10.0.2.2:8765/GIZTV.apk",
          "sha256": "${"d".repeat(64)}"
        }
        """.trimIndent()
      )

    assertEquals("http://10.0.2.2:8765/GIZTV.apk", update.apkUrl)
  }

  @Test
  fun invalidChecksumIsRejected() {
    assertThrows(IllegalArgumentException::class.java) {
      parseUpdateManifest(
        """
        {
          "versionCode": 19,
          "versionName": "1.7.1",
          "apkUrl": "https://example.com/GIZTV.apk",
          "sha256": "not-a-checksum"
        }
        """.trimIndent()
      )
    }
  }

  @Test
  fun installIntentUsesPrivateContentUriAndReadGrant() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val updateDirectory = File(context.cacheDir, "updates").apply { mkdirs() }
    val apk = File(updateDirectory, "test.apk")

    val intent = buildInstallIntent(context, apk)

    assertEquals(Intent.ACTION_VIEW, intent.action)
    assertEquals("content", intent.data?.scheme)
    assertEquals("application/vnd.android.package-archive", intent.type)
    assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
  }

  @Test
  fun installedApkPassesPackageVersionAndSignatureVerification() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val installedApk = File(context.applicationInfo.sourceDir)
    val installedVersion =
      context.packageManager.getPackageInfo(context.packageName, 0).let { packageInfo ->
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
          packageInfo.longVersionCode
        } else {
          @Suppress("DEPRECATION")
          packageInfo.versionCode.toLong()
        }
      }
    val update =
      AppUpdateInfo(
        versionCode = installedVersion,
        versionName = "test",
        apkUrl = "https://example.com/GIZTV.apk",
        sha256 = "c".repeat(64),
        releaseNotes = "",
      )

    AppUpdateService.verifyDownloadedApk(context, installedApk, update)
  }
}
