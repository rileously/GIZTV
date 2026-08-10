package com.giztv.tv.home

import android.content.Context
import com.giztv.tv.data.PlaybackContext
import com.giztv.tv.data.WatchHistoryEntry
import com.giztv.tv.data.WatchHistoryStore

internal const val HOME_SURFACE_LIMIT = 6

internal fun WatchHistoryStore.continueWatchingAnywhere(
  limit: Int = HOME_SURFACE_LIMIT
): List<WatchHistoryEntry> =
  (continueWatching(shortForm = false, limit = limit) +
      continueWatching(shortForm = true, limit = limit))
    .sortedByDescending { it.updatedAtMs }
    .take(limit)

internal fun resumeContextFor(context: Context, pageUrl: String?): PlaybackContext? {
  val entry = pageUrl?.let { WatchHistoryStore(context).find(it) } ?: return null
  return PlaybackContext(
    pageUrl = entry.pageUrl,
    title = entry.title,
    subtitle = entry.subtitle,
    posterUrl = entry.posterUrl,
    showId = entry.showId,
    seasonNumber = entry.seasonNumber,
    episodeNumber = entry.episodeNumber,
    shortForm = entry.shortForm,
  )
}

internal fun refreshHomeSurfaces(context: Context) {}
