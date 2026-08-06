package com.example.auroratv

import com.example.auroratv.ui.catalog.CANONICAL_PROVIDER
import com.example.auroratv.ui.catalog.CatalogTarget
import com.example.auroratv.ui.catalog.STREAM_PROVIDERS
import com.example.auroratv.ui.catalog.catalogTargetOf
import com.example.auroratv.ui.catalog.nextProviderPageUrl
import com.example.auroratv.ui.catalog.providerPageUrl
import com.example.auroratv.ui.catalog.vidfastEpisodeUrl
import com.example.auroratv.ui.catalog.vidfastMovieUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamProviderTest {

  @Test
  fun everyProvider_buildsBothKindsOfAddressFromTheSameIdentifiers() {
    STREAM_PROVIDERS.forEach { provider ->
      val movie = provider.movieUrl(786892)
      val episode = provider.episodeUrl(94997, 1, 1)

      assertTrue("${provider.id} movie url: $movie", movie.contains("786892"))
      assertTrue("${provider.id} movie url: $movie", movie.contains(provider.host))
      assertTrue("${provider.id} episode url: $episode", episode.contains("94997/1/1"))
      assertTrue("${provider.id} episode url: $episode", episode.contains(provider.host))
    }
  }

  @Test
  fun providerAddresses_matchEachSitesDocumentedShape() {
    val byId = STREAM_PROVIDERS.associateBy { it.id }

    assertTrue(byId.getValue("vidrock").movieUrl(786892).startsWith("https://vidrock.ru/movie/786892"))
    assertEquals("https://vidrock.ru/tv/94997/1/1", byId.getValue("vidrock").episodeUrl(94997, 1, 1))

    assertTrue(byId.getValue("vidlink").movieUrl(786892).startsWith("https://vidlink.pro/movie/786892"))
    assertTrue(byId.getValue("vidlink").episodeUrl(94997, 1, 1).startsWith("https://vidlink.pro/tv/94997/1/1"))

    // vsembed is the odd one: its player lives under /embed.
    assertTrue(byId.getValue("vsembed").movieUrl(786892).startsWith("https://vsembed.ru/embed/movie/786892"))
    assertTrue(byId.getValue("vsembed").episodeUrl(94997, 1, 1).startsWith("https://vsembed.ru/embed/tv/94997/1/1"))
  }

  @Test
  fun theAddressATitleIsRememberedBy_doesNotRideOnResolutionOrder() {
    assertEquals(CANONICAL_PROVIDER.movieUrl(786892), vidfastMovieUrl(786892))
    assertEquals(CANONICAL_PROVIDER.episodeUrl(94997, 1, 1), vidfastEpisodeUrl(94997, 1, 1))
    // Watch history written by earlier builds is keyed on these exact addresses, so reordering
    // the providers must never change them.
    assertEquals("https://vidfast.vc/movie/786892?autoPlay=true&sub=en&chromecast=false", vidfastMovieUrl(786892))
    assertEquals(
      "https://vidfast.vc/tv/94997/1/1?autoPlay=true&sub=en&chromecast=false",
      vidfastEpisodeUrl(94997, 1, 1),
    )
  }

  @Test
  fun anAddress_readsBackIntoTheTitleItStandsFor() {
    assertEquals(CatalogTarget(786892), catalogTargetOf(vidfastMovieUrl(786892)))
    assertEquals(CatalogTarget(94997, 1, 1), catalogTargetOf(vidfastEpisodeUrl(94997, 1, 1)))
    // Whichever provider served it, including the one that nests its player under /embed.
    assertEquals(CatalogTarget(786892), catalogTargetOf("https://vsembed.ru/embed/movie/786892?autoplay=1"))
    assertEquals(CatalogTarget(94997, 2, 5), catalogTargetOf("https://vidlink.pro/tv/94997/2/5?title=false"))
    assertEquals(CatalogTarget(786892), catalogTargetOf("https://vidrock.ru/movie/786892"))
  }

  @Test
  fun somethingThatIsNotACatalogTitle_hasNoProviderToFallBackOn() {
    assertNull(catalogTargetOf("https://skyflix.to/some-film"))
    assertNull(catalogTargetOf("https://vidfast.vc/"))
    assertNull(nextProviderPageUrl("https://skyflix.to/some-film", 1))
  }

  @Test
  fun failover_walksTheListAndThenStops() {
    val canonical = vidfastMovieUrl(786892)
    val visited = generateSequence(1) { it + 1 }
      .map { nextProviderPageUrl(canonical, it) }
      .takeWhile { it != null }
      .toList()

    // Every provider after the first gets a turn...
    assertEquals(STREAM_PROVIDERS.size - 1, visited.size)
    // ...each a different site, and never the one that just failed.
    assertEquals(visited.size, visited.distinct().size)
    assertTrue(visited.none { it == canonical })
    // ...and once they are exhausted there is nothing further to try automatically.
    assertNull(nextProviderPageUrl(canonical, STREAM_PROVIDERS.size))
  }

  @Test
  fun failover_keepsAskingForTheSameEpisode() {
    val canonical = vidfastEpisodeUrl(94997, 2, 5)
    for (attempt in 1 until STREAM_PROVIDERS.size) {
      val next = nextProviderPageUrl(canonical, attempt)
      assertNotNull("attempt $attempt", next)
      assertEquals(CatalogTarget(94997, 2, 5), catalogTargetOf(next!!))
    }
  }

  @Test
  fun providerAddresses_areRequestedByPosition() {
    val target = CatalogTarget(786892)
    STREAM_PROVIDERS.forEachIndexed { index, provider ->
      assertEquals(provider.movieUrl(786892), providerPageUrl(target, index))
    }
    assertNull(providerPageUrl(target, STREAM_PROVIDERS.size))
  }
}
