package com.example.auroratv

import com.example.auroratv.ui.detailHistoryAfterBack
import com.example.auroratv.ui.detailHistoryAfterOpen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailNavigationTest {
  @Test
  fun backUnwindsNestedMoviePersonAndDirectorPagesBeforeTheCatalog() {
    var history = detailHistoryAfterOpen<String>(emptyList(), current = null, fromCatalog = true)

    history = detailHistoryAfterOpen(history, current = "Movie A", fromCatalog = false)
    history = detailHistoryAfterOpen(history, current = "Movie B", fromCatalog = false)
    history = detailHistoryAfterOpen(history, current = "Actor / director", fromCatalog = false)

    var back = detailHistoryAfterBack(history)
    assertEquals("Actor / director", back.second)

    back = detailHistoryAfterBack(back.first)
    assertEquals("Movie B", back.second)

    back = detailHistoryAfterBack(back.first)
    assertEquals("Movie A", back.second)

    back = detailHistoryAfterBack(back.first)
    assertNull(back.second)
    assertEquals(emptyList<String>(), back.first)
  }

  @Test
  fun openingFromCatalogDropsAnOldDetailHistory() {
    assertEquals(
      emptyList<String>(),
      detailHistoryAfterOpen(listOf("Old movie", "Old actor"), "Old detail", fromCatalog = true),
    )
  }
}
