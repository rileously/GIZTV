package com.example.auroratv

import com.example.auroratv.ui.browser.isDecoyMediaUrl
import com.example.auroratv.ui.browser.isPlayableStreamUrl
import com.example.auroratv.ui.browser.isProgressiveMediaUrl
import com.example.auroratv.ui.browser.isStoryboardTrackUrl
import com.example.auroratv.ui.browser.shouldReplacePendingStream
import com.example.auroratv.ui.browser.shouldWaitForMoreSubtitles
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
  fun subtitlesAreWaitedForWhileTheyAreArriving_andNotAMomentLonger() {
    // Nothing found means nothing to wait for, however early it is.
    assertFalse(shouldWaitForMoreSubtitles(hasSubtitles = false, elapsedMs = 0, sinceLastSubtitleMs = 0))

    // A subtitle catalog already quiet before the video was found is complete, so it does not add a
    // fixed 1.2-second delay to the player handoff.
    assertFalse(
      shouldWaitForMoreSubtitles(hasSubtitles = true, elapsedMs = 700, sinceLastSubtitleMs = 5_000)
    )

    // A track that arrived after the video may have siblings behind it, so that burst keeps the
    // original settle floor.
    assertTrue(
      shouldWaitForMoreSubtitles(hasSubtitles = true, elapsedMs = 700, sinceLastSubtitleMs = 100)
    )

    // Past the floor with nothing new arriving, the film goes to the player. This is the case that
    // used to sit out the full two and a half seconds for nothing.
    assertFalse(
      shouldWaitForMoreSubtitles(hasSubtitles = true, elapsedMs = 1_300, sinceLastSubtitleMs = 5_000)
    )

    // Past the floor but tracks are still landing, so the window follows them.
    assertTrue(
      shouldWaitForMoreSubtitles(hasSubtitles = true, elapsedMs = 1_300, sinceLastSubtitleMs = 100)
    )

    // And a page that never stops producing them still has to let go.
    assertFalse(
      shouldWaitForMoreSubtitles(hasSubtitles = true, elapsedMs = 2_500, sinceLastSubtitleMs = 0)
    )
  }

  @Test
  fun aScrubBarStoryboard_isNotMistakenForTheFilm() {
    // Measured on cinesrc: the `/hls/` in this path was enough for the resolver to call a subtitle
    // file the playlist, hand it to the player, and stop waiting for the film that never came.
    val storyboard =
      "https://nebula.bright67.online/hls/e54477ca-315f-481b-adfd-81571ca97033/thumbnails/thumbnails.vtt"
    assertTrue(isStoryboardTrackUrl(storyboard))
    assertFalse(isPlayableStreamUrl(storyboard))

    // A text track is never the video, whatever the rest of the address suggests.
    assertFalse(isPlayableStreamUrl("https://cdn.example.net/hls/abc/English.vtt"))
    assertFalse(isPlayableStreamUrl("https://cdn.example.net/playlist/subs.srt"))

    // A storyboard is not the film even when it is served as a playlist or a file.
    assertFalse(isPlayableStreamUrl("https://cdn.example.net/storyboard/index.m3u8"))
    assertFalse(isPlayableStreamUrl("https://cdn.example.net/sprites/preview.mp4"))

    // And the real thing is still recognised on the same host and the same /hls/ prefix.
    assertTrue(
      isPlayableStreamUrl("https://nebula.bright67.online/hls/e54477ca-315f-481b-adfd-81571ca97033/master.m3u8")
    )
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
