package com.example.auroratv

import com.example.auroratv.ui.browser.isDecoyMediaUrl
import com.example.auroratv.ui.browser.isPlayableStreamUrl
import com.example.auroratv.ui.browser.isProgressiveMediaUrl
import com.example.auroratv.ui.browser.shouldReplacePendingStream
import com.example.auroratv.ui.browser.streamDispatchGraceMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamDetectionTest {

  @Test
  fun progressiveFiles_areRecognisedAsSomethingThePlayerCanBeHanded() {
    // The address vidfast actually serves. Recognising only .m3u8 meant this went past unnoticed
    // and every title sat on "Finding the best stream..." until the viewer gave up.
    assertTrue(
      isProgressiveMediaUrl(
        "https://moon.ironwallnet.net/mp4/MDFobzNlX1lMVVJzeWlVR0NLekJFZzpTM2V3QzdJ/1080p.mp4"
      )
    )
    assertTrue(isProgressiveMediaUrl("https://example.net/video.m4v"))
    assertTrue(isProgressiveMediaUrl("https://example.net/video.mkv"))
    assertTrue(isProgressiveMediaUrl("https://example.net/video.webm"))
  }

  @Test
  fun progressiveFiles_areRecognisedThroughQueriesAndFragments() {
    assertTrue(isProgressiveMediaUrl("https://example.net/1080p.mp4?token=abc&expires=123"))
    assertTrue(isProgressiveMediaUrl("https://example.net/1080p.mp4#t=10"))
    assertTrue(isProgressiveMediaUrl("https://example.net/1080P.MP4"))
  }

  @Test
  fun aPagesPlaceholderVideo_isNotMistakenForTheFilm() {
    // vidrock loads this before it has resolved anything. Taking it is what made the site
    // unplayable: the real playlist arrived seconds later and was thrown away.
    assertTrue(isDecoyMediaUrl("https://vidrock.ru/demo-video.mp4"))
    assertFalse(isProgressiveMediaUrl("https://vidrock.ru/demo-video.mp4"))
    assertFalse(isPlayableStreamUrl("https://vidrock.ru/demo-video.mp4"))

    assertTrue(isDecoyMediaUrl("https://example.net/sample.mp4"))
    assertTrue(isDecoyMediaUrl("https://example.net/preview_1080.mp4"))
    // A real delivery address is not a decoy just because it sits under a folder of that name.
    assertFalse(isDecoyMediaUrl("https://moon.ironwallnet.net/mp4/TOKEN/1080p.mp4"))
    assertFalse(isDecoyMediaUrl("https://example.net/demonstration.mp4"))
  }

  @Test
  fun aPlaylist_overtakesAPlainFileThatArrivedFirst() {
    val decoy = "https://vidrock.ru/placeholder.mp4"
    val film = "https://cdn.example.net/stream2/abc/index.m3u8"

    // Nothing held yet, so anything is an improvement.
    assertTrue(shouldReplacePendingStream(null, decoy))
    // The playlist is the film, so it wins.
    assertTrue(shouldReplacePendingStream(decoy, film))
    // ...but nothing displaces a playlist once one is held, including another playlist.
    assertFalse(shouldReplacePendingStream(film, "https://cdn.example.net/other/index.m3u8"))
    assertFalse(shouldReplacePendingStream(film, decoy))
    // And one plain file does not displace another; the first is as good a guess as the second.
    assertFalse(shouldReplacePendingStream(decoy, "https://example.net/1080p.mp4"))
  }

  @Test
  fun aPlainFile_isHeldLongEnoughToBeOvertaken() {
    val film = "https://cdn.example.net/stream2/abc/index.m3u8"
    val file = "https://moon.ironwallnet.net/mp4/TOKEN/1080p.mp4"

    // A playlist only waits for its subtitles; a plain file waits out the window in which a page
    // that loaded a placeholder reveals the real playlist.
    assertTrue(streamDispatchGraceMs(file) > streamDispatchGraceMs(film))
    assertEquals(700L, streamDispatchGraceMs(film))
    assertEquals(2_500L, streamDispatchGraceMs(file))
  }

  @Test
  fun ordinaryPageFurniture_isNotMistakenForTheFilm() {
    assertFalse(isProgressiveMediaUrl("https://example.net/script.js"))
    assertFalse(isProgressiveMediaUrl("https://example.net/poster.jpg"))
    assertFalse(isProgressiveMediaUrl("https://example.net/playlist.m3u8"))
    // A path that merely mentions the extension is not a file with it.
    assertFalse(isProgressiveMediaUrl("https://example.net/mp4/player"))
    assertFalse(isProgressiveMediaUrl("https://example.net/watch?src=movie.mp4&mode=embed"))
  }
}
