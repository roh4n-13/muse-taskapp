package com.tasktracker.pro.data

import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Single source of truth for task data. Every mutation persists locally first,
 * stamps lastUpdated, and marks the row PENDING so the sync engine can push it
 * when the Google Sheets integration is enabled.
 */
class TaskRepository(private val dao: TaskDao) {

    /**
     * Called after every successful LOCAL mutation (create/update/complete/
     * reopen/delete/restore). Never fired for remote-applied changes, so it
     * cannot cause a sync loop. The app wires this to trigger an instant sync.
     */
    var onMutated: () -> Unit = {}

    fun observeActive(): Flow<List<Task>> = dao.observeActive()
    fun observeCompleted(): Flow<List<Task>> = dao.observeCompleted()
    fun observeById(id: String): Flow<Task?> = dao.observeById(id)
    fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    suspend fun getById(id: String): Task? = dao.getById(id)
    suspend fun getAllActive(): List<Task> = dao.getAllActive()
    suspend fun getUnsynced(): List<Task> = dao.getUnsynced()

    /** Creates a task. Returns the created task, or null when validation fails. */
    suspend fun createTask(
        taskName: String,
        type: String = "",
        progress: String = Task.PROGRESS_NOT_STARTED,
        priority: String = "",
        deadline: String = "",
        vendor: String = "",
        contactNumber: String = "",
        estimatedCostInr: String = "",
        actualCostInr: String = "",
        nextFollowUp: String = "",
        deliveryDate: String = "",
        paymentStatus: String = "",
        link: String = "",
        taskFocus: String = "",
        valueAdd: String = "",
        notes: String = "",
        reminderAt: String = ""
    ): Task? {
        val name = taskName.trim()
        if (name.isEmpty()) return null
        if (deadline.isNotBlank() && Dates.parseDate(deadline) == null) return null
        val now = Dates.nowString()
        val task = Task(
            id = UUID.randomUUID().toString(),
            taskName = name,
            type = type.trim(),
            progress = progress,
            priority = priority,
            deadline = deadline.trim(),
            vendor = vendor.trim(),
            contactNumber = contactNumber.trim(),
            estimatedCostInr = estimatedCostInr.trim(),
            actualCostInr = actualCostInr.trim(),
            nextFollowUp = nextFollowUp.trim(),
            deliveryDate = deliveryDate.trim(),
            paymentStatus = paymentStatus.trim(),
            link = link.trim(),
            taskFocus = taskFocus.trim(),
            valueAdd = valueAdd.trim(),
            notes = notes.trim(),
            reminderAt = reminderAt.trim(),
            taskAdded = now,
            taskStarted = if (progress != Task.PROGRESS_NOT_STARTED) now else "",
            taskCompleted = if (progress == Task.PROGRESS_COMPLETED) now else "",
            lastUpdated = now,
            syncState = Task.SYNC_STATE_PENDING,
            origin = Task.ORIGIN_LOCAL
        )
        dao.upsert(task)
        onMutated()
        return task
    }

    /** Applies user edits. Returns false when the task is missing or validation fails. */
    suspend fun updateTask(updated: Task): Boolean {
        val existing = dao.getById(updated.id) ?: return false
        if (existing.deleted) return false
        val name = updated.taskName.trim()
        if (name.isEmpty()) return false
        if (updated.deadline.isNotBlank() && Dates.parseDate(updated.deadline) == null) return false
        val now = Dates.nowString()
        val merged = updated.copy(
            taskName = name,
            // Preserve immutable audit fields; recompute lifecycle stamps.
            taskAdded = existing.taskAdded.ifBlank { now },
            taskStarted = when {
                existing.taskStarted.isNotBlank() -> existing.taskStarted
                updated.progress != Task.PROGRESS_NOT_STARTED -> now
                else -> ""
            },
            taskCompleted = when {
                updated.progress == Task.PROGRESS_COMPLETED ->
                    existing.taskCompleted.ifBlank { now }
                else -> ""
            },
            lastUpdated = now,
            syncState = Task.SYNC_STATE_PENDING
        )
        dao.update(merged)
        onMutated()
        return true
    }

    suspend fun completeTask(id: String): Boolean {
        val t = dao.getById(id) ?: return false
        if (t.deleted || t.isCompleted) return false
        val now = Dates.nowString()
        dao.update(
            t.copy(
                progress = Task.PROGRESS_COMPLETED,
                taskStarted = t.taskStarted.ifBlank { now },
                taskCompleted = now,
                lastUpdated = now,
                syncState = Task.SYNC_STATE_PENDING
            )
        )
        onMutated()
        return true
    }

    suspend fun reopenTask(id: String): Boolean {
        val t = dao.getById(id) ?: return false
        if (t.deleted || !t.isCompleted) return false
        dao.update(
            t.copy(
                progress = Task.PROGRESS_IN_PROGRESS,
                taskCompleted = "",
                lastUpdated = Dates.nowString(),
                syncState = Task.SYNC_STATE_PENDING
            )
        )
        onMutated()
        return true
    }

    /**
     * Soft delete: marks a tombstone so the deletion can propagate to the sheet
     * on the next sync. Never hard-deletes user data here.
     */
    suspend fun deleteTask(id: String): Boolean {
        val t = dao.getById(id) ?: return false
        if (t.deleted) return false
        dao.update(
            t.copy(
                deleted = true,
                lastUpdated = Dates.nowString(),
                syncState = Task.SYNC_STATE_PENDING
            )
        )
        onMutated()
        return true
    }

    /** Restores a soft-deleted task (undo). */
    suspend fun restoreTask(id: String): Boolean {
        val t = dao.getById(id) ?: return false
        if (!t.deleted) return false
        dao.update(
            t.copy(
                deleted = false,
                lastUpdated = Dates.nowString(),
                syncState = Task.SYNC_STATE_PENDING
            )
        )
        onMutated()
        return true
    }

    // ---- sync-engine helpers (package-internal use) ----

    /** Insert or update a task received from the sheet; never resurrects tombstones. */
    suspend fun applyRemoteTask(remote: Task) {
        val existing = dao.getById(remote.id)
        if (existing != null && existing.deleted && existing.syncState == Task.SYNC_STATE_PENDING) {
            // Local delete not yet synced — keep the tombstone; conflict resolved at push time.
            return
        }
        dao.upsert(
            remote.copy(
                deleted = false,
                syncState = Task.SYNC_STATE_SYNCED,
                origin = Task.ORIGIN_REMOTE,
                lastSyncError = "",
                // reminderAt is a local-only setting (not a sheet column):
                // a remote copy must never wipe the reminder set on this phone.
                reminderAt = existing?.reminderAt ?: ""
            )
        )
    }

    /** Applies a validated remote deletion: only for tasks that were synced before. */
    suspend fun applyRemoteDeletion(id: String): Boolean {
        val existing = dao.getById(id) ?: return false
        // Never erase unsynced local work because a remote row disappeared.
        if (existing.syncState == Task.SYNC_STATE_PENDING && existing.origin == Task.ORIGIN_LOCAL) {
            return false
        }
        dao.update(existing.copy(deleted = true, syncState = Task.SYNC_STATE_SYNCED))
        return true
    }

    suspend fun markSynced(id: String) {
        val t = dao.getById(id) ?: return
        dao.update(t.copy(syncState = Task.SYNC_STATE_SYNCED, origin = Task.ORIGIN_REMOTE, lastSyncError = ""))
    }

    suspend fun markSyncError(id: String, error: String) {
        val t = dao.getById(id) ?: return
        dao.update(t.copy(syncState = Task.SYNC_STATE_ERROR, lastSyncError = error.take(300)))
    }

    /** Removes a tombstone after its deletion was confirmed by the backend. */
    suspend fun purgeTombstone(id: String) {
        val t = dao.getById(id)
        if (t != null && t.deleted) dao.hardDelete(id)
    }

    /**
     * Repair: marks all live tasks as needing upload. Use when tasks were
     * incorrectly marked synced (e.g. server silently skipped them).
     * Next sync will re-push everything.
     */
    suspend fun markAllForUpload() {
        dao.markAllPending()
    }
}
