package com.giztv.tv.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MyListStoreTest {
  private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
  private val store = MyListStore(context)

  private val show =
    LibraryItem(
      id = 94997,
      kind = LibraryKind.SHOW,
      title = "House of the Dragon",
      date = "2022-08-21",
      voteAverage = 8.4,
      overview = "A Targaryen story.",
      posterPath = "/poster.jpg",
    )

  @Before
  fun clearStore() {
    store.all().forEach { store.remove(it.kind, it.id) }
  }

  @Test
  fun toggle_addsThenRemoves() {
    assertTrue(store.toggle(show))
    assertTrue(store.contains(LibraryKind.SHOW, show.id))

    assertFalse(store.toggle(show))
    assertFalse(store.contains(LibraryKind.SHOW, show.id))
  }

  @Test
  fun savedItem_keepsTheFieldsNeededToRebuildTheCatalogModel() {
    store.add(show)

    val saved = store.all().single()
    assertEquals("House of the Dragon", saved.title)
    assertEquals("2022", saved.year)
    assertEquals("https://image.tmdb.org/t/p/w500/poster.jpg", saved.posterUrl)
    assertEquals(8.4, saved.voteAverage, 0.001)
  }

  @Test
  fun movieAndShowWithTheSameIdAreStoredSeparately() {
    store.add(show)
    store.add(show.copy(kind = LibraryKind.MOVIE, title = "Same Id Movie"))

    assertEquals(2, store.all().size)
    assertTrue(store.contains(LibraryKind.MOVIE, show.id))
    assertTrue(store.contains(LibraryKind.SHOW, show.id))
  }

  @Test
  fun all_returnsNewestFirst() {
    store.add(show.copy(id = 1, title = "Older", addedAtMs = 1_000L))
    store.add(show.copy(id = 2, title = "Newer", addedAtMs = 2_000L))

    assertEquals(listOf("Newer", "Older"), store.all().map { it.title })
  }
}
