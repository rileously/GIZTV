package com.giztv.tv.home

import android.content.Context
import android.content.Intent
import com.giztv.tv.MainActivity
import com.giztv.tv.data.PlaybackContext
import com.giztv.tv.data.WatchHistoryEntry
import com.giztv.tv.data.WatchHistoryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** What the phone widget shows at its largest, and what the television channel carries. */
internal const val HOME_SURFACE_LIMIT = 6

internal const val EXTRA_RESUME_PAGE_URL = "com.giztv.tv.extra.RESUME_PAGE_URL"

/**
 * Unfinished titles of both kinds, most recently watched first.
 *
 * The catalog keeps short dramas in a row of their own because a run of two-minute episodes would
 * crowd everything else out of it within an evening. A home screen is not a catalog: it holds a
 * handful of items and the only sensible order for them is the one the viewer left them in.
 */
internal fun WatchHistoryStore.continueWatchingAnywhere(
  limit: Int = HOME_SURFACE_LIMIT
): List<WatchHistoryEntry> =
  (continueWatching(shortForm = false, limit = limit) +
      continueWatching(shortForm = true, limit = limit))
    .sortedByDescending { it.updatedAtMs }
    .take(limit)

/**
 * Picks up a title from outside the app.
 *
 * The stream behind a page expires, so nothing is carried across but the page to resolve again;
 * the app looks the rest up in the same history the surface was drawn from.
 */
internal fun resumeIntent(context: Context, entry: WatchHistoryEntry): Intent =
  resumeIntent(context, entry.pageUrl)

internal fun resumeIntent(context: Context, pageUrl: String): Intent =
  Intent(context, MainActivity::class.java)
    .setAction(Intent.ACTION_VIEW)
    .putExtra(EXTRA_RESUME_PAGE_URL, pageUrl)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

/**
 * Finds the title a home surface asked to resume, and enough about it to play.
 *
 * The season's other episodes are not kept with the history, so a title resumed this way plays the
 * episode it was asked for and stops at the end of it rather than rolling into the next one.
 */
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

/**
 * Outlives the screen that asks for the refresh.
 *
 * The refresh is wanted precisely as the app is being left, and an activity's own scope is torn down
 * a moment later — running it there would cancel the write about as often as it completed.
 */
private val homeSurfaceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * Brings whichever home screen this device has back in step with the history.
 *
 * A phone has a widget the viewer may have placed, a television has a row on its own home screen,
 * and neither exists on the other, so both are asked and the one that does not apply does nothing.
 */
internal fun refreshHomeSurfaces(context: Context) {
  val appContext = context.applicationContext
  // Launched apart rather than one after the other: these are two unrelated surfaces owned by two
  // unrelated system services, and a device where one of them stalls should not be a device where
  // the other silently never updates.
  homeSurfaceScope.launch { refreshContinueWatchingWidget(appContext) }
  homeSurfaceScope.launch { ContinueWatchingChannel.refresh(appContext) }
}
