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

    assertTrue("Expected a substantial HTTPS-only fallback catalog, got ${playlist.channels.size}", playlist.channels.size >= 1_200)
    assertTrue(iptvGroups(playlist.channels).any { it.equals("Sports", ignoreCase = true) })
    val categories = iptvCategories(playlist.channels)
    assertEquals(playlist.channels.size, categories.first().channelCount)
    assertEquals(playlist.channels.size, categories.drop(1).sumOf(IptvCategory::channelCount))
    assertTrue(categories.any { it.label == IPTV_CATEGORY_SPORTS && it.channelCount >= 400 })
    assertTrue(categories.any { it.label == IPTV_CATEGORY_REGIONAL })
    assertTrue(iptvGroupsForCategory(playlist.channels, IPTV_CATEGORY_SPORTS).size >= 5)
    assertTrue(playlist.channels.any { it.mimeType == MimeTypes.APPLICATION_M3U8 })
    assertTrue(playlist.channels.any { it.mimeType == MimeTypes.APPLICATION_MPD })
    assertTrue(playlist.channels.any { it.headers.containsKey("User-Agent") })
    assertTrue(playlist.channels.any { it.drm?.scheme == StreamDrmScheme.CLEARKEY })
    assertTrue(playlist.channels.all { channel -> channel.playbackSources.all { it.url.startsWith("https://") } })
    assertTrue(playlist.channels.any { it.playbackSources.size > 1 })
    assertEquals(playlist.channels.size, playlist.channels.map(IptvChannel::id).distinct().size)
  }
}
