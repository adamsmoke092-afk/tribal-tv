package com.example.iptvapp.movies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class VidSrcUrlBuilderTest {

    // ---- IMDb ID validation ----

    @Test
    fun `valid imdb id is accepted`() {
        assertTrue(VidSrcUrlBuilder.isValidImdbId("tt0111161"))
        assertTrue(VidSrcUrlBuilder.isValidImdbId("tt0000001"))
        assertTrue(VidSrcUrlBuilder.isValidImdbId("tt9999999"))
    }

    @Test
    fun `invalid imdb id is rejected`() {
        assertFalse(VidSrcUrlBuilder.isValidImdbId(""))
        assertFalse(VidSrcUrlBuilder.isValidImdbId("t0111161"))
        assertFalse(VidSrcUrlBuilder.isValidImdbId("tt"))
        assertFalse(VidSrcUrlBuilder.isValidImdbId("ttabc123"))
        assertFalse(VidSrcUrlBuilder.isValidImdbId("123456"))
        assertFalse(VidSrcUrlBuilder.isValidImdbId(null))
    }

    // ---- TMDB ID validation ----

    @Test
    fun `valid tmdb id is accepted`() {
        assertTrue(VidSrcUrlBuilder.isValidTmdbId("111161"))
        assertTrue(VidSrcUrlBuilder.isValidTmdbId("0"))
        assertTrue(VidSrcUrlBuilder.isValidTmdbId("999999999"))
    }

    @Test
    fun `invalid tmdb id is rejected`() {
        assertFalse(VidSrcUrlBuilder.isValidTmdbId(""))
        assertFalse(VidSrcUrlBuilder.isValidTmdbId("tt0111161"))
        assertFalse(VidSrcUrlBuilder.isValidTmdbId("abc123"))
        assertFalse(VidSrcUrlBuilder.isValidTmdbId("12-34"))
        assertFalse(VidSrcUrlBuilder.isValidTmdbId(null))
    }

    // ---- General ID validation ----

    @Test
    fun `general validation accepts both formats`() {
        assertTrue(VidSrcUrlBuilder.isValidId("tt0111161"))
        assertTrue(VidSrcUrlBuilder.isValidId("111161"))
        assertFalse(VidSrcUrlBuilder.isValidId("not an id"))
        assertFalse(VidSrcUrlBuilder.isValidId(""))
        assertFalse(VidSrcUrlBuilder.isValidId(null))
    }

    // ---- Movie embed URL construction ----

    @Test
    fun `movie embed url from imdb id`() {
        val url = VidSrcUrlBuilder.buildEmbedUrl(MediaType.MOVIE, "tt0111161")
        assertEquals("https://vidsrc.sh/embed/movie/tt0111161", url)
    }

    @Test
    fun `movie embed url from tmdb id`() {
        val url = VidSrcUrlBuilder.buildEmbedUrl(MediaType.MOVIE, "111161")
        assertEquals("https://vidsrc.sh/embed/movie/111161", url)
    }

    // ---- TV embed URL construction ----

    @Test
    fun `tv embed url from imdb id`() {
        val url = VidSrcUrlBuilder.buildEmbedUrl(MediaType.TV, "tt0944947")
        assertEquals("https://vidsrc.sh/embed/tv/tt0944947", url)
    }

    @Test
    fun `tv embed url from tmdb id`() {
        val url = VidSrcUrlBuilder.buildEmbedUrl(MediaType.TV, "62062")
        assertEquals("https://vidsrc.sh/embed/tv/62062", url)
    }

    // ---- Invalid input rejection ----

    @Test(expected = IllegalArgumentException::class)
    fun `buildEmbedUrl rejects invalid id string`() {
        VidSrcUrlBuilder.buildEmbedUrl(MediaType.MOVIE, "not-an-id")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `buildEmbedUrl rejects empty id`() {
        VidSrcUrlBuilder.buildEmbedUrl(MediaType.MOVIE, "")
    }

    // ---- Best ID selection ----

    @Test
    fun `bestId prefers imdb id when both present`() {
        val item = VidSrcItem(
            imdbId = "tt0111161",
            tmdbId = "111161",
            title = "Test",
            embedUrl = null,
            embedUrlTmdb = null,
            quality = null,
            timeAdded = null
        )
        assertEquals("tt0111161", VidSrcUrlBuilder.bestId(item))
    }

    @Test
    fun `bestId falls back to tmdb id when imdb missing`() {
        val item = VidSrcItem(
            imdbId = null,
            tmdbId = "111161",
            title = "Test",
            embedUrl = null,
            embedUrlTmdb = null,
            quality = null,
            timeAdded = null
        )
        assertEquals("111161", VidSrcUrlBuilder.bestId(item))
    }

    @Test
    fun `bestId returns null when neither id is valid`() {
        val item = VidSrcItem(
            imdbId = "invalid",
            tmdbId = "also-invalid",
            title = "Test",
            embedUrl = null,
            embedUrlTmdb = null,
            quality = null,
            timeAdded = null
        )
        assertEquals(null, VidSrcUrlBuilder.bestId(item))
    }
}

/**
 * Parsing tests for the real [VidSrcRepository.parseFeed]. Run under
 * Robolectric so `org.json` is the real implementation rather than the
 * mockable android.jar stub (which throws "not mocked" in plain JVM
 * unit tests).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VidSrcFeedParsingTest {

    @Test
    fun `feed with full items parses correctly`() {
        val feed = VidSrcRepository.parseFeed(
            """
            {
              "result": [
                {
                  "imdb_id": "tt0111161",
                  "tmdb_id": "111161",
                  "title": "The Shawshank Redemption",
                  "embed_url": "https://vidsrc.sh/embed/movie/tt0111161",
                  "embed_url_tmdb": "https://vidsrc.sh/embed/movie/111161",
                  "quality": "1080p",
                  "time_added": "2020-01-01"
                }
              ],
              "pages": 5
            }
            """.trimIndent()
        )

        assertEquals(1, feed.result.size)
        val item = feed.result.first()
        assertEquals("tt0111161", item.imdbId)
        assertEquals("111161", item.tmdbId)
        assertEquals("The Shawshank Redemption", item.title)
        assertEquals("1080p", item.quality)
        assertEquals("2020-01-01", item.timeAdded)
        assertEquals(5, feed.pages)
    }

    @Test
    fun `feed with missing optional fields handles gracefully`() {
        val feed = VidSrcRepository.parseFeed(
            """
            {
              "result": [ { "title": "Movie with minimal data" } ],
              "pages": 1
            }
            """.trimIndent()
        )

        assertEquals(1, feed.result.size)
        val item = feed.result.first()
        assertEquals("Movie with minimal data", item.title)
        assertEquals(null, item.imdbId)
        assertEquals(null, item.tmdbId)
        assertEquals(null, item.quality)
        assertEquals(1, feed.pages)
    }

    @Test
    fun `json null fields are treated as absent`() {
        val feed = VidSrcRepository.parseFeed(
            """
            {
              "result": [ { "imdb_id": null, "quality": null, "title": "X" } ],
              "pages": 1
            }
            """.trimIndent()
        )
        assertEquals(1, feed.result.size)
        assertEquals(null, feed.result.first().imdbId)
        assertEquals(null, feed.result.first().quality)
        assertEquals("X", feed.result.first().title)
    }

    @Test
    fun `empty result array is valid`() {
        val feed = VidSrcRepository.parseFeed("""{ "result": [], "pages": 1 }""")
        assertTrue(feed.result.isEmpty())
        assertEquals(1, feed.pages)
    }

    @Test
    fun `missing pages defaults to one`() {
        val feed = VidSrcRepository.parseFeed("""{ "result": [] }""")
        assertEquals(1, feed.pages)
    }
}
