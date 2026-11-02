package com.example.iptvapp.movies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VidSrcUrlBuilderTest {

    // ---- URL construction ----

    @Test
    fun `movie embed url`() {
        assertEquals(
            "https://vidsrc.wiki/embed/movie/550/",
            VidSrcUrlBuilder.buildMovieEmbedUrl(550)
        )
    }

    @Test
    fun `tv embed url includes season and episode`() {
        assertEquals(
            "https://vidsrc.wiki/embed/tv/1399/1/1/",
            VidSrcUrlBuilder.buildTvEmbedUrl(1399, 1, 1)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `movie rejects zero tmdb id`() {
        VidSrcUrlBuilder.buildMovieEmbedUrl(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `tv rejects zero season`() {
        VidSrcUrlBuilder.buildTvEmbedUrl(1399, 0, 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `tv rejects zero episode`() {
        VidSrcUrlBuilder.buildTvEmbedUrl(1399, 1, 0)
    }

    // ---- Validation: accepted ----

    @Test
    fun `built movie url is allowed`() {
        assertTrue(VidSrcUrlBuilder.isAllowedEmbedUrl("https://vidsrc.wiki/embed/movie/550/"))
    }

    @Test
    fun `movie url without trailing slash is allowed`() {
        assertTrue(VidSrcUrlBuilder.isAllowedEmbedUrl("https://vidsrc.wiki/embed/movie/550"))
    }

    @Test
    fun `built tv url is allowed`() {
        assertTrue(VidSrcUrlBuilder.isAllowedEmbedUrl("https://vidsrc.wiki/embed/tv/1399/1/1/"))
    }

    // ---- Validation: rejected ----

    @Test
    fun `http is rejected`() {
        assertFalse(VidSrcUrlBuilder.isAllowedEmbedUrl("http://vidsrc.wiki/embed/movie/550/"))
    }

    @Test
    fun `wrong host is rejected`() {
        assertFalse(VidSrcUrlBuilder.isAllowedEmbedUrl("https://evil.com/embed/movie/550/"))
    }

    @Test
    fun `host suffix attack is rejected`() {
        assertFalse(
            VidSrcUrlBuilder.isAllowedEmbedUrl("https://vidsrc.wiki.evil.com/embed/movie/550/")
        )
    }

    @Test
    fun `user info is rejected`() {
        assertFalse(
            VidSrcUrlBuilder.isAllowedEmbedUrl("https://user@vidsrc.wiki/embed/movie/550/")
        )
    }

    @Test
    fun `explicit port is rejected`() {
        assertFalse(
            VidSrcUrlBuilder.isAllowedEmbedUrl("https://vidsrc.wiki:8443/embed/movie/550/")
        )
    }

    @Test
    fun `query is rejected`() {
        assertFalse(
            VidSrcUrlBuilder.isAllowedEmbedUrl("https://vidsrc.wiki/embed/movie/550/?x=1")
        )
    }

    @Test
    fun `fragment is rejected`() {
        assertFalse(
            VidSrcUrlBuilder.isAllowedEmbedUrl("https://vidsrc.wiki/embed/movie/550/#x")
        )
    }

    @Test
    fun `non numeric id is rejected`() {
        assertFalse(
            VidSrcUrlBuilder.isAllowedEmbedUrl("https://vidsrc.wiki/embed/movie/tt550/")
        )
    }

    @Test
    fun `extra movie path segment is rejected`() {
        assertFalse(
            VidSrcUrlBuilder.isAllowedEmbedUrl("https://vidsrc.wiki/embed/movie/550/1/")
        )
    }

    @Test
    fun `tv without episode is rejected`() {
        assertFalse(
            VidSrcUrlBuilder.isAllowedEmbedUrl("https://vidsrc.wiki/embed/tv/1399/1/")
        )
    }

    @Test
    fun `garbage is rejected`() {
        assertFalse(VidSrcUrlBuilder.isAllowedEmbedUrl("not a url"))
    }
}