package com.example.iptvapp.movies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Parsing tests for [TmdbRepository]'s pure companion parsers. Run under
 * Robolectric so `org.json` is the real implementation rather than the
 * mockable android.jar stub (which throws "not mocked" on plain JVM).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TmdbParsingTest {

    @Test
    fun `movie page parses full item`() {
        val page = TmdbRepository.parseMoviePage(
            """
            {
              "page": 2,
              "total_pages": 10,
              "results": [
                {
                  "id": 550,
                  "title": "Fight Club",
                  "release_date": "1999-10-15",
                  "poster_path": "/p.jpg",
                  "vote_average": 8.4
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(2, page.page)
        assertEquals(10, page.totalPages)
        assertEquals(1, page.items.size)

        val item = page.items.first()
        assertEquals(550, item.tmdbId)
        assertEquals("Fight Club", item.title)
        assertEquals("1999", item.year)
        assertEquals("/p.jpg", item.posterPath)
        assertEquals(8.4, item.rating!!, 0.001)
        assertEquals(MediaType.MOVIE, item.mediaType)
    }

    @Test
    fun `tv page uses name and first air date`() {
        val page = TmdbRepository.parseTvPage(
            """
            {
              "page": 1,
              "total_pages": 3,
              "results": [
                {
                  "id": 1399,
                  "name": "Game of Thrones",
                  "first_air_date": "2011-04-17",
                  "poster_path": null,
                  "vote_average": 8.4
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(1, page.items.size)
        val item = page.items.first()
        assertEquals(1399, item.tmdbId)
        assertEquals("Game of Thrones", item.title)
        assertEquals("2011", item.year)
        assertNull(item.posterPath)
        assertEquals(MediaType.TV, item.mediaType)
    }

    @Test
    fun `page skips zero and missing ids`() {
        val page = TmdbRepository.parseMoviePage(
            """
            {
              "page": 1,
              "total_pages": 1,
              "results": [
                { "id": 0, "title": "Bad" },
                { "title": "No id" },
                { "id": 12, "title": "Good" }
              ]
            }
            """.trimIndent()
        )
        assertEquals(1, page.items.size)
        assertEquals(12, page.items.first().tmdbId)
    }

    @Test
    fun `missing optional fields become null`() {
        val page = TmdbRepository.parseMoviePage(
            """
            { "page": 1, "total_pages": 1, "results": [ { "id": 7, "title": "X" } ] }
            """.trimIndent()
        )
        val item = page.items.first()
        assertNull(item.year)
        assertNull(item.posterPath)
        assertNull(item.rating)
    }

    @Test
    fun `empty results is valid`() {
        val page = TmdbRepository.parseMoviePage(
            """{ "page": 1, "total_pages": 1, "results": [] }"""
        )
        assertTrue(page.items.isEmpty())
        assertEquals(1, page.page)
    }

    @Test
    fun `seasons parse and sort by number`() {
        val seasons = TmdbRepository.parseTvSeasons(
            """
            {
              "seasons": [
                { "season_number": 2, "name": "Season 2", "episode_count": 10 },
                { "season_number": 0, "name": "Specials", "episode_count": 3 },
                { "season_number": 1, "name": "", "episode_count": 10 }
              ]
            }
            """.trimIndent()
        )

        // Season 0 (Specials) is excluded so every value is a real,
        // embed-safe season number.
        assertEquals(listOf(1, 2), seasons.map { it.seasonNumber })
        assertEquals("Season 2", seasons[1].name)
        // Blank season name falls back to "Season N".
        assertEquals("Season 1", seasons[0].name)
        assertEquals(10, seasons[0].episodeCount)
    }

    @Test
    fun `episodes parse sort and handle null air date`() {
        val episodes = TmdbRepository.parseTvEpisodes(
            """
            {
              "episodes": [
                { "episode_number": 2, "name": "Second", "air_date": "2011-04-24" },
                { "episode_number": 1, "name": "First", "air_date": null },
                { "episode_number": 3, "name": "", "air_date": "2011-05-01" }
              ]
            }
            """.trimIndent()
        )

        assertEquals(listOf(1, 2, 3), episodes.map { it.episodeNumber })
        assertNull(episodes[0].airDate)
        assertEquals("2011-04-24", episodes[1].airDate)
        // Blank name falls back to "Episode N".
        assertEquals("Episode 3", episodes[2].name)
    }
}