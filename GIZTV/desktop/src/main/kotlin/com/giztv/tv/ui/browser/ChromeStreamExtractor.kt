package com.giztv.tv.ui.browser

import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import com.giztv.tv.ui.player.transportStreamPayloadOffset

/**
 * Finds a stream by watching a real browser fetch it.
 *
 * These sites assemble their address in JavaScript and play it through MediaSource. That second
 * part is what decides the engine: JavaFX ships an old WebKit with no MediaSource at all, so the
 * players there detect an unsupported browser and never request anything. Measured, and it is not
 * subtle — three providers, pages fully loaded, twenty-odd resources each, and between them not one
 * request for a playlist. The television build gets away with the same approach only because
 * Android's WebView is Chromium underneath.
 *
 * So this drives Chromium instead, over the DevTools protocol, and simply reads the requests as
 * they leave. That is the same thing the television build watches for. Videasy is the fourth
 * provider and needs one extra browser action: its page resolves the title on load, then waits for
 * its play control before it emits the playlist request.
 */
internal object ChromeStreamExtractor {

  /** A media address together with the request identity that made the CDN serve real bytes. */
  data class CapturedStream(val url: String, val headers: Map<String, String>)

  private val http =
    OkHttpClient.Builder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .readTimeout(0, TimeUnit.MILLISECONDS) // A websocket is meant to stay open.
      .build()

  @Volatile private var browser: Process? = null

  /** Why no browser could be started, if that is what went wrong. */
  @Volatile var unavailableReason: String? = null
    private set

  /**
   * The address the page's player asked for, or null if it never asked for one.
   *
   * Returns as soon as a playlist appears rather than waiting out the window, because a playlist is
   * the film and there is nothing better coming.
   */
  suspend fun extract(
    pageUrl: String,
    timeoutMs: Long,
    onStatus: suspend (String) -> Unit,
  ): CapturedStream? {
    val endpoint = ensureBrowser() ?: return null
    onStatus("Opening ${originOf(pageUrl)} in a browser…")

    val fallbackHeaders =
      mapOf(
        "Referer" to pageOrigin(pageUrl) + "/",
        "Origin" to "https://" + originOf(pageUrl),
        "User-Agent" to EXTRACTOR_USER_AGENT,
      )
    val seen = ConcurrentLinkedQueue<CapturedStream>()
    val socket =
      runCatching { openSocket(endpoint, seen) }
        .getOrElse {
          unavailableReason = "Could not talk to the browser: ${it.message}"
          return null
        }

    return try {
      socket.send(command(1, "Network.enable"))
      // This Chrome process is deliberately reused, but signed HLS addresses are not reusable.
      // Disable its HTTP cache before every navigation or replaying the same title can emit an
      // expired master URL from memory and hand VLC the provider's decoy response.
      socket.send(
        command(2, "Network.setCacheDisabled", JSONObject().put("cacheDisabled", true))
      )
      socket.send(command(3, "Network.clearBrowserCache"))
      val freshPageUrl =
        pageUrl + (if ('?' in pageUrl) "&" else "?") + "giztv_refresh=${System.nanoTime()}"
      socket.send(command(4, "Page.navigate", JSONObject().put("url", freshPageUrl)))

      val deadline = System.currentTimeMillis() + timeoutMs
      var announced = 0L
      var videasyStartAttempts = 0
      var nextVideasyStartAtMs = 500L
      val rejected = mutableSetOf<String>()
      while (System.currentTimeMillis() < deadline) {
        currentCoroutineContext().ensureActive()
        delay(POLL_MS)
        val waited = timeoutMs - (deadline - System.currentTimeMillis())
        if (
          isVideasyPlayer(pageUrl) &&
            videasyStartAttempts < VIDEASY_START_ATTEMPTS &&
            waited >= nextVideasyStartAtMs
        ) {
          // A DevTools user gesture also keeps this reliable when Chromium's autoplay policy is
          // stricter than Android WebView's. Repeated readiness probes are idempotent after the
          // initial play control has appeared and been activated.
          socket.send(
            command(
              100 + videasyStartAttempts,
              "Runtime.evaluate",
              JSONObject()
                .put("expression", VIDEASY_START_EXPRESSION)
                .put("userGesture", true),
            )
          )
          videasyStartAttempts += 1
          nextVideasyStartAtMs += VIDEASY_START_INTERVAL_MS
        }
        // A playlist is the film; take the first good one and stop — but only if what it points at
        // is actually video. See carriesRealMedia.
        seen.firstOrNull {
          isPlaylist(it.url) && isUsableMedia(it.url) && it.url !in rejected
        }?.let { candidate ->
          val playable = candidate.withFallbackHeaders(fallbackHeaders)
          if (carriesRealMedia(playable.url, playable.headers)) return playable
          println("[extractor] rejected, not decodable video: ${candidate.url.take(110)}")
          rejected.add(candidate.url)
          onStatus("That stream cannot be decoded; trying elsewhere…")
        }
        if (waited - announced >= 4_000L) {
          announced = waited
          onStatus("Waiting for stream… (${waited / 1000}s)")
        }
      }
      // These pages load advertising MP4s before the title. Without an HLS playlist there is no
      // trustworthy movie candidate, so let the next provider try instead of playing an advert.
      null
    } finally {
      runCatching { socket.close(1000, null) }
    }
  }

  /** The websocket address of a page in a running browser, starting one if none is running. */
  private fun ensureBrowser(): String? {
    devToolsPage()?.let { return it }
    synchronized(this) {
      devToolsPage()?.let { return it }
      val executable =
        BROWSER_CANDIDATES.map(::File).firstOrNull { it.isFile }
          ?: run {
            unavailableReason =
              "No Chrome or Edge was found on this computer. The desktop player needs one of them " +
                "to work out where a stream is."
            return null
          }
      val profile = File(System.getProperty("java.io.tmpdir"), "giztv-extractor-profile")
      profile.mkdirs()
      browser =
        runCatching {
            ProcessBuilder(
                executable.absolutePath,
                "--headless=new",
                "--remote-debugging-port=$DEVTOOLS_PORT",
                // Without this the browser refuses the debugging socket outright.
                "--remote-allow-origins=*",
                "--user-data-dir=${profile.absolutePath}",
                "--no-first-run",
                "--no-default-browser-check",
                "--disable-gpu",
                "--mute-audio",
                "--user-agent=$EXTRACTOR_USER_AGENT",
                "about:blank",
              )
              .redirectErrorStream(true)
              .redirectOutput(ProcessBuilder.Redirect.DISCARD)
              .start()
          }
          .getOrElse {
            unavailableReason = "Could not start a browser: ${it.message}"
            return null
          }
      repeat(STARTUP_ATTEMPTS) {
        devToolsPage()?.let { page -> return page }
        Thread.sleep(STARTUP_POLL_MS)
      }
      unavailableReason = "The browser started but never answered."
      return null
    }
  }

  private fun devToolsPage(): String? =
    runCatching {
        val body =
          http.newCall(Request.Builder().url("http://127.0.0.1:$DEVTOOLS_PORT/json").build())
            .execute()
            .use { it.body?.string() }
            .orEmpty()
        org.json.JSONArray(body)
          .let { targets ->
            (0 until targets.length())
              .map(targets::getJSONObject)
              .firstOrNull { it.optString("type") == "page" }
              ?.optString("webSocketDebuggerUrl")
          }
          ?.takeIf(String::isNotBlank)
      }
      .getOrNull()

  private fun openSocket(endpoint: String, seen: ConcurrentLinkedQueue<CapturedStream>): WebSocket =
    http.newWebSocket(
      Request.Builder().url(endpoint).build(),
      object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
          val event = runCatching { JSONObject(text) }.getOrNull() ?: return
          if (event.optString("method") != "Network.requestWillBeSent") return
          val request = event.optJSONObject("params")?.optJSONObject("request") ?: return
          val requested = request.optString("url").orEmpty()
          if (requested.contains(".m3u8") || requested.contains(".mp4")) {
            seen.add(CapturedStream(requested, playbackHeaders(request.optJSONObject("headers"))))
          }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
          unavailableReason = "Browser connection failed: ${t.message}"
        }
      },
    )

  private fun command(id: Int, method: String, params: JSONObject? = null): String =
    JSONObject()
      .put("id", id)
      .put("method", method)
      .put("params", params ?: JSONObject())
      .toString()

  private fun isVideasyPlayer(url: String): Boolean =
    runCatching {
        val host = java.net.URI(url).host?.lowercase().orEmpty()
        host == "player.videasy.to" || host == "player.videasy.net"
      }
      .getOrDefault(false)

  // Signed proxy endpoints often carry the real playlist address inside `?url=...`; the extension
  // is therefore in the query rather than the visible path.
  private fun isPlaylist(url: String) = url.contains(".m3u8", ignoreCase = true)

  /**
   * Whether a playlist leads to bytes a player can actually decode.
   *
   * Some sites disguise their segments so only their own page can play them: VidRock prefixes real
   * MPEG-TS packets with a tiny PNG and labels the response as an image. Accept that only when four
   * transport-stream sync packets prove there is video underneath; StreamHeaderProxy removes the
   * wrapper before VLC sees it.
   *
   * Judged from the first thing the playlist names, by what the bytes are rather than what they are
   * called: the transport-stream sync byte, or one of the box types an MP4 fragment opens with.
   * When it fails, the address is discarded and the search carries on somewhere else, which is what
   * turns "pick a different server yourself" into something the app does on the viewer's behalf.
   */
  private fun carriesRealMedia(playlistUrl: String, headers: Map<String, String>): Boolean =
    runCatching {
        val playlistResponse = fetch(playlistUrl, headers) ?: return true
        if (isJunkRedirect(playlistResponse.finalUrl)) return false
        val playlist = playlistResponse.string()
        var isInitializationSegment = false
        val firstEntry =
          playlist.lineSequence()
            .map(String::trim)
            .firstNotNullOfOrNull { line ->
              when {
                line.startsWith("#EXT-X-MAP") -> {
                  isInitializationSegment = true
                  Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.get(1)
                }
                line.isNotEmpty() && !line.startsWith("#") -> line
                else -> null
              }
            } ?: return true // Nothing named yet; let the player decide rather than refuse here.
        // A playlist of playlists: follow one level down to reach real segments.
        val target = absoluteAgainst(playlistUrl, firstEntry)
        if (isPlaylist(target)) return carriesRealMedia(target, headers)
        val headResponse = fetch(target, headers, firstBytes = true) ?: return true
        if (isJunkRedirect(headResponse.finalUrl)) return false
        val head = headResponse.bytes()
        // CineSrc deliberately names both child playlists and media fragments as images. Follow
        // the bytes, not the suffix: a child beginning with EXTM3U is still a playlist.
        if (String(head, Charsets.UTF_8).trimStart().startsWith("#EXTM3U")) {
          return carriesRealMedia(target, headers)
        }
        if (isInitializationSegment) looksLikeMp4Initialization(head) else looksLikeMedia(head)
      }
      .getOrDefault(true)

  private fun looksLikeMedia(head: ByteArray): Boolean {
    if (head.size < 8) return true
    // VidRock puts a valid MPEG-TS payload after a tiny, valid 1x1 PNG. Its browser player drops
    // that wrapper before appending the bytes to MediaSource. Accept it here; StreamHeaderProxy
    // performs the same unwrap for VLC. Raw TS mislabeled as GIF is accepted by the same test.
    if (transportStreamPayloadOffset(head) != null) return true
    val boxType = String(head, 4, 4, Charsets.ISO_8859_1)
    return boxType in setOf("ftyp", "styp", "moof", "mdat", "sidx", "free")
  }

  /**
   * A fragmented-MP4 init segment must describe its tracks in a `moov` box somewhere in the file.
   * Redirects to known placeholder hosts are rejected before this structural fallback is reached.
   */
  private fun looksLikeMp4Initialization(head: ByteArray): Boolean {
    val moov = head.indexOfAscii("moov")
    return moov >= 4
  }

  private fun ByteArray.indexOfAscii(value: String): Int {
    val needle = value.toByteArray(Charsets.ISO_8859_1)
    return indices.firstOrNull { start ->
      start + needle.size <= size && needle.indices.all { offset -> this[start + offset] == needle[offset] }
    } ?: -1
  }

  private fun fetch(url: String, headers: Map<String, String>, firstBytes: Boolean = false) =
    runCatching {
        val builder = Request.Builder().url(url)
        headers.forEach { (name, value) -> builder.header(name, value) }
        if (firstBytes) builder.header("Range", "bytes=0-4095")
        http.newCall(builder.build()).execute().use { response ->
          if (response.isSuccessful) {
            response.body?.let { PeekedBody(it.bytes(), response.request.url.toString()) }
          } else {
            null
          }
        }
      }
      .getOrNull()

  /** The bytes, already read, so the response can be closed before they are looked at. */
  private class PeekedBody(private val bytes: ByteArray, val finalUrl: String) {
    fun bytes() = bytes

    fun string() = String(bytes, Charsets.UTF_8)
  }

  private fun isJunkRedirect(url: String): Boolean =
    JUNK_HOST_MARKERS.any { marker -> url.contains(marker, ignoreCase = true) }

  private fun absoluteAgainst(base: String, entry: String): String =
    if (entry.startsWith("http")) entry
    else runCatching { java.net.URI(base).resolve(entry).toString() }.getOrDefault(entry)

  /** A real delivery address, rather than a placeholder, an advert, or a strip of scrub images. */
  private fun isUsableMedia(url: String): Boolean {
    if (!url.startsWith("http")) return false
    val path = url.substringBefore('#').substringBefore('?').lowercase()
    if (JUNK_HOST_MARKERS.any { url.lowercase().contains(it) }) return false
    if (DECORATIVE_MARKERS.any(path::contains)) return false
    val filename = path.substringAfterLast('/')
    val stem = filename.substringBeforeLast('.')
    // An init segment describes tracks but is not a standalone movie. It is visible to the browser
    // alongside the HLS playlist, so the plain-file fallback must not mistake it for the title.
    if (filename.endsWith(".mp4") && (stem.startsWith("init-") || stem.startsWith("seg-"))) {
      return false
    }
    // vidrock serves a demo-video.mp4 before it has resolved anything; taking it hands the player a
    // file with no moov atom while the real playlist is still on its way.
    return DECOY_STEMS.none { stem == it || stem.startsWith("$it-") || stem.startsWith("${it}_") }
  }

  private fun originOf(url: String): String {
    val host = url.substringAfter("://", "").substringBefore('/')
    return host.ifEmpty { url }
  }

  private fun pageOrigin(url: String): String =
    runCatching {
        val parsed = java.net.URI(url)
        "${parsed.scheme}://${parsed.authority}"
      }
      .getOrElse { "https://${originOf(url)}" }

  /**
   * Only replay identity headers. Hop-by-hop and representation headers belong to each individual
   * proxy request; replaying a captured Range or compressed Accept-Encoding would corrupt later
   * playlist and segment responses.
   */
  private fun playbackHeaders(raw: JSONObject?): Map<String, String> {
    if (raw == null) return emptyMap()
    val allowed = setOf("referer", "origin", "user-agent", "cookie", "authorization")
    return buildMap {
      raw.keys().forEach { name ->
        if (name.lowercase() in allowed) {
          raw.optString(name).takeIf(String::isNotBlank)?.let { put(name, it) }
        }
      }
    }
  }

  private fun CapturedStream.withFallbackHeaders(fallback: Map<String, String>): CapturedStream =
    copy(headers = fallback + headers)

  private val DECOY_STEMS =
    listOf("demo-video", "demo", "sample", "placeholder", "intro", "trailer")

  private val DECORATIVE_MARKERS =
    listOf("thumbnail", "storyboard", "sprite", "poster", "banner", "logo")

  /** Advertising dressed as a stream; vidfast's page requests one of these alongside the film. */
  private val JUNK_HOST_MARKERS = listOf("terms-of-service-abuse", "doubleclick", "googlesyndication")

  private val BROWSER_CANDIDATES =
    listOfNotNull(
      System.getenv("LOCALAPPDATA")?.let { "$it\\Google\\Chrome\\Application\\chrome.exe" },
      "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
      "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
      // Edge is Chromium too, and is on every Windows install.
      "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
      "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
    )

  private const val DEVTOOLS_PORT = 9333
  private const val POLL_MS = 250L
  private const val VIDEASY_START_INTERVAL_MS = 500L
  private const val VIDEASY_START_ATTEMPTS = 20
  private const val STARTUP_ATTEMPTS = 40
  private const val STARTUP_POLL_MS = 250L

  private const val VIDEASY_START_EXPRESSION =
    """
    (() => {
      if (window.__giztvVideasyStarted) return true;
      const buttons = Array.from(document.querySelectorAll('button'));
      const button = buttons.find(node => {
        const label = (node.getAttribute('aria-label') || node.getAttribute('title') || node.textContent || '').toLowerCase();
        return label.includes('play');
      }) || buttons[0];
      if (!button) return false;
      window.__giztvVideasyStarted = true;
      button.click();
      return true;
    })()
    """

  private const val EXTRACTOR_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
      "Chrome/124.0.0.0 Safari/537.36"
}
