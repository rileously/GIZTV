package com.giztv.tv.data

import android.content.Context
import android.net.http.HttpResponseCache
import java.io.File

private const val HTTP_CACHE_DIRECTORY = "http"
private const val HTTP_CACHE_BYTES = 48L * 1024 * 1024

/**
 * A disk cache shared by every [java.net.HttpURLConnection] the app opens.
 *
 * Catalog listings and posters were fetched again on every cold start, so the app opened to empty
 * rails over a slow connection and to nothing at all without one. Both go through
 * `HttpURLConnection`, so installing this once covers them together.
 */
internal fun installHttpResponseCache(context: Context) {
  if (HttpResponseCache.getInstalled() != null) return
  runCatching {
    HttpResponseCache.install(File(context.cacheDir, HTTP_CACHE_DIRECTORY), HTTP_CACHE_BYTES)
  }
}

/** Written out so a cache surviving the process is worth having at all. */
internal fun flushHttpResponseCache() {
  runCatching { HttpResponseCache.getInstalled()?.flush() }
}
