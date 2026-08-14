package com.giztv.tv.ui.iptv

import androidx.media3.common.MimeTypes
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giztv.tv.ui.player.StreamDrmScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IptvPlaylistAssetTest {
  @Test
  fun bundledPlaylist_hasBrowsableChannelsAndPreservesPlaybackMetadata() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val playlist =
      context.assets.open("iptv/play.m3u").bufferedReader().use(::parseIptvPlaylist)

    assertTrue("Expected a substantial fallback catalog, got ${playlist.channels.size}", playlist.channels.size >= 1_200)
    assertTrue(iptvGroups(playlist.channels).any { it.equals("Sports", ignoreCase = true) })
    val categories = iptvCategories(playlist.channels)
    // Counted after the collapse rather than against the raw listing count: the playlist repeats a
    // channel wherever a mirror exists, and those repeats become that channel's backup sources
    // rather than cards of their own.
    assertTrue(
      "Expected the collapsed catalog to stay substantial, got ${categories.first().channelCount}",
      categories.first().channelCount >= 1_200,
    )
    // Not an equality: a channel listed in two categories is collapsed within each of them and so
    // counted in both, while All channels collapses across the lot. Every channel reaches at least
    // one category, so the parts cannot come to less than the whole.
    assertTrue(
      "Categories must account for every channel",
      categories.drop(1).sumOf(IptvCategory::channelCount) >= categories.first().channelCount,
    )
    assertTrue(categories.any { it.label == IPTV_CATEGORY_SPORTS && it.channelCount >= 400 })
    assertTrue(categories.any { it.label == IPTV_CATEGORY_REGIONAL })
    assertTrue(iptvGroupsForCategory(playlist.channels, IPTV_CATEGORY_SPORTS).size >= 5)
    assertTrue(playlist.channels.any { it.mimeType == MimeTypes.APPLICATION_M3U8 })
    assertTrue(playlist.channels.any { it.mimeType == MimeTypes.APPLICATION_MPD })
    assertTrue(playlist.channels.any { it.headers.containsKey("User-Agent") })
    assertTrue(playlist.channels.any { it.drm?.scheme == StreamDrmScheme.CLEARKEY })
    // Every address the player might reach for — the listed one and each mirror behind it — has to
    // be one the network config actually permits, or choosing that channel fails at the point the
    // fallback is used rather than when it is listed.
    assertTrue(
      "A playback source must be TLS, or plain text on a permitted host",
      playlist.channels.all { channel -> channel.playbackSources.all { isPermittedStreamUrl(it.url) } },
    )
    assertTrue(playlist.channels.any { it.playbackSources.size > 1 })
    assertEquals(playlist.channels.size, playlist.channels.map(IptvChannel::id).distinct().size)
  }
}

/** TLS, or plain text on a host res/xml/network_security_config.xml names. */
private fun isPermittedStreamUrl(url: String): Boolean {
  val lower = url.lowercase()
  return when {
    lower.startsWith("https://") -> true
    lower.startsWith("http://") ->
      lower.removePrefix("http://").substringBefore('/').substringBefore('?')
        .substringAfterLast('@').substringBefore(':') in IPTV_CLEARTEXT_HOSTS
    else -> false
  }
}
