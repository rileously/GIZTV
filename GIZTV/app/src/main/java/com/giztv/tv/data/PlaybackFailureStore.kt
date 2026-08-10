package com.giztv.tv.data

import android.content.Context

private const val PLAYBACK_FAILURE_PREFERENCES = "giztv_playback_failures"
private const val KEY_FAILED_PAGES = "failed_pages"

/**
 * How many dead pages are worth remembering.
 *
 * A catalogue turns over, and a title that failed a year ago is no longer a useful thing to know.
 * The oldest entries are dropped once the list grows past this, so the record stays about what is
 * broken now rather than everything that ever was.
 */
private const val MAXIMUM_REMEMBERED_FAILURES = 400

/**
 * Pages that were opened to play something and never produced a stream.
 *
 * A listing can offer a title its own site no longer serves. Nothing about the entry says so — the
 * cover, the title and the episode count all look exactly like a working one — and the only way to
 * find out is to open it and wait out the whole resolution timeout. A viewer who does that twice
 * has learned something the app should have remembered the first time.
 *
 * Recorded per page rather than per episode: the address is kept without its query, so every
 * episode of a short drama collapses to the one entry. That is deliberate. When one episode of a
 * short drama cannot be resolved it is almost always the drama's page that has gone, not that
 * episode alone, and hiding the whole title is the answer that matches what is actually wrong.
 *
 * A failure is not a life sentence. [clear] runs whenever a page does give up a stream, so a title
 * that was briefly unreachable comes back on its own the next time anything succeeds there.
 */
internal class PlaybackFailureStore(context: Context) {
  private val preferences =
    context.applicationContext.getSharedPreferences(
      PLAYBACK_FAILURE_PREFERENCES,
      Context.MODE_PRIVATE,
    )

  /** Remembers that [pageUrl] was opened to play something and gave up nothing. */
  fun record(pageUrl: String) {
    val key = failureKey(pageUrl) ?: return
    val current = ordered()
    if (current.lastOrNull() == key) return
    val next = (current - key) + key
    write(next.takeLast(MAXIMUM_REMEMBERED_FAILURES))
  }

  /** Forgets [pageUrl], because it has just worked. */
  fun clear(pageUrl: String) {
    val key = failureKey(pageUrl) ?: return
    val current = ordered()
    if (key !in current) return
    write(current - key)
  }

  /** Whether [pageUrl] has failed and has not worked since. */
  fun hasFailed(pageUrl: String): Boolean {
    val key = failureKey(pageUrl) ?: return false
    return key in ordered()
  }

  fun all(): Set<String> = ordered().toSet()

  fun forgetAll() {
    preferences.edit().remove(KEY_FAILED_PAGES).apply()
  }

  /** Insertion order matters, so this is one delimited string rather than a StringSet. */
  private fun ordered(): List<String> =
    preferences.getString(KEY_FAILED_PAGES, null)
      ?.split('\n')
      ?.filter(String::isNotBlank)
      .orEmpty()

  private fun write(keys: List<String>) {
    preferences.edit().putString(KEY_FAILED_PAGES, keys.joinToString("\n")).apply()
  }
}

/**
 * The part of [pageUrl] that identifies what was being watched.
 *
 * The query is where the episode number lives, so dropping it is what makes every episode of a
 * drama one entry. A fragment never survives a request at all.
 */
internal fun failureKey(pageUrl: String): String? =
  pageUrl.trim().substringBefore('#').substringBefore('?').trimEnd('/').takeIf { it.isNotBlank() }
