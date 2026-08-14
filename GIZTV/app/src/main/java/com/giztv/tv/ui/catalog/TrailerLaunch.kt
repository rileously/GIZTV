package com.giztv.tv.ui.catalog

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri

/**
 * The YouTube apps, phone and television, in the order they are worth trying.
 *
 * Named rather than left to a chooser so that a device with YouTube installed goes straight there
 * instead of asking the viewer to pick a browser first.
 */
private val YOUTUBE_PACKAGES = listOf("com.google.android.youtube", "com.google.android.youtube.tv")

/**
 * Hands a trailer to YouTube.
 *
 * Playing it inside the app is not on offer: YouTube declines to embed a great many trailers, and
 * the refusal arrives as a black rectangle that no amount of retrying turns into a video. Its own
 * app plays every one of them, knows the viewer's account and quality settings, and is already on
 * the televisions this runs on.
 *
 * The named apps are tried first, then anything else that opens a YouTube address, so a device
 * without the app still gets the trailer in a browser.
 */
internal fun openTrailerInYouTube(context: Context, trailer: TmdbTrailer) {
  val opened =
    YOUTUBE_PACKAGES.any { packageName ->
      startTrailer(context, trailerIntent(trailer).setPackage(packageName))
    }
  if (opened) return
  if (startTrailer(context, trailerIntent(trailer))) return
  Log.w("GizTvTrailer", "Nothing on this device opens ${trailer.watchUrl}")
}

private fun trailerIntent(trailer: TmdbTrailer): Intent =
  Intent(Intent.ACTION_VIEW, trailer.watchUrl.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** Whether this particular way of opening the trailer actually took. */
private fun startTrailer(context: Context, intent: Intent): Boolean =
  runCatching { context.startActivity(intent) }.isSuccess
