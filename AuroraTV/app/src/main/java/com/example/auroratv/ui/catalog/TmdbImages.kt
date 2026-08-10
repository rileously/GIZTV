package com.example.auroratv.ui.catalog

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.SizeResolver
import coil3.toBitmap
import com.example.auroratv.BuildConfig
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.io.File
import java.util.concurrent.TimeUnit

private const val IMAGE_DISK_CACHE_DIRECTORY = "coil_images"
private const val IMAGE_DISK_CACHE_BYTES = 64L * 1024 * 1024

internal object CoilImageLoaderProvider {
  @Volatile private var instance: ImageLoader? = null

  fun get(context: Context): ImageLoader =
    instance ?: synchronized(this) {
      instance ?: buildImageLoader(context.applicationContext).also { instance = it }
    }

  private fun buildImageLoader(context: Context): ImageLoader {
    val okHttpClient =
      OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
          val request =
            chain.request()
              .newBuilder()
              .header("User-Agent", "GIZTV/${BuildConfig.VERSION_NAME}")
              .build()
          chain.proceed(request)
        }
        .build()

    return ImageLoader.Builder(context)
      .components {
        add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
      }
      .memoryCache {
        MemoryCache.Builder()
          .maxSizePercent(context, 0.20)
          .build()
      }
      // Coil's OkHttp fetcher does not go through the app's HttpResponseCache, so without this a
      // cold start refetches every poster on the screen. Kept on disk, artwork survives the process.
      .diskCache {
        DiskCache.Builder()
          .directory(File(context.cacheDir, IMAGE_DISK_CACHE_DIRECTORY).toOkioPath())
          .maxSizeBytes(IMAGE_DISK_CACHE_BYTES)
          .build()
      }
      .build()
  }
}

/**
 * Loads a TMDB poster or still through Coil's memory and disk caches.
 *
 * This executes the request directly rather than going through `rememberAsyncImagePainter`. That
 * painter resolves the request's size from its own `DrawScope`, so a painter that is never drawn —
 * which is the case here, since callers want the bitmap and not a `Painter` — leaves the request
 * suspended forever and the state never leaves `Loading`. That is why every artwork slot fell back
 * to its placeholder.
 *
 * [produceState] keeps its prior value across [url] changes — it only restarts the producer — so
 * the previous bitmap is cleared before the new fetch. Otherwise a recycled slot (pause tip,
 * swapped poster) can show the last face beside the new name.
 */
@Composable
internal fun rememberTmdbImage(url: String?): ImageBitmap? {
  val context = LocalContext.current
  val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = url) {
    // Drop the previous URL's pixels immediately so a slow fetch cannot leave them under new text.
    value = null
    if (url.isNullOrBlank()) return@produceState
    val request =
      ImageRequest.Builder(context)
        .data(url)
        // No draw scope to measure against; decode what the server sent.
        .size(SizeResolver.ORIGINAL)
        .build()
    value =
      runCatching {
          (CoilImageLoaderProvider.get(context).execute(request) as? SuccessResult)
            ?.image
            ?.toBitmap()
            ?.asImageBitmap()
        }
        .getOrNull()
  }
  return bitmap
}
