package com.example.auroratv.ui.player

import android.view.ContextThemeWrapper
import android.content.pm.ActivityInfo
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import androidx.media3.common.MimeTypes
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.extractor.text.CuesWithTiming
import com.google.android.gms.cast.MediaTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
  fun playerControls_hideQuicklyOnPhoneAndRemainTvNavigable() {
    assertEquals(3_000, playerControllerTimeoutMs(isTelevision = false))
    assertEquals(5_000, playerControllerTimeoutMs(isTelevision = true))
    assertEquals(PlayerBackAction.CLOSE_SETTINGS, playerBackAction(settingsOpen = true, controlsVisible = true))
    assertEquals(PlayerBackAction.HIDE_CONTROLS, playerBackAction(settingsOpen = false, controlsVisible = true))
    assertEquals(PlayerBackAction.EXIT_PLAYER, playerBackAction(settingsOpen = false, controlsVisible = false))
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
  fun adaptiveHlsStream_exposesAndAppliesManualQualityChoices() {
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
      player = createHlsPlayer(instrumentation.targetContext, HlsStreamRequest(TEST_HLS_URL, emptyMap()))
    }

    try {
      instrumentation.runOnMainSync {
        assertEquals(640, player.trackSelectionParameters.maxVideoWidth)
        assertEquals(360, player.trackSelectionParameters.maxVideoHeight)

        applyAutomaticQualityPhase(player, AutomaticQualityPhase.BALANCED)
        assertEquals(1280, player.trackSelectionParameters.maxVideoWidth)
        assertEquals(720, player.trackSelectionParameters.maxVideoHeight)

        applyAutomaticQualityPhase(player, AutomaticQualityPhase.UNRESTRICTED)
        assertEquals(Int.MAX_VALUE, player.trackSelectionParameters.maxVideoWidth)
        assertEquals(Int.MAX_VALUE, player.trackSelectionParameters.maxVideoHeight)
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
  fun subtitleSync_shiftsCueTimingAndClipsAtVideoStart() {
    val cues = CuesWithTiming(emptyList(), 2_000_000L, 1_000_000L)

    val delayed = shiftSubtitleCues(cues, 500_000L)
    assertEquals(2_500_000L, delayed.startTimeUs)
    assertEquals(1_000_000L, delayed.durationUs)

    val advanced = shiftSubtitleCues(cues, -2_500_000L)
    assertEquals(0L, advanced.startTimeUs)
    assertEquals(500_000L, advanced.durationUs)
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
  fun subtitleSyncControls_useFineStepsAndClampAtTenSeconds() {
    assertEquals(-500L, adjustSubtitleSync(0L, -500L))
    assertEquals(100L, adjustSubtitleSync(0L, 100L))
    assertEquals(10_000L, adjustSubtitleSync(9_900L, 500L))
    assertEquals(-10_000L, adjustSubtitleSync(-9_900L, -500L))
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

  private companion object {
    const val TEST_HLS_URL = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
  }
}
