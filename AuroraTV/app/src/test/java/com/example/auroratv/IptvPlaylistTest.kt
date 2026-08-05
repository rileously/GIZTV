package com.example.auroratv

import androidx.media3.common.MimeTypes
import com.example.auroratv.ui.iptv.ALL_IPTV_CHANNELS
import com.example.auroratv.ui.iptv.iptvGroups
import com.example.auroratv.ui.iptv.parseIptvPlaylist
import com.example.auroratv.ui.iptv.visibleIptvChannels
import com.example.auroratv.ui.player.StreamDrmScheme
import com.example.auroratv.ui.player.normalizeClearKeyLicense
import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IptvPlaylistTest {
  private val sample =
    """
      #EXTM3U url-tvg="https://example.com/epg.xml"
      #EXTINF:-1 tvg-id="news.one" tvg-name="News One" tvg-logo="https://img.example/news.png" group-title="News",News One HD
      #EXTVLCOPT:http-user-agent=Playlist Agent
      #EXTVLCOPT:http-referrer=https://portal.example/
      https://stream.example/live/index.m3u8|Origin=https%3A%2F%2Fportal.example&User-Agent=Inline%20Agent
      #EXTINF:-1 tvg-id="sport.one" group-title="Sports",Sport One
      #KODIPROP:inputstream.adaptive.manifest_type=mpd
      #KODIPROP:inputstream.adaptive.license_type=org.w3.clearkey
      #KODIPROP:inputstream.adaptive.license_key=00000000000000000000000000000000:11111111111111111111111111111111
      https://stream.example/sport/manifest.mpd
      #EXTINF:-1 group-title="Info",## GENERAL ##
      https://example.com/info.mp4
      #EXTINF:-1 group-title="Info",Telegram
      https://t.me/channel
    """.trimIndent()

  @Test
  fun m3uParser_keepsChannelMetadataHeadersFormatsAndDrm() {
    val playlist = parseIptvPlaylist(StringReader(sample))

    assertEquals("https://example.com/epg.xml", playlist.epgUrl)
    assertEquals(2, playlist.channels.size)
    val news = playlist.channels[0]
    assertEquals("News One HD", news.name)
    assertEquals("News", news.group)
    assertEquals("news.one", news.tvgId)
    assertEquals(MimeTypes.APPLICATION_M3U8, news.mimeType)
    assertEquals("Inline Agent", news.headers["User-Agent"])
    assertEquals("https://portal.example/", news.headers["Referer"])
    assertEquals("https://portal.example", news.headers["Origin"])

    val sport = playlist.channels[1]
    assertEquals(MimeTypes.APPLICATION_MPD, sport.mimeType)
    assertEquals(StreamDrmScheme.CLEARKEY, sport.drm?.scheme)
  }

  @Test
  fun groupsAndSearch_areCaseInsensitiveAndComposable() {
    val channels = parseIptvPlaylist(StringReader(sample)).channels

    assertEquals(listOf(ALL_IPTV_CHANNELS, "News", "Sports"), iptvGroups(channels))
    assertEquals(listOf("Sport One"), visibleIptvChannels(channels, "Sports", "sport").map { it.name })
    assertTrue(visibleIptvChannels(channels, "News", "sport").isEmpty())
    assertEquals(listOf("News One HD"), visibleIptvChannels(channels, null, "NEWS.ONE").map { it.name })
  }

  @Test
  fun clearKeyPair_isConvertedToAndroidJwkWithoutLeakingHexKeys() {
    val jwk =
      normalizeClearKeyLicense(
        "00000000000000000000000000000000:11111111111111111111111111111111"
      ).orEmpty()

    assertTrue(jwk.contains("\"kid\":\"AAAAAAAAAAAAAAAAAAAAAA\""))
    assertTrue(jwk.contains("\"k\":\"EREREREREREREREREREREQ\""))
    assertFalse(jwk.contains("00000000000000000000000000000000"))
    assertFalse(jwk.contains("11111111111111111111111111111111"))
  }
}
