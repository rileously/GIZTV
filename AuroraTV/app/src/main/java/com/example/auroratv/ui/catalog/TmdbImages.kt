package com.example.auroratv.ui.catalog

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.toBitmap
import com.example.auroratv.BuildConfig
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

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
      .crossfade(true)
      .build()
  }
}

/**
 * Loads a TMDB poster or still using Coil 3 with memory caching, OkHttp network fetcher,
 * and automatic bitmap pooling.
 */
@Suppress("CAST_NEVER_SUCCEED")
@Composable
internal fun rememberTmdbImage(url: String?): ImageBitmap? {
  if (url.isNullOrBlank()) return null
  val context = LocalContext.current
  val imageLoader = CoilImageLoaderProvider.get(context)
  val painter =
    rememberAsyncImagePainter(
      model =
        ImageRequest.Builder(context)
          .data(url)
          .crossfade(true)
          .build(),
      imageLoader = imageLoader,
    )
  val state = painter.state
  return if (state is AsyncImagePainter.State.Success) {
    state.result.image.toBitmap().asImageBitmap()
  } else {
    null
  }
}


