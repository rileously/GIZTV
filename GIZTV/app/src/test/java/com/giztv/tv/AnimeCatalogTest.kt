package com.giztv.tv

import com.giztv.tv.ui.anime.parseAnimeCards
import com.giztv.tv.ui.anime.parseAnimeDetails
import com.giztv.tv.ui.anime.parseAnimeEpisodes
import com.giztv.tv.ui.anime.parseAnimeLanguages
import com.giztv.tv.ui.anime.parseEmbedStreamUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures are cut from anidb.app's own responses rather than written to suit the parser. */
class AnimeCatalogTest {
  private val browseGrid =
    """
      <div class="anime-grid">
      <a href="https://anidb.app/anime/one-piece-3880" class="anime-card block group" title="One Piece">
          <div class="relative overflow-hidden rounded-xl bg-elevated" style="aspect-ratio:2/3">
              <img src="https://cdn.xlsbox.com/poster/small/1782735600/3880.jpg" alt="One Piece" loading="lazy"
                   class="w-full h-full object-cover" onerror="this.onerror=null;this.src='/img/placeholder.svg'">
              <div class="absolute top-1.5 left-1.5 right-1.5 flex justify-between items-start">
                  <span class="badge badge-orange text-[9px]" style="background:rgba(249,115,22,.85);color:#fff">TV</span>
                  <span class="badge badge-gray flex items-center gap-0.5 text-[9px]">
                      <svg class="w-2.5 h-2.5" viewBox="0 0 20 20" fill="currentColor"><path d="M9.049 2.927z"/></svg>
                      8.7
                  </span>
              </div>
          </div>
          <p class="text-xs text-faint mt-1.5">One Piece</p>
      </a>
      <a href="https://anidb.app/anime/violet-evergarden-the-movie-2261" class="anime-card block group" title="Violet Evergarden: The Movie">
          <div class="relative overflow-hidden rounded-xl bg-elevated" style="aspect-ratio:2/3">
              <img src="https://cdn.xlsbox.com/poster/small/1782735600/2261.jpg" alt="Violet Evergarden: The Movie" loading="lazy">
              <span class="badge badge-orange text-[9px]">Movie</span>
              <span class="badge badge-gray flex items-center gap-0.5 text-[9px]">
                  <svg viewBox="0 0 20 20"><path d="M9 2z"/></svg>
                  8.8
              </span>
          </div>
      </a>
      <a href="https://anidb.app/anime/tomorrows-joe-2-1180" class="anime-card block group" title="Tomorrow&#039;s Joe 2">
          <img src="https://cdn.xlsbox.com/poster/small/1782735600/1180.jpg" alt="Tomorrow&#039;s Joe 2">
      </a>
      </div>
    """.trimIndent()

  @Test
  fun browseGrid_yieldsSlugIdTitleArtworkAndBadges() {
    val anime = parseAnimeCards(browseGrid)

    assertEquals(3, anime.size)
    val onePiece = anime[0]
    assertEquals("one-piece-3880", onePiece.slug)
    // The number on the end of the slug is what every episode endpoint is keyed on.
    assertEquals(3880, onePiece.id)
    assertEquals("One Piece", onePiece.title)
    assertEquals("https://cdn.xlsbox.com/poster/small/1782735600/3880.jpg", onePiece.posterUrl)
    assertEquals("TV", onePiece.kind)
    assertEquals("8.7", onePiece.score)
    assertEquals("https://anidb.app/anime/one-piece-3880", onePiece.pageUrl)

    assertEquals("Movie", anime[1].kind)
    assertEquals("8.8", anime[1].score)
    // A card's fields are read from its own markup, never bled in from the card before it.
    assertEquals(2261, anime[1].id)
  }

  @Test
  fun cardTitles_areDecodedRatherThanLeftAsEntities() {
    assertEquals("Tomorrow's Joe 2", parseAnimeCards(browseGrid)[2].title)
  }

  @Test
  fun cardsWithoutBadges_areStillListed() {
    val bare = parseAnimeCards(browseGrid)[2]

    assertNull(bare.kind)
    assertNull(bare.score)
    assertEquals("Tomorrow's Joe 2", bare.title)
  }

  @Test
  fun aSlugWithoutATrailingNumber_isSkippedRatherThanGuessedAt() {
    val noId =
      """<a href="https://anidb.app/anime/mystery-title" class="anime-card block group" title="Mystery">"""

    assertTrue(parseAnimeCards(noId).isEmpty())
  }

  @Test
  fun detailPage_yieldsSynopsisAndTheFactsPanel() {
    val detail =
      """
        <div class="relative max-h-24 overflow-hidden">
          <p class="text-sm text-faint leading-relaxed">Young Thorfinn grew up listening to the stories
          of old sailors. It&#039;s said to be warm and fertile.</p>
        </div>
        <dl>
          <dt class="text-faint">Type</dt><dd class="font-semibold">TV</dd>
          <dt class="text-faint">Status</dt><dd class="font-semibold">Finished Airing</dd>
          <dt class="text-faint">Season</dt><dd class="font-semibold"><a href="/browse">Summer 2019</a></dd>
          <dt class="text-faint">Score</dt><dd class="font-semibold">8.8</dd>
          <dt class="text-faint">Studios</dt><dd class="font-semibold">Wit Studio</dd>
        </dl>
      """.trimIndent()

    val details = parseAnimeDetails(detail)

    assertEquals(
      "Young Thorfinn grew up listening to the stories of old sailors. It's said to be warm and fertile.",
      details.synopsis,
    )
    assertEquals("TV", details.fact("Type"))
    // A fact whose value is a link keeps the text and drops the markup.
    assertEquals("Summer 2019", details.fact("Season"))
    assertEquals("Wit Studio", details.fact("Studios"))
    assertNull(details.fact("Demographic"))
  }

  @Test
  fun theRatingGate_catchesExplicitTitlesAndLeavesOrdinaryOnesAlone() {
    fun rated(rating: String) =
      parseAnimeDetails("<dl><dt>Rating</dt><dd>$rating</dd></dl>")

    // The one explicit grade on the site's scale, taken from a real adult title's facts panel.
    assertTrue(rated("Rx - Hentai").isAdult)
    // Violence and nudity in ordinary shows: these must keep playing.
    assertFalse(rated("R - 17+ (violence &amp; profanity)").isAdult)
    assertFalse(rated("R+ - Mild Nudity").isAdult)
    assertFalse(rated("PG-13 - Teens 13 or older").isAdult)
    assertFalse(rated("G - All Ages").isAdult)
    // A title whose page states no rating is not evidence of anything.
    assertFalse(parseAnimeDetails("<dl><dt>Type</dt><dd>TV</dd></dl>").isAdult)
  }

  @Test
  fun episodeList_keepsNumberingAndFillerMarks() {
    val episodes =
      parseAnimeEpisodes(
        """{"episodes":[{"id":62819,"number":1,"number2":null,"filler":false},
           {"id":62820,"number":2,"number2":null,"filler":true},
           {"id":0,"number":3,"filler":false}]}"""
      )

    assertEquals(2, episodes.size)
    assertEquals(62819, episodes[0].id)
    assertEquals(1, episodes[0].number)
    assertFalse(episodes[0].filler)
    assertTrue(episodes[1].filler)
  }

  @Test
  fun languages_carryTheirEmbedAndSayWhichIsSubtitled() {
    val languages =
      parseAnimeLanguages(
        """{"languages":[
             {"code":"eng","name":"English","embed_url":"https:\/\/anidb.app\/embed\/AAA"},
             {"code":"jpn","name":"Japanese","embed_url":"https:\/\/anidb.app\/embed\/BBB"},
             {"code":"broken","name":"Nowhere","embed_url":""}]}"""
      )

    assertEquals(listOf("English", "Japanese"), languages.map { it.name })
    assertEquals("https://anidb.app/embed/AAA", languages[0].embedUrl)
    assertFalse(languages[0].isSubtitled)
    assertTrue(languages[1].isSubtitled)
  }

  @Test
  fun embedPage_yieldsTheMasterPlaylist() {
    val embed =
      """
        var EPISODE_ID = 62819;
        var setup = {
            sources: [{ file: 'https://hls.anidb.app/stream/-bZt5jcecQKT9YYj/master.m3u8', type: 'hls' }],
            width: '100%',
        };
      """.trimIndent()

    assertEquals(
      "https://hls.anidb.app/stream/-bZt5jcecQKT9YYj/master.m3u8",
      parseEmbedStreamUrl(embed),
    )
    assertNull(parseEmbedStreamUrl("<html><body>Nothing to play here</body></html>"))
  }
}
