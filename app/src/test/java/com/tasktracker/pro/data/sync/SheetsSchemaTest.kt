package com.tasktracker.pro.data.sync

import com.tasktracker.pro.data.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetsSchemaTest {

    private val expectedHeaders = listOf(
        "ID",
        "Task Name",
        "Type",
        "Progress",
        "Priority",
        "Deadline",
        "Vendor / Craftsman",
        "Contact Number",
        "Estimated Cost (INR)",
        "Actual Cost (INR)",
        "Next Follow-up",
        "Delivery Date",
        "Payment Status",
        "Link",
        "Task Focus",
        "Value Add",
        "Notes",
        "Task Added",
        "Task Started",
        "Task Completed",
        "Last Updated"
    )

    private fun fullTask() = Task(
        id = "t-1",
        taskName = "Build deck",
        type = "Work",
        progress = "In Progress",
        priority = "High",
        deadline = "2026-10-25",
        vendor = "Acme",
        contactNumber = "9999999999",
        estimatedCostInr = "5000",
        actualCostInr = "4500",
        nextFollowUp = "2026-10-21",
        deliveryDate = "2026-10-30",
        paymentStatus = "Pending",
        link = "https://example.com/plan",
        taskFocus = "Launch",
        valueAdd = "Revenue",
        notes = "Some notes",
        taskAdded = "2026-10-19T09:00:00+05:30",
        taskStarted = "2026-10-19T10:00:00+05:30",
        taskCompleted = "",
        lastUpdated = "2026-10-19T12:00:00+05:30"
    )

    @Test
    fun `HEADERS has exactly 21 entries`() {
        assertEquals(21, SheetsSchema.HEADERS.size)
    }

    @Test
    fun `HEADERS are in the exact specified order`() {
        assertEquals(expectedHeaders, SheetsSchema.HEADERS)
    }

    @Test
    fun `taskToRow produces 21 values in header order`() {
        val row = SheetsSchema.taskToRow(fullTask())
        assertEquals(21, row.size)
        assertEquals(
            listOf(
                "t-1", "Build deck", "Work", "In Progress", "High", "2026-10-25",
                "Acme", "9999999999", "5000", "4500", "2026-10-21", "2026-10-30",
                "Pending", "https://example.com/plan", "Launch", "Revenue", "Some notes",
                "2026-10-19T09:00:00+05:30", "2026-10-19T10:00:00+05:30", "",
                "2026-10-19T12:00:00+05:30"
            ),
            row
        )
    }

    @Test
    fun `taskToMap keys match HEADERS exactly`() {
        val map = SheetsSchema.taskToMap(fullTask())
        assertEquals(SheetsSchema.HEADERS.toSet(), map.keys)
        assertEquals("Build deck", map["Task Name"])
        assertEquals("5000", map["Estimated Cost (INR)"])
        assertEquals("2026-10-19T12:00:00+05:30", map["Last Updated"])
    }

    @Test
    fun `task to map to task round trip preserves all 21 fields`() {
        val original = fullTask()
        val parsed = SheetsSchema.mapToTask(SheetsSchema.taskToMap(original))
        assertNotNull(parsed)
        assertEquals(
            original.copy(
                deleted = false,
                syncState = Task.SYNC_STATE_SYNCED,
                origin = Task.ORIGIN_REMOTE,
                lastSyncError = ""
            ),
            parsed
        )
    }

    @Test
    fun `mapToTask returns null for blank ID`() {
        assertNull(SheetsSchema.mapToTask(mapOf("ID" to "   ", "Task Name" to "No id")))
        assertNull(SheetsSchema.mapToTask(mapOf("ID" to "", "Task Name" to "No id")))
    }

    @Test
    fun `mapToTask returns null for missing ID`() {
        assertNull(SheetsSchema.mapToTask(mapOf("Task Name" to "No id key")))
        assertNull(SheetsSchema.mapToTask(emptyMap()))
    }

    @Test
    fun `mapToTask defaults blank progress to Not Started`() {
        val parsed = SheetsSchema.mapToTask(mapOf("ID" to "x", "Task Name" to "T", "Progress" to ""))
        assertNotNull(parsed)
        assertEquals(Task.PROGRESS_NOT_STARTED, parsed!!.progress)
    }

    @Test
    fun `mapToTask tolerates missing optional columns`() {
        val parsed = SheetsSchema.mapToTask(mapOf("ID" to "x", "Task Name" to "T"))
        assertNotNull(parsed)
        assertEquals("", parsed!!.notes)
        assertEquals("", parsed.deadline)
        assertEquals("", parsed.lastUpdated)
        assertFalse(parsed.deleted)
        assertEquals(Task.SYNC_STATE_SYNCED, parsed.syncState)
        assertEquals(Task.ORIGIN_REMOTE, parsed.origin)
    }

    @Test
    fun `validateHeaders accepts the exact header row`() {
        assertTrue(SheetsSchema.validateHeaders(expectedHeaders))
    }

    @Test
    fun `validateHeaders accepts extra trailing columns`() {
        assertTrue(SheetsSchema.validateHeaders(expectedHeaders + listOf("", "Extra")))
    }

    @Test
    fun `validateHeaders rejects reordered headers`() {
        val reordered = expectedHeaders.toMutableList()
        reordered[0] = expectedHeaders[1]
        reordered[1] = expectedHeaders[0]
        assertFalse(SheetsSchema.validateHeaders(reordered))
    }

    @Test
    fun `validateHeaders rejects renamed headers`() {
        val renamed = expectedHeaders.map { if (it == "Task Name") "Name" else it }
        assertFalse(SheetsSchema.validateHeaders(renamed))
    }

    @Test
    fun `validateHeaders rejects short header rows`() {
        assertFalse(SheetsSchema.validateHeaders(expectedHeaders.dropLast(1)))
        assertFalse(SheetsSchema.validateHeaders(emptyList()))
    }

    @Test
    fun `validateHeaders trims whitespace before comparing`() {
        assertTrue(SheetsSchema.validateHeaders(expectedHeaders.map { "  $it  " }))
    }
}
