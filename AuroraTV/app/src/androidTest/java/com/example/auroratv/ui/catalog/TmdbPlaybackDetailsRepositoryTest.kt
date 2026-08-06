package com.example.auroratv.ui.catalog

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TmdbPlaybackDetailsRepositoryTest {
  @Test
  fun movieDetails_includeStoryGenresTopCastDirectorTaglineAndReviews() {
    val details =
      parseTmdbPlaybackDetails(
        """
        {
          "release_date":"2026-05-12",
          "runtime":118,
          "vote_average":8.24,
          "overview":"A dangerous new mission begins.",
          "tagline":"No one walks away clean.",
          "genres":[{"name":"Thriller"},{"name":"Mystery"}],
          "credits":{
            "cast":[
              {"name":"Anya Taylor-Joy","character":"Furiosa","profile_path":"/anya.jpg","order":0,"known_for_department":"Acting"},
              {"name":"John Doe","character":"Pilot","order":1,"known_for_department":"Acting"}
            ],
            "crew":[
              {"name":"Cam Writer","job":"Writer"},
              {"name":"Greta Gerwig","job":"Director","profile_path":"/greta.jpg"},
              {"name":"Other Voice","job":"Director","profile_path":"/other.jpg"}
            ]
          },
          "reviews":{
            "results":[
              {
                "id":"rev-good",
                "author":"Brett Pascoe",
                "author_details":{"name":"Brett Pascoe","username":"brett","rating":9.0},
                "content":"In my top five of all time favourite movies. Great story line and a movie you can watch over and over again.",
                "iso_639_1":"en"
              },
              {
                "id":"rev-short",
                "author":"Tiny",
                "content":"Cool!",
                "iso_639_1":"en"
              },
              {
                "id":"rev-fr",
                "author":"Marie",
                "content":"Un film absolument remarquable avec des performances magnifiques et une mise en scène élégante du début à la fin.",
                "iso_639_1":"fr"
              }
            ]
          }
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
    assertEquals("Furiosa", details.cast[0].character)
    assertEquals("https://image.tmdb.org/t/p/w185/anya.jpg", details.cast[0].photoUrl)
    assertEquals(0, details.cast[0].order)
    assertEquals("No one walks away clean.", details.tagline)
    assertEquals("Greta Gerwig", details.director?.name)
    assertEquals("https://image.tmdb.org/t/p/w185/greta.jpg", details.director?.photoUrl)
    assertEquals(2, details.directors.size)
    assertEquals(1, details.reviews.size)
    assertEquals("rev-good", details.reviews[0].id)
    assertEquals("Brett Pascoe", details.reviews[0].author)
    assertEquals(9.0, details.reviews[0].rating ?: 0.0, 0.001)
    assertTrue(details.reviews[0].excerpt.contains("top five"))
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
          "credits":{"cast":[{"name":"Lead Actor","character":"Detective","order":0},{"name":"Guest Actor"}]},
          "guest_stars":[{"name":"Guest Actor"},{"name":"New Guest","character":"Witness"}]
        }
        """.trimIndent(),
        episode = true,
      )

    assertEquals("2026", details.year)
    assertEquals(listOf("Lead Actor", "Guest Actor", "New Guest"), details.castNames)
    assertTrue(details.cast.last().guest)
    assertEquals("Witness", details.cast.last().character)
  }

  @Test
  fun movieId_isReadOnlyFromMoviePlaybackUrls() {
    assertEquals(1265609, tmdbMovieIdFromPlaybackUrl("https://vidfast.vc/movie/1265609?sub=en"))
    assertNull(tmdbMovieIdFromPlaybackUrl("https://vidfast.vc/tv/94997/1/3"))
  }
}
