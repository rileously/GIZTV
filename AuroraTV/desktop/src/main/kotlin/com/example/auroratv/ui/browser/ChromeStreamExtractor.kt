package com.example.auroratv.ui.browser

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
 * they leave. That is the same thing the television build watches for, and against the same three
 * providers it produced an address in two to five seconds each.
 */
internal object ChromeStreamExtractor {

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
  ): String? {
    val endpoint = ensureBrowser() ?: return null
    onStatus("Opening ${originOf(pageUrl)} in a browser…")

    val seen = ConcurrentLinkedQueue<String>()
    val socket =
      runCatching { openSocket(endpoint, seen) }
        .getOrElse {
          unavailableReason = "Could not talk to the browser: ${it.message}"
          return null
        }

    return try {
      socket.send(command(1, "Network.enable"))
      socket.send(command(2, "Page.navigate", JSONObject().put("url", pageUrl)))

      val deadline = System.currentTimeMillis() + timeoutMs
      var announced = 0L
      while (System.currentTimeMillis() < deadline) {
        currentCoroutineContext().ensureActive()
        delay(POLL_MS)
        // A playlist is the film; take the first good one and stop.
        seen.firstOrNull { isPlaylist(it) && isUsableMedia(it) }?.let { return it }
        val waited = timeoutMs - (deadline - System.currentTimeMillis())
        if (waited - announced >= 4_000L) {
          announced = waited
          onStatus("Waiting for stream… (${waited / 1000}s)")
        }
      }
      // No playlist arrived, so a plain file is better than nothing.
      seen.firstOrNull { isUsableMedia(it) }
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

  private fun openSocket(endpoint: String, seen: ConcurrentLinkedQueue<String>): WebSocket =
    http.newWebSocket(
      Request.Builder().url(endpoint).build(),
      object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
          val event = runCatching { JSONObject(text) }.getOrNull() ?: return
          if (event.optString("method") != "Network.requestWillBeSent") return
          val requested =
            event.optJSONObject("params")?.optJSONObject("request")?.optString("url").orEmpty()
          if (requested.contains(".m3u8") || requested.contains(".mp4")) seen.add(requested)
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

  private fun isPlaylist(url: String) = url.substringBefore('?').contains(".m3u8")

  /** A real delivery address, rather than a placeholder, an advert, or a strip of scrub images. */
  private fun isUsableMedia(url: String): Boolean {
    if (!url.startsWith("http")) return false
    val path = url.substringBefore('#').substringBefore('?').lowercase()
    if (JUNK_HOST_MARKERS.any { url.lowercase().contains(it) }) return false
    if (DECORATIVE_MARKERS.any(path::contains)) return false
    val stem = path.substringAfterLast('/').substringBeforeLast('.')
    // vidrock serves a demo-video.mp4 before it has resolved anything; taking it hands the player a
    // file with no moov atom while the real playlist is still on its way.
    return DECOY_STEMS.none { stem == it || stem.startsWith("$it-") || stem.startsWith("${it}_") }
  }

  private fun originOf(url: String): String {
    val host = url.substringAfter("://", "").substringBefore('/')
    return host.ifEmpty { url }
  }

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
  private const val STARTUP_ATTEMPTS = 40
  private const val STARTUP_POLL_MS = 250L

  private const val EXTRACTOR_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
      "Chrome/124.0.0.0 Safari/537.36"
}
