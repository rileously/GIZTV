package com.giztv.tv.update

import android.content.Context

private const val UPDATE_NOTIFICATIONS = "giztv_update_notifications"
private const val KEY_LAST_NOTIFIED_VERSION_CODE = "last_notified_version_code"

/** Remembers which release has already been announced, so the shade is not told about it twice. */
internal class UpdateNotificationStore(context: Context) {
  private val preferences =
    context.applicationContext.getSharedPreferences(UPDATE_NOTIFICATIONS, Context.MODE_PRIVATE)

  fun lastNotifiedVersionCode(): Long = preferences.getLong(KEY_LAST_NOTIFIED_VERSION_CODE, 0L)

  fun setLastNotifiedVersionCode(versionCode: Long) {
    preferences.edit().putLong(KEY_LAST_NOTIFIED_VERSION_CODE, versionCode).apply()
  }
}

/**
 * Whether this release is worth interrupting someone for.
 *
 * A notification is a one-time announcement rather than a reminder: a viewer who read it and chose
 * not to update is not told again, because the overlay on the next launch is already there for
 * anyone who changes their mind. A release newer than the announced one is a fresh piece of news and
 * gets its own notification.
 */
internal fun shouldNotifyAboutUpdate(
  update: AppUpdateInfo?,
  installedVersionCode: Long,
  lastNotifiedVersionCode: Long,
): Boolean =
  update != null &&
    update.versionCode > installedVersionCode &&
    update.versionCode > lastNotifiedVersionCode
