package com.giztv.tv

import com.giztv.tv.update.AppUpdateInfo
import com.giztv.tv.update.shouldNotifyAboutUpdate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateNotificationTest {
  private fun update(versionCode: Long) =
    AppUpdateInfo(
      versionCode = versionCode,
      versionName = "1.9.$versionCode",
      apkUrl = "https://example.com/GIZTV.apk",
      sha256 = "a".repeat(64),
      releaseNotes = "",
    )

  @Test
  fun newerReleaseIsAnnounced() {
    assertTrue(shouldNotifyAboutUpdate(update(31), installedVersionCode = 30, lastNotifiedVersionCode = 0))
  }

  @Test
  fun sameReleaseIsAnnouncedOnlyOnce() {
    assertFalse(shouldNotifyAboutUpdate(update(31), installedVersionCode = 30, lastNotifiedVersionCode = 31))
  }

  @Test
  fun releaseNewerThanTheAnnouncedOneIsFreshNews() {
    assertTrue(shouldNotifyAboutUpdate(update(32), installedVersionCode = 30, lastNotifiedVersionCode = 31))
  }

  @Test
  fun alreadyInstalledReleaseIsNotAnnounced() {
    assertFalse(shouldNotifyAboutUpdate(update(30), installedVersionCode = 30, lastNotifiedVersionCode = 0))
  }

  @Test
  fun olderReleaseIsNotAnnounced() {
    assertFalse(shouldNotifyAboutUpdate(update(29), installedVersionCode = 30, lastNotifiedVersionCode = 0))
  }

  @Test
  fun nothingToReportIsNotAnnounced() {
    assertFalse(shouldNotifyAboutUpdate(null, installedVersionCode = 30, lastNotifiedVersionCode = 0))
  }

  /** A viewer who takes the update stops being told about it, without the store being cleared. */
  @Test
  fun installingTheAnnouncedReleaseSilencesIt() {
    assertFalse(shouldNotifyAboutUpdate(update(31), installedVersionCode = 31, lastNotifiedVersionCode = 31))
  }
}
