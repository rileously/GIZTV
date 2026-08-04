package com.example.auroratv.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.auroratv.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * How wide a poster is decoded for the widget, in pixels.
 *
 * A widget is drawn by the launcher out of a parcelled bundle with a hard size limit, and a handful
 * of full-size TMDB posters would burst it and leave the viewer with a blank box. This is wider than
 * the thumbnail is ever drawn, so it still looks right on a dense screen.
 */
private const val POSTER_TARGET_WIDTH = 160

/**
 * Fetches a poster small enough to hand to the launcher.
 *
 * The bytes come through `HttpURLConnection` and so through the response cache installed at startup,
 * which is usually already holding the poster the catalog drew moments earlier.
 */
internal suspend fun loadWidgetPoster(url: String): Bitmap? =
  withContext(Dispatchers.IO) {
    runCatching {
        val bytes = readPosterBytes(url) ?: return@runCatching null
        val bounds =
          BitmapFactory.Options().apply { inJustDecodeBounds = true }.also {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, it)
          }
        BitmapFactory.decodeByteArray(
          bytes,
          0,
          bytes.size,
          BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, POSTER_TARGET_WIDTH)
            inPreferredConfig = Bitmap.Config.RGB_565
          },
        )
      }
      .getOrNull()
  }

private fun readPosterBytes(url: String): ByteArray? {
  val connection = URL(url).openConnection() as HttpURLConnection
  return try {
    connection.connectTimeout = 8_000
    connection.readTimeout = 10_000
    connection.setRequestProperty("User-Agent", "GIZTV/${BuildConfig.VERSION_NAME}")
    connection.inputStream.use { it.readBytes() }
  } finally {
    connection.disconnect()
  }
}

/** The largest power of two that keeps the decoded poster at or above [targetWidth]. */
internal fun sampleSizeFor(sourceWidth: Int, targetWidth: Int): Int {
  if (sourceWidth <= 0 || targetWidth <= 0) return 1
  var sampleSize = 1
  while (sourceWidth / (sampleSize * 2) >= targetWidth) sampleSize *= 2
  return sampleSize
}
