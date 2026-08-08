package com.example.auroratv

import androidx.media3.common.MimeTypes
import com.example.auroratv.ui.iptv.ALL_IPTV_CHANNELS
import com.example.auroratv.ui.iptv.ALL_IPTV_GROUPS
import com.example.auroratv.ui.iptv.DLHD_IPTV_GROUP
import com.example.auroratv.ui.iptv.IPTV_CATEGORY_DADDYLIVE
import com.example.auroratv.ui.iptv.IPTV_CATEGORY_FAITH
import com.example.auroratv.ui.iptv.IPTV_CATEGORY_KIDS
import com.example.auroratv.ui.iptv.IPTV_CATEGORY_KNOWLEDGE
import com.example.auroratv.ui.iptv.IPTV_CATEGORY_MUSIC
import com.example.auroratv.ui.iptv.IPTV_CATEGORY_NEWS
import com.example.auroratv.ui.iptv.IPTV_CATEGORY_REGIONAL
import com.example.auroratv.ui.iptv.IPTV_CATEGORY_SPORTS
import com.example.auroratv.ui.iptv.iptvCategories
import com.example.auroratv.ui.iptv.iptvCategoryFor
import com.example.auroratv.ui.iptv.iptvGroups
import com.example.auroratv.ui.iptv.iptvGroupsForCategory
import com.example.auroratv.ui.iptv.parseIptvPlaylist
import com.example.auroratv.ui.iptv.parseRemoteIptvPlaylist
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
      https://backup.example/live/index.m3u8
      #EXTINF:-1 tvg-id="sport.one" group-title="Sports",Sport One
      #KODIPROP:inputstream.adaptive.manifest_type=mpd
      #KODIPROP:inputstream.adaptive.license_type=org.w3.clearkey
      #KODIPROP:inputstream.adaptive.license_key=00000000000000000000000000000000:11111111111111111111111111111111
      https://stream.example/sport/manifest.mpd
      #EXTINF:-1 group-title="News",Cleartext Cannot Play
      http://stream.example/blocked/index.m3u8
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
    assertEquals(2, news.playbackSources.size)
    assertEquals("https://backup.example/live/index.m3u8", news.playbackSources[1].url)
    assertEquals(listOf(0, 1), news.toPlaybackRequests().map { it.sourceIndex })
    assertEquals(listOf(2, 2), news.toPlaybackRequests().map { it.sourceCount })

    val sport = playlist.channels[1]
    assertEquals(MimeTypes.APPLICATION_MPD, sport.mimeType)
    assertEquals(StreamDrmScheme.CLEARKEY, sport.drm?.scheme)
  }

  @Test
  fun remotePlaylistValidation_rejectsSeizureHtmlAndAcceptsRealM3u() {
    val seizedPage = "<html><title>This domain has been seized</title></html>"

    assertEquals(null, parseRemoteIptvPlaylist(seizedPage, minimumChannels = 1))
    assertEquals(2, parseRemoteIptvPlaylist(sample, minimumChannels = 1)?.channels?.size)
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
  fun categories_consolidateProviderLabelsAndKeepSubgroupsAvailable() {
    val channels = parseIptvPlaylist(StringReader(sample)).channels
    val categories = iptvCategories(channels)

    assertEquals(listOf(ALL_IPTV_CHANNELS, IPTV_CATEGORY_SPORTS, IPTV_CATEGORY_NEWS), categories.map { it.label })
    assertEquals(listOf(2, 1, 1), categories.map { it.channelCount })
    assertEquals(listOf(ALL_IPTV_GROUPS, "Sports"), iptvGroupsForCategory(channels, IPTV_CATEGORY_SPORTS))
    assertEquals(
      listOf("Sport One"),
      visibleIptvChannels(channels, IPTV_CATEGORY_SPORTS, ALL_IPTV_GROUPS, "").map { it.name },
    )
  }

  @Test
  fun categoryRules_coverTheBundledPlaylistsProviderStyles() {
    assertEquals(IPTV_CATEGORY_DADDYLIVE, iptvCategoryFor(DLHD_IPTV_GROUP))
    assertEquals(IPTV_CATEGORY_SPORTS, iptvCategoryFor("HilayTV | Sports"))
    assertEquals(IPTV_CATEGORY_SPORTS, iptvCategoryFor("FIFA World Cup 2026 Channels"))
    assertEquals(IPTV_CATEGORY_REGIONAL, iptvCategoryFor("Maldives (IPTV) Medianet"))
    assertEquals(IPTV_CATEGORY_REGIONAL, iptvCategoryFor("Hindi"))
    assertEquals(IPTV_CATEGORY_KIDS, iptvCategoryFor("Kids;movies"))
    assertEquals(IPTV_CATEGORY_MUSIC, iptvCategoryFor("HilayTV | Dhivehi Radio"))
    assertEquals(IPTV_CATEGORY_KNOWLEDGE, iptvCategoryFor("Educational"))
    assertEquals(IPTV_CATEGORY_KNOWLEDGE, iptvCategoryFor("Documentary"))
    assertEquals(IPTV_CATEGORY_FAITH, iptvCategoryFor("HilayTV | Islam"))
    assertEquals(IPTV_CATEGORY_FAITH, iptvCategoryFor("Religious"))
    assertEquals(IPTV_CATEGORY_NEWS, iptvCategoryFor("Business"))
    assertEquals(IPTV_CATEGORY_KIDS, iptvCategoryFor("Animation"))
    assertEquals(IPTV_CATEGORY_NEWS, iptvCategoryFor("Other", "BBC World News"))
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
