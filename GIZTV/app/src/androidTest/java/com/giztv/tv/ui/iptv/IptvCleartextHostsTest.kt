package com.giztv.tv.ui.iptv

import android.security.NetworkSecurityPolicy
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.giztv.tv.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/**
 * Keeps the cleartext allowlist, the network config and the playlist telling the same story.
 *
 * Three things have to agree for an http channel to work: the playlist offers it, the network
 * config permits its host, and the guide's own filter lets it through. A host missing from one of
 * them fails quietly — either a channel that is listed and cannot play, or one that could play and
 * is never shown — so the drift is caught here rather than by a viewer.
 */
@RunWith(AndroidJUnit4::class)
class IptvCleartextHostsTest {

  @Test
  fun everyCleartextHostInThePlaylistIsPermitted() {
    val missing = playlistCleartextHosts() - IPTV_CLEARTEXT_HOSTS
    assertTrue(
      "Cleartext hosts in play.m3u that nothing permits, so their channels are hidden: $missing. " +
        "Regenerate IptvCleartextHosts.kt and res/xml/network_security_config.xml.",
      missing.isEmpty(),
    )
  }

  @Test
  fun theAllowlistAndTheNetworkConfigNameTheSameHosts() {
    assertEquals(IPTV_CLEARTEXT_HOSTS, networkConfigCleartextDomains())
  }

  @Test
  fun theAllowlistCarriesNothingThePlaylistNoLongerOffers() {
    val stale = IPTV_CLEARTEXT_HOSTS - playlistCleartextHosts()
    assertTrue("Hosts permitted in plain text that no channel uses any more: $stale", stale.isEmpty())
  }

  /**
   * Asks the platform itself, rather than a live stream.
   *
   * This is what actually decides whether an http channel plays, and it answers without depending
   * on some third-party server still being up — including for the bare IP addresses that most of
   * the allowlist consists of, which are the entries most likely to be rejected out of hand.
   */
  @Test
  fun theSystemPermitsEveryAllowlistedHostAndNothingElse() {
    val policy = NetworkSecurityPolicy.getInstance()

    val refused = IPTV_CLEARTEXT_HOSTS.filterNot(policy::isCleartextTrafficPermitted)
    assertTrue("Hosts the network config names but the system still refuses: $refused", refused.isEmpty())

    val addresses = IPTV_CLEARTEXT_HOSTS.filter { it.matches(Regex("""\d+\.\d+\.\d+\.\d+""")) }
    assertTrue("The allowlist should be exercising bare IP addresses", addresses.isNotEmpty())
    assertTrue(
      "Bare IP addresses must be permitted too, or a third of the channels stay broken",
      addresses.all(policy::isCleartextTrafficPermitted),
    )

    listOf("example.com", "iptv-org.github.io", "some-host-not-in-the-list.test").forEach { host ->
      assertTrue(
        "Cleartext must stay refused for $host — the browser loads addresses the viewer types",
        !policy.isCleartextTrafficPermitted(host),
      )
    }
  }

  @Test
  fun aPermittedCleartextChannelIsOfferedAndAnUnknownOneIsNot() {
    val permitted = IPTV_CLEARTEXT_HOSTS.first()
    val playlist =
      """
      #EXTM3U
      #EXTINF:-1 group-title="Test",Permitted
      http://$permitted/live/stream.m3u8
      #EXTINF:-1 group-title="Test",Unknown host
      http://not-in-the-allowlist.example/live/stream.m3u8
      #EXTINF:-1 group-title="Test",Secure
      https://secure.example/live/stream.m3u8
      """
        .trimIndent()
        .reader()
        .use(::parseIptvPlaylist)

    val names = playlist.channels.map { it.name }
    assertTrue("A permitted cleartext channel should be listed", names.contains("Permitted"))
    assertTrue("A secure channel should be listed", names.contains("Secure"))
    assertTrue("An unpermitted cleartext channel would fail, so it stays hidden", !names.contains("Unknown host"))
  }

  private fun playlistCleartextHosts(): Set<String> {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val text = context.assets.open("iptv/play.m3u").bufferedReader().use { it.readText() }
    return text
      .lineSequence()
      .map(String::trim)
      .filter { it.startsWith("http://", ignoreCase = true) }
      .mapNotNull { line ->
        Regex("^http://([^/|?#]+)", RegexOption.IGNORE_CASE)
          .find(line)
          ?.groupValues
          ?.get(1)
          ?.substringAfterLast('@')
          ?.substringBefore('|')
          ?.replace(Regex(":\\d+$"), "")
          ?.lowercase()
          ?.takeIf(String::isNotBlank)
      }
      .toSet()
  }

  private fun networkConfigCleartextDomains(): Set<String> {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val domains = mutableSetOf<String>()
    context.resources.getXml(R.xml.network_security_config).use { parser ->
      var permitting = false
      while (parser.eventType != XmlPullParser.END_DOCUMENT) {
        when (parser.eventType) {
          XmlPullParser.START_TAG ->
            when (parser.name) {
              // Network security config attributes carry no namespace, unlike the manifest's.
              "domain-config" ->
                permitting = parser.getAttributeValue(null, "cleartextTrafficPermitted") == "true"
              "domain" -> if (permitting) parser.nextText().trim().lowercase().let(domains::add)
            }
          XmlPullParser.END_TAG -> if (parser.name == "domain-config") permitting = false
        }
        parser.next()
      }
    }
    return domains
  }

}
