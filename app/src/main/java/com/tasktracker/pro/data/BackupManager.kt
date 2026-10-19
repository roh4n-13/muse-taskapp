package com.tasktracker.pro.data

import com.tasktracker.pro.data.sync.SheetsSchema
import java.util.UUID

/**
 * JSON backup/restore. The export contains task data only — never the Sheets
 * URL/token (those stay in encrypted storage). Import MERGES: it never
 * replaces or deletes existing tasks.
 */
class BackupManager(private val repo: TaskRepository) {

    /** All active tasks as a JSON array of header→value objects. */
    suspend fun exportToJson(): String {
        val tasks = repo.getAllActive()
        val maps = tasks.map { SheetsSchema.taskToMap(it) }
        return JsonUtil.backupToJson(maps, Dates.nowString())
    }

    data class ImportResult(val imported: Int, val skipped: Int, val errors: List<String>)

    /**
     * Merges tasks from a backup file. Tasks whose ID already exists locally get
     * a fresh ID (duplicates are never created, existing tasks are never
     * overwritten). Malformed entries are skipped and reported.
     */
    suspend fun importFromJson(json: String): ImportResult {
        val trimmed = json.trimStart()
        // First: is it JSON at all?
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return ImportResult(0, 0, listOf("File is not valid JSON."))
        }
        val backup = try {
            JsonUtil.parseBackup(json)
        } catch (e: Exception) {
            return ImportResult(0, 0, listOf("File is not valid JSON."))
        }
        if (backup == null) {
            // Valid JSON but not a backup (missing tasks array, or malformed tasks).
            // Distinguish: if there's no "tasks" key at all, say so.
            return if (!json.contains("\"tasks\"")) {
                ImportResult(0, 0, listOf("Not a Task Tracker backup: missing \"tasks\" array."))
            } else {
                ImportResult(0, 0, listOf("File is not valid JSON."))
            }
        }
        var imported = 0
        var skipped = 0
        val errors = mutableListOf<String>()
        // Malformed rows (non-objects) are skipped and reported.
        for (badIndex in backup.malformedIndices) {
            skipped++
            errors.add("Row ${badIndex + 1}: malformed entry, skipped.")
        }
        for ((i, map) in backup.tasks.withIndex()) {
            try {
                val parsed = SheetsSchema.mapToTask(map)
                if (parsed == null) {
                    skipped++
                    errors.add("Row ${i + 1}: missing task ID, skipped.")
                    continue
                }
                val existing = repo.getById(parsed.id)
                val toInsert = if (existing != null) {
                    // Same ID exists — keep both, give the import a new ID.
                    parsed.copy(id = UUID.randomUUID().toString())
                } else parsed
                repo.applyRemoteTask(
                    toInsert.copy(
                        deleted = false,
                        syncState = Task.SYNC_STATE_PENDING,
                        origin = Task.ORIGIN_LOCAL
                    )
                )
                imported++
            } catch (e: Exception) {
                skipped++
                errors.add("Row ${i + 1}: ${e.message}")
            }
        }
        return ImportResult(imported, skipped, errors.take(20))
    }
}
