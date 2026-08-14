package com.giztv.tv.ui.player

import android.content.Context
import com.giztv.tv.ui.catalog.providerIdOf

private const val SUBTITLE_SYNC_PREFERENCES = "giztv_subtitle_sync"
private const val TITLE_KEY_PREFIX = "title_"
private const val PROVIDER_KEY_SEPARATOR = "_provider_"
private const val TRACK_KEY_SEPARATOR = "_track_"

/** The subtitle delay a viewer dialled in, kept so closing the player does not throw it away. */
internal class SubtitleSyncStore(context: Context) {
  private val preferences =
    context.applicationContext.getSharedPreferences(
      SUBTITLE_SYNC_PREFERENCES,
      Context.MODE_PRIVATE,
    )

  fun load(key: String): Long = preferences.getLong(key, 0L)

  fun save(key: String, offsetMs: Long) {
    preferences.edit().apply {
      if (offsetMs == 0L) remove(key) else putLong(key, offsetMs)
    }.apply()
  }
}

/**
 * A delay belongs to one subtitle track for one title/episode on one provider.
 *
 * Different episodes and servers can use different edits of the video, so sharing a delay across
 * a whole show, carrying it from VidRock to Videasy, or applying it to a different subtitle file
 * turns a manual correction into an incorrect default. [playbackProgressKey] supplies the exact
 * catalog title/episode identity; the suffixes separate alternative video and subtitle encodes.
 */
internal fun subtitleSyncKey(
  request: HlsStreamRequest,
  subtitleTrackIdentity: String? = null,
): String =
  buildString {
    append(TITLE_KEY_PREFIX)
    append(playbackProgressKey(request))
    providerIdOf(request.sourcePageUrl)?.let { providerId ->
      append(PROVIDER_KEY_SEPARATOR)
      append(providerId)
    }
    subtitleTrackIdentity?.takeIf(String::isNotBlank)?.let { identity ->
      append(TRACK_KEY_SEPARATOR)
      // URLs can be very long and may contain short-lived query parameters. The resolver-provided
      // identity is stable for the session; a compact key keeps SharedPreferences readable.
      append(Integer.toHexString(identity.substringBefore('?').hashCode()))
    }
  }
