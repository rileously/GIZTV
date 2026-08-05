package com.example.auroratv.ui.iptv

import androidx.media3.common.MimeTypes
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.auroratv.ui.player.StreamDrmScheme
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

    assertTrue(playlist.channels.size >= 1_500)
    assertTrue(iptvGroups(playlist.channels).any { it.equals("Sports", ignoreCase = true) })
    assertTrue(playlist.channels.any { it.mimeType == MimeTypes.APPLICATION_M3U8 })
    assertTrue(playlist.channels.any { it.mimeType == MimeTypes.APPLICATION_MPD })
    assertTrue(playlist.channels.any { it.headers.containsKey("User-Agent") })
    assertTrue(playlist.channels.any { it.drm?.scheme == StreamDrmScheme.CLEARKEY })
    assertEquals(playlist.channels.size, playlist.channels.map(IptvChannel::id).distinct().size)
  }
}
