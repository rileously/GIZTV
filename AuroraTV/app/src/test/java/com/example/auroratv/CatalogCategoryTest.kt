package com.example.auroratv

import com.example.auroratv.ui.catalog.catalogCategories
import com.example.auroratv.ui.catalog.decadeCategory
import com.example.auroratv.ui.catalog.decadeStarts
import com.example.auroratv.ui.catalog.genreTopRatedCategories
import com.example.auroratv.ui.catalog.genreTopRatedCategory
import com.example.auroratv.ui.catalog.parseTopBilledActor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogCategoryTest {

  @Test
  fun decades_countBackFromTheOneInProgress() {
    assertEquals(listOf(2020, 2010, 2000, 1990), decadeStarts(2026))
    // The first year of a new decade starts the new one, not the last.
    assertEquals(listOf(2030, 2020, 2010, 2000), decadeStarts(2030))
    assertEquals(listOf(2020, 2010, 2000, 1990), decadeStarts(2029))
  }

  @Test
  fun aDecadeStillRunning_saysSoAndDoesNotAskForYearsThatHaveNotHappened() {
    val current = decadeCategory(2020, currentYear = 2026)
    assertEquals("Best of the 2020s so far", current.label)
    assertEquals("2020-01-01", current.movieParams["primary_release_date.gte"])
    // Not 2029: asking for the future returns nothing and dates the rail badly.
    assertEquals("2026-12-31", current.movieParams["primary_release_date.lte"])
  }

  @Test
  fun aFinishedDecade_coversAllTenYearsAndDropsTheQualifier() {
    val past = decadeCategory(2010, currentYear = 2026)
    assertEquals("Best of the 2010s", past.label)
    assertEquals("2010-01-01", past.movieParams["primary_release_date.gte"])
    assertEquals("2019-12-31", past.movieParams["primary_release_date.lte"])
    // Shows are dated by when they first aired rather than released.
    assertEquals("2010-01-01", past.showParams["first_air_date.gte"])
  }

  @Test
  fun ratingRails_demandEnoughVotesForTheAverageToMeanSomething() {
    val acclaimed = catalogCategories(currentYear = 2026).first { it.id == "acclaimed" }
    assertEquals("vote_average.desc", acclaimed.movieParams["sort_by"])
    // Without a floor this rail is films four people rated ten.
    assertTrue(acclaimed.movieParams.getValue("vote_count.gte").toInt() >= 1000)
    assertTrue(acclaimed.showParams.getValue("vote_count.gte").toInt() >= 100)
  }

  @Test
  fun everyRail_hasItsOwnIdentityAndBothKindsOfPath() {
    val categories = catalogCategories(currentYear = 2026)
    assertEquals(categories.size, categories.map { it.id }.distinct().size)
    assertEquals(categories.size, categories.map { it.label }.distinct().size)
    categories.forEach {
      assertTrue(it.label, it.moviePath.isNotBlank())
      assertTrue(it.label, it.showPath.isNotBlank())
    }
    // The year rail names the year it is actually showing.
    assertTrue(categories.any { it.label == "New in 2026" })
  }

  @Test
  fun genreRails_filterByGenreAndSortByRating() {
    val horror = genreTopRatedCategory("genre-horror", "Top Rated Horror", movieGenreId = 27)
    assertEquals("27", horror.movieParams["with_genres"])
    assertEquals("vote_average.desc", horror.movieParams["sort_by"])
    assertTrue(horror.movieParams.getValue("vote_count.gte").toInt() >= 1000)
    assertTrue(horror.standaloneLabel)
  }

  @Test
  fun catalogIncludesTopRatedGenreRails() {
    val labels = catalogCategories(currentYear = 2026).map { it.label }
    assertTrue(labels.contains("Top Rated Horror"))
    assertTrue(labels.contains("Top Rated Romance"))
    assertTrue(labels.contains("Top Rated Action"))
    assertEquals(10, genreTopRatedCategories().size)
  }

  @Test
  fun theBilledLead_isTakenFromTheCastAndOnlyFromPerformers() {
    val credits =
      """
      {"cast":[
        {"id":11,"name":"A Director","known_for_department":"Directing"},
        {"id":22,"name":"A Performer","known_for_department":"Acting"},
        {"id":33,"name":"Another Performer","known_for_department":"Acting"}
      ]}
      """
    val actor = parseTopBilledActor(credits)
    assertNotNull(actor)
    // The director tops the list but is not who "more with" should mean.
    assertEquals(22, actor!!.id)
    assertEquals("A Performer", actor.name)
  }

  @Test
  fun creditsWithNobodyUsable_yieldNoRailRatherThanAnEmptyOne() {
    assertNull(parseTopBilledActor("""{"cast":[]}"""))
    assertNull(parseTopBilledActor("""{}"""))
    assertNull(parseTopBilledActor("""{"cast":[{"id":0,"name":"","known_for_department":"Acting"}]}"""))
  }
}
