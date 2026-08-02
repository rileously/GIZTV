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

/** Loads a TMDB poster or still, serving repeat requests from an in-memory cache. */
@Composable
internal fun rememberTmdbImage(url: String?): ImageBitmap? {
  val bitmap by produceState<ImageBitmap?>(initialValue = url?.let(TmdbImageCache::get), key1 = url) {
    if (url == null || value != null) return@produceState
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
  private val cache =
    object : LruCache<String, ImageBitmap>(10 * 1024) {
      override fun sizeOf(key: String, value: ImageBitmap): Int =
        (value.width * value.height * 4 / 1024).coerceAtLeast(1)
    }

  @Synchronized fun get(url: String): ImageBitmap? = cache.get(url)

  @Synchronized fun put(url: String, bitmap: ImageBitmap) {
    cache.put(url, bitmap)
  }
}
