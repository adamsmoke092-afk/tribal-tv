package com.example.iptvapp.movies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

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

/** JSON parsing tests for VidSrc feed responses. */
class VidSrcFeedParsingTest {

    @Test
    fun `feed with full items parses correctly`() {
        val json = JSONObject(
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

        val resultArray = json.optJSONArray("result") ?: org.json.JSONArray()
        assertEquals(1, resultArray.length())
        val obj = resultArray.optJSONObject(0)!!
        assertEquals("tt0111161", obj.optString("imdb_id"))
        assertEquals("111161", obj.optString("tmdb_id"))
        assertEquals("The Shawshank Redemption", obj.optString("title"))
        assertEquals("1080p", obj.optString("quality"))
        assertEquals(5, json.optInt("pages", 1))
    }

    @Test
    fun `feed with missing optional fields handles gracefully`() {
        val json = JSONObject(
            """
            {
              "result": [
                {
                  "title": "Movie with minimal data"
                }
              ],
              "pages": 1
            }
            """.trimIndent()
        )

        val resultArray = json.optJSONArray("result") ?: org.json.JSONArray()
        assertEquals(1, resultArray.length())
        val obj = resultArray.optJSONObject(0)!!
        assertEquals("Movie with minimal data", obj.optString("title"))
        assertEquals("", obj.optString("imdb_id", ""))
        assertEquals("", obj.optString("tmdb_id", ""))
        assertEquals(1, json.optInt("pages", 1))
    }

    @Test
    fun `empty result array is valid`() {
        val json = JSONObject(
            """
            {
              "result": [],
              "pages": 1
            }
            """.trimIndent()
        )
        val resultArray = json.optJSONArray("result") ?: org.json.JSONArray()
        assertEquals(0, resultArray.length())
    }
}
