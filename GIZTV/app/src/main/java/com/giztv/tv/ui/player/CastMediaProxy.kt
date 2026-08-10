package com.giztv.tv.ui.player

import android.content.Context
import android.util.Log
import com.giztv.tv.link.localIpAddresses
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

private const val LOG_TAG = "GizTvCastProxy"
private const val PREFERRED_PORT = 47891
private const val CONNECT_TIMEOUT_MS = 8_000
private const val READ_TIMEOUT_MS = 30_000
private const val MAX_PLAYLIST_BYTES = 2_000_000
private const val COPY_BUFFER_BYTES = 256 * 1024
private const val SOCKET_BUFFER_BYTES = 256 * 1024

/**
 * Tiny LAN HTTP reverse-proxy so Chromecast can play the same header-gated streams the phone can.
 *
 * The default Cast receiver fetches media itself and cannot send Referer/Cookie/Origin. Typical
 * GIZTV sources reject that bare request, which looks like "connected but nothing plays".
 * While a Cast session is active the phone stays on the same Wi-Fi and serves playlists (and
 * auth-gated segments) with the original headers attached. Media segments are rewritten to the
 * CDN directly whenever safe so Cast is not bottlenecked through the phone.
 */
internal object CastMediaProxy {
  private val started = AtomicBoolean(false)
  private val sessions = ConcurrentHashMap<String, ProxySession>()
  private val executor = Executors.newCachedThreadPool()
  @Volatile private var serverSocket: ServerSocket? = null
  @Volatile private var boundPort: Int = 0
  @Volatile private var appContext: Context? = null

  fun init(context: Context) {
    appContext = context.applicationContext
    ensureStarted()
  }

  fun ensureStarted(): Boolean {
    if (started.get()) return boundPort > 0
    synchronized(this) {
      if (started.get()) return boundPort > 0
      val socket =
        runCatching { ServerSocket(PREFERRED_PORT, 50, InetAddress.getByName("0.0.0.0")) }
          .recoverCatching { ServerSocket(0, 50, InetAddress.getByName("0.0.0.0")) }
          .getOrElse { error ->
            Log.w(LOG_TAG, "Cast proxy could not bind a port", error)
            return false
          }
      serverSocket = socket
      boundPort = socket.localPort
      started.set(true)
      thread(name = "giztv-cast-proxy", isDaemon = true) { acceptLoop(socket) }
      Log.i(LOG_TAG, "Cast proxy listening on $boundPort")
      return true
    }
  }

  /**
   * Registers [originUrl] under a fresh session and returns a LAN URL Chromecast can fetch, or null
   * when the phone has no usable Wi-Fi address / the proxy failed to start.
   */
  fun publicUrl(originUrl: String, headers: Map<String, String>): String? {
    if (!ensureStarted()) return null
    val host = localIpAddresses().firstOrNull() ?: return null
    val sessionId = UUID.randomUUID().toString().replace("-", "")
    val entryId = "root"
    val session =
      ProxySession(
        headers = castSensitiveHeaders(headers),
        entries = ConcurrentHashMap<String, String>().apply { put(entryId, originUrl) },
      )
    sessions[sessionId] = session
    return "http://$host:$boundPort/cast/$sessionId/$entryId"
  }

  /** Drops idle sessions; safe to call when leaving the player. */
  fun clearSessions() {
    sessions.clear()
  }

  private fun acceptLoop(socket: ServerSocket) {
    while (!socket.isClosed) {
      val client = runCatching { socket.accept() }.getOrNull() ?: break
      executor.execute { runCatching { handleClient(client) }.onFailure { client.closeQuietly() } }
    }
  }

  private fun handleClient(socket: Socket) {
    socket.soTimeout = READ_TIMEOUT_MS
    socket.tcpNoDelay = true
    socket.receiveBufferSize = maxOf(socket.receiveBufferSize, SOCKET_BUFFER_BYTES)
    socket.sendBufferSize = maxOf(socket.sendBufferSize, SOCKET_BUFFER_BYTES)
    socket.use { client ->
      val input = BufferedInputStream(client.getInputStream(), SOCKET_BUFFER_BYTES)
      val output = BufferedOutputStream(client.getOutputStream(), SOCKET_BUFFER_BYTES)
      // Chromecast often reuses the TCP socket for the next segment/playlist poll.
      while (true) {
        val requestLine = readLine(input) ?: break
        val parts = requestLine.split(' ')
        if (parts.size < 2 || (parts[0] != "GET" && parts[0] != "HEAD")) {
          writeStatus(output, 405, "Method Not Allowed")
          break
        }
        val path = parts[1].substringBefore('?')
        val headers = readHeaders(input)
        val match = CAST_PATH.matchEntire(path)
        if (match == null) {
          writeStatus(output, 404, "Not Found")
          break
        }
        val sessionId = match.groupValues[1]
        val entryId = match.groupValues[2]
        val session = sessions[sessionId]
        val originUrl = session?.entries?.get(entryId)
        if (session == null || originUrl.isNullOrBlank()) {
          writeStatus(output, 404, "Unknown cast media")
          break
        }
        val keepAlive =
          headers.getIgnoreCase("Connection")?.equals("keep-alive", ignoreCase = true) == true
        proxyOrigin(
          method = parts[0],
          originUrl = originUrl,
          session = session,
          sessionId = sessionId,
          requestHeaders = headers,
          output = output,
          keepAlive = keepAlive,
        )
        if (!keepAlive) break
      }
    }
  }

  private fun proxyOrigin(
    method: String,
    originUrl: String,
    session: ProxySession,
    sessionId: String,
    requestHeaders: Map<String, String>,
    output: OutputStream,
    keepAlive: Boolean,
  ) {
    val connection =
      (URL(originUrl).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = CONNECT_TIMEOUT_MS
        readTimeout = READ_TIMEOUT_MS
        requestMethod = "GET"
        // Prefer identity so we can stream/copy without decompressing on the phone.
        setRequestProperty("Accept", "*/*")
        setRequestProperty("Accept-Encoding", "identity")
        session.headers.forEach { (name, value) -> setRequestProperty(name, value) }
        requestHeaders.getIgnoreCase("Range")?.let { setRequestProperty("Range", it) }
      }
    try {
      connection.connect()
      val status = connection.responseCode
      val stream =
        if (status in 200..299) connection.inputStream else connection.errorStream
      if (stream == null) {
        writeStatus(output, status.coerceAtLeast(502), "Upstream empty")
        return
      }
      val contentType = connection.contentType
      val treatAsPlaylist =
        originUrlLooksLikePlaylist(originUrl) ||
          contentType?.contains("mpegurl", ignoreCase = true) == true ||
          contentType?.contains("m3u8", ignoreCase = true) == true

      if (treatAsPlaylist) {
        val buffered = BufferedInputStream(stream, 64_768)
        val raw = readLimited(buffered, MAX_PLAYLIST_BYTES)
        val rewritten =
          rewriteHlsPlaylistForCastProxy(raw, originUrl) { absolute ->
            if (castCanCastFetchDirect(absolute, session.headers)) {
              absolute
            } else {
              registerEntry(sessionId, session, absolute)
            }
          }
        val bytes = rewritten.toByteArray(Charsets.UTF_8)
        writeResponse(
          output = output,
          status = 200,
          contentType = "application/vnd.apple.mpegurl",
          body = if (method == "HEAD") null else bytes,
          contentLength = bytes.size.toLong(),
          extraHeaders = emptyMap(),
          keepAlive = keepAlive,
        )
        return
      }

      val buffered = BufferedInputStream(stream, COPY_BUFFER_BYTES)
      pipeBinary(method, status, contentType, connection, buffered, output, keepAlive)
    } catch (error: Exception) {
      Log.w(LOG_TAG, "Cast proxy failed for $originUrl", error)
      runCatching { writeStatus(output, 502, "Bad Gateway") }
    } finally {
      connection.disconnect()
    }
  }

  private fun pipeBinary(
    method: String,
    status: Int,
    contentType: String?,
    connection: HttpURLConnection,
    input: InputStream,
    output: OutputStream,
    keepAlive: Boolean,
  ) {
    val length =
      connection.getHeaderField("Content-Length")?.toLongOrNull()?.takeIf { it >= 0 }
        ?: connection.contentLength.toLong().takeIf { it >= 0 }
    val extra =
      buildMap {
        connection.getHeaderField("Content-Range")?.let { put("Content-Range", it) }
        connection.getHeaderField("Accept-Ranges")?.let { put("Accept-Ranges", it) }
          ?: put("Accept-Ranges", "bytes")
      }
    writeResponseHeaders(
      output = output,
      status = status,
      contentType = contentType ?: "application/octet-stream",
      contentLength = length,
      extraHeaders = extra,
      keepAlive = keepAlive,
    )
    if (method != "HEAD") {
      input.copyTo(output, bufferSize = COPY_BUFFER_BYTES)
      output.flush()
    }
  }

  private fun registerEntry(sessionId: String, session: ProxySession, absoluteUrl: String): String {
    val existing = session.entries.entries.firstOrNull { it.value == absoluteUrl }?.key
    val entryId =
      existing
        ?: UUID.randomUUID().toString().replace("-", "").take(16).also { session.entries[it] = absoluteUrl }
    val host = localIpAddresses().firstOrNull() ?: return absoluteUrl
    return "http://$host:$boundPort/cast/$sessionId/$entryId"
  }

  private data class ProxySession(
    val headers: Map<String, String>,
    val entries: ConcurrentHashMap<String, String>,
  )

  private val CAST_PATH = Regex("""^/cast/([A-Za-z0-9]+)/([A-Za-z0-9]+)$""")
}

private fun originUrlLooksLikePlaylist(url: String): Boolean {
  val path = url.substringBefore('#').substringBefore('?').lowercase()
  return path.endsWith(".m3u8") || path.contains(".m3u8")
}

private fun Map<String, String>.getIgnoreCase(name: String): String? {
  val target = name.lowercase()
  entries.forEach { (key, value) -> if (key.equals(target, ignoreCase = true)) return value }
  return null
}

private fun writeStatus(output: OutputStream, code: Int, reason: String) {
  writeResponse(
    output,
    code,
    "text/plain; charset=utf-8",
    reason.toByteArray(),
    reason.length.toLong(),
    emptyMap(),
    keepAlive = false,
  )
}

private fun writeResponse(
  output: OutputStream,
  status: Int,
  contentType: String,
  body: ByteArray?,
  contentLength: Long,
  extraHeaders: Map<String, String>,
  keepAlive: Boolean,
) {
  writeResponseHeaders(output, status, contentType, contentLength, extraHeaders, keepAlive)
  if (body != null) {
    output.write(body)
    output.flush()
  }
}

private fun writeResponseHeaders(
  output: OutputStream,
  status: Int,
  contentType: String,
  contentLength: Long?,
  extraHeaders: Map<String, String>,
  keepAlive: Boolean = false,
) {
  val reason =
    when (status) {
      200 -> "OK"
      206 -> "Partial Content"
      404 -> "Not Found"
      405 -> "Method Not Allowed"
      502 -> "Bad Gateway"
      else -> "Error"
    }
  val header =
    buildString {
      append("HTTP/1.1 $status $reason\r\n")
      append("Content-Type: $contentType\r\n")
      if (contentLength != null) append("Content-Length: $contentLength\r\n")
      append(if (keepAlive) "Connection: keep-alive\r\n" else "Connection: close\r\n")
      append("Access-Control-Allow-Origin: *\r\n")
      extraHeaders.forEach { (name, value) -> append("$name: $value\r\n") }
      append("\r\n")
    }
  output.write(header.toByteArray(Charsets.US_ASCII))
  output.flush()
}

private fun readHeaders(input: InputStream): Map<String, String> {
  val headers = linkedMapOf<String, String>()
  while (true) {
    val line = readLine(input) ?: break
    if (line.isEmpty()) break
    val colon = line.indexOf(':')
    if (colon <= 0) continue
    headers[line.substring(0, colon).trim()] = line.substring(colon + 1).trim()
  }
  return headers
}

private fun readLine(input: InputStream): String? {
  val buffer = ByteArrayOutputStream()
  while (true) {
    val next = input.read()
    if (next < 0) return if (buffer.size() == 0) null else buffer.toString(Charsets.US_ASCII.name())
    if (next == '\n'.code) break
    if (next != '\r'.code) buffer.write(next)
  }
  return buffer.toString(Charsets.US_ASCII.name())
}

private fun readLimited(input: InputStream, maxBytes: Int): String {
  val bytes = input.readNBytesCompat(maxBytes + 1)
  if (bytes.size > maxBytes) error("Playlist too large for cast proxy")
  return bytes.toString(Charsets.UTF_8)
}

private fun InputStream.readNBytesCompat(len: Int): ByteArray {
  val buffer = ByteArrayOutputStream(len.coerceAtMost(16_384))
  val chunk = ByteArray(8_192)
  var remaining = len
  while (remaining > 0) {
    val read = read(chunk, 0, minOf(chunk.size, remaining))
    if (read < 0) break
    buffer.write(chunk, 0, read)
    remaining -= read
  }
  return buffer.toByteArray()
}

private fun Socket.closeQuietly() = runCatching { close() }
