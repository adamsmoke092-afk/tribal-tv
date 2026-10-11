package com.example.iptvapp

import com.example.iptvapp.playlist.Converters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `empty map round-trips to empty`() {
        assertEquals("", converters.fromHeaders(emptyMap()))
        assertTrue(converters.toHeaders("").isEmpty())
        assertTrue(converters.toHeaders(null).isEmpty())
    }

    @Test
    fun `headers round-trip losslessly including special characters`() {
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (X; Y=Z) & more",
            "Referer" to "http://ex.com/p?a=1&b=2"
        )
        val encoded = converters.fromHeaders(headers)
        val decoded = converters.toHeaders(encoded)
        assertEquals(headers, decoded)
    }

    @Test
    fun `single header round-trips`() {
        val headers = mapOf("User-Agent" to "Agent/1.0")
        assertEquals(headers, converters.toHeaders(converters.fromHeaders(headers)))
    }
}
