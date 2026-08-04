package com.example.auroratv.data

import com.example.auroratv.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PROBE_CONNECT_TIMEOUT_MS = 3_000
private const val PROBE_READ_TIMEOUT_MS = 3_000

/**
 * Asks a remembered stream whether it is still there.
 *
 * A signed address can stop working before the moment it claims to — revoked, rotated, or tied to a
 * session that has since ended — and offering a dead one turns a slow start into an instant
 * failure, which is worse than the wait it was meant to save. A few hundred milliseconds spent
 * asking is the price of never doing that.
 *
 * Only a plain refusal counts against it. A stream that cannot be reached at all is more likely to
 * be a network that has gone away than an address that has expired, and the player will say so
 * better than a guess here would.
 */
internal suspend fun streamStillLive(url: String, headers: Map<String, String>): Boolean =
  withContext(Dispatchers.IO) {
    val connection =
      runCatching { URL(url).openConnection() as HttpURLConnection }.getOrNull() ?: return@withContext true
    try {
      connection.requestMethod = "GET"
      connection.instanceFollowRedirects = true
      connection.connectTimeout = PROBE_CONNECT_TIMEOUT_MS
      connection.readTimeout = PROBE_READ_TIMEOUT_MS
      // The first few bytes are enough to know it answers; a playlist is not worth downloading twice.
      connection.setRequestProperty("Range", "bytes=0-1023")
      connection.setRequestProperty("User-Agent", "GIZTV/${BuildConfig.VERSION_NAME}")
      headers.forEach { (name, value) -> runCatching { connection.setRequestProperty(name, value) } }
      val code = runCatching { connection.responseCode }.getOrNull() ?: return@withContext true
      // Anything the server answers with is fine except being told no.
      code !in 400..499
    } finally {
      runCatching { connection.disconnect() }
    }
  }
