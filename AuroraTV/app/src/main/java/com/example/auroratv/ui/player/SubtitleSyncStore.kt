package com.example.auroratv.ui.player

import android.content.Context

private const val SUBTITLE_SYNC_PREFERENCES = "giztv_subtitle_sync"
private const val SHOW_KEY_PREFIX = "show_"
private const val TITLE_KEY_PREFIX = "title_"

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
 * Captions drift because of the subtitle file, not the evening it is watched, and every episode of
 * a show is subtitled the same way. So an episode picks up the offset set on any other episode of
 * the same show, and anything the catalog does not know about is remembered on its own.
 */
internal fun subtitleSyncKey(request: HlsStreamRequest): String =
  request.context?.showId?.let { showId -> SHOW_KEY_PREFIX + showId }
    ?: (TITLE_KEY_PREFIX + playbackProgressKey(request))
