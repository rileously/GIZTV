package com.example.auroratv

import com.example.auroratv.link.LinkCommand
import com.example.auroratv.link.LinkEvent
import com.example.auroratv.link.LinkKey
import com.example.auroratv.link.PAIRING_CODE_LENGTH
import com.example.auroratv.link.RemoteOptionGroup
import com.example.auroratv.link.RemoteOptionItem
import com.example.auroratv.link.constantTimeEquals
import com.example.auroratv.link.decodeCommand
import com.example.auroratv.link.decodeEvent
import com.example.auroratv.link.encode
import com.example.auroratv.link.newPairingCode
import com.example.auroratv.link.newPairingToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkProtocolTest {

  private fun roundTrip(command: LinkCommand) = decodeCommand(command.encode())

  @Test
  fun everyCommandSurvivesTheWire() {
    val commands =
      listOf(
        LinkCommand.Hello(token = "abc123", name = "Pixel 8"),
        LinkCommand.Pair(code = "482913", name = "Pixel 8"),
        LinkCommand.Play(
          pageUrl = "https://vidfast.pro/movie/1234",
          title = "The Odyssey",
          subtitle = "2026",
          posterUrl = "https://image.tmdb.org/t/p/w300/x.jpg",
          positionMs = 4_930_000L,
        ),
        LinkCommand.Pause,
        LinkCommand.Resume,
        LinkCommand.Stop,
        LinkCommand.Seek(positionMs = 90_000L),
        LinkCommand.SeekBy(deltaMs = -10_000L),
        LinkCommand.Volume(delta = -1),
        LinkCommand.SetVolume(percent = 40),
        LinkCommand.Key(LinkKey.CENTER),
        LinkCommand.Key(LinkKey.BACKSPACE),
        LinkCommand.Text(text = "the odyssey"),
        LinkCommand.SelectOption(groupId = "subtitle", itemId = "English"),
        LinkCommand.SubtitleSync(deltaMs = -500L),
      )

    commands.forEach { assertEquals(it, roundTrip(it)) }
  }

  @Test
  fun everyEventSurvivesTheWire() {
    val events =
      listOf(
        LinkEvent.PairingRequired,
        LinkEvent.Paired(token = "deadbeef"),
        LinkEvent.Refused(reason = "Wrong code"),
        LinkEvent.Options(
          groups =
            listOf(
              RemoteOptionGroup(
                id = "subtitle",
                label = "Subtitles",
                items =
                  listOf(
                    RemoteOptionItem("English", "English", true),
                    RemoteOptionItem("Off", "Off", false),
                  ),
              )
            ),
          subtitleOffsetMs = -1_500L,
        ),
        LinkEvent.State(
          playing = true,
          title = "The Odyssey",
          subtitle = "2026",
          posterUrl = "https://image.tmdb.org/t/p/w300/x.jpg",
          positionMs = 61_000L,
          durationMs = 9_935_000L,
          volume = 35,
        ),
      )

    events.forEach { assertEquals(it, decodeEvent(it.encode())) }
  }

  @Test
  fun aStateWithNothingPlayingKeepsItsEmptyFields() {
    val idle =
      LinkEvent.State(
        playing = false,
        title = null,
        subtitle = null,
        posterUrl = null,
        positionMs = 0L,
        durationMs = 0L,
      )

    assertEquals(idle, decodeEvent(idle.encode()))
  }

  /** A television must survive anything a phone sends it, including a phone that is not GIZTV. */
  @Test
  fun rubbishIsIgnoredRatherThanThrown() {
    listOf(
        "",
        "not json at all",
        "{}",
        "[1,2,3]",
        """{"v":1}""",
        """{"v":1,"cmd":"nonsense"}""",
        """{"v":1,"cmd":"key","key":"eject"}""",
        """{"v":1,"cmd":"play"}""",
        """{"v":1,"cmd":"hello"}""",
        """{"v":1,"cmd":"pair"}""",
      )
      .forEach { assertNull("should not decode: $it", decodeCommand(it)) }
  }

  @Test
  fun aDifferentProtocolVersionIsNotGuessedAt() {
    assertNull(decodeCommand("""{"v":99,"cmd":"pause"}"""))
    assertNull(decodeEvent("""{"v":99,"evt":"needPair"}"""))
  }

  @Test
  fun typingIsCappedSoOneMessageCannotBeAnEssay() {
    val long = decodeCommand(LinkCommand.Text("x".repeat(5_000)).encode()) as LinkCommand.Text

    assertTrue(long.text.length <= 256)
  }

  @Test
  fun volumeStepsAreClampedToSomethingASpeakerCanSurvive() {
    val loud = decodeCommand("""{"v":1,"cmd":"volume","delta":9999}""") as LinkCommand.Volume

    assertEquals(10, loud.delta)
  }

  @Test
  fun aNegativeSeekIsClampedToTheStart() {
    val rewound = decodeCommand("""{"v":1,"cmd":"seek","positionMs":-5000}""") as LinkCommand.Seek

    assertEquals(0L, rewound.positionMs)
  }

  @Test
  fun anAbsoluteVolumeIsClampedToAPercentage() {
    val loud = decodeCommand("""{"v":1,"cmd":"setVolume","percent":400}""") as LinkCommand.SetVolume
    val quiet = decodeCommand("""{"v":1,"cmd":"setVolume","percent":-8}""") as LinkCommand.SetVolume

    assertEquals(100, loud.percent)
    assertEquals(0, quiet.percent)
  }

  /** An empty list is a real answer: a stream with no subtitles has no subtitle row. */
  @Test
  fun optionsWithNoGroupsSurviveTheWire() {
    val bare = LinkEvent.Options(groups = emptyList(), subtitleOffsetMs = 0L)

    assertEquals(bare, decodeEvent(bare.encode()))
  }

  @Test
  fun anAbsurdSubtitleOffsetIsClamped() {
    val far = decodeCommand("""{"v":1,"cmd":"subtitleSync","deltaMs":900000}""") as LinkCommand.SubtitleSync

    assertEquals(60_000L, far.deltaMs)
  }

  /** A title handed over with nothing but a page still opens, named after the page. */
  @Test
  fun aHandoverWithoutDetailFallsBackToThePage() {
    val bare = decodeCommand("""{"v":1,"cmd":"play","pageUrl":"https://example.com/x"}""") as LinkCommand.Play

    assertEquals("https://example.com/x", bare.pageUrl)
    assertEquals("https://example.com/x", bare.title)
    assertEquals(0L, bare.positionMs)
  }

  @Test
  fun aHandoverCannotArriveAtANegativePosition() {
    val odd = decodeCommand("""{"v":1,"cmd":"play","pageUrl":"https://example.com/x","positionMs":-9}""") as LinkCommand.Play

    assertEquals(0L, odd.positionMs)
  }

  @Test
  fun aPairingCodeIsSixDigitsAndNotAlwaysTheSame() {
    val codes = (1..50).map { newPairingCode() }

    codes.forEach {
      assertEquals(PAIRING_CODE_LENGTH, it.length)
      assertTrue("not digits: $it", it.all(Char::isDigit))
    }
    assertTrue("a fixed code would not be a secret", codes.toSet().size > 1)
  }

  @Test
  fun tokensAreLongAndDistinct() {
    val first = newPairingToken()
    val second = newPairingToken()

    assertEquals(48, first.length)
    assertNotEquals(first, second)
  }

  @Test
  fun tokenComparisonAcceptsOnlyAnExactMatch() {
    // Fixed rather than generated: a random token ending in the same character the test appends
    // would make a correct comparison look wrong once every sixteen runs.
    val token = "a".repeat(48)

    assertTrue(constantTimeEquals(token, token))
    assertFalse(constantTimeEquals(token, token.dropLast(1)))
    assertFalse(constantTimeEquals(token, token.dropLast(1) + "b"))
    assertFalse(constantTimeEquals("b" + token.drop(1), token))
    assertFalse(constantTimeEquals("", token))
  }
}
