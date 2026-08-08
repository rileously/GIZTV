package com.example.auroratv

import com.example.auroratv.data.decodeBookmark
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookmarkStoreTest {

  @Test
  fun `decodes a saved page`() {
    val json =
      JSONObject()
        .put("url", "https://example.com/watch")
        .put("title", "Example")
        .put("savedAtMs", 1_700_000_000_000L)
        .toString()

    val bookmark = decodeBookmark(json)

    assertEquals("https://example.com/watch", bookmark?.url)
    assertEquals("Example", bookmark?.title)
    assertEquals(1_700_000_000_000L, bookmark?.savedAtMs)
  }

  @Test
  fun `an untitled page falls back to its url`() {
    val json = JSONObject().put("url", "https://example.com").put("title", "").toString()

    assertEquals("https://example.com", decodeBookmark(json)?.title)
  }

  @Test
  fun `a row without a url is dropped rather than listed blank`() {
    assertNull(decodeBookmark(JSONObject().put("title", "Nowhere").toString()))
  }

  @Test
  fun `malformed json is dropped rather than thrown`() {
    assertNull(decodeBookmark("not json at all"))
  }
}
