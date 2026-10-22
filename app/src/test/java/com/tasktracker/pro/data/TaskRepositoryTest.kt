package com.tasktracker.pro.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TaskRepositoryTest {

    private lateinit var dao: FakeTaskDao
    private lateinit var repo: TaskRepository

    @Before
    fun setUp() {
        dao = FakeTaskDao()
        repo = TaskRepository(dao)
    }

    // ---------- createTask ----------

    @Test
    fun `createTask rejects a blank task name`() = runTest {
        assertNull(repo.createTask(""))
        assertNull(repo.createTask("   "))
        assertEquals(0, dao.countActive())
    }

    @Test
    fun `createTask rejects an invalid deadline`() = runTest {
        assertNull(repo.createTask("Bad date", deadline = "tomorrow"))
        assertNull(repo.createTask("Bad date", deadline = "2026-13-40"))
        assertEquals(0, dao.countActive())
    }

    @Test
    fun `createTask stamps and initializes a new task`() = runTest {
        val t = repo.createTask("Write tests", deadline = "2026-10-25")!!
        assertTrue(t.id.isNotBlank())
        assertEquals("Write tests", t.taskName)
        assertEquals(Task.PROGRESS_NOT_STARTED, t.progress)
        assertTrue(t.taskAdded.isNotBlank())
        assertTrue(t.lastUpdated.isNotBlank())
        assertTrue(t.taskStarted.isBlank())
        assertTrue(t.taskCompleted.isBlank())
        assertEquals(Task.SYNC_STATE_PENDING, t.syncState)
        assertEquals(Task.ORIGIN_LOCAL, t.origin)
        assertFalse(t.deleted)
    }

    @Test
    fun `createTask sets taskStarted for in-progress tasks`() = runTest {
        val t = repo.createTask("Started", progress = Task.PROGRESS_IN_PROGRESS)!!
        assertTrue(t.taskStarted.isNotBlank())
        assertTrue(t.taskCompleted.isBlank())
    }

    @Test
    fun `createTask sets taskCompleted for completed tasks`() = runTest {
        val t = repo.createTask("Done", progress = Task.PROGRESS_COMPLETED)!!
        assertTrue(t.taskStarted.isNotBlank())
        assertTrue(t.taskCompleted.isNotBlank())
        assertTrue(t.isCompleted)
    }

    // ---------- updateTask ----------

    @Test
    fun `updateTask preserves taskAdded and refreshes stamps`() = runTest {
        val created = repo.createTask("Original", notes = "n")!!
        val updated = created.copy(taskName = "Renamed", deadline = "2026-11-01", notes = "changed")
        assertTrue(repo.updateTask(updated))
        val fetched = repo.getById(created.id)!!
        assertEquals("Renamed", fetched.taskName)
        assertEquals("2026-11-01", fetched.deadline)
        assertEquals(created.taskAdded, fetched.taskAdded)
        assertTrue(fetched.lastUpdated.isNotBlank())
        assertEquals(Task.SYNC_STATE_PENDING, fetched.syncState)
    }

    @Test
    fun `updateTask rejects a blank task name`() = runTest {
        val created = repo.createTask("Original")!!
        assertFalse(repo.updateTask(created.copy(taskName = "   ")))
        assertEquals("Original", repo.getById(created.id)!!.taskName)
    }

    @Test
    fun `updateTask rejects an invalid deadline`() = runTest {
        val created = repo.createTask("Original", deadline = "2026-10-25")!!
        assertFalse(repo.updateTask(created.copy(deadline = "never")))
        assertEquals("2026-10-25", repo.getById(created.id)!!.deadline)
    }

    @Test
    fun `updateTask returns false for a missing or deleted task`() = runTest {
        val ghost = Task(id = "ghost", taskName = "Ghost")
        assertFalse(repo.updateTask(ghost))
        val created = repo.createTask("Doomed")!!
        repo.deleteTask(created.id)
        assertFalse(repo.updateTask(created.copy(taskName = "Edited")))
    }

    // ---------- completeTask / reopenTask ----------

    @Test
    fun `completeTask transitions a task and stamps completion`() = runTest {
        val t = repo.createTask("Do it")!!
        assertTrue(repo.completeTask(t.id))
        val done = repo.getById(t.id)!!
        assertTrue(done.isCompleted)
        assertEquals(Task.PROGRESS_COMPLETED, done.progress)
        assertTrue(done.taskCompleted.isNotBlank())
        assertTrue(done.taskStarted.isNotBlank())
        assertEquals(Task.SYNC_STATE_PENDING, done.syncState)
    }

    @Test
    fun `completeTask returns false when already completed or missing`() = runTest {
        val t = repo.createTask("Do it")!!
        assertTrue(repo.completeTask(t.id))
        assertFalse(repo.completeTask(t.id))
        assertFalse(repo.completeTask("nope"))
    }

    @Test
    fun `reopenTask clears completion and returns to in progress`() = runTest {
        val t = repo.createTask("Do it")!!
        repo.completeTask(t.id)
        assertTrue(repo.reopenTask(t.id))
        val r = repo.getById(t.id)!!
        assertFalse(r.isCompleted)
        assertEquals(Task.PROGRESS_IN_PROGRESS, r.progress)
        assertTrue(r.taskCompleted.isBlank())
        assertEquals(Task.SYNC_STATE_PENDING, r.syncState)
    }

    @Test
    fun `reopenTask returns false when the task is not completed`() = runTest {
        val t = repo.createTask("Not done")!!
        assertFalse(repo.reopenTask(t.id))
        assertFalse(repo.reopenTask("nope"))
    }

    // ---------- deleteTask / restoreTask ----------

    @Test
    fun `deleteTask creates a tombstone instead of hard-deleting`() = runTest {
        val t = repo.createTask("Gone")!!
        assertTrue(repo.deleteTask(t.id))
        val tomb = repo.getById(t.id)
        assertNotNull(tomb) // still in storage for sync
        assertTrue(tomb!!.deleted)
        assertEquals(Task.SYNC_STATE_PENDING, tomb.syncState)
        assertTrue(repo.getAllActive().isEmpty()) // hidden from active views
        assertEquals(0, dao.countActive())
    }

    @Test
    fun `deleteTask returns false for a missing or already deleted task`() = runTest {
        assertFalse(repo.deleteTask("nope"))
        val t = repo.createTask("Gone")!!
        assertTrue(repo.deleteTask(t.id))
        assertFalse(repo.deleteTask(t.id))
    }

    @Test
    fun `restoreTask undoes a soft delete`() = runTest {
        val t = repo.createTask("Back")!!
        repo.deleteTask(t.id)
        assertTrue(repo.restoreTask(t.id))
        val restored = repo.getById(t.id)!!
        assertFalse(restored.deleted)
        assertEquals(1, repo.getAllActive().size)
        assertEquals(Task.SYNC_STATE_PENDING, restored.syncState)
    }

    @Test
    fun `restoreTask returns false when the task is not deleted`() = runTest {
        val t = repo.createTask("Fine")!!
        assertFalse(repo.restoreTask(t.id))
        assertFalse(repo.restoreTask("nope"))
    }

    // ---------- sync-engine helpers ----------

    @Test
    fun `applyRemoteTask does not resurrect a pending tombstone`() = runTest {
        val t = repo.createTask("Local gone")!!
        repo.deleteTask(t.id) // tombstone is PENDING
        repo.applyRemoteTask(
            Task(id = t.id, taskName = "Remote version", lastUpdated = "2026-10-20T10:00:00+05:30")
        )
        val kept = repo.getById(t.id)!!
        assertTrue(kept.deleted)
        assertEquals("Local gone", kept.taskName)
    }

    @Test
    fun `applyRemoteTask inserts new remote tasks as synced`() = runTest {
        val remote = Task(
            id = "r-9",
            taskName = "From sheet",
            lastUpdated = "2026-10-19T10:00:00+05:30"
        )
        repo.applyRemoteTask(remote)
        val got = repo.getById("r-9")!!
        assertEquals("From sheet", got.taskName)
        assertEquals(Task.SYNC_STATE_SYNCED, got.syncState)
        assertEquals(Task.ORIGIN_REMOTE, got.origin)
        assertFalse(got.deleted)
    }

    @Test
    fun `applyRemoteDeletion refuses to delete unsynced local tasks`() = runTest {
        val t = repo.createTask("Unsynced")!! // PENDING + LOCAL
        assertFalse(repo.applyRemoteDeletion(t.id))
        val kept = repo.getById(t.id)!!
        assertFalse(kept.deleted)
    }

    @Test
    fun `applyRemoteDeletion removes previously synced tasks`() = runTest {
        val t = repo.createTask("Synced")!!
        repo.markSynced(t.id)
        assertTrue(repo.applyRemoteDeletion(t.id))
        val deleted = repo.getById(t.id)!!
        assertTrue(deleted.deleted)
        assertEquals(Task.SYNC_STATE_SYNCED, deleted.syncState)
        assertTrue(repo.getAllActive().isEmpty())
    }

    @Test
    fun `applyRemoteDeletion returns false for an unknown id`() = runTest {
        assertFalse(repo.applyRemoteDeletion("nope"))
    }

    @Test
    fun `markSynced and markSyncError update sync state`() = runTest {
        val t = repo.createTask("Sync me")!!
        repo.markSyncError(t.id, "boom")
        val err = repo.getById(t.id)!!
        assertEquals(Task.SYNC_STATE_ERROR, err.syncState)
        assertEquals("boom", err.lastSyncError)

        repo.markSynced(t.id)
        val ok = repo.getById(t.id)!!
        assertEquals(Task.SYNC_STATE_SYNCED, ok.syncState)
        assertEquals(Task.ORIGIN_REMOTE, ok.origin)
        assertTrue(ok.lastSyncError.isBlank())
    }

    @Test
    fun `markSyncError truncates long error messages`() = runTest {
        val t = repo.createTask("Sync me")!!
        repo.markSyncError(t.id, "x".repeat(400))
        assertEquals(300, repo.getById(t.id)!!.lastSyncError.length)
    }

    @Test
    fun `purgeTombstone removes only tombstones`() = runTest {
        val tomb = repo.createTask("Tomb")!!
        repo.deleteTask(tomb.id)
        repo.purgeTombstone(tomb.id)
        assertNull(repo.getById(tomb.id))

        val live = repo.createTask("Live")!!
        repo.purgeTombstone(live.id)
        assertNotNull(repo.getById(live.id))
    }

    // ---------- onMutated ----------

    @Test
    fun `onMutated fires after every successful local mutation`() = runTest {
        var count = 0
        repo.onMutated = { count++ }

        val t = repo.createTask("Mutated")!!
        assertEquals(1, count)

        assertTrue(repo.updateTask(t.copy(notes = "edited")))
        assertEquals(2, count)

        assertTrue(repo.completeTask(t.id))
        assertEquals(3, count)

        assertTrue(repo.reopenTask(t.id))
        assertEquals(4, count)

        assertTrue(repo.deleteTask(t.id))
        assertEquals(5, count)

        assertTrue(repo.restoreTask(t.id))
        assertEquals(6, count)
    }

    @Test
    fun `onMutated does not fire on failed mutations or remote applies`() = runTest {
        var count = 0
        repo.onMutated = { count++ }

        assertNull(repo.createTask("")) // validation failure
        assertFalse(repo.updateTask(Task(id = "nope", taskName = "x")))
        assertEquals(0, count)

        val t = repo.createTask("Local")!!
        assertEquals(1, count)

        // Remote-applied changes must not trigger a sync loop.
        repo.applyRemoteTask(t.copy(taskName = "Remote edit"))
        repo.applyRemoteDeletion("unknown-id")
        repo.markSynced(t.id)
        repo.markSyncError(t.id, "boom")
        assertEquals(1, count)
    }
}
