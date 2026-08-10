package com.giztv.tv.update

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.giztv.tv.MainActivity
import com.giztv.tv.R

internal const val UPDATE_NOTIFICATION_ID = 4022
private const val UPDATE_CHANNEL_ID = "giztv_app_updates"

/**
 * Whether the device has a shade to put this in.
 *
 * A television has no notification drawer to pull down and no lock screen to glance at, so the
 * update is announced by the overlay the app already shows on launch instead.
 */
internal fun Context.supportsUpdateNotifications(): Boolean =
  !packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

/**
 * Tells the viewer a newer release is out, without them having to open the app to find out.
 *
 * Returns whether the notification actually reached the shade: Android 13 onwards drops it silently
 * when the permission was refused, and the caller uses the answer to decide whether this version has
 * been announced yet.
 */
internal fun notifyUpdateAvailable(context: Context, update: AppUpdateInfo): Boolean {
  if (!context.supportsUpdateNotifications()) return false
  val manager = NotificationManagerCompat.from(context)
  if (!manager.areNotificationsEnabled()) return false
  if (
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
  ) {
    return false
  }

  manager.createNotificationChannel(
    NotificationChannelCompat.Builder(UPDATE_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
      .setName(context.getString(R.string.update_channel_name))
      .setDescription(context.getString(R.string.update_channel_description))
      .build()
  )

  // Release notes are the reason to care about the update, so they get the expanded view when the
  // feed carries them and the plain invitation stands in when it does not.
  val body =
    update.releaseNotes.takeIf(String::isNotBlank)
      ?: context.getString(R.string.update_notification_text)
  val notification =
    NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_update_notification)
      .setContentTitle(context.getString(R.string.update_notification_title, update.versionName))
      .setContentText(body)
      .setStyle(NotificationCompat.BigTextStyle().bigText(body))
      .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
      .setPriority(NotificationCompat.PRIORITY_DEFAULT)
      .setAutoCancel(true)
      .setContentIntent(openAppIntent(context))
      .build()

  // Posting can still be refused by a restricted profile or a locked-down launcher; the check for a
  // new version has done its job either way and must not bring the worker down with it.
  return runCatching { manager.notify(UPDATE_NOTIFICATION_ID, notification) }.isSuccess
}

/** Once the app is open the overlay says the same thing better, so the shade entry is redundant. */
internal fun cancelUpdateNotification(context: Context) {
  runCatching { NotificationManagerCompat.from(context).cancel(UPDATE_NOTIFICATION_ID) }
}

/**
 * Opens the app itself rather than the installer.
 *
 * Android 12 onwards refuses a notification that hands off to a service or a receiver to start the
 * real screen, and the download has not happened yet at this point anyway: the launch check finds
 * the same release again and offers it with its progress and checksum verification intact.
 */
private fun openAppIntent(context: Context): PendingIntent =
  PendingIntent.getActivity(
    context,
    1,
    Intent(context, MainActivity::class.java).addFlags(
      Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
    ),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
  )
