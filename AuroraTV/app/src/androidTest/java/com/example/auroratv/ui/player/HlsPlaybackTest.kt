package com.example.auroratv.ui.player

import android.app.NotificationManager
import android.view.ContextThemeWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.util.Rational
import androidx.media3.ui.PlayerNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertNotNull
import androidx.test.core.app.ActivityScenario
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.mediarouter.app.MediaRouteButton
import androidx.mediarouter.app.MediaRouteChooserDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.auroratv.R
import com.example.auroratv.MainActivity
import com.example.auroratv.gizTvOrientation
import com.example.auroratv.data.PlaybackContext
import com.example.auroratv.data.watchHistoryKey
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import androidx.media3.common.MimeTypes
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.NoSampleRenderer
import com.google.android.gms.cast.MediaTrack
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class HlsPlaybackTest {
  @Test
  fun orientationPolicy_keepsPhoneBrowserPortraitAndPlayerLandscape() {
    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
      gizTvOrientation(isTelevision = false, playerActive = false),
    )
    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
      gizTvOrientation(isTelevision = false, playerActive = true),
    )
    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
      gizTvOrientation(isTelevision = true, playerActive = false),
    )
  }

  @Test
  fun orientationPolicy_staysUprightForAVerticalShortDramaOnAPhone() {
    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
      gizTvOrientation(isTelevision = false, playerActive = true, verticalVideo = true),
    )
    // A television has nowhere to turn, so it stays landscape whatever the shape of the picture.
    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
      gizTvOrientation(isTelevision = true, playerActive = true, verticalVideo = true),
    )
  }

  @Test
  fun playerControls_hideQuicklyOnPhoneAndRemainTvNavigable() {
    assertEquals(3_000, playerControllerTimeoutMs(isTelevision = false))
    assertEquals(5_000, playerControllerTimeoutMs(isTelevision = true))
    assertEquals(PlayerBackAction.CLOSE_SETTINGS, playerBackAction(settingsOpen = true, controlsVisible = true))
    assertEquals(PlayerBackAction.HIDE_CONTROLS, playerBackAction(settingsOpen = false, controlsVisible = true))
    assertEquals(PlayerBackAction.EXIT_PLAYER, playerBackAction(settingsOpen = false, controlsVisible = false))
    assertEquals(
      PlayerBackAction.MINIMIZE_PLAYER,
      playerBackAction(settingsOpen = false, controlsVisible = false, canMinimize = true),
    )
  }

  @Test
  fun modernPlayerTimeline_formatsTimeAndClampsTenSecondSeeks() {
    assertEquals("00:00", formatPlayerTime(0L))
    assertEquals("03:07", formatPlayerTime(187_000L))
    assertEquals("1:02:03", formatPlayerTime(3_723_000L))
    assertEquals(0L, seekTargetPosition(currentPositionMs = 4_000L, deltaMs = -10_000L, durationMs = 600_000L))
    assertEquals(22_000L, seekTargetPosition(currentPositionMs = 12_000L, deltaMs = 10_000L, durationMs = 600_000L))
    assertEquals(600_000L, seekTargetPosition(currentPositionMs = 595_000L, deltaMs = 10_000L, durationMs = 600_000L))
  }

  @Test
  fun tvRemoteSeek_acceleratesWhileDirectionIsHeld() {
    assertEquals(10_000L, remoteSeekDeltaMs(direction = 1, repeatCount = 0))
    assertEquals(-10_000L, remoteSeekDeltaMs(direction = -1, repeatCount = 7))
    assertEquals(30_000L, remoteSeekDeltaMs(direction = 1, repeatCount = 8))
    assertEquals(-30_000L, remoteSeekDeltaMs(direction = -1, repeatCount = 17))
    assertEquals(60_000L, remoteSeekDeltaMs(direction = 1, repeatCount = 18))
  }

  @Test
  fun tvTouchSeek_mapsTimelineTouchesToPlaybackPositions() {
    assertEquals(0L, touchSeekPositionMs(x = 0f, trackWidthPx = 960, durationMs = 600_000L))
    assertEquals(300_000L, touchSeekPositionMs(x = 480f, trackWidthPx = 960, durationMs = 600_000L))
    assertEquals(600_000L, touchSeekPositionMs(x = 960f, trackWidthPx = 960, durationMs = 600_000L))
    // Drags that run past either end of the timeline clamp instead of overshooting.
    assertEquals(0L, touchSeekPositionMs(x = -220f, trackWidthPx = 960, durationMs = 600_000L))
    assertEquals(600_000L, touchSeekPositionMs(x = 1_400f, trackWidthPx = 960, durationMs = 600_000L))
    // An unmeasured track or an unknown duration must never seek somewhere arbitrary.
    assertEquals(0L, touchSeekPositionMs(x = 480f, trackWidthPx = 0, durationMs = 600_000L))
    assertEquals(0L, touchSeekPositionMs(x = 480f, trackWidthPx = 960, durationMs = 0L))
  }

  @Test
  fun pictureSize_defaultsToUncroppedFitAndOffersTvResizeModes() {
    assertEquals("Fit", VideoResizeOption.entries.first().label)
    assertEquals(
      androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT,
      VideoResizeOption.FIT.resizeMode,
    )
    assertTrue(VideoResizeOption.entries.any { it.label == "Zoom" })
    assertTrue(VideoResizeOption.entries.any { it.label == "Stretch" })
    assertTrue(VideoResizeOption.entries.map { it.resizeMode }.distinct().size == VideoResizeOption.entries.size)
  }

  @Test
  fun playbackProgress_usesMoviePageAcrossTemporaryStreamUrls() {
    val first =
      HlsStreamRequest(
        url = "https://cdn.example.com/video.m3u8?token=first",
        headers = emptyMap(),
        sourcePageUrl = "https://example.com/watch/movie/42#player",
      )
    val second =
      HlsStreamRequest(
        url = "https://other-cdn.example/video.m3u8?token=second",
        headers = emptyMap(),
        sourcePageUrl = "https://example.com/watch/movie/42",
      )

    assertEquals("https://example.com/watch/movie/42", playbackProgressIdentity(first))
    assertEquals(playbackProgressKey(first), playbackProgressKey(second))
  }

  @Test
  fun playbackProgress_survivesTheEmbeddedPlayerLandingSomewhereElseEachTime() {
    // The catalog builds the same page every time. Where the embedded player ends up by the time
    // it asks for the video does not repeat: a fresh session lands on a different URL, and keying
    // on that filed every play separately, so Continue watching offered a position the player
    // then never found and the title started from the beginning.
    val context =
      PlaybackContext(pageUrl = "https://vidfast.vc/movie/42?autoPlay=true", title = "A Movie")
    val firstPlay =
      HlsStreamRequest(
        url = "https://cdn.example.com/one.m3u8",
        headers = emptyMap(),
        sourcePageUrl = "https://player.example/embed?session=aaa",
        context = context,
      )
    val secondPlay =
      HlsStreamRequest(
        url = "https://cdn.example.com/two.m3u8",
        headers = emptyMap(),
        sourcePageUrl = "https://player.example/embed?session=bbb",
        context = context,
      )

    assertEquals("https://vidfast.vc/movie/42?autoPlay=true", playbackProgressIdentity(firstPlay))
    assertEquals(playbackProgressKey(firstPlay), playbackProgressKey(secondPlay))
    // The same identity Continue watching files it under, so the two agree on where it got to.
    assertEquals(watchHistoryKey(context.pageUrl), watchHistoryKey(playbackProgressIdentity(firstPlay)))
  }

  @Test
  fun playbackProgress_savesUsefulPositionAndClearsFinishedVideo() {
    assertEquals(
      125_000L,
      resumablePlaybackPosition(
        positionMs = 125_000L,
        durationMs = 600_000L,
        playbackState = Player.STATE_READY,
      ),
    )
    assertNull(
      resumablePlaybackPosition(
        positionMs = 3_000L,
        durationMs = 600_000L,
        playbackState = Player.STATE_READY,
      )
    )
    assertNull(
      resumablePlaybackPosition(
        positionMs = 595_000L,
        durationMs = 600_000L,
        playbackState = Player.STATE_READY,
      )
    )
    assertNull(
      resumablePlaybackPosition(
        positionMs = 125_000L,
        durationMs = 600_000L,
        playbackState = Player.STATE_ENDED,
      )
    )
  }

  @Test
  fun playbackProgress_persistsAcrossPlayerInstances() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val key = "test-${System.nanoTime()}"
    val firstStore = PlaybackProgressStore(context)
    firstStore.update(
      key = key,
      positionMs = 87_000L,
      durationMs = 600_000L,
      playbackState = Player.STATE_READY,
    )

    try {
      assertEquals(87_000L, PlaybackProgressStore(context).load(key))
    } finally {
      firstStore.clear(key)
    }
  }

  @Test
  fun playbackProgress_clearAllForgetsEveryResumePosition() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val firstKey = "clear-all-first-${System.nanoTime()}"
    val secondKey = "clear-all-second-${System.nanoTime()}"
    val store = PlaybackProgressStore(context)
    store.update(firstKey, 87_000L, 600_000L, Player.STATE_READY)
    store.update(secondKey, 125_000L, 600_000L, Player.STATE_READY)

    store.clearAll()

    assertEquals(0L, store.load(firstKey))
    assertEquals(0L, store.load(secondKey))
  }

  @Test
  fun appTheme_canCreateCastButtonWithoutCrashingPlayerScreen() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()

    instrumentation.runOnMainSync {
      val themedContext = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_AuroraTV)
      MediaRouteButton(themedContext)
    }
  }

  @Test
  fun appTheme_canOpenCastChooserWithoutCrashing() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        MediaRouteChooserDialog(activity).apply {
          show()
          dismiss()
        }
      }
    }
  }

  @Test
  fun adaptiveHlsStream_reachesReadyState() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val ready = CountDownLatch(1)
    val playbackError = AtomicReference<PlaybackException?>()
    lateinit var player: ExoPlayer

    instrumentation.runOnMainSync {
      player =
        createHlsPlayer(
          context = instrumentation.targetContext,
          request = HlsStreamRequest(TEST_HLS_URL, emptyMap()),
        )
      player.addListener(
        object : Player.Listener {
          override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) ready.countDown()
          }

          override fun onPlayerError(error: PlaybackException) {
            playbackError.set(error)
            ready.countDown()
          }
        }
      )
      player.prepare()
    }

    try {
      assertTrue("HLS stream did not become ready within 45 seconds", ready.await(45, TimeUnit.SECONDS))
      assertNull("HLS playback failed: ${playbackError.get()?.message}", playbackError.get())
    } finally {
      instrumentation.runOnMainSync { player.release() }
    }
  }

  @Test
  fun adaptiveHlsStream_appliesDataSaverAndManualQualityChoices() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val videoTracksReady = CountDownLatch(1)
    val playbackError = AtomicReference<PlaybackException?>()
    lateinit var player: ExoPlayer

    instrumentation.runOnMainSync {
      player = createHlsPlayer(instrumentation.targetContext, HlsStreamRequest(TEST_HLS_URL, emptyMap()))
      player.addListener(
        object : Player.Listener {
          override fun onTracksChanged(tracks: Tracks) {
            if (tracks.groups.any { it.type == C.TRACK_TYPE_VIDEO && it.isSupported }) videoTracksReady.countDown()
          }

          override fun onPlayerError(error: PlaybackException) {
            playbackError.set(error)
            videoTracksReady.countDown()
          }
        }
      )
      player.prepare()
    }

    try {
      assertTrue("Adaptive quality tracks were not available within 45 seconds", videoTracksReady.await(45, TimeUnit.SECONDS))
      assertNull("HLS playback failed: ${playbackError.get()?.message}", playbackError.get())
      instrumentation.runOnMainSync {
        val options = videoQualityOptions(player.currentTracks, compatibilityMode = false)
        assertTrue("No fixed video quality was exposed", options.any { !it.isAuto })
        val dataSaver = options.first { it.isStable }
        selectVideoQuality(player, dataSaver)
        assertTrue(
          "Data Saver did not pin the source's lowest video rendition",
          player.trackSelectionParameters.overrides.values.any { it.type == C.TRACK_TYPE_VIDEO },
        )
        val fixedQuality = options.first { !it.isAuto }
        selectVideoQuality(player, fixedQuality)
        assertTrue(
          "Selecting ${fixedQuality.label} did not create a video track override",
          player.trackSelectionParameters.overrides.values.any { it.type == C.TRACK_TYPE_VIDEO },
        )
      }
    } finally {
      instrumentation.runOnMainSync { player.release() }
    }
  }

  @Test
  fun nativePlayer_prefersEnglishAudioAndSubtitles() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    lateinit var player: ExoPlayer

    instrumentation.runOnMainSync {
      player =
        createHlsPlayer(
          context = instrumentation.targetContext,
          request = HlsStreamRequest(TEST_HLS_URL, emptyMap()),
        )
    }

    try {
      instrumentation.runOnMainSync {
        val parameters = player.trackSelectionParameters
        assertTrue("English is not a preferred audio language", "en" in parameters.preferredAudioLanguages)
        assertTrue("English is not a preferred subtitle language", "en" in parameters.preferredTextLanguages)
        assertTrue("Unlabelled subtitle fallback is disabled", parameters.selectUndeterminedTextLanguage)
      }
    } finally {
      instrumentation.runOnMainSync { player.release() }
    }
  }

  @Test
  fun subtitleChooser_canTurnCaptionsOffAndRestoreAutoEnglish() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    lateinit var player: ExoPlayer

    instrumentation.runOnMainSync {
      player = createHlsPlayer(instrumentation.targetContext, HlsStreamRequest(TEST_HLS_URL, emptyMap()))
      selectSubtitleTrack(player, SubtitleTrackOption("Off", disabled = true))
      assertTrue(C.TRACK_TYPE_TEXT in player.trackSelectionParameters.disabledTrackTypes)

      selectSubtitleTrack(player, SubtitleTrackOption("Auto English"))
      assertFalse(C.TRACK_TYPE_TEXT in player.trackSelectionParameters.disabledTrackTypes)
      assertTrue("English preference was not restored", "en" in player.trackSelectionParameters.preferredTextLanguages)
    }

    instrumentation.runOnMainSync { player.release() }
  }

  @Test
  fun adaptiveHlsStream_exposesAndAppliesManualAudioChoice() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val audioTracksReady = CountDownLatch(1)
    val playbackError = AtomicReference<PlaybackException?>()
    lateinit var player: ExoPlayer

    instrumentation.runOnMainSync {
      player = createHlsPlayer(instrumentation.targetContext, HlsStreamRequest(TEST_HLS_URL, emptyMap()))
      player.addListener(
        object : Player.Listener {
          override fun onTracksChanged(tracks: Tracks) {
            if (tracks.groups.any { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }) audioTracksReady.countDown()
          }

          override fun onPlayerError(error: PlaybackException) {
            playbackError.set(error)
            audioTracksReady.countDown()
          }
        }
      )
      player.prepare()
    }

    try {
      assertTrue("Audio tracks were not available within 45 seconds", audioTracksReady.await(45, TimeUnit.SECONDS))
      assertNull("HLS playback failed: ${playbackError.get()?.message}", playbackError.get())
      instrumentation.runOnMainSync {
        val options = audioTrackOptions(player.currentTracks)
        assertTrue("No manual audio choice was exposed", options.any { !it.isAutomatic })
        selectAudioTrack(player, options.first { !it.isAutomatic })
        assertTrue(
          "Selecting an audio track did not create an audio override",
          player.trackSelectionParameters.overrides.values.any { it.type == C.TRACK_TYPE_AUDIO },
        )
      }
    } finally {
      instrumentation.runOnMainSync { player.release() }
    }
  }

  @Test
  fun compatibilityMode_capsVideoWhileKeepingEnglishSubtitlePreference() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    lateinit var player: ExoPlayer

    instrumentation.runOnMainSync {
      player =
        createHlsPlayer(
          context = instrumentation.targetContext,
          request = HlsStreamRequest(TEST_HLS_URL, emptyMap()),
          compatibilityMode = true,
        )
    }

    try {
      instrumentation.runOnMainSync {
        val parameters = player.trackSelectionParameters
        assertEquals(1280, parameters.maxVideoWidth)
        assertEquals(720, parameters.maxVideoHeight)
        assertEquals(5_000_000, parameters.maxVideoBitrate)
        assertTrue("English preference was lost in compatibility mode", "en" in parameters.preferredTextLanguages)
      }
    } finally {
      instrumentation.runOnMainSync { player.release() }
    }
  }

  @Test
  fun automaticQuality_startsLowThenRemovesTheTemporaryCeiling() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    lateinit var player: ExoPlayer

    instrumentation.runOnMainSync {
      player =
        createHlsPlayer(
          instrumentation.targetContext,
          HlsStreamRequest(TEST_HLS_URL, emptyMap()),
          isTelevision = true,
        )
    }

    try {
      instrumentation.runOnMainSync {
        assertEquals(640, player.trackSelectionParameters.maxVideoWidth)
        assertEquals(360, player.trackSelectionParameters.maxVideoHeight)
        assertEquals(800_000, player.trackSelectionParameters.maxVideoBitrate)

        applyAutomaticQualityPhase(player, AutomaticQualityPhase.BALANCED)
        assertEquals(1280, player.trackSelectionParameters.maxVideoWidth)
        assertEquals(720, player.trackSelectionParameters.maxVideoHeight)
        assertEquals(Int.MAX_VALUE, player.trackSelectionParameters.maxVideoBitrate)

        applyAutomaticQualityPhase(player, AutomaticQualityPhase.UNRESTRICTED)
        assertEquals(Int.MAX_VALUE, player.trackSelectionParameters.maxVideoWidth)
        assertEquals(Int.MAX_VALUE, player.trackSelectionParameters.maxVideoHeight)
        assertEquals(Int.MAX_VALUE, player.trackSelectionParameters.maxVideoBitrate)

        applyAutomaticQualityPhase(
          player,
          AutomaticQualityPhase.UNRESTRICTED,
          isTelevision = false,
        )
        assertEquals(1280, player.trackSelectionParameters.maxVideoWidth)
        assertEquals(720, player.trackSelectionParameters.maxVideoHeight)
        assertEquals(3_000_000, player.trackSelectionParameters.maxVideoBitrate)
        assertEquals(30, player.trackSelectionParameters.maxVideoFrameRate)
      }
    } finally {
      instrumentation.runOnMainSync { player.release() }
    }
  }

  @Test
  fun mediaCodecVideoRendererFailure_isRecognizedForAutomaticRetry() {
    assertTrue(isVideoDecoderFailure(IllegalStateException("MediaCodecVideoRenderer error")))
    assertFalse(isVideoDecoderFailure(IllegalStateException("HTTP 403")))
  }

  @Test
  fun mediaItem_attachesSeparateEnglishSubtitle() {
    val item =
      createMediaItem(
        HlsStreamRequest(
          url = TEST_HLS_URL,
          headers = emptyMap(),
          subtitles =
            listOf(
              ExternalSubtitleTrack(
                url = "https://example.com/movie.en.vtt",
                label = "English",
                language = "en",
                mimeType = MimeTypes.TEXT_VTT,
              )
            ),
        )
      )

    val subtitles = requireNotNull(item.localConfiguration).subtitleConfigurations
    assertEquals(1, subtitles.size)
    assertEquals("en", subtitles.single().language)
    assertEquals(MimeTypes.TEXT_VTT, subtitles.single().mimeType)
  }

  @Test
  fun castConverter_includesAndSelectsSeparateEnglishSubtitle() {
    val item =
      createMediaItem(
        HlsStreamRequest(
          url = TEST_HLS_URL,
          headers = emptyMap(),
          subtitles =
            listOf(
              ExternalSubtitleTrack(
                url = "https://example.com/movie.en.vtt",
                label = "English",
                language = "en",
                mimeType = MimeTypes.TEXT_VTT,
              )
            ),
        )
      )

    val castItem = CastSubtitleMediaItemConverter().toMediaQueueItem(item)
    val track = requireNotNull(requireNotNull(castItem.media).mediaTracks).single()
    assertEquals(MediaTrack.TYPE_TEXT, track.type)
    assertEquals(MediaTrack.SUBTYPE_SUBTITLES, track.subtype)
    assertEquals("en", track.language)
    assertEquals("https://example.com/movie.en.vtt", track.contentId)
    assertTrue(track.id in requireNotNull(castItem.activeTrackIds))
  }

  @Test
  fun subtitleSync_movesTheRendererClockImmediatelyInBothDirections() {
    val recordingRenderer =
      object : NoSampleRenderer() {
        var renderedPositionUs = C.TIME_UNSET

        override fun getName(): String = "Recording text renderer"

        override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
          renderedPositionUs = positionUs
        }
      }
    val offsetMs = AtomicLong(500L)
    val renderer = SubtitleOffsetRenderer(recordingRenderer, offsetMs)

    renderer.render(2_000_000L, 0L)
    assertEquals(1_500_000L, recordingRenderer.renderedPositionUs)

    offsetMs.set(-500L)
    renderer.render(2_000_000L, 0L)
    assertEquals(2_500_000L, recordingRenderer.renderedPositionUs)

    offsetMs.set(MAX_SUBTITLE_SYNC_MS)
    renderer.render(2_000_000L, 0L)
    assertEquals(0L, recordingRenderer.renderedPositionUs)
  }

  @Test
  fun subtitleSync_refreshesAnAlreadyLoadedCueWithoutRestartingVideo() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val ready = CountDownLatch(1)
    val synchronizedCue = CountDownLatch(1)
    val playbackError = AtomicReference<PlaybackException?>()
    val visibleCueText = AtomicReference<List<String>>(emptyList())
    val offsetMs = AtomicLong(5_000L)
    lateinit var player: ExoPlayer

    instrumentation.runOnMainSync {
      player =
        createHlsPlayer(
          context = instrumentation.targetContext,
          request =
            HlsStreamRequest(
              url = TEST_HLS_URL,
              headers = emptyMap(),
              subtitles =
                listOf(
                  ExternalSubtitleTrack(
                    url = "asset:///subtitle-sync-test.vtt",
                    label = "English",
                    language = "en",
                    mimeType = MimeTypes.TEXT_VTT,
                  )
                ),
            ),
          subtitleOffset = offsetMs,
          startPositionMs = 7_000L,
        )
      player.addListener(
        object : Player.Listener {
          override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) ready.countDown()
          }

          override fun onCues(cueGroup: CueGroup) {
            val text = cueGroup.cues.mapNotNull { it.text?.toString() }
            visibleCueText.set(text)
            if ("Subtitle sync renderer test." in text) synchronizedCue.countDown()
          }

          override fun onPlayerError(error: PlaybackException) {
            playbackError.set(error)
            ready.countDown()
            synchronizedCue.countDown()
          }
        }
      )
      player.prepare()
    }

    try {
      assertTrue("Playback did not become ready", ready.await(45, TimeUnit.SECONDS))
      assertNull("Playback failed: ${playbackError.get()?.message}", playbackError.get())
      assertFalse("The +5s delay was ignored", "Subtitle sync renderer test." in visibleCueText.get())

      instrumentation.runOnMainSync {
        offsetMs.set(0L)
        refreshSubtitleRenderer(player)
      }

      assertTrue("The loaded cue did not update after sync changed", synchronizedCue.await(10, TimeUnit.SECONDS))
      assertNull("Playback failed after subtitle refresh", playbackError.get())
      val positionAfterRefresh = AtomicLong()
      instrumentation.runOnMainSync { positionAfterRefresh.set(player.currentPosition) }
      assertEquals(7_000L, positionAfterRefresh.get())
    } finally {
      instrumentation.runOnMainSync { player.release() }
    }
  }

  @Test
  fun subtitleSyncLabels_areClearForEarlierAndLaterTiming() {
    assertEquals("-0.5s", subtitleSyncLabel(-500L))
    assertEquals("0s", subtitleSyncLabel(0L))
    assertEquals("+0.5s", subtitleSyncLabel(500L))
    assertEquals("Captions use the source timing", subtitleSyncDescription(0L))
    assertEquals("Captions appear 0.5s earlier", subtitleSyncDescription(-500L))
    assertEquals("Captions appear 1.0s later", subtitleSyncDescription(1_000L))
  }

  @Test
  fun subtitleSyncControls_useFineStepsAndClampAtOneMinute() {
    assertEquals(-500L, adjustSubtitleSync(0L, -500L))
    assertEquals(100L, adjustSubtitleSync(0L, 100L))
    assertEquals(60_000L, adjustSubtitleSync(59_900L, 500L))
    assertEquals(-60_000L, adjustSubtitleSync(-59_900L, -500L))
  }

  @Test
  fun pictureInPicture_takesTheShapeOfTheVideoWithinTheSystemLimits() {
    assertEquals(Rational(16, 9), pictureInPictureAspectRatio(1920, 1080))
    // A short drama is shot 9:16 and the window follows it upright.
    assertEquals(Rational(9, 16), pictureInPictureAspectRatio(1080, 1920))
    // Nothing measured yet, so the window opens widescreen rather than being refused.
    assertEquals(Rational(16, 9), pictureInPictureAspectRatio(0, 0))
    // Beyond what the system accepts either way, nudged back to the limit.
    assertEquals(Rational(239, 100), pictureInPictureAspectRatio(3000, 500))
    assertEquals(Rational(100, 239), pictureInPictureAspectRatio(500, 3000))
  }

  @Test
  fun pictureInPicture_staysOutOfTheWayOfCastingAndFailedStreams() {
    assertTrue(
      shouldEnterPictureInPicture(
        supported = true,
        isCasting = false,
        hasError = false,
        playbackFinished = false,
      )
    )
    // The television is already showing it.
    assertFalse(
      shouldEnterPictureInPicture(
        supported = true,
        isCasting = true,
        hasError = false,
        playbackFinished = false,
      )
    )
    // Nothing to float: no picture, or the episode is over.
    assertFalse(
      shouldEnterPictureInPicture(
        supported = true,
        isCasting = false,
        hasError = true,
        playbackFinished = false,
      )
    )
    assertFalse(
      shouldEnterPictureInPicture(
        supported = true,
        isCasting = false,
        hasError = false,
        playbackFinished = true,
      )
    )
    assertFalse(
      shouldEnterPictureInPicture(
        supported = false,
        isCasting = false,
        hasError = false,
        playbackFinished = false,
      )
    )
  }

  @Test
  fun pictureInPicture_isOfferedOnPhonesAndLeftAloneOnTelevisions() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val packageManager = context.packageManager
    val isTelevision = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    val hasWindows = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    assertEquals(
      !isTelevision && hasWindows && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O,
      context.supportsPictureInPicture(),
    )
  }

  @Test
  fun subtitleSync_isStillThereAfterThePlayerCloses() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val request =
      HlsStreamRequest(
        url = "https://cdn.example.com/video.m3u8?token=first",
        headers = emptyMap(),
        sourcePageUrl = "https://example.com/watch/movie/77",
      )
    val key = subtitleSyncKey(request)
    SubtitleSyncStore(context).save(key, 0L)

    SubtitleSyncStore(context).save(key, -1_200L)
    // A fresh store stands in for the player being opened again later.
    assertEquals(-1_200L, SubtitleSyncStore(context).load(key))

    // Back on the source timing there is nothing left to remember.
    SubtitleSyncStore(context).save(key, 0L)
    assertEquals(0L, SubtitleSyncStore(context).load(key))
  }

  @Test
  fun subtitleSync_isSharedByEpisodesOfOneShowAndKeptApartFromOtherTitles() {
    val episodeOne = episodeRequest(showId = 12, episodeNumber = 1)
    val episodeTwo = episodeRequest(showId = 12, episodeNumber = 2)
    val otherShow = episodeRequest(showId = 13, episodeNumber = 1)
    val movie =
      HlsStreamRequest(
        url = "https://cdn.example.com/movie.m3u8",
        headers = emptyMap(),
        sourcePageUrl = "https://example.com/watch/movie/77",
      )

    assertEquals(subtitleSyncKey(episodeOne), subtitleSyncKey(episodeTwo))
    assertNotEquals(subtitleSyncKey(episodeOne), subtitleSyncKey(otherShow))
    assertNotEquals(subtitleSyncKey(episodeOne), subtitleSyncKey(movie))
  }

  private fun episodeRequest(showId: Int, episodeNumber: Int): HlsStreamRequest {
    val pageUrl = "https://example.com/watch/tv/$showId/1/$episodeNumber"
    return HlsStreamRequest(
      url = "https://cdn.example.com/episode-$episodeNumber.m3u8",
      headers = emptyMap(),
      sourcePageUrl = pageUrl,
      context =
        PlaybackContext(
          pageUrl = pageUrl,
          title = "Show $showId",
          showId = showId,
          seasonNumber = 1,
          episodeNumber = episodeNumber,
        ),
    )
  }

  @Test
  fun separateEnglishSubtitle_becomesSelectedDuringPlayback() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val textTrackSelected = CountDownLatch(1)
    val playbackError = AtomicReference<PlaybackException?>()
    lateinit var player: ExoPlayer

    instrumentation.runOnMainSync {
      player =
        createHlsPlayer(
          context = instrumentation.targetContext,
          request =
            HlsStreamRequest(
              url = TEST_HLS_URL,
              headers = emptyMap(),
              subtitles =
                listOf(
                  ExternalSubtitleTrack(
                    url = "asset:///external-english.vtt",
                    label = "English",
                    language = "en",
                    mimeType = MimeTypes.TEXT_VTT,
                  )
                ),
            ),
        )
      player.addListener(
        object : Player.Listener {
          override fun onTracksChanged(tracks: Tracks) {
            if (tracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.isSelected }) {
              textTrackSelected.countDown()
            }
          }

          override fun onPlayerError(error: PlaybackException) {
            playbackError.set(error)
            textTrackSelected.countDown()
          }
        }
      )
      player.prepare()
    }

    try {
      assertTrue("Separate subtitle was not selected within 45 seconds", textTrackSelected.await(45, TimeUnit.SECONDS))
      assertNull("Playback with separate subtitle failed: ${playbackError.get()?.message}", playbackError.get())
    } finally {
      instrumentation.runOnMainSync { player.release() }
    }
  }

  @Test
  fun mediaControls_areOfferedOnPhonesAndLeftOffTelevisions() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val isTelevision = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    assertEquals(!isTelevision, context.supportsMediaNotification())
  }

  @Test
  fun mediaControls_reachTheShadeOncePlaybackStarts() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    instrumentation.uiAutomation.grantRuntimePermission(
      context.packageName,
      "android.permission.POST_NOTIFICATIONS",
    )
    val notificationManager = context.getSystemService(NotificationManager::class.java)
    val artworkScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var player: ExoPlayer
    lateinit var notifications: PlayerNotificationManager
    var sessionToken: android.media.session.MediaSession.Token? = null

    instrumentation.runOnMainSync {
      player = createHlsPlayer(context, HlsStreamRequest(TEST_HLS_URL, emptyMap()))
      // The player the screen really hands over is wrapped for casting, and a session has to be
      // able to take that wrapper, not just a plain ExoPlayer.
      val castAware = createCastAwarePlayer(context, player, isTelevision = false)
      val session = createMediaSession(context, castAware, "giztv_test_session")
      sessionToken = session?.platformToken
      notifications =
        createMediaNotificationManager(
          context = context,
          title = "Big Buck Bunny",
          subtitle = "Season 1 · Episode 2",
          artworkUrl = null,
          artworkScope = artworkScope,
          onDismissed = {},
        )
      sessionToken?.let { notifications.setMediaSessionToken(it) }
      notifications.setPlayer(castAware)
      player.prepare()
      player.playWhenReady = true
    }

    try {
      assertNotNull("A media session could not be opened for the player", sessionToken)
      val posted = AtomicReference<android.app.Notification?>(null)
      val deadline = System.currentTimeMillis() + 45_000L
      while (System.currentTimeMillis() < deadline && posted.get() == null) {
        notificationManager.activeNotifications
          .firstOrNull { it.id == MEDIA_NOTIFICATION_ID }
          ?.let { posted.set(it.notification) }
        if (posted.get() == null) Thread.sleep(500L)
      }

      val notification = posted.get()
      assertNotNull("No media notification was posted within 45 seconds", notification)
      assertEquals(
        "Big Buck Bunny",
        notification?.extras?.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString(),
      )
      // Pause and play at minimum, so the shade is worth pulling down.
      assertTrue("The notification carried no controls", (notification?.actions?.size ?: 0) > 0)
    } finally {
      instrumentation.runOnMainSync {
        notifications.setPlayer(null)
        player.release()
      }
      artworkScope.cancel()
    }
  }

  private companion object {
    const val TEST_HLS_URL = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
  }
}
