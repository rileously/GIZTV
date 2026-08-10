package com.giztv.tv

import com.giztv.tv.ui.dlhd.isDlhdAdultChannel
import com.giztv.tv.ui.dlhd.parseDlhd24x7Channels
import com.giztv.tv.ui.iptv.DLHD_IPTV_GROUP
import com.giztv.tv.ui.iptv.IPTV_CATEGORY_DADDYLIVE
import com.giztv.tv.ui.iptv.iptvCategoryFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

  @Test
  fun parser_dropsAdultAnd18PlusChannels() {
    val channels = parseDlhd24x7Channels(ADULT_MIXED_HTML)

    assertEquals(
      listOf("Adult Swim", "FOX USA", "La Sexta Spain"),
      channels.map { it.name },
    )
  }

  @Test
  fun adultFilter_flagsAdultTitlesAndKeepsMainstreamOnes() {
    val removed =
      listOf(
        "18+ (Player-01)",
        "18+ (Player-20)",
        "XXX",
        "Adult Channel",
        "Porn Hub Live",
        "Brazzers TV",
        "Playboy TV",
        "Hustler TV",
        "Red Light",
        "Eros",
        "Spice",
        "X-Rated Night",
      )
    val kept =
      listOf(
        "FOX USA",
        "Fox News",
        "Adult Swim",
        "La Sexta Spain",
        "ABC USA",
        "Animal Planet",
      )

    removed.forEach { name -> assertTrue(name, isDlhdAdultChannel(name)) }
    kept.forEach { name -> assertFalse(name, isDlhdAdultChannel(name)) }
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

    val ADULT_MIXED_HTML =
      """
      <a class="card" href="/watch.php?id=51" data-title="abc usa" data-first="A">
        <div class="card__title">FOX USA</div>
      </a>
      <a class="card" href="/watch.php?id=9001" data-title="18+ player-01" data-first="1">
        <div class="card__title">18+ (Player-01)</div>
      </a>
      <a class="card" href="/watch.php?id=9002" data-title="playboy tv" data-first="P">
        <div class="card__title">Playboy TV</div>
      </a>
      <a class="card" href="/watch.php?id=9003" data-title="adult swim" data-first="A">
        <div class="card__title">Adult Swim</div>
      </a>
      <a class="card" href="/watch.php?id=9004" data-title="xxx" data-first="X">
        <div class="card__title">XXX</div>
      </a>
      <a class="card" href="/watch.php?id=9005" data-title="la sexta spain" data-first="L">
        <div class="card__title">La Sexta Spain</div>
      </a>
      """
        .trimIndent()
  }
}
