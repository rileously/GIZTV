package com.example.auroratv.ui.catalog

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TmdbPlaybackDetailsRepositoryTest {
  @Test
  fun movieDetails_includeStoryGenresAndTopCast() {
    val details =
      parseTmdbPlaybackDetails(
        """
        {
          "release_date":"2026-05-12",
          "runtime":118,
          "vote_average":8.24,
          "overview":"A dangerous new mission begins.",
          "genres":[{"name":"Thriller"},{"name":"Mystery"}],
          "credits":{"cast":[{"name":"Anya Taylor-Joy"},{"name":"John Doe"}]}
        }
        """.trimIndent(),
        episode = false,
      )

    assertEquals("2026", details.year)
    assertEquals("2026-05-12", details.releaseDate)
    assertEquals(118, details.runtimeMinutes)
    assertEquals(8.24, details.rating ?: 0.0, 0.001)
    assertEquals(listOf("Thriller", "Mystery"), details.genres)
    assertEquals(listOf("Anya Taylor-Joy", "John Doe"), details.castNames)
  }

  @Test
  fun episodeDetails_mergeRegularCastAndGuestStarsWithoutDuplicates() {
    val details =
      parseTmdbPlaybackDetails(
        """
        {
          "air_date":"2026-08-02",
          "runtime":54,
          "vote_average":7.8,
          "overview":"The investigation turns inward.",
          "credits":{"cast":[{"name":"Lead Actor"},{"name":"Guest Actor"}]},
          "guest_stars":[{"name":"Guest Actor"},{"name":"New Guest"}]
        }
        """.trimIndent(),
        episode = true,
      )

    assertEquals("2026", details.year)
    assertEquals(listOf("Lead Actor", "Guest Actor", "New Guest"), details.castNames)
  }

  @Test
  fun movieId_isReadOnlyFromMoviePlaybackUrls() {
    assertEquals(1265609, tmdbMovieIdFromPlaybackUrl("https://vidfast.vc/movie/1265609?sub=en"))
    assertNull(tmdbMovieIdFromPlaybackUrl("https://vidfast.vc/tv/94997/1/3"))
  }
}
