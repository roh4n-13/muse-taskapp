package com.tasktracker.pro.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskShareTest {

    @Test
    fun formatIncludesFilledFields() {
        val text = TaskShare.format(
            Task(
                id = "1",
                taskName = "Almari banana",
                progress = Task.PROGRESS_IN_PROGRESS,
                deadline = "2026-10-25",
                vendor = "Sharma Furniture",
                contactNumber = "9876543210",
                estimatedCostInr = "12000",
                paymentStatus = "Pending"
            )
        )
        assertTrue(text.contains("Almari banana"))
        assertTrue(text.contains("Deadline: 2026-10-25"))
        assertTrue(text.contains("Vendor: Sharma Furniture"))
        assertTrue(text.contains("₹12000"))
    }

    @Test
    fun formatOmitsBlankFields() {
        val text = TaskShare.format(Task(id = "1", taskName = "Simple"))
        assertFalse(text.contains("Vendor:"))
        assertFalse(text.contains("Deadline:"))
        assertFalse(text.contains("Actual Cost"))
    }
}
