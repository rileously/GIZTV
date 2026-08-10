package com.giztv.tv

import com.giztv.tv.ui.browser.isPlayableStreamUrl
import com.giztv.tv.ui.browser.isStaticAssetUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `/hls/` in a path is a fair guess for a playlist and a bad one for a player's own scripts. These
 * pin the line between the two, so a CDN asset cannot be handed to Media3 as the video.
 */
class StreamAssetFilterTest {

  @Test
  fun theP2pEngineScriptIsNotAStream() {
    val script = "https://cdn.jsdelivr.net/npm/@swarmcloud/hls/p2p-engine.min.js"
    assertTrue(isStaticAssetUrl(script))
    assertFalse(isPlayableStreamUrl(script))
  }

  @Test
  fun playerBundlesAndStylesAreNotStreams() {
    assertFalse(isPlayableStreamUrl("https://cdn.jsdelivr.net/npm/@clappr/player@0.11.6/dist/clappr.min.js"))
    assertFalse(isPlayableStreamUrl("https://example.com/hls/player.css"))
    assertFalse(isPlayableStreamUrl("https://example.com/playlist/thumb.png"))
  }

  @Test
  fun realPlaylistsStillCount() {
    assertTrue(isPlayableStreamUrl("https://xameleon.phantemlis.top/two/secure/abc/1786397551/premium387/index.m3u8"))
    assertTrue(isPlayableStreamUrl("https://example.com/hls/master"))
    assertTrue(isPlayableStreamUrl("https://example.com/live/playlist"))
  }

  @Test
  fun aQueryStringCannotDisguiseAPlaylistAsAScript() {
    // The extension is read off the path, so this stays a stream.
    assertFalse(isStaticAssetUrl("https://example.com/live/index.m3u8?ref=player.js"))
    assertTrue(isPlayableStreamUrl("https://example.com/live/index.m3u8?ref=player.js"))
  }

  @Test
  fun aPathlessUrlDoesNotCrash() {
    assertFalse(isStaticAssetUrl("https://example.com"))
  }
}
