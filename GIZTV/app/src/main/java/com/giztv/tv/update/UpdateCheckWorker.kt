package com.giztv.tv.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.giztv.tv.BuildConfig
import java.util.concurrent.TimeUnit

/**
 * Checks the release feed while the app is closed and puts a new version in the notification shade.
 *
 * The app already checks on launch, but someone who has stopped opening it is exactly the person who
 * never hears about the release that would bring them back.
 */
internal class UpdateCheckWorker(context: Context, parameters: WorkerParameters) :
  CoroutineWorker(context, parameters) {

  override suspend fun doWork(): Result {
    val manifestUrl = BuildConfig.UPDATE_MANIFEST_URL
    if (manifestUrl.isBlank()) return Result.success()
    if (!applicationContext.supportsUpdateNotifications()) return Result.success()

    val installedVersionCode = BuildConfig.VERSION_CODE.toLong()
    val update =
      AppUpdateService.checkForUpdate(manifestUrl, installedVersionCode).getOrElse {
        // A feed that is unreachable now is usually reachable shortly afterwards; a feed that is
        // still unreachable after a few tries is the release server's problem and waiting for the
        // next run costs nothing.
        return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
      }

    val store = UpdateNotificationStore(applicationContext)
    if (!shouldNotifyAboutUpdate(update, installedVersionCode, store.lastNotifiedVersionCode())) {
      return Result.success()
    }
    // Only remembered once it has actually been shown, so a refused permission does not swallow the
    // announcement of this version for good.
    if (notifyUpdateAvailable(applicationContext, checkNotNull(update))) {
      store.setLastNotifiedVersionCode(update.versionCode)
    }
    return Result.success()
  }

  internal companion object {
    private const val WORK_NAME = "giztv_update_check"
    private const val MAX_ATTEMPTS = 3
    private const val INTERVAL_HOURS = 12L
    private const val INITIAL_DELAY_HOURS = 2L

    /**
     * Starts the recurring check, leaving an already scheduled one alone.
     *
     * Replacing it on every launch would restart the clock each time and a viewer who opens the app
     * often would never reach the end of an interval. The first run is held back because the launch
     * check has just asked the same question.
     */
    fun schedule(context: Context) {
      if (BuildConfig.UPDATE_MANIFEST_URL.isBlank()) return
      if (!context.supportsUpdateNotifications()) return
      val request =
        PeriodicWorkRequestBuilder<UpdateCheckWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
          .setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
          )
          .setInitialDelay(INITIAL_DELAY_HOURS, TimeUnit.HOURS)
          .build()
      // A device that cannot start WorkManager still has a perfectly good app; the launch check
      // remains the way updates are found there.
      runCatching {
        WorkManager.getInstance(context)
          .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
      }
    }
  }
}
