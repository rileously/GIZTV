package com.example.auroratv.ui.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbTvRepositoryTest {
  @Test
  fun parser_readsShowFieldsAndSkipsInvalidRows() {
    val shows =
      parseTmdbShows(
        """
        {
          "results": [
            {
              "id": 94997,
              "name": "Test Show",
              "first_air_date": "2022-08-21",
              "vote_average": 8.44,
              "overview": "A test overview.",
              "poster_path": "/poster.jpg"
            },
            { "id": 0, "name": "Invalid" }
          ]
        }
        """.trimIndent()
      )

    assertEquals(1, shows.size)
    assertEquals(94997, shows.single().id)
    assertEquals("Test Show", shows.single().name)
    assertEquals("2022", shows.single().year)
    assertEquals("https://image.tmdb.org/t/p/w500/poster.jpg", shows.single().posterUrl)
  }

  @Test
  fun seasonParser_dropsEmptySeasonsAndSortsSpecialsLast() {
    val seasons =
      parseTmdbSeasons(
        """
        {
          "seasons": [
            { "season_number": 0, "name": "Specials", "episode_count": 81 },
            { "season_number": 2, "name": "Season 2", "episode_count": 8 },
            { "season_number": 1, "name": "Season 1", "episode_count": 10 },
            { "season_number": 3, "name": "Announced", "episode_count": 0 }
          ]
        }
        """.trimIndent()
      )

    assertEquals(listOf(1, 2, 0), seasons.map { it.seasonNumber })
    assertEquals(listOf("S1", "S2", "SP"), seasons.map { it.shortLabel })
    assertEquals(10, seasons.first().episodeCount)
  }

  @Test
  fun episodeParser_readsEpisodeFieldsAndSortsByNumber() {
    val episodes =
      parseTmdbEpisodes(
        """
        {
          "season_number": 1,
          "episodes": [
            {
              "episode_number": 2,
              "name": "The Rogue Prince",
              "overview": "Second episode.",
              "still_path": "/still2.jpg",
              "runtime": 54
            },
            {
              "episode_number": 1,
              "name": "The Heirs of the Dragon",
              "overview": "First episode.",
              "still_path": null,
              "runtime": 66
            },
            { "episode_number": 0, "name": "Invalid" }
          ]
        }
        """.trimIndent()
      )

    assertEquals(listOf(1, 2), episodes.map { it.episodeNumber })
    assertEquals(1, episodes.first().seasonNumber)
    assertEquals("66 min", episodes.first().runtimeLabel)
    assertNull(episodes.first().stillUrl)
    assertEquals("https://image.tmdb.org/t/p/w300/still2.jpg", episodes.last().stillUrl)
  }

  @Test
  fun episodeParser_fallsBackToAGeneratedEpisodeName() {
    val episode = parseTmdbEpisodes("""{"season_number":4,"episodes":[{"episode_number":7,"name":""}]}""").single()

    assertEquals("Episode 7", episode.name)
    assertEquals(4, episode.seasonNumber)
    assertNull(episode.runtimeLabel)
  }

  @Test
  fun vidfastEpisodeUrl_carriesShowSeasonAndEpisode() {
    assertEquals(
      "https://vidfast.vc/tv/94997/1/3?autoPlay=true&sub=en&chromecast=false",
      vidfastEpisodeUrl(showId = 94997, seasonNumber = 1, episodeNumber = 3),
    )
  }
}
