package com.example.auroratv.ui.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubtitleCatalogTest {
  @Test
  fun streamHeaders_useStoredPageUrlWithoutReadingWebViewFromNetworkThread() {
    val headers =
      completeStreamRequestHeaders(
        requestHeaders = emptyMap(),
        userAgent = "GIZTV test",
        fallbackReferer = "https://example.com/watch",
        cookie = "session=test",
      )

    assertEquals("GIZTV test", headers["User-Agent"])
    assertEquals("https://example.com/watch", headers["Referer"])
    assertEquals("session=test", headers["Cookie"])
  }

  @Test
  fun vixCloudExtensionlessMasterPlaylist_isDetectedAsHls() {
    assertTrue(
      isHlsUrl(
        "https://vixsrc.to/playlist/775794?b=1&token=temporary&expires=1790763112&h=1&lang=en"
      )
    )
  }

  @Test
  fun vixCloudChildRenditions_areLeftForMedia3ToLoad() {
    listOf("video", "audio", "subtitle").forEach { type ->
      assertTrue(
        "The $type rendition was mistaken for the HLS master",
        !isHlsUrl(
          "https://vixsrc.to/playlist/775794?type=$type&rendition=eng&token=temporary&expires=1790763112"
        ),
      )
    }
  }

  @Test
  fun hoofootLiveMatch_isDetectedAsHlsDespiteHavingNoExtension() {
    // A live match arrives from /ltv with no extension and an "application/text" content type, so
    // only the host and path say it is a playlist at all.
    assertTrue(isHlsUrl("https://hoofoot.ru/ltv?id=26893297"))
    assertTrue(isHlsUrl("https://www.hoofoot.ru/ltv?id=26893297"))
    // The pages around it are still pages, not streams.
    assertTrue(!isHlsUrl("https://hoofoot.ru/iptv/live-player?id=348291095"))
    assertTrue(!isHlsUrl("https://hoofoot.ru/api/main-server?id=348291095"))
    // Another site's /ltv is not hoofoot's.
    assertTrue(!isHlsUrl("https://example.com/ltv?id=26893297"))
  }

  @Test
  fun attackerEmbeddedPlayers_produceTheMovieSubtitleCatalogUrl() {
    val expected = listOf("https://sub.vdrk.site/v1/movie/1481343")

    assertEquals(expected, subtitleCatalogUrlsForEmbeddedPlayer("https://vidfast.vc/movie/1481343"))
    assertEquals(expected, subtitleCatalogUrlsForEmbeddedPlayer("https://vixsrc.to/movie/1481343"))
  }

  @Test
  fun directVidfastMoviePage_producesItsSubtitleCatalogUrl() {
    assertEquals(
      listOf("https://sub.vdrk.site/v1/movie/1265609"),
      subtitleCatalogUrlsForPage("https://vidfast.vc/movie/1265609"),
    )
  }

  @Test
  fun tvEpisodePlayers_produceTheEpisodeSubtitleCatalogUrl() {
    val expected = listOf("https://sub.vdrk.site/v1/tv/94997/1/3")

    assertEquals(expected, subtitleCatalogUrlsForEmbeddedPlayer("https://vidfast.vc/tv/94997/1/3"))
    assertEquals(expected, subtitleCatalogUrlsForEmbeddedPlayer("https://vixsrc.to/tv/94997/1/3"))
    assertEquals(
      expected,
      subtitleCatalogUrlsForPage("https://vidfast.vc/tv/94997/1/3?autoPlay=true&sub=en"),
    )
  }

  @Test
  fun incompleteTvEpisodePath_doesNotRequestTheWrongSubtitleCatalog() {
    assertTrue(subtitleCatalogUrlsForEmbeddedPlayer("https://vidfast.vc/tv/94997/1").isEmpty())
  }

  @Test
  fun untrustedEmbeddedPlayer_cannotTriggerSubtitleCatalogLookup() {
    assertTrue(
      subtitleCatalogUrlsForEmbeddedPlayer("https://example.com/movie/1481343").isEmpty()
    )
  }

  @Test
  fun directCatalog_extractsAndPrioritizesExactEnglishSubtitle() {
    val tracks =
      parseSubtitleCatalog(
        """
        [
          {"label":"Danish","file":"https://cache.vdrk.site/Danish.vtt"},
          {"label":"English Hi2","file":"https://cache.vdrk.site/English Hi2.vtt"},
          {"label":"English4","file":"https://cache.vdrk.site/English4.vtt"},
          {"label":"English","file":"https://cache.vdrk.site/English.vtt"}
        ]
        """.trimIndent()
      )

    assertEquals(listOf("English", "English4", "English Hi2"), tracks.map { it.label })
    assertTrue(tracks.all { it.language == "en" })
    assertTrue(tracks.single { it.label == "English Hi2" }.url.contains("English%20Hi2.vtt"))
  }

  @Test
  fun bingrCatalog_extractsEnglishFromLanguageCodeAndUrlField() {
    val tracks =
      parseSubtitleCatalog(
        """
        {
          "subtitles": [
            {"url":"https://cache.vdrk.site/v1/vtt/movie/299536/Spanish.vtt","lang":"es","label":"Spanish"},
            {"url":"https://cache.vdrk.site/v1/vtt/movie/299536/English Hi5.vtt","lang":"en","label":"English Hi5 (HI)"},
            {"url":"https://cache.vdrk.site/v1/vtt/movie/299536/English.vtt","lang":"en","label":"English"}
          ]
        }
        """.trimIndent()
      )

    assertEquals(listOf("English", "English Hi5 (HI)"), tracks.map { it.label })
    assertTrue(tracks.all { it.language == "en" })
  }

  @Test
  fun bingrWatchPage_producesItsFirstPartySubtitleCatalogUrl() {
    assertEquals(
      listOf("https://api.bingr.one/api/subtitles/vdrk/movie/299536"),
      subtitleCatalogUrlsForPage("https://bingr.one/watch/movie/299536"),
    )
  }

  @Test
  fun bingrTvWatchPage_carriesTheEpisodeCoordinatesToItsSubtitleCatalog() {
    assertEquals(
      listOf("https://api.bingr.one/api/subtitles/vdrk/tv/94997?season=1&ep=3"),
      subtitleCatalogUrlsForPage("https://bingr.one/watch/tv/94997?season=1&episode=3"),
    )
  }

  @Test
  fun bingrMovieCatalog_liveResponseContainsSelectableEnglishTracks() {
    val json =
      downloadSubtitleCatalog(
        url = "https://api.bingr.one/api/subtitles/vdrk/movie/299536",
        headers = mapOf("Origin" to "https://bingr.one", "Referer" to "https://bingr.one/watch/movie/299536"),
        userAgent = "GIZTV/1.0",
      )
    val tracks = parseSubtitleCatalog(json)

    assertTrue("Bingr returned no English subtitle choices", tracks.isNotEmpty())
    assertTrue("Bingr's exact English track was not prioritized", tracks.first().label == "English")
  }
}
