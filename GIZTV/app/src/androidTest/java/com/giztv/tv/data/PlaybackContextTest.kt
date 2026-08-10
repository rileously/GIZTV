package com.giztv.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackContextTest {
  private val playlist =
    listOf(
      PlaylistEntry(1, "The Heirs of the Dragon", "https://vidfast.vc/tv/94997/1/1"),
      PlaylistEntry(2, "The Rogue Prince", "https://vidfast.vc/tv/94997/1/2"),
      PlaylistEntry(3, "Second of His Name", "https://vidfast.vc/tv/94997/1/3"),
    )

  private fun contextAt(index: Int) =
    PlaybackContext(
      pageUrl = playlist[index].pageUrl,
      title = "House of the Dragon",
      showId = 94997,
      seasonNumber = 1,
      episodeNumber = playlist[index].episodeNumber,
      playlist = playlist,
    )

  @Test
  fun nextEntry_followsTheSeasonOrder() {
    assertEquals(playlist[1], contextAt(0).nextEntry)
    assertEquals("E2 · The Rogue Prince", contextAt(0).nextLabel)
  }

  @Test
  fun nextEntry_isAbsentOnTheLastEpisode() {
    assertNull(contextAt(2).nextEntry)
    assertNull(contextAt(2).nextLabel)
  }

  @Test
  fun nextEntry_isAbsentWithoutAPlaylist() {
    val movie = PlaybackContext(pageUrl = "https://vidfast.vc/movie/1", title = "A Movie")

    assertNull(movie.nextEntry)
    assertEquals(false, movie.isEpisode)
  }

  @Test
  fun advanceTo_keepsTheChainGoingForTheFollowingEpisode() {
    val advanced = contextAt(0).advanceTo(playlist[1])

    assertEquals(playlist[1].pageUrl, advanced.pageUrl)
    assertEquals(2, advanced.episodeNumber)
    assertEquals("S1 · E2 · The Rogue Prince", advanced.subtitle)
    // The new context still knows what comes after it.
    assertEquals(playlist[2], advanced.nextEntry)
  }

  @Test
  fun advanceTo_refreshesTheEpisodeDetailsShownDuringPreparation() {
    val next =
      PlaylistEntry(
        episodeNumber = 2,
        name = "The Rogue Prince",
        pageUrl = "https://vidfast.vc/tv/94997/1/2",
        overview = "Rhaenyra confronts a realm in crisis.",
        runtimeMinutes = 54,
        airDate = "2022-08-28",
        rating = 8.1,
      )
    val advanced = contextAt(0).copy(castNames = listOf("Previous Actor")).advanceTo(next)

    assertEquals(next.overview, advanced.overview)
    assertEquals(54, advanced.runtimeMinutes)
    assertEquals("2022-08-28", advanced.airDate)
    assertEquals(8.1, advanced.rating ?: 0.0, 0.001)
    assertEquals(emptyList<String>(), advanced.castNames)
  }
}
