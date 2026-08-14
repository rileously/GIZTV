package com.giztv.tv

import com.giztv.tv.ui.catalog.TmdbTrailer
import com.giztv.tv.ui.catalog.parseTmdbTrailers
import com.giztv.tv.ui.catalog.preferredTrailer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrailerCatalogTest {

  private fun video(
    key: String,
    type: String,
    site: String = "YouTube",
    official: Boolean = true,
    publishedAt: String = "2024-01-01T00:00:00.000Z",
    language: String = "en",
  ) =
    """
    {"key":"$key","name":"$type for the film","site":"$site","type":"$type",
     "official":$official,"published_at":"$publishedAt","iso_639_1":"$language"}
    """
      .trimIndent()

  private fun payload(vararg videos: String) = """{"results":[${videos.joinToString(",")}]}"""

  @Test
  fun onlyYoutubeHostedPromosAreKept() {
    val parsed =
      parseTmdbTrailers(
        payload(
          video("abc", "Trailer"),
          video("def", "Trailer", site = "Vimeo"),
          // A behind-the-scenes reel is not what a Trailer button promises.
          video("ghi", "Featurette"),
          video("", "Trailer"),
        )
      )
    assertEquals(listOf("abc"), parsed.map { it.key })
  }

  @Test
  fun aTrailerBeatsATeaserWhichBeatsAClip() {
    val best =
      preferredTrailer(
        parseTmdbTrailers(payload(video("clip", "Clip"), video("teaser", "Teaser"), video("full", "Trailer")))
      )
    assertEquals("full", best?.key)
  }

  @Test
  fun anOfficialUploadBeatsARePostOfTheSameKind() {
    val best =
      preferredTrailer(
        parseTmdbTrailers(
          payload(video("repost", "Trailer", official = false), video("studio", "Trailer"))
        )
      )
    assertEquals("studio", best?.key)
  }

  @Test
  fun englishWinsAmongEquals_andTheNewestWinsAfterThat() {
    val best =
      preferredTrailer(
        parseTmdbTrailers(
          payload(
            video("french", "Trailer", language = "fr", publishedAt = "2025-06-01T00:00:00.000Z"),
            video("old", "Trailer", publishedAt = "2023-01-01T00:00:00.000Z"),
            video("recut", "Trailer", publishedAt = "2025-01-01T00:00:00.000Z"),
          )
        )
      )
    assertEquals("recut", best?.key)
  }

  @Test
  fun nothingUsable_leavesTheButtonOff() {
    assertNull(preferredTrailer(emptyList()))
    assertNull(preferredTrailer(parseTmdbTrailers("""{"results":[]}""")))
  }

  @Test
  fun theTrailerIsKnownByTheAddressYoutubeItselfOpens() {
    val trailer =
      TmdbTrailer(key = "xyz", name = "Trailer", type = "Trailer", official = true, publishedAt = null)
    // What the YouTube app is handed, so it must be a watch page rather than an embed.
    assertEquals("https://www.youtube.com/watch?v=xyz", trailer.watchUrl)
  }
}
