package com.example.auroratv.ui.player

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Puts the headers back on requests VLC makes on its own.
 *
 * These CDNs refuse anything without a Referer — measured, and not only on the playlist a viewer
 * asks for: the master answers 403 without one, and so does every child playlist beneath it. VLC
 * will set a referer on the address it is handed and then drop it for everything its adaptive
 * demuxer fetches afterwards, which is most of a film. Proven outside this app entirely, by running
 * VLC from a command line with `--http-referrer` set: the master opens, the children are refused,
 * and it gives up with "Failed to create demuxer".
 *
 * So VLC is pointed at this instead. Every request it makes arrives here, is forwarded upstream
 * with the headers the page would have sent, and comes back unchanged. Relative addresses inside a
 * playlist resolve against this server and therefore come back through it without any rewriting;
 * absolute ones are rewritten so they cannot escape.
 *
 * It listens on the loopback address only, so nothing outside this machine can reach it.
 */
internal object StreamHeaderProxy {

  private data class Upstream(val url: String, val headers: Map<String, String>)

  private val sessions = ConcurrentHashMap<String, Upstream>()
  private val nextToken = AtomicLong(0)

  private val http =
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(60, TimeUnit.SECONDS)
      .followRedirects(true)
      .build()

  @Volatile private var server: HttpServer? = null

  /**
   * A local address that plays [url], with [headers] attached to every request it leads to.
   *
   * Returns the original address unchanged if the proxy cannot be started, so a failure here costs
   * at worst what was happening before it existed.
   */
  fun proxied(url: String, headers: Map<String, String>): String {
    if (headers.isEmpty()) return url
    val port = ensureStarted() ?: return url
    val token = nextToken.incrementAndGet().toString()
    sessions[token] = Upstream(url, headers)
    return "http://127.0.0.1:$port/m/$token/"
  }

  private fun ensureStarted(): Int? {
    server?.let { return it.address.port }
    synchronized(this) {
      server?.let { return it.address.port }
      return runCatching {
          // Port 0 asks the system for a free one; loopback keeps it off every other interface.
          val created = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0)
          created.createContext("/", ::handle)
          created.executor = Executors.newCachedThreadPool()
          created.start()
          server = created
          println("[proxy] listening on 127.0.0.1:${created.address.port}")
          created.address.port
        }
        .onFailure { println("[proxy] could not start: $it") }
        .getOrNull()
    }
  }

  private fun handle(exchange: HttpExchange) {
    try {
      val target = resolveTarget(exchange.requestURI.path)
      if (target == null) {
        println("[proxy] 404 for ${exchange.requestURI.path}")
        exchange.sendResponseHeaders(404, -1)
        return
      }
      println("[proxy] -> ${target.first.take(100)}")
      forward(exchange, target)
    } catch (error: Exception) {
      println("[proxy] failed ${exchange.requestURI.path}: $error")
      runCatching { exchange.sendResponseHeaders(502, -1) }
    } finally {
      runCatching { exchange.close() }
    }
  }

  /** Which upstream address a local path stands for, and the headers it should carry. */
  private fun resolveTarget(path: String): Pair<String, Map<String, String>>? {
    val parts = path.removePrefix("/").split("/", limit = 3)
    val kind = parts.getOrNull(0) ?: return null
    val token = parts.getOrNull(1) ?: return null
    val session = sessions[token] ?: return null
    val rest = parts.getOrNull(2).orEmpty()
    return when (kind) {
      // The address the viewer asked for, and anything sitting beside it in the same folder.
      "m" ->
        if (rest.isEmpty()) session.url to session.headers
        else resolveAgainst(session.url, rest) to session.headers
      // A playlist entry that named a full address of its own.
      "a" -> decodeAbsolute(rest) ?.let { it to session.headers }
      else -> null
    }
  }

  private fun forward(exchange: HttpExchange, target: Pair<String, Map<String, String>>) {
    val (url, headers) = target
    val builder = Request.Builder().url(url)
    headers.forEach { (name, value) -> builder.header(name, value) }
    // A player asking for part of a file must have that passed on, or seeking breaks.
    exchange.requestHeaders.getFirst("Range")?.let { builder.header("Range", it) }

    http.newCall(builder.build()).execute().use { response ->
      val body = response.body
      val contentType = response.header("Content-Type").orEmpty()
      val token = exchange.requestURI.path.removePrefix("/").split("/").getOrNull(1).orEmpty()

      if (isPlaylist(url, contentType)) {
        val rewritten = rewritePlaylist(body?.string().orEmpty(), token, url).toByteArray()
        exchange.responseHeaders.add("Content-Type", contentType.ifBlank { "application/vnd.apple.mpegurl" })
        exchange.sendResponseHeaders(response.code, rewritten.size.toLong())
        exchange.responseBody.use { it.write(rewritten) }
        return
      }

      if (contentType.isNotBlank()) exchange.responseHeaders.add("Content-Type", contentType)
      // Fragmented MP4 is read by seeking: without this the demuxer decides the stream cannot be
      // seeked, looks for a moov it will never find in front of the mdat, and gives up.
      exchange.responseHeaders.add("Accept-Ranges", "bytes")
      response.header("Content-Range")?.let { exchange.responseHeaders.add("Content-Range", it) }
      val length = body?.contentLength() ?: -1L
      exchange.sendResponseHeaders(response.code, if (length > 0) length else 0L)
      body?.byteStream()?.use { upstream ->
        exchange.responseBody.use { downstream -> upstream.copyTo(downstream, DEFAULT_BUFFER_SIZE) }
      }
    }
  }

  private fun isPlaylist(url: String, contentType: String): Boolean =
    url.substringBefore('?').endsWith(".m3u8", ignoreCase = true) ||
      contentType.contains("mpegurl", ignoreCase = true)

  /**
   * Sends every address a playlist names back through this server, as a whole address.
   *
   * Each entry is resolved against the playlist it was found in before being folded into a local
   * path, so nothing relative survives. Leaving them relative looks tidier and does not work: a
   * child playlist is itself fetched through a rewritten path, so its own relative entries would
   * resolve against that path rather than against the folder they actually live in. Measured, that
   * is what lost the initialisation segment — VLC fetched a media segment, found no moov in front
   * of the mdat, and gave up.
   */
  private fun rewritePlaylist(body: String, token: String, playlistUrl: String): String =
    body.lineSequence().joinToString("\n") { line ->
      val trimmed = line.trim()
      when {
        trimmed.isEmpty() -> line
        // Encryption keys and initialisation segments are named inside a tag rather than on a line
        // of their own.
        trimmed.startsWith("#") ->
          Regex("URI=\"([^\"]+)\"").replace(line) { match ->
            "URI=\"/a/$token/${encodeAbsolute(resolveAgainst(playlistUrl, match.groupValues[1]))}\""
          }
        else -> "/a/$token/${encodeAbsolute(resolveAgainst(playlistUrl, trimmed))}"
      }
    }

  private fun resolveAgainst(base: String, relative: String): String =
    runCatching { URI(base).resolve(relative).toString() }.getOrElse {
      base.substringBeforeLast('/') + "/" + relative
    }

  private fun encodeAbsolute(url: String): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(url.toByteArray())

  private fun decodeAbsolute(encoded: String): String? =
    runCatching { String(Base64.getUrlDecoder().decode(encoded.substringBefore('/'))) }.getOrNull()
}
