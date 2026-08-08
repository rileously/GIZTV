package com.example.auroratv

import com.example.auroratv.ui.dlhd.parseDlhd24x7Channels
import com.example.auroratv.ui.iptv.DLHD_IPTV_GROUP
import com.example.auroratv.ui.iptv.IPTV_CATEGORY_DADDYLIVE
import com.example.auroratv.ui.iptv.iptvCategoryFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** DaddyLive 24/7 channel grid → IPTV rows. */
class DlhdChannelsTest {
  @Test
  fun parser_readsChannelCardsIntoBrowserResolvedIptvRows() {
    val channels = parseDlhd24x7Channels(SAMPLE_HTML)

    assertEquals(3, channels.size)
    val abc = channels.first { it.name == "ABC USA" }
    assertEquals("dlhd-51", abc.id)
    assertEquals("51", abc.tvgId)
    assertEquals("https://dlhd.st/watch.php?id=51", abc.url)
    assertEquals(DLHD_IPTV_GROUP, abc.group)
    assertTrue(abc.resolveViaBrowser)
    assertEquals("DLHD", abc.formatLabel)
    assertEquals(IPTV_CATEGORY_DADDYLIVE, iptvCategoryFor(abc))
  }

  @Test
  fun parser_decodesEntitiesAndSortsByName() {
    val channels = parseDlhd24x7Channels(SAMPLE_HTML)

    assertEquals(listOf("A&E USA", "ABC USA", "Animal Planet"), channels.map { it.name })
  }

  @Test
  fun parser_skipsDuplicateChannelIds() {
    val channels = parseDlhd24x7Channels(DUPLICATE_HTML)

    assertEquals(1, channels.size)
    assertEquals("ABC USA", channels.single().name)
  }

  private companion object {
    val SAMPLE_HTML =
      """
      <div class="grid">
        <a class="card" href="/watch.php?id=302" data-title="a&amp;e usa" data-first="A">
          <div class="card__title">A&amp;E USA</div>
          <div class="">ID: 302</div>
        </a>
        <a class="card" href="/watch.php?id=304" data-title="animal planet" data-first="A">
          <div class="card__title">Animal Planet</div>
          <div class="">ID: 304</div>
        </a>
        <a class="card" href="/watch.php?id=51" data-title="abc usa" data-first="A">
          <div class="card__title">ABC USA</div>
          <div class="">ID: 51</div>
        </a>
      </div>
      """
        .trimIndent()

    val DUPLICATE_HTML =
      """
      <a class="card" href="/watch.php?id=51" data-title="abc usa" data-first="A">
        <div class="card__title">ABC USA</div>
      </a>
      <a class="card" href="/watch.php?id=51" data-title="abc usa again" data-first="A">
        <div class="card__title">ABC USA Duplicate</div>
      </a>
      """
        .trimIndent()
  }
}
