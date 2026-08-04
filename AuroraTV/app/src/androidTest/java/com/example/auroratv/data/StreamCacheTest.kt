package com.example.auroratv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The expiry written into a signed address, and what to do when there is not one. */
class StreamCacheTest {
  private val now = 1_800_000_000_000L
  private val twentyMinutes = 20L * 60L * 1000L
  private val minute = 60_000L

  private fun expiry(url: String) = expiryOf(url, nowMs = now)

  @Test
  fun anExpiryInSecondsIsRead() {
    val expiresAt = now / 1000L + 3_600L

    assertEquals(expiresAt * 1000L - minute, expiry("https://cdn.example.com/x.m3u8?expires=$expiresAt"))
  }

  @Test
  fun anExpiryInMillisecondsIsRead() {
    val expiresAt = now + 3_600_000L

    assertEquals(expiresAt - minute, expiry("https://cdn.example.com/x.m3u8?exp=$expiresAt"))
  }

  /** A small number is a lifetime rather than a moment: seconds from now, not since 1970. */
  @Test
  fun aLifetimeIsCountedFromNow() {
    assertEquals(now + 3_600_000L - minute, expiry("https://cdn.example.com/x.m3u8?ttl=3600"))
  }

  @Test
  fun theShortNamesTheseHostsUseAreUnderstood() {
    val expiresAt = now / 1000L + 7_200L

    listOf("e", "st", "expire", "validto").forEach { key ->
      assertEquals(
        "key $key",
        expiresAt * 1000L - minute,
        expiry("https://cdn.example.com/x.m3u8?$key=$expiresAt&token=abc"),
      )
    }
  }

  /** Nothing to read: a short guess rather than trusting it forever. */
  @Test
  fun anAddressThatSaysNothingGetsAShortLife() {
    assertEquals(now + twentyMinutes, expiry("https://cdn.example.com/master.m3u8?token=abc"))
  }

  @Test
  fun rubbishInTheExpiryIsIgnored() {
    assertEquals(now + twentyMinutes, expiry("https://cdn.example.com/x.m3u8?expires=soon"))
    assertEquals(now + twentyMinutes, expiry("https://cdn.example.com/x.m3u8?expires=-5"))
    assertEquals(now + twentyMinutes, expiry("not a url at all"))
  }

  /** An expiry already gone says the reading was wrong, not that the stream is; guess instead. */
  @Test
  fun anExpiryAlreadyPastFallsBack() {
    val longAgo = now / 1000L - 10_000L

    assertEquals(now + twentyMinutes, expiry("https://cdn.example.com/x.m3u8?expires=$longAgo"))
  }

  /** A claim of a week is not believed; six hours is as far as this goes. */
  @Test
  fun anAbsurdlyDistantExpiryIsCapped() {
    val nextWeek = now / 1000L + 7L * 24L * 3_600L
    val capped = expiry("https://cdn.example.com/x.m3u8?expires=$nextWeek")

    assertEquals(now + 6L * 60L * 60L * 1000L, capped)
  }

  @Test
  fun everyAnswerIsInTheFuture() {
    listOf(
        "https://cdn.example.com/x.m3u8",
        "https://cdn.example.com/x.m3u8?expires=1",
        "https://cdn.example.com/x.m3u8?e=0",
        "https://cdn.example.com/x.m3u8?expires=99999999999999999",
      )
      .forEach { assertTrue("still in the past: $it", expiry(it) > now) }
  }
}
