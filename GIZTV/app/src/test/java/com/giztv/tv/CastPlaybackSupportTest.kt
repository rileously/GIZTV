package com.giztv.tv

import androidx.media3.common.MimeTypes
import com.giztv.tv.ui.player.castCanCastFetchDirect
import com.giztv.tv.ui.player.castRequiresPhoneProxy
import com.giztv.tv.ui.player.castSensitiveHeaders
import com.giztv.tv.ui.player.isCastContainerSupported
import com.giztv.tv.ui.player.looksLikeHlsPlaylist
import com.giztv.tv.ui.player.resolveAgainst
import com.giztv.tv.ui.player.resolvePlaybackMimeType
import com.giztv.tv.ui.player.rewriteHlsPlaylistForCastProxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CastPlaybackSupportTest {
  @Test
  fun resolvePlaybackMimeType_prefersFilenameOverStaleHlsMime() {
    assertEquals(
      MimeTypes.VIDEO_MP4,
      resolvePlaybackMimeType(
        "https://cdn.example/mp4/TOKEN/1080p.mp4",
        MimeTypes.APPLICATION_M3U8,
      ),
    )
    assertEquals(
      MimeTypes.APPLICATION_M3U8,
      resolvePlaybackMimeType("https://cdn.example/index.m3u8", null),
    )
    assertEquals(
      MimeTypes.VIDEO_WEBM,
      resolvePlaybackMimeType("https://cdn.example/clip.webm", "video/mp4"),
    )
  }

  @Test
  fun castRequiresPhoneProxy_whenRefererOrCookiePresent() {
    assertFalse(castRequiresPhoneProxy(emptyMap()))
    assertTrue(castRequiresPhoneProxy(mapOf("Referer" to "https://skyflix.to/")))
    assertTrue(castRequiresPhoneProxy(mapOf("Cookie" to "a=b")))
    assertTrue(castRequiresPhoneProxy(mapOf("User-Agent" to "GIZTV/1.0")))
    assertFalse(castRequiresPhoneProxy(mapOf("Accept-Encoding" to "gzip")))
  }

  @Test
  fun castSensitiveHeaders_dropsHopByHopNames() {
    val filtered =
      castSensitiveHeaders(
        mapOf(
          "Referer" to "https://example/",
          "Host" to "cdn.example",
          "Range" to "bytes=0-1",
          "Cookie" to "sid=1",
        ),
      )
    assertEquals(setOf("Referer", "Cookie"), filtered.keys)
  }

  @Test
  fun rewriteHlsPlaylistForCastProxy_rewritesBareAndAttributeUris() {
    val playlist =
      """
      #EXTM3U
      #EXT-X-KEY:METHOD=AES-128,URI="keys/1.key"
      #EXT-X-MAP:URI="init.mp4"
      segment0.ts
      https://cdn.example/abs.ts
      """.trimIndent()
    val rewritten =
      rewriteHlsPlaylistForCastProxy(
        body = playlist,
        playlistUrl = "https://cdn.example/live/master.m3u8",
        proxyUriFor = { absolute -> "http://phone/cast/${absolute.substringAfterLast('/')}" },
      )
    assertTrue(rewritten.contains("""URI="http://phone/cast/1.key""""))
    assertTrue(rewritten.contains("""URI="http://phone/cast/init.mp4""""))
    assertTrue(rewritten.contains("http://phone/cast/segment0.ts"))
    assertTrue(rewritten.contains("http://phone/cast/abs.ts"))
    assertTrue(rewritten.lines().first().startsWith("#EXTM3U"))
  }

  @Test
  fun resolveAgainst_handlesRelativeAndAbsolute() {
    assertEquals(
      "https://cdn.example/live/seg.ts",
      resolveAgainst("https://cdn.example/live/index.m3u8", "seg.ts"),
    )
    assertEquals(
      "https://other.example/a.ts",
      resolveAgainst("https://cdn.example/live/index.m3u8", "https://other.example/a.ts"),
    )
  }

  @Test
  fun looksLikeHlsPlaylist_detectsMimeAndBody() {
    assertTrue(looksLikeHlsPlaylist("application/vnd.apple.mpegurl", ""))
    assertTrue(looksLikeHlsPlaylist(null, "#EXTM3U\n#EXTINF"))
    assertFalse(looksLikeHlsPlaylist("video/mp4", "ftyp"))
  }

  @Test
  fun castCanCastFetchDirect_sendsMediaSegmentsStraightToCdn() {
    val refererOnly = mapOf("Referer" to "https://skyflix.to/")
    assertTrue(castCanCastFetchDirect("https://cdn.example/live/seg001.ts", refererOnly))
    assertTrue(castCanCastFetchDirect("https://cdn.example/live/seg001.m4s", refererOnly))
    assertFalse(castCanCastFetchDirect("https://cdn.example/live/index.m3u8", refererOnly))
    assertFalse(castCanCastFetchDirect("https://cdn.example/live/keys/1.key", refererOnly))
    assertFalse(castCanCastFetchDirect("https://cdn.example/live/init.mp4", refererOnly))
    assertFalse(
      castCanCastFetchDirect(
        "https://cdn.example/live/seg001.ts",
        mapOf("Cookie" to "sid=1", "Referer" to "https://skyflix.to/"),
      ),
    )
  }

  @Test
  fun rewriteHlsPlaylistForCastProxy_canLeaveSegmentsOnCdn() {
    val playlist =
      """
      #EXTM3U
      #EXT-X-KEY:METHOD=AES-128,URI="keys/1.key"
      #EXTINF:4,
      segment0.ts
      variant.m3u8
      """.trimIndent()
    val headers = mapOf("Referer" to "https://example/")
    val rewritten =
      rewriteHlsPlaylistForCastProxy(
        body = playlist,
        playlistUrl = "https://cdn.example/live/master.m3u8",
        proxyUriFor = { absolute ->
          if (castCanCastFetchDirect(absolute, headers)) absolute
          else "http://phone/cast/${absolute.substringAfterLast('/')}"
        },
      )
    assertTrue(rewritten.contains("""URI="http://phone/cast/1.key""""))
    assertTrue(rewritten.contains("https://cdn.example/live/segment0.ts"))
    assertTrue(rewritten.contains("http://phone/cast/variant.m3u8"))
  }

  @Test
  fun isCastContainerSupported_allowsCommonCastableTypes() {
    assertTrue(isCastContainerSupported(MimeTypes.APPLICATION_M3U8))
    assertTrue(isCastContainerSupported(MimeTypes.VIDEO_MP4))
    assertFalse(isCastContainerSupported("video/x-matroska"))
  }
}
