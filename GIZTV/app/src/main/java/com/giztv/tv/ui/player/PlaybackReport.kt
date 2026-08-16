@file:Suppress("UnsafeOptInUsageError")

package com.giztv.tv.ui.player

import androidx.media3.exoplayer.analytics.PlaybackStats

/**
 * What a playback actually did, in one line.
 *
 * Written because four changes to how video is fetched went out together with no way to tell which
 * of them helped, and the answer to "it feels worse" has to be something better than another guess.
 * Read it with `adb logcat -s GizPlayback`.
 *
 * The numbers that matter, in the order they matter: how long the viewer waited for a picture, how
 * much of the playback was spent waiting for more of it, how often that happened, and what quality
 * they got for it.
 */
internal fun playbackSessionSummary(stats: PlaybackStats, title: String): String {
  val playMs = stats.totalPlayTimeMs
  val rebufferMs = stats.totalRebufferTimeMs
  val rebufferPercent = if (playMs > 0L) rebufferMs * 100.0 / playMs else 0.0
  return buildString {
    append(title)
    append(" · start ").append(stats.meanJoinTimeMs).append("ms")
    append(" · rebuffer ").append(rebufferMs).append("ms")
    append(" (").append(String.format(java.util.Locale.US, "%.2f", rebufferPercent)).append("%)")
    append(" ×").append(stats.totalRebufferCount)
    append(" · opened at ").append(stats.meanInitialVideoFormatHeight).append("p")
    append(" · played ").append(stats.meanVideoFormatHeight).append("p")
    append(" · link ").append(stats.meanBandwidth / 1_000).append("kbps")
    append(" · dropped ").append(stats.totalDroppedFrames)
    append(" · watched ").append(playMs / 1_000).append("s")
    // What the connection actually carried for it.
    append(" · ").append(PlaybackTraffic.summary())
  }
}
