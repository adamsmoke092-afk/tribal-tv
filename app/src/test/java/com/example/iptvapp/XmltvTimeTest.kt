package com.example.iptvapp

import com.example.iptvapp.epg.parseXmltvInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class XmltvTimeTest {

    @Test
    fun `same instant in different offsets parses to the same epoch`() {
        val plus2 = parseXmltvInstant("20260101183000 +0200")
        val utc = parseXmltvInstant("20260101163000 +0000")
        assertNotNull(plus2)
        assertEquals(utc, plus2)
    }

    @Test
    fun `missing offset is treated as UTC`() {
        assertEquals(
            parseXmltvInstant("20260101163000 +0000"),
            parseXmltvInstant("20260101163000")
        )
    }

    @Test
    fun `offset with a colon is accepted`() {
        assertEquals(
            parseXmltvInstant("20260101183000 +0200"),
            parseXmltvInstant("20260101183000 +02:00")
        )
    }

    @Test
    fun `one hour apart differs by 3600000 ms`() {
        val a = parseXmltvInstant("20260101120000 +0000")
        val b = parseXmltvInstant("20260101130000 +0000")
        assertNotNull(a)
        assertNotNull(b)
        assertEquals(3_600_000L, b!! - a!!)
    }

    @Test
    fun `malformed input returns null`() {
        assertNull(parseXmltvInstant(null))
        assertNull(parseXmltvInstant(""))
        assertNull(parseXmltvInstant("not a date"))
        assertNull(parseXmltvInstant("2026"))
        assertNull(parseXmltvInstant("20260101183000 +9999")) // invalid offset
    }
}