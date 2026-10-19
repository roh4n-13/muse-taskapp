package com.tasktracker.pro.data.sync

import com.tasktracker.pro.data.FakeSheetsApi
import com.tasktracker.pro.data.FakeSyncPrefs
import com.tasktracker.pro.data.FakeTaskDao
import com.tasktracker.pro.data.Task
import com.tasktracker.pro.data.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncEngineTest {

    private lateinit var dao: FakeTaskDao
    private lateinit var repo: TaskRepository
    private lateinit var prefs: FakeSyncPrefs
    private lateinit var api: FakeSheetsApi
    private lateinit var engine: SyncEngine
    private var online = true

    private val validUrl = "https://script.google.com/macros/s/AKfycbx123/exec"

    @Before
    fun setUp() {
        dao = FakeTaskDao()
        repo = TaskRepository(dao)
        prefs = FakeSyncPrefs()
        api = FakeSheetsApi()
        online = true
        engine = SyncEngine(repo, prefs, api, Dispatchers.Unconfined) { online }
    }

    private fun configureSync() {
        prefs.webAppUrl = validUrl
        prefs.token = "test-token"
        engine.setSyncEnabled(true)
    }

    private fun remoteTask(id: String, name: String, lastUpdated: String) = Task(
        id = id,
        taskName = name,
        lastUpdated = lastUpdated,
        syncState = Task.SYNC_STATE_SYNCED,
        origin = Task.ORIGIN_REMOTE
    )

    // ---------- testConnection ----------

    @Test
    fun `testConnection rejects a malformed URL`() = runTest {
        val r = engine.testConnection("http://example.com/not-google", "tok")
        assertFalse(r.ok)
        assertTrue(api.pingCalls.isEmpty()) // never even attempted
    }

    @Test
    fun `testConnection rejects a URL not ending in exec`() = runTest {
        val r = engine.testConnection("https://script.google.com/macros/s/ABC", "tok")
        assertFalse(r.ok)
        assertTrue(api.pingCalls.isEmpty())
    }

    @Test
    fun `testConnection rejects an empty URL`() = runTest {
        val r = engine.testConnection("   ", "tok")
        assertFalse(r.ok)
        assertTrue(r.message.contains("URL", ignoreCase = true))
    }

    @Test
    fun `testConnection rejects a blank token`() = runTest {
        val r = engine.testConnection(validUrl, "   ")
        assertFalse(r.ok)
        assertTrue(r.message.contains("token", ignoreCase = true))
        assertTrue(api.pingCalls.isEmpty())
    }

    @Test
    fun `testConnection surfaces a ping failure`() = runTest {
        api.pingResult = ApiResult.failure("Invalid token.")
        val r = engine.testConnection(validUrl, "bad-token")
        assertFalse(r.ok)
        assertTrue(r.message.contains("Invalid token."))
        assertEquals(listOf(validUrl to "bad-token"), api.pingCalls)
    }

    @Test
    fun `testConnection succeeds on a valid ping and changes nothing`() = runTest {
        api.pingResult = ApiResult.success(PingInfo(version = 1, sheetTitle = "My Sheet", taskCount = 7))
        val r = engine.testConnection(validUrl, "good-token")
        assertTrue(r.ok)
        assertTrue(r.message.contains("My Sheet"))
        assertTrue(r.message.contains("7"))
        // Read-only verification: nothing stored, nothing enabled.
        assertFalse(prefs.isConfigured)
        assertFalse(prefs.syncEnabled)
    }

    @Test
    fun `testConnection fails when offline`() = runTest {
        online = false
        val r = engine.testConnection(validUrl, "tok")
        assertFalse(r.ok)
        assertTrue(r.message.contains("internet", ignoreCase = true))
        assertTrue(api.pingCalls.isEmpty())
    }

    // ---------- syncNow gating ----------

    @Test
    fun `syncNow is a no-op reporting not-enabled when sync is disabled`() = runTest {
        val t = repo.createTask("Local only")!!
        val r = engine.syncNow()
        assertFalse(r.ok)
        assertEquals("Sync is not enabled.", r.message)
        assertEquals(0, r.pushed)
        assertEquals(0, r.pulled)
        assertTrue(api.batchCalls.isEmpty())
        assertTrue(api.snapshotCalls.isEmpty())
        // Local work untouched.
        assertEquals(Task.SYNC_STATE_PENDING, repo.getById(t.id)!!.syncState)
    }

    // ---------- push ----------

    @Test
    fun `syncNow pushes pending tasks via batchUpsert and marks them synced`() = runTest {
        configureSync()
        val a = repo.createTask("Task A")!!
        val b = repo.createTask("Task B")!!
        api.batchResult = ApiResult.success(2)
        api.snapshotResult = ApiResult.success(emptyList())

        val r = engine.syncNow()

        assertTrue(r.ok)
        assertEquals(1, api.batchCalls.size)
        assertEquals(setOf(a.id, b.id), api.batchCalls[0].map { it.id }.toSet())
        assertTrue(api.upsertCalls.isEmpty()) // batch covered everything
        assertEquals(Task.SYNC_STATE_SYNCED, repo.getById(a.id)!!.syncState)
        assertEquals(Task.SYNC_STATE_SYNCED, repo.getById(b.id)!!.syncState)
    }

    @Test
    fun `syncNow deletes tombstones remotely then purges them`() = runTest {
        configureSync()
        val t = repo.createTask("Gone")!!
        repo.deleteTask(t.id)
        api.snapshotResult = ApiResult.success(emptyList())

        val r = engine.syncNow()

        assertTrue(r.ok)
        assertEquals(listOf(t.id), api.deleteCalls)
        assertNull(repo.getById(t.id)) // purged after confirmed delete
    }

    // ---------- pull ----------

    @Test
    fun `syncNow pulls new remote tasks`() = runTest {
        configureSync()
        api.snapshotResult = ApiResult.success(
            listOf(remoteTask("r1", "From sheet", "2026-10-19T10:00:00+05:30"))
        )

        val r = engine.syncNow()

        assertTrue(r.ok)
        assertEquals(1, r.pulled)
        val local = repo.getById("r1")!!
        assertEquals("From sheet", local.taskName)
        assertEquals(Task.SYNC_STATE_SYNCED, local.syncState)
        assertEquals(Task.ORIGIN_REMOTE, local.origin)
        assertEquals(setOf("r1"), prefs.lastSyncedIds)
    }

    @Test
    fun `remote newer Last Updated wins over the synced local copy`() = runTest {
        dao.upsert(
            Task(
                id = "w1",
                taskName = "Local old",
                lastUpdated = "2026-10-19T10:00:00+05:30",
                syncState = Task.SYNC_STATE_SYNCED,
                origin = Task.ORIGIN_REMOTE
            )
        )
        prefs.lastSyncedIds = setOf("w1")
        configureSync()
        api.snapshotResult = ApiResult.success(
            listOf(remoteTask("w1", "Remote new", "2026-10-19T12:00:00+05:30"))
        )

        val r = engine.syncNow()

        assertTrue(r.ok)
        assertEquals(1, r.pulled)
        assertEquals("Remote new", repo.getById("w1")!!.taskName)
    }

    @Test
    fun `local pending edit wins the conflict and is re-pushed`() = runTest {
        // g1 uploads fine; c1 keeps failing so it stays unsynced for the merge.
        dao.upsert(
            Task(
                id = "g1", taskName = "G one",
                lastUpdated = "2026-10-19T10:00:00+05:30",
                syncState = Task.SYNC_STATE_PENDING, origin = Task.ORIGIN_LOCAL
            )
        )
        dao.upsert(
            Task(
                id = "c1", taskName = "Local edit",
                lastUpdated = "2026-10-19T11:00:00+05:30",
                syncState = Task.SYNC_STATE_PENDING, origin = Task.ORIGIN_LOCAL
            )
        )
        prefs.lastSyncedIds = setOf("c1")
        configureSync()
        api.batchResult = ApiResult.failure("batch down")
        api.upsertHandler = { t ->
            if (t.id == "c1") ApiResult.failure("single down") else ApiResult.success(t.id)
        }
        api.snapshotResult = ApiResult.success(
            listOf(remoteTask("c1", "Remote other", "2026-10-19T12:00:00+05:30"))
        )

        val r = engine.syncNow()

        assertTrue(r.ok)
        assertEquals(1, r.conflicts)
        // Local edit preserved, never overwritten by the remote row...
        assertEquals("Local edit", repo.getById("c1")!!.taskName)
        // ...and re-pushed so the sheet converges to it (once in push, once in merge).
        assertEquals(2, api.upsertCalls.count { it.id == "c1" })
        // The task that uploaded fine is synced.
        assertEquals(Task.SYNC_STATE_SYNCED, repo.getById("g1")!!.syncState)
    }

    // ---------- deletion reconciliation ----------

    @Test
    fun `remote deletion reconciles only ids from lastSyncedIds`() = runTest {
        dao.upsert(
            Task(
                id = "a1", taskName = "Was on sheet",
                lastUpdated = "2026-10-19T10:00:00+05:30",
                syncState = Task.SYNC_STATE_SYNCED, origin = Task.ORIGIN_REMOTE
            )
        )
        val c2 = repo.createTask("Never synced")!! // PENDING + LOCAL, id not in lastSyncedIds
        prefs.lastSyncedIds = setOf("a1")
        configureSync()
        api.batchResult = ApiResult.success(1)
        api.snapshotResult = ApiResult.success(emptyList()) // both rows missing remotely

        val r = engine.syncNow()

        assertTrue(r.ok)
        assertEquals(1, r.remoteDeleted)
        assertTrue(repo.getById("a1")!!.deleted) // previously synced -> reconciled
        assertFalse(repo.getById(c2.id)!!.deleted) // never synced -> never deleted
        assertTrue(prefs.lastSyncedIds.isEmpty())
    }

    @Test
    fun `a failed snapshot never deletes anything`() = runTest {
        dao.upsert(
            Task(
                id = "a1", taskName = "Safe",
                lastUpdated = "2026-10-19T10:00:00+05:30",
                syncState = Task.SYNC_STATE_SYNCED, origin = Task.ORIGIN_REMOTE
            )
        )
        prefs.lastSyncedIds = setOf("a1")
        configureSync()
        api.snapshotResult = ApiResult.failure("Auth failed")

        val r = engine.syncNow()

        assertFalse(r.ok)
        assertEquals(0, r.remoteDeleted)
        assertFalse(repo.getById("a1")!!.deleted)
        assertEquals(setOf("a1"), prefs.lastSyncedIds) // untouched
        assertTrue(prefs.lastSyncError.contains("Auth failed"))
    }

    // ---------- offline ----------

    @Test
    fun `offline sync keeps everything pending and makes no network calls`() = runTest {
        configureSync()
        val t = repo.createTask("Offline work")!!
        online = false

        val r = engine.syncNow()

        assertFalse(r.ok)
        assertTrue(r.message.contains("internet", ignoreCase = true))
        assertTrue(api.batchCalls.isEmpty())
        assertTrue(api.snapshotCalls.isEmpty())
        assertTrue(api.upsertCalls.isEmpty())
        assertTrue(api.deleteCalls.isEmpty())
        assertEquals(Task.SYNC_STATE_PENDING, repo.getById(t.id)!!.syncState)
        assertTrue(prefs.lastSyncError.isNotBlank())
        assertNotNull(repo.getById(t.id))
    }
}
