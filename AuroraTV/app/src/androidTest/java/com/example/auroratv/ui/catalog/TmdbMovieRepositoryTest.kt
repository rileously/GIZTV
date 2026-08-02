package com.example.auroratv.ui.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbMovieRepositoryTest {
  @Test
  fun parser_readsMovieFieldsAndSkipsInvalidRows() {
    val movies =
      parseTmdbMovies(
        """
        {
          "results": [
            {
              "id": 1265609,
              "title": "Test Movie",
              "release_date": "2025-04-18",
              "vote_average": 7.45,
              "overview": "A test overview.",
              "poster_path": "/poster.jpg"
            },
            { "id": 0, "title": "Invalid" }
          ]
        }
        """.trimIndent()
      )

    assertEquals(1, movies.size)
    assertEquals(1265609, movies.single().id)
    assertEquals("Test Movie", movies.single().title)
    assertEquals("2025", movies.single().year)
    assertEquals("https://image.tmdb.org/t/p/w500/poster.jpg", movies.single().posterUrl)
  }

  @Test
  fun parser_treatsMissingPosterAsNull() {
    val movie = parseTmdbMovies("""{"results":[{"id":11,"title":"Movie","poster_path":null}]}""").single()

    assertNull(movie.posterUrl)
  }

  @Test
  fun vidfastUrl_usesTmdbIdAndRequestsEnglishSubtitles() {
    assertEquals(
      "https://vidfast.vc/movie/1265609?autoPlay=true&sub=en&chromecast=false",
      vidfastMovieUrl(1265609),
    )
  }
}
