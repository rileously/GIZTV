package com.example.auroratv

import com.example.auroratv.ui.browser.isProgressiveMediaUrl
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
  fun ordinaryPageFurniture_isNotMistakenForTheFilm() {
    assertFalse(isProgressiveMediaUrl("https://example.net/script.js"))
    assertFalse(isProgressiveMediaUrl("https://example.net/poster.jpg"))
    assertFalse(isProgressiveMediaUrl("https://example.net/playlist.m3u8"))
    // A path that merely mentions the extension is not a file with it.
    assertFalse(isProgressiveMediaUrl("https://example.net/mp4/player"))
    assertFalse(isProgressiveMediaUrl("https://example.net/watch?src=movie.mp4&mode=embed"))
  }
}
