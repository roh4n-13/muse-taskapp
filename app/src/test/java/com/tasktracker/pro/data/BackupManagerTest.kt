package com.tasktracker.pro.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackupManagerTest {

    private lateinit var dao: FakeTaskDao
    private lateinit var manager: BackupManager

    @Before
    fun setUp() {
        dao = FakeTaskDao()
        manager = BackupManager(TaskRepository(dao))
    }

    @Test
    fun `exportToJson contains all tasks with header keys`() = runTest {
        dao.upsert(Task(id = "e1", taskName = "Export one", notes = "hello"))
        dao.upsert(Task(id = "e2", taskName = "Export two", priority = Task.PRIORITY_HIGH))

        val json = manager.exportToJson()
        val backup = JsonUtil.parseBackup(json)!!
        assertEquals(2, backup.tasks.size)
        val names = backup.tasks.map { it["Task Name"] }.toSet()
        assertEquals(setOf("Export one", "Export two"), names)
        assertEquals("hello", backup.tasks[0]["Notes"])
        // Every row uses the exact sheet header keys.
        val keys = backup.tasks[0].keys
        assertTrue(keys.containsAll(listOf("ID", "Task Name", "Last Updated")))
        // Top-level app marker present.
        assertTrue(json.contains("\"app\":\"TaskTrackerPro\""))
    }

    @Test
    fun `exportToJson contains no URL or token strings`() = runTest {
        dao.upsert(Task(id = "e1", taskName = "Plain task"))
        val json = manager.exportToJson()
        assertFalse(json.contains("script.google"))
        assertFalse(json.contains("https://"))
        assertFalse(json.contains("token", ignoreCase = true))
    }

    @Test
    fun `importFromJson merges without touching existing tasks`() = runTest {
        dao.upsert(Task(id = "local-1", taskName = "Local original", notes = "keep me"))

        val json = backupJson(
            row("imp-2", "Imported new"),
            row("imp-3", "Imported other")
        )
        val result = manager.importFromJson(json)

        assertEquals(2, result.imported)
        assertEquals(0, result.skipped)
        assertTrue(result.errors.isEmpty())
        // Existing task untouched.
        val kept = dao.getById("local-1")!!
        assertEquals("Local original", kept.taskName)
        assertEquals("keep me", kept.notes)
        // Imported tasks present; nothing replaced.
        assertNotNull(dao.getById("imp-2"))
        assertNotNull(dao.getById("imp-3"))
        assertEquals(3, dao.countActive())
    }

    @Test
    fun `importFromJson regenerates the id on collision`() = runTest {
        dao.upsert(Task(id = "dup", taskName = "Local original"))

        val result = manager.importFromJson(backupJson(row("dup", "Imported same id")))

        assertEquals(1, result.imported)
        assertEquals(0, result.skipped)
        // Original keeps its id and content.
        assertEquals("Local original", dao.getById("dup")!!.taskName)
        // The import was kept too, under a fresh id.
        val copies = dao.getAllActive().filter { it.taskName == "Imported same id" }
        assertEquals(1, copies.size)
        assertTrue(copies[0].id.isNotBlank())
        assertFalse(copies[0].id == "dup")
    }

    @Test
    fun `importFromJson skips rows with missing id and malformed rows`() = runTest {
        // Build JSON manually: one good row, one row without ID, one malformed string item.
        val json = "{\"app\":\"TaskTrackerPro\",\"version\":1,\"tasks\":[" +
            JsonUtil.mapToJson(mapOf("ID" to "ok-1", "Task Name" to "Good row", "Progress" to Task.PROGRESS_NOT_STARTED)) +
            ",{\"Task Name\":\"Missing ID\"}" +
            ",\"just a string\"" +
            "]}"

        val result = manager.importFromJson(json)

        assertEquals(1, result.imported)
        assertEquals(2, result.skipped)
        assertEquals(2, result.errors.size)
        assertNotNull(dao.getById("ok-1"))
        assertEquals(1, dao.countActive())
    }

    @Test
    fun `importFromJson rejects non-JSON input`() = runTest {
        val result = manager.importFromJson("this is not json {{{")
        assertEquals(0, result.imported)
        assertEquals(0, result.skipped)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0].contains("not valid JSON"))
        assertEquals(0, dao.countActive())
    }

    @Test
    fun `importFromJson rejects JSON without a tasks array`() = runTest {
        val result = manager.importFromJson("{\"app\":\"TaskTrackerPro\"}")
        assertEquals(0, result.imported)
        assertTrue(result.errors.any { it.contains("missing \"tasks\"") })
        assertEquals(0, dao.countActive())
    }

    @Test
    fun `importFromJson handles an empty tasks array`() = runTest {
        val result = manager.importFromJson(backupJson())
        assertEquals(0, result.imported)
        assertEquals(0, result.skipped)
        assertTrue(result.errors.isEmpty())
    }

    // ---------- helpers ----------

    private fun row(id: String, name: String): Map<String, String?> =
        mapOf(
            "ID" to id,
            "Task Name" to name,
            "Progress" to Task.PROGRESS_NOT_STARTED
        )

    private fun backupJson(vararg rows: Map<String, String?>): String =
        JsonUtil.backupToJson(rows.toList(), "2026-01-01T00:00:00")
}
