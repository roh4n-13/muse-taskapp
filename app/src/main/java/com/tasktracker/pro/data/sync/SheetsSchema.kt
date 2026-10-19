package com.tasktracker.pro.data.sync

import com.tasktracker.pro.data.Task

/**
 * Canonical 21-column schema shared by the app, the Google Sheet, and the
 * Apps Script backend. Order is significant — never reorder.
 */
object SheetsSchema {
    val HEADERS: List<String> = listOf(
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

    /** Task → row values in header order. Tombstones are never uploaded as rows. */
    fun taskToRow(t: Task): List<String> = listOf(
        t.id, t.taskName, t.type, t.progress, t.priority, t.deadline,
        t.vendor, t.contactNumber, t.estimatedCostInr, t.actualCostInr,
        t.nextFollowUp, t.deliveryDate, t.paymentStatus, t.link,
        t.taskFocus, t.valueAdd, t.notes, t.taskAdded, t.taskStarted,
        t.taskCompleted, t.lastUpdated
    )

    /** Task → JSON-friendly map keyed by exact header names (for the Apps Script API). */
    fun taskToMap(t: Task): Map<String, String> =
        HEADERS.zip(taskToRow(t)).toMap()

    /**
     * Map (from a sheet row / API snapshot) → Task. Missing keys become "".
     * Returns null when the ID is blank — a row without an ID is not a task.
     */
    fun mapToTask(map: Map<String, String?>): Task? {
        fun get(h: String): String = (map[h] ?: "").trim()
        val id = get("ID")
        if (id.isEmpty()) return null
        return Task(
            id = id,
            taskName = get("Task Name"),
            type = get("Type"),
            progress = get("Progress").ifBlank { Task.PROGRESS_NOT_STARTED },
            priority = get("Priority"),
            deadline = get("Deadline"),
            vendor = get("Vendor / Craftsman"),
            contactNumber = get("Contact Number"),
            estimatedCostInr = get("Estimated Cost (INR)"),
            actualCostInr = get("Actual Cost (INR)"),
            nextFollowUp = get("Next Follow-up"),
            deliveryDate = get("Delivery Date"),
            paymentStatus = get("Payment Status"),
            link = get("Link"),
            taskFocus = get("Task Focus"),
            valueAdd = get("Value Add"),
            notes = get("Notes"),
            taskAdded = get("Task Added"),
            taskStarted = get("Task Started"),
            taskCompleted = get("Task Completed"),
            lastUpdated = get("Last Updated"),
            deleted = false,
            syncState = Task.SYNC_STATE_SYNCED,
            origin = Task.ORIGIN_REMOTE
        )
    }

    /** Validates that a header row matches the canonical schema. */
    fun validateHeaders(actual: List<String>): Boolean {
        val a = actual.map { it.trim() }
        if (a.size < HEADERS.size) return false
        return HEADERS.indices.all { a[it] == HEADERS[it] }
    }
}
