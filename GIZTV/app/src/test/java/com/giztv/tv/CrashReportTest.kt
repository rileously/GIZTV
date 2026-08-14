package com.giztv.tv

import com.giztv.tv.crash.CrashReport
import com.giztv.tv.crash.buildCrashReportBody
import com.giztv.tv.crash.crashHeadline
import com.giztv.tv.crash.describeCrash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportTest {

  @Test
  fun describe_carriesTheFaultAndTheDeviceButNotTheViewer() {
    val report = describeCrash(Thread.currentThread(), boom(), isTelevision = true)

    assertTrue(report.contains("GIZTV "))
    assertTrue(report.contains("Android "))
    assertTrue(report.contains("Device "))
    assertTrue(report.contains("television"))
    assertTrue(report.contains("IllegalStateException"))
    assertTrue(report.contains("the stream went away"))
  }

  @Test
  fun describe_marksAPhoneAsAPhone() {
    assertTrue(describeCrash(Thread.currentThread(), boom(), isTelevision = false).contains("phone"))
    assertFalse(
      describeCrash(Thread.currentThread(), boom(), isTelevision = false).contains("television")
    )
  }

  @Test
  fun describe_survivesAThrowableWithNoMessage() {
    val report = describeCrash(Thread.currentThread(), RuntimeException(), isTelevision = false)
    assertTrue(report.contains("RuntimeException"))
  }

  @Test
  fun headline_isTheFaultRatherThanTheHeader() {
    val report = CrashReport(savedAtMs = 0L, text = describeCrash(Thread.currentThread(), boom(), false))
    val headline = crashHeadline(report)

    assertTrue(headline.contains("IllegalStateException"))
    assertFalse(headline.startsWith("GIZTV "))
    assertFalse(headline.startsWith("Thread "))
  }

  @Test
  fun headline_fallsBackWhenTheReportIsNotOneOfOurs() {
    assertEquals(
      "GIZTV closed unexpectedly.",
      crashHeadline(CrashReport(savedAtMs = 0L, text = "")),
    )
  }

  @Test
  fun body_keepsEveryReportAndSeparatesThem() {
    val body =
      buildCrashReportBody(
        listOf(CrashReport(1L, "first fault"), CrashReport(2L, "second fault"))
      )

    assertTrue(body.contains("first fault"))
    assertTrue(body.contains("second fault"))
    assertTrue(body.contains("-".repeat(60)))
  }

  @Test
  fun body_ofASingleReportAddsNoSeparator() {
    assertEquals("only fault", buildCrashReportBody(listOf(CrashReport(1L, "only fault"))))
  }

  private fun boom(): Throwable = IllegalStateException("the stream went away")
}
