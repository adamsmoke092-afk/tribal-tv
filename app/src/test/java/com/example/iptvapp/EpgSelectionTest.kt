package com.example.iptvapp

import com.example.iptvapp.epg.EpgProgramme
import com.example.iptvapp.epg.selectNowNext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpgSelectionTest {

    private fun p(
        start: Long,
        stop: Long,
        title: String,
        source: String = "guide-a"
    ) = EpgProgramme(
        source = source,
        channelId = "c",
        startMs = start,
        stopMs = stop,
        title = title
    )

    @Test
    fun `picks the programme containing now and the next one after it`() {
        val programmes = listOf(
            p(0, 100, "A"),
            p(100, 200, "B"),
            p(200, 300, "C")
        )
        val nn = selectNowNext(programmes, nowMs = 150)
        assertEquals("B", nn.current?.title)
        assertEquals("C", nn.next?.title)
    }

    @Test
    fun `exact start of a programme counts as current, and it is not also next`() {
        val programmes = listOf(
            p(100, 200, "B"),
            p(200, 300, "C")
        )
        val nn = selectNowNext(programmes, nowMs = 200)
        assertEquals("C", nn.current?.title)
        assertEquals(null, nn.next) // nothing starts strictly after 200
    }

    @Test
    fun `past end of the last programme gives no current and no next`() {
        val programmes = listOf(p(0, 100, "A"))
        val nn = selectNowNext(programmes, nowMs = 100)
        assertNull(nn.current)
        assertNull(nn.next)
    }

    @Test
    fun `in a gap there is no current but the next programme is returned`() {
        val programmes = listOf(
            p(0, 100, "A"),
            p(300, 400, "B")
        )
        val nn = selectNowNext(programmes, nowMs = 200)
        assertNull(nn.current)
        assertEquals("B", nn.next?.title)
    }

    @Test
    fun `unsorted input still resolves correctly`() {
        val programmes = listOf(
            p(200, 300, "C"),
            p(0, 100, "A"),
            p(100, 200, "B")
        )
        val nn = selectNowNext(programmes, nowMs = 150)
        assertEquals("B", nn.current?.title)
        assertEquals("C", nn.next?.title)
    }

    @Test
    fun `empty list yields nulls`() {
        val nn = selectNowNext(emptyList(), nowMs = 12345)
        assertNull(nn.current)
        assertNull(nn.next)
    }

    // ---- two guides describing the same channel ----

    @Test
    fun `duplicate channel and start time across sources resolves deterministically`() {
        val fromA = p(100, 200, "A-show", source = "guide-a")
        val fromB = p(100, 200, "B-show", source = "guide-b")

        // The lower source string wins the tie regardless of list order, so
        // Now/Next cannot flip between runs.
        assertEquals("A-show", selectNowNext(listOf(fromA, fromB), 150).current?.title)
        assertEquals("A-show", selectNowNext(listOf(fromB, fromA), 150).current?.title)
    }

    @Test
    fun `a second source can supply the next programme when the first has a gap`() {
        val programmes = listOf(
            p(0, 100, "A-show", source = "guide-a"),
            p(200, 300, "B-show", source = "guide-b")
        )
        val nn = selectNowNext(programmes, nowMs = 150)
        assertNull(nn.current) // both sources agree there is a gap
        assertEquals("B-show", nn.next?.title)
    }
}
