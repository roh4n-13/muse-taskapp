package com.tasktracker.pro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class DatesTest {

    // ---------- parseDate ----------

    @Test
    fun `parseDate parses a valid yyyy-MM-dd date`() {
        assertEquals(LocalDate.of(2026, 10, 20), Dates.parseDate("2026-10-20"))
    }

    @Test
    fun `parseDate returns null for invalid input`() {
        assertNull(Dates.parseDate("not-a-date"))
        assertNull(Dates.parseDate("2026-13-45"))
        assertNull(Dates.parseDate("20/10/2026"))
        assertNull(Dates.parseDate("2026-10-20T14:30:00"))
    }

    @Test
    fun `parseDate returns null for blank input`() {
        assertNull(Dates.parseDate(""))
        assertNull(Dates.parseDate("   "))
    }

    // ---------- parseDateTime ----------

    @Test
    fun `parseDateTime parses an ISO offset date-time`() {
        val parsed = Dates.parseDateTime("2026-10-20T14:30:00+05:30")
        assertNotNull(parsed)
        assertEquals(ZoneOffset.of("+05:30"), parsed!!.offset)
        assertEquals(14, parsed.hour)
        assertEquals(30, parsed.minute)
        assertEquals(LocalDate.of(2026, 10, 20), parsed.toLocalDate())
    }

    @Test
    fun `parseDateTime parses Apps Script format without an offset`() {
        // The Apps Script backend writes timestamps without an offset; they are
        // interpreted in the device zone and must never throw.
        val parsed = Dates.parseDateTime("2026-10-20T14:30:00")
        assertNotNull(parsed)
        assertEquals(LocalDate.of(2026, 10, 20), parsed!!.toLocalDate())
        assertEquals(14, parsed.hour)
        assertEquals(30, parsed.minute)
    }

    @Test
    fun `parseDateTime returns null for blank or invalid input`() {
        assertNull(Dates.parseDateTime(""))
        assertNull(Dates.parseDateTime("   "))
        assertNull(Dates.parseDateTime("yesterday at noon"))
        assertNull(Dates.parseDateTime("2026-10-20"))
    }

    // ---------- isOverdue / isToday / isUpcoming ----------

    private val today = LocalDate.of(2026, 10, 20)

    @Test
    fun `isOverdue detects deadlines before today`() {
        assertTrue(Dates.isOverdue("2026-10-19", today))
        assertTrue(Dates.isOverdue("2020-01-01", today))
    }

    @Test
    fun `isOverdue is false for today, future and invalid deadlines`() {
        assertFalse(Dates.isOverdue("2026-10-20", today))
        assertFalse(Dates.isOverdue("2026-10-21", today))
        assertFalse(Dates.isOverdue("", today))
        assertFalse(Dates.isOverdue("garbage", today))
    }

    @Test
    fun `isToday matches only the given day`() {
        assertTrue(Dates.isToday("2026-10-20", today))
        assertFalse(Dates.isToday("2026-10-19", today))
        assertFalse(Dates.isToday("2026-10-21", today))
        assertFalse(Dates.isToday("", today))
    }

    @Test
    fun `isUpcoming covers today through seven days ahead`() {
        assertTrue(Dates.isUpcoming("2026-10-20", today))
        assertTrue(Dates.isUpcoming("2026-10-21", today))
        assertTrue(Dates.isUpcoming("2026-10-27", today))
    }

    @Test
    fun `isUpcoming rejects past, distant and invalid dates`() {
        assertFalse(Dates.isUpcoming("2026-10-19", today))
        assertFalse(Dates.isUpcoming("2026-10-28", today))
        assertFalse(Dates.isUpcoming("", today))
        assertFalse(Dates.isUpcoming("nope", today))
    }

    // ---------- compareLastUpdated ----------

    @Test
    fun `compareLastUpdated orders by timestamp`() {
        val older = "2026-10-19T10:00:00+05:30"
        val newer = "2026-10-19T12:00:00+05:30"
        assertTrue(Dates.compareLastUpdated(older, newer) < 0)
        assertTrue(Dates.compareLastUpdated(newer, older) > 0)
        assertEquals(0, Dates.compareLastUpdated(newer, newer))
    }

    @Test
    fun `compareLastUpdated treats blank as oldest`() {
        val ts = "2026-10-19T10:00:00+05:30"
        assertTrue(Dates.compareLastUpdated("", ts) < 0)
        assertTrue(Dates.compareLastUpdated(ts, "") > 0)
        assertEquals(0, Dates.compareLastUpdated("", ""))
    }

    @Test
    fun `compareLastUpdated treats invalid timestamps as oldest`() {
        val ts = "2026-10-19T10:00:00+05:30"
        assertTrue(Dates.compareLastUpdated("not-a-time", ts) < 0)
        assertTrue(Dates.compareLastUpdated(ts, "not-a-time") > 0)
    }
}
