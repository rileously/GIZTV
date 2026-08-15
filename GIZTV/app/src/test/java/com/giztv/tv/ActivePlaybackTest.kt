package com.giztv.tv

import com.giztv.tv.ui.player.ActivePlayback
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Something that remembers being told to stop, standing in for a player or a resolver page. */
private class RecordingSource : ActivePlayback.Source {
  var silencedTimes = 0
    private set

  override fun silence() {
    silencedTimes += 1
  }
}

class ActivePlaybackTest {
  private val registered = mutableListOf<ActivePlayback.Source>()

  private fun register(source: RecordingSource): RecordingSource {
    ActivePlayback.register(source)
    registered += source
    return source
  }

  @After
  fun clearRegistry() {
    registered.forEach(ActivePlayback::unregister)
    registered.clear()
  }

  @Test
  fun playingSilencesEverythingElseThatCouldBeHeard() {
    val player = register(RecordingSource())
    val resolverPage = register(RecordingSource())
    val prefetcher = register(RecordingSource())

    ActivePlayback.claim(player)

    assertEquals(0, player.silencedTimes)
    assertEquals(1, resolverPage.silencedTimes)
    assertEquals(1, prefetcher.silencedTimes)
  }

  @Test
  fun aPageThatTakesOverSilencesThePlayer() {
    val player = register(RecordingSource())
    val webPage = register(RecordingSource())

    ActivePlayback.claim(player)
    ActivePlayback.claim(webPage)

    assertEquals(1, player.silencedTimes)
    assertEquals(1, webPage.silencedTimes)
  }

  @Test
  fun somethingThatHasLeftIsNeverToldAnything() {
    val player = register(RecordingSource())
    val goneAway = RecordingSource()
    ActivePlayback.register(goneAway)
    ActivePlayback.unregister(goneAway)

    ActivePlayback.claim(player)

    assertEquals(0, goneAway.silencedTimes)
  }

  @Test
  fun registeringTwiceStillSilencesOnce() {
    val player = register(RecordingSource())
    val resolverPage = register(RecordingSource())
    ActivePlayback.register(resolverPage)

    ActivePlayback.claim(player)

    assertEquals(1, resolverPage.silencedTimes)
  }

  @Test
  fun silencingIsFreeToUnregisterWhateverItSilences() {
    val player = register(RecordingSource())
    lateinit var self: ActivePlayback.Source
    var silenced = false
    self =
      ActivePlayback.Source {
        silenced = true
        ActivePlayback.unregister(self)
      }
    ActivePlayback.register(self)
    registered += self

    ActivePlayback.claim(player)

    assertTrue(silenced)
  }
}
