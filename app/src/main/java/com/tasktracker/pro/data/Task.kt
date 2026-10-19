package com.tasktracker.pro.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single task. The 21 data columns map 1:1 to the Google Sheet headers
 * (see sync.SheetsSchema.HEADERS) in the exact same order.
 *
 * Dates are "yyyy-MM-dd" or "" when unset.
 * Timestamps (taskAdded/taskStarted/taskCompleted/lastUpdated) are ISO-8601
 * offset date-times, e.g. "2026-10-20T14:30:00+05:30", or "" when unset.
 */
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey val id: String,
    val taskName: String,
    val type: String = "",
    val progress: String = PROGRESS_NOT_STARTED,
    val priority: String = "",
    val deadline: String = "",
    val vendor: String = "",
    val contactNumber: String = "",
    val estimatedCostInr: String = "",
    val actualCostInr: String = "",
    val nextFollowUp: String = "",
    val deliveryDate: String = "",
    val paymentStatus: String = "",
    val link: String = "",
    val taskFocus: String = "",
    val valueAdd: String = "",
    val notes: String = "",
    val taskAdded: String = "",
    val taskStarted: String = "",
    val taskCompleted: String = "",
    val lastUpdated: String = "",
    // --- local sync metadata (never sent to the sheet as columns) ---
    /** Soft-delete tombstone: true once the user deletes the task locally. */
    val deleted: Boolean = false,
    /** SYNCED | PENDING | ERROR */
    val syncState: String = SYNC_STATE_SYNCED,
    /** LOCAL = created locally and never successfully synced; REMOTE = synced at least once. */
    val origin: String = ORIGIN_LOCAL,
    /** Last sync error message, for honest status display. */
    val lastSyncError: String = ""
) {
    val isCompleted: Boolean get() = progress == PROGRESS_COMPLETED

    companion object {
        const val PROGRESS_NOT_STARTED = "Not Started"
        const val PROGRESS_IN_PROGRESS = "In Progress"
        const val PROGRESS_COMPLETED = "Completed"
        val PROGRESS_VALUES = listOf(PROGRESS_NOT_STARTED, PROGRESS_IN_PROGRESS, PROGRESS_COMPLETED)

        const val PRIORITY_LOW = "Low"
        const val PRIORITY_MEDIUM = "Medium"
        const val PRIORITY_HIGH = "High"
        val PRIORITY_VALUES = listOf("", PRIORITY_LOW, PRIORITY_MEDIUM, PRIORITY_HIGH)

        const val SYNC_STATE_SYNCED = "SYNCED"
        const val SYNC_STATE_PENDING = "PENDING"
        const val SYNC_STATE_ERROR = "ERROR"

        const val ORIGIN_LOCAL = "LOCAL"
        const val ORIGIN_REMOTE = "REMOTE"
    }
}
