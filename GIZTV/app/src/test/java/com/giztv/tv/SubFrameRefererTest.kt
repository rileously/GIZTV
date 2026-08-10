package com.giztv.tv

import android.net.Uri
import android.webkit.WebResourceRequest
import com.giztv.tv.ui.browser.isSubFrameDocumentRequest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A stream CDN checks the frame that asked for the video, not the page around it. These pin which
 * requests count as "the frame", so the fallback Referer names a player iframe and not the last
 * script it happened to pull in.
 */
class SubFrameRefererTest {

  private fun request(
    url: String,
    mainFrame: Boolean,
    accept: String?,
  ): WebResourceRequest =
    object : WebResourceRequest {
      // Never read: the check takes the URL as a string so it stays free of android.net.Uri.
      override fun getUrl(): Uri = throw UnsupportedOperationException(url)

      override fun isForMainFrame(): Boolean = mainFrame

      override fun isRedirect(): Boolean = false

      override fun hasGesture(): Boolean = false

      override fun getMethod(): String = "GET"

      override fun getRequestHeaders(): Map<String, String> =
        accept?.let { mapOf("Accept" to it) }.orEmpty()
    }

  @Test
  fun playerIframeCounts() {
    assertTrue(
      isSubFrameDocumentRequest(
        request(PLAYER, mainFrame = false, accept = "text/html,application/xhtml+xml"),
        PLAYER,
      )
    )
  }

  @Test
  fun theMainDocumentIsNotAFrame() {
    assertFalse(
      isSubFrameDocumentRequest(
        request(PAGE, mainFrame = true, accept = "text/html"),
        PAGE,
      )
    )
  }

  @Test
  fun assetsInsideTheFrameDoNotOverwriteIt() {
    // The case that makes `isForMainFrame` alone wrong: a script arriving after the iframe would
    // otherwise become the Referer, and a CDN would see an ad host instead of the player.
    assertFalse(
      isSubFrameDocumentRequest(
        request(SCRIPT, mainFrame = false, accept = "*/*"),
        SCRIPT,
      )
    )
    assertFalse(
      isSubFrameDocumentRequest(
        request(MEDIA, mainFrame = false, accept = "*/*"),
        MEDIA,
      )
    )
  }

  @Test
  fun aRequestWithNoAcceptIsNotTreatedAsAFrame() {
    assertFalse(isSubFrameDocumentRequest(request(MEDIA, mainFrame = false, accept = null), MEDIA))
  }

  @Test
  fun nonWebSchemesAreIgnored() {
    val data = "data:text/html,<html></html>"
    assertFalse(isSubFrameDocumentRequest(request(data, mainFrame = false, accept = "text/html"), data))
  }

  private companion object {
    const val PAGE = "https://dlhd.st/watch.php?id=891"
    const val PLAYER = "https://hamis.romponalis.st/premiumtv/daddy3.php?id=891"
    const val SCRIPT = "https://cdn.jsdelivr.net/npm/@clappr/player@0.11.6/dist/clappr.min.js"
    const val MEDIA = "https://xameleon.phantemlis.top/three/secure/premium891/index.m3u8"
  }
}
