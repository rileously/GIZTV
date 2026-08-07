package com.example.auroratv.ui.catalog

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.auroratv.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads a TMDB poster or still, serving repeat requests from an in-memory cache.
 *
 * The bytes behind it survive the process too: this goes through `HttpURLConnection`, so the
 * response cache installed at startup keeps the poster on disk and a cold start does not refetch
 * every image on the screen.
 *
 * [produceState] keeps its prior [State] across [url] changes — it only restarts the producer —
 * so this must replace a stale bitmap when the address moves, not early-return because [value]
 * is still the previous image. Otherwise a recycled slot (pause tip, swapped poster) can show
 * the last face beside the new name.
 */
@Composable
internal fun rememberTmdbImage(url: String?): ImageBitmap? {
  val bitmap by produceState<ImageBitmap?>(initialValue = url?.let(TmdbImageCache::get), key1 = url) {
    val cached = url?.let(TmdbImageCache::get)
    if (cached != null) {
      value = cached
      return@produceState
    }
    // Drop the previous URL's pixels immediately so a slow fetch cannot leave them under new text.
    value = null
    if (url == null) return@produceState
    value =
      withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
              connection.connectTimeout = 8_000
              connection.readTimeout = 10_000
              connection.setRequestProperty("User-Agent", "GIZTV/${BuildConfig.VERSION_NAME}")
              connection.inputStream.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            } finally {
              connection.disconnect()
            }
          }
          .getOrNull()
      }
    value?.let { TmdbImageCache.put(url, it) }
  }
  return bitmap
}

private object TmdbImageCache {
  /**
   * A share of the heap rather than a fixed size.
   *
   * The old ten megabytes was written as though posters were thumbnails: a w500 poster decodes to
   * about a megabyte and a half, so the cache held seven of them and a rail of twenty evicted its
   * own first card before the last one had been reached. Scrolling back then decoded everything
   * again, which is most of what the stutter was.
   */
  private val cache =
    object : LruCache<String, ImageBitmap>(cacheKilobytes()) {
      override fun sizeOf(key: String, value: ImageBitmap): Int =
        (value.width * value.height * 4 / 1024).coerceAtLeast(1)
    }

  @Synchronized fun get(url: String): ImageBitmap? = cache.get(url)

  @Synchronized fun put(url: String, bitmap: ImageBitmap) {
    cache.put(url, bitmap)
  }

  /** An eighth of the heap, floored so a small device still holds a screenful of artwork. */
  private fun cacheKilobytes(): Int {
    val heapKilobytes = (Runtime.getRuntime().maxMemory() / 1024).coerceAtMost(Int.MAX_VALUE.toLong())
    return (heapKilobytes / 8).toInt().coerceIn(24 * 1024, 96 * 1024)
  }
}
