package com.example.auroratv

import com.example.auroratv.ui.player.isPromotionalSubtitleCue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleAdFilterTest {

  @Test
  fun aTracksOwnAdvertising_isKeptOffThePicture() {
    // The one actually seen rendering over a film.
    assertTrue(
      isPromotionalSubtitleCue("Visit hoofoot.ru to watch all sports livestream and highlights for free")
    )
    assertTrue(isPromotionalSubtitleCue("Watch free movies at example-site.com"))
    assertTrue(isPromotionalSubtitleCue("Download subtitles from opensubtitles.org"))
    assertTrue(isPromotionalSubtitleCue("Join our telegram channel t.me/somechannel"))
  }

  @Test
  fun aNakedLink_isNeverDialogue() {
    assertTrue(isPromotionalSubtitleCue("https://some-streaming-site.example"))
    assertTrue(isPromotionalSubtitleCue("www.some-streaming-site.example"))
    assertTrue(isPromotionalSubtitleCue("Support us at http://example.org/donate"))
  }

  @Test
  fun theUsualSubtitleHouseCredits_areRecognisedWithoutAnAddress() {
    assertTrue(isPromotionalSubtitleCue("Subtitles by ExampleTeam"))
    assertTrue(isPromotionalSubtitleCue("Sync by someone"))
    assertTrue(isPromotionalSubtitleCue("Corrected by a volunteer"))
    assertTrue(isPromotionalSubtitleCue("Advertise your product or brand here"))
  }

  @Test
  fun markupAroundAnAdvert_doesNotHideItFromTheFilter() {
    assertTrue(isPromotionalSubtitleCue("<i>Visit example-site.com to watch free</i>"))
    assertTrue(isPromotionalSubtitleCue("{\\an8}Visit example-site.com for free movies"))
  }

  @Test
  fun ordinaryDialogue_isLeftAlone() {
    assertFalse(isPromotionalSubtitleCue("I'm not going to watch you die."))
    assertFalse(isPromotionalSubtitleCue("It was free, so I took it."))
    assertFalse(isPromotionalSubtitleCue("Get down!"))
    assertFalse(isPromotionalSubtitleCue("Support is on the way."))
    assertFalse(isPromotionalSubtitleCue("- Where are we going?\n- Downtown."))
    assertFalse(isPromotionalSubtitleCue(null))
    assertFalse(isPromotionalSubtitleCue(""))
  }

  @Test
  fun dialogueThatMerelyNamesAWebsite_survives() {
    // Dropping a real line is worse than leaving an advert up, so an address alone is not enough.
    assertFalse(isPromotionalSubtitleCue("She works at Initech.com now."))
    assertFalse(isPromotionalSubtitleCue("The company is called Umbrella.net."))
  }

  @Test
  fun sentencesThatRanTogether_areNotMistakenForAddresses() {
    // A missing space after a full stop must not read as a domain.
    assertFalse(isPromotionalSubtitleCue("Let's go.To the car, now!"))
    assertFalse(isPromotionalSubtitleCue("I saw it.Me too."))
    assertFalse(isPromotionalSubtitleCue("No.Stream of consciousness, that's all."))
  }
}
