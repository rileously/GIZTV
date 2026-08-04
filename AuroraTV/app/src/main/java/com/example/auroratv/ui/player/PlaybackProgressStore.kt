package com.example.auroratv.ui.player

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.Player
import java.security.MessageDigest

private const val PLAYBACK_PROGRESS_PREFERENCES = "giztv_playback_progress"
private const val MINIMUM_RESUME_POSITION_MS = 5_000L
private const val FINISHED_REMAINING_MS = 10_000L

internal class PlaybackProgressStore(context: Context) {
  private val preferences =
    context.applicationContext.getSharedPreferences(
      PLAYBACK_PROGRESS_PREFERENCES,
      Context.MODE_PRIVATE,
    )

  fun load(key: String): Long = preferences.getLong(key, 0L).coerceAtLeast(0L)

  fun update(
    key: String,
    positionMs: Long,
    durationMs: Long,
    playbackState: Int,
  ) {
    val resumePosition = resumablePlaybackPosition(positionMs, durationMs, playbackState)
    preferences.edit().apply {
      if (resumePosition == null) remove(key) else putLong(key, resumePosition)
    }.apply()
  }

  fun clear(key: String) {
    preferences.edit().remove(key).apply()
  }
}

/**
 * The same key, worked out from a catalog page alone.
 *
 * A title arriving from a phone has no request yet — the stream has not been found on this device —
 * but it has the page it came from, which is exactly what the key is built from anyway.
 */
internal fun playbackProgressKeyForPage(pageUrl: String): String =
  hashProgressIdentity(
    runCatching { pageUrl.toUri().buildUpon().fragment(null).build().toString() }
      .getOrDefault(pageUrl.substringBefore('#'))
  )

internal fun playbackProgressKey(request: HlsStreamRequest): String {
  val identity = playbackProgressIdentity(request)
  return hashProgressIdentity(identity)
}

private fun hashProgressIdentity(identity: String): String {
  val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
  return digest.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

/**
 * What a resume position belongs to.
 *
 * A catalog title is identified by the page the catalog built, which is the same every time it is
 * opened and the same one Continue watching files it under. The page the stream was finally found
 * on is not: it is wherever the embedded player had redirected to, carrying whatever session the
 * site handed out that evening, so keying on it wrote a new entry per play and every title started
 * from the beginning. Streams found by browsing have no catalog page and still use it.
 */
internal fun playbackProgressIdentity(request: HlsStreamRequest): String {
  val identity =
    request.context?.pageUrl?.takeIf { it.isNotBlank() }
      ?: request.sourcePageUrl?.takeIf { it.isNotBlank() }
      ?: request.url
  return runCatching { identity.toUri().buildUpon().fragment(null).build().toString() }
    .getOrDefault(identity.substringBefore('#'))
}

internal fun resumablePlaybackPosition(
  positionMs: Long,
  durationMs: Long,
  playbackState: Int,
): Long? {
  val safePosition = positionMs.coerceAtLeast(0L)
  if (playbackState == Player.STATE_ENDED || safePosition < MINIMUM_RESUME_POSITION_MS) return null
  if (
    durationMs != C.TIME_UNSET &&
      durationMs > 0L &&
      safePosition >= (durationMs - FINISHED_REMAINING_MS).coerceAtLeast(0L)
  ) {
    return null
  }
  return safePosition
}
