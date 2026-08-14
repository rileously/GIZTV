package com.giztv.tv

import com.giztv.tv.ui.catalog.CANONICAL_PROVIDER
import com.giztv.tv.ui.catalog.CatalogTarget
import com.giztv.tv.ui.catalog.STREAM_PROVIDERS
import com.giztv.tv.ui.catalog.catalogTargetOf
import com.giztv.tv.ui.catalog.nextProviderPageUrl
import com.giztv.tv.ui.catalog.playbackServerOptions
import com.giztv.tv.ui.catalog.providerPageUrl
import com.giztv.tv.ui.catalog.selectedPlaybackServerIndex
import com.giztv.tv.ui.catalog.serverLabelFor
import com.giztv.tv.ui.catalog.vidfastEpisodeUrl
import com.giztv.tv.ui.catalog.vidfastMovieUrl
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
      assertTrue("${provider.id} episode url: $episode", episode.contains(provider.host))
      // Asserted by reading the address back rather than by looking for a path, because the shape
      // is the site's business: cinesrc names the episode in the query string.
      assertEquals("${provider.id} movie url: $movie", CatalogTarget(786892), catalogTargetOf(movie))
      assertEquals(
        "${provider.id} episode url: $episode",
        CatalogTarget(94997, 1, 1),
        catalogTargetOf(episode),
      )
    }
  }

  @Test
  fun providerAddresses_matchEachSitesDocumentedShape() {
    val byId = STREAM_PROVIDERS.associateBy { it.id }

    assertTrue(byId.getValue("vidrock").movieUrl(786892).startsWith("https://vidrock.ru/movie/786892"))
    assertEquals("https://vidrock.ru/tv/94997/1/1", byId.getValue("vidrock").episodeUrl(94997, 1, 1))

    // Measured against the site: the movie form takes a bare TMDB id under /embed/, and the
    // three-segment episode form every other provider uses answers 404 here.
    assertEquals("https://cinesrc.st/embed/movie/786892", byId.getValue("cinesrc").movieUrl(786892))
    assertEquals(
      "https://cinesrc.st/embed/tv/94997?season=1&episode=1",
      byId.getValue("cinesrc").episodeUrl(94997, 1, 1),
    )

    assertTrue(byId.getValue("vidfast").movieUrl(786892).startsWith("https://vidfast.vc/movie/786892"))
    assertTrue(byId.getValue("vidfast").episodeUrl(94997, 1, 1).startsWith("https://vidfast.vc/tv/94997/1/1"))

    assertEquals("https://player.videasy.to/movie/786892", byId.getValue("videasy").movieUrl(786892))
    assertEquals("https://player.videasy.to/tv/94997/1/1", byId.getValue("videasy").episodeUrl(94997, 1, 1))
  }

  @Test
  fun onlySitesThatWereMeasuredPlaying_areInTheList() {
    // VidSrc assembles its address inside the page behind an anti-inspection script, so the
    // resolver never sees one. Carrying it would only add a dead attempt to every failover.
    assertTrue(STREAM_PROVIDERS.none { it.host.contains("vsembed") })
    assertEquals(listOf("vidrock", "cinesrc", "vidfast", "videasy"), STREAM_PROVIDERS.map { it.id })
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
    assertEquals(CatalogTarget(94997, 2, 5), catalogTargetOf("https://vidrock.ru/tv/94997/2/5"))
    assertEquals(CatalogTarget(786892), catalogTargetOf("https://vidrock.ru/movie/786892"))
    // And the one that names the episode beside the address rather than inside it.
    assertEquals(CatalogTarget(94997, 2, 5), catalogTargetOf(STREAM_PROVIDERS.first { it.id == "cinesrc" }.episodeUrl(94997, 2, 5)))
    assertEquals(CatalogTarget(786892), catalogTargetOf("https://cinesrc.st/embed/movie/786892"))
    // A show with no episode named is not something that can be played, so it is not a target.
    assertNull(catalogTargetOf("https://cinesrc.st/embed/tv/94997"))
    assertNull(catalogTargetOf("https://cinesrc.st/embed/tv/94997?season=2"))
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

    // Every provider after the primary gets a turn...
    assertEquals(STREAM_PROVIDERS.size - 1, visited.size)
    // ...each a different site...
    assertEquals(visited.size, visited.distinct().size)
    // ...and never the primary, which is the one that just failed. Note this is not the same as
    // "never the canonical address": vidfast is a perfectly good fallback now that it no longer
    // leads, and the address a title is remembered by is still its.
    assertTrue(visited.none { it == providerPageUrl(CatalogTarget(786892), 0) })
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
  fun attemptZero_isTheHeadOfTheListRatherThanTheAddressTheTitleIsRememberedBy() {
    // The whole point of the ordering: a title opened by hand must start at the primary provider,
    // not at whichever provider happens to own the remembered address.
    val canonical = vidfastMovieUrl(786892)
    assertEquals(STREAM_PROVIDERS.first().movieUrl(786892), nextProviderPageUrl(canonical, 0))
    assertEquals("vidrock", STREAM_PROVIDERS.first().id)
    // ...and the remembered address still belongs to vidfast, so nobody loses their place.
    assertEquals("vidfast", CANONICAL_PROVIDER.id)
  }

  @Test
  fun theServingSite_isNamedByItsPlaceInTheRunningOrder() {
    assertEquals("SR1", serverLabelFor("https://vidrock.ru/movie/786892"))
    assertEquals("SR2", serverLabelFor("https://cinesrc.st/embed/tv/94997?season=1&episode=1"))
    assertEquals("SR3", serverLabelFor(vidfastMovieUrl(786892)))
    assertEquals("SR4", serverLabelFor("https://player.videasy.to/movie/786892"))
    // Subdomains still belong to their provider.
    assertEquals("SR1", serverLabelFor("https://cdn.vidrock.ru/movie/786892"))
  }

  @Test
  fun somethingServedFromOutsideTheList_isNotGivenAServerNumber() {
    assertNull(serverLabelFor("https://hoofoot.ru/gl?id=hd11"))
    assertNull(serverLabelFor("https://skyflix.to/some-film"))
    // VidSrc was measured and dropped; nothing should still be pointing at it.
    assertNull(serverLabelFor("https://vsembed.ru/embed/movie/786892"))
    assertNull(serverLabelFor(null))
    assertNull(serverLabelFor(""))
  }

  @Test
  fun providerAddresses_areRequestedByPosition() {
    val target = CatalogTarget(786892)
    STREAM_PROVIDERS.forEachIndexed { index, provider ->
      assertEquals(provider.movieUrl(786892), providerPageUrl(target, index))
    }
    assertNull(providerPageUrl(target, STREAM_PROVIDERS.size))
  }

  @Test
  fun catalogTitles_offerEveryProviderAsAServerChoice() {
    val options = playbackServerOptions(catalogPageUrl = vidfastMovieUrl(786892), sourceCount = 1)
    assertEquals(listOf("SR1", "SR2", "SR3", "SR4"), options.map { it.label })
    assertEquals(listOf(0, 1, 2, 3), options.map { it.index })
  }

  @Test
  fun nonCatalogPages_haveNoServerPicker() {
    assertTrue(playbackServerOptions(catalogPageUrl = "https://skyflix.to/some-film", sourceCount = 1).isEmpty())
    assertTrue(playbackServerOptions(catalogPageUrl = null, sourceCount = 1).isEmpty())
  }

  @Test
  fun iptvChannels_offerEachBackupLinkWhenThereIsMoreThanOne() {
    val options = playbackServerOptions(catalogPageUrl = null, sourceCount = 3)
    assertEquals(listOf("Link 1", "Link 2", "Link 3"), options.map { it.label })
    // Backup links win over a catalog page when both are present: the stream request already
    // carries the IPTV mirrors, and those are what a hand-pick should change.
    assertEquals(
      listOf("Link 1", "Link 2"),
      playbackServerOptions(catalogPageUrl = vidfastMovieUrl(786892), sourceCount = 2).map { it.label },
    )
  }

  @Test
  fun selectedServer_followsTheServingProviderOrIptvIndex() {
    assertEquals(
      1,
      selectedPlaybackServerIndex(
        sourcePageUrl = "https://cinesrc.st/embed/movie/1",
        sourceIndex = 0,
        sourceCount = 1,
      ),
    )
    assertEquals(
      2,
      selectedPlaybackServerIndex(sourcePageUrl = null, sourceIndex = 2, sourceCount = 4),
    )
  }
}
