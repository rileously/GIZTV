package com.giztv.tv

import com.giztv.tv.ui.player.isEnglishSubtitleLabel
import com.giztv.tv.ui.player.isHearingImpairedSubtitleLabel
import com.giztv.tv.ui.player.preferredEnglishSubtitleIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferredEnglishSubtitleTest {

  @Test
  fun autoEnglish_keepsTheCatalogsFirstEnglishWhenTwoExist() {
    val labels = listOf("English", "English", "Spanish")
    val index =
      preferredEnglishSubtitleIndex(
        count = labels.size,
        isEnglish = { isEnglishSubtitleLabel(labels[it], null) },
        isHearingImpaired = { isHearingImpairedSubtitleLabel(labels[it]) },
      )
    assertEquals(0, index)
  }

  @Test
  fun autoEnglish_fallsBackToSoleEnglishTrack() {
    val labels = listOf("Spanish", "English", "French")
    val index =
      preferredEnglishSubtitleIndex(
        count = labels.size,
        isEnglish = { index ->
          isEnglishSubtitleLabel(labels[index], language = if (labels[index] == "English") "en" else null)
        },
        isHearingImpaired = { false },
      )
    assertEquals(1, index)
  }

  @Test
  fun autoEnglish_skipsHearingImpairedWhenAPlainEnglishAlternateExists() {
    val labels = listOf("English Hi", "English", "English")
    val index =
      preferredEnglishSubtitleIndex(
        count = labels.size,
        isEnglish = { isEnglishSubtitleLabel(labels[it], null) },
        isHearingImpaired = { isHearingImpairedSubtitleLabel(labels[it]) },
      )
    assertEquals(1, index)
  }

  @Test
  fun autoEnglish_returnsNullWhenNoEnglishTracks() {
    val labels = listOf("Spanish", "French")
    assertNull(
      preferredEnglishSubtitleIndex(
        count = labels.size,
        isEnglish = { isEnglishSubtitleLabel(labels[it], null) },
        isHearingImpaired = { false },
      )
    )
  }

  @Test
  fun englishLabelDetection_acceptsLanguageCodesAndNames() {
    assertTrue(isEnglishSubtitleLabel("English", null))
    assertTrue(isEnglishSubtitleLabel(null, "en"))
    assertTrue(isEnglishSubtitleLabel(null, "eng"))
    assertTrue(isEnglishSubtitleLabel("English Hi2", "und"))
  }
}
