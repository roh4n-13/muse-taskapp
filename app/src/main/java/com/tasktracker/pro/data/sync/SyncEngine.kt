package com.tasktracker.pro.data.sync

import com.tasktracker.pro.data.Dates
import com.tasktracker.pro.data.Task
import com.tasktracker.pro.data.TaskRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Two-way sync between the local Room database and the Google Sheet via the
 * Apps Script backend. Sync NEVER runs unless the user explicitly enabled it.
 *
 * Conflict policy (documented, deterministic):
 *  - Local edits that have not synced yet ALWAYS win over remote data.
 *    They are pushed; the remote row is never allowed to overwrite them.
 *  - Otherwise the newer `Last Updated` wins.
 *  - A conflict is counted (and kept visible via pending state) whenever both
 *    sides changed the same task since the last sync.
 *
 * Deletion safety:
 *  - Remote deletions are only applied after a complete, validated snapshot.
 *  - A missing remote row deletes the local copy only if that task was
 *    synced before (tracked in lastSyncedIds) and has no unsynced local edits.
 *  - Timeouts, empty responses, or auth failures are NEVER treated as deletions.
 */
class SyncEngine(
    private val repo: TaskRepository,
    private val prefs: SyncPrefs,
    private val client: SheetsApi = AppsScriptClient(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val isOnline: () -> Boolean = { true }
) {
    private val mutex = Mutex()

    val isEnabled: Boolean get() = prefs.syncEnabled && prefs.isConfigured

    data class ConnectionTest(val ok: Boolean, val message: String)

    /** Read-only verification. Saves nothing and changes nothing. */
    suspend fun testConnection(url: String, token: String): ConnectionTest = withContext(io) {
        val u = url.trim()
        if (u.isEmpty()) return@withContext ConnectionTest(false, "Please enter the Web App URL.")
        if (!client.isValidWebAppUrl(u)) {
            return@withContext ConnectionTest(false, "That doesn't look like an Apps Script Web App URL. It should start with https://script.google… and end with /exec.")
        }
        if (token.isBlank()) return@withContext ConnectionTest(false, "Please enter the secret token.")
        if (!isOnline()) return@withContext ConnectionTest(false, "No internet connection. Connect and try again.")
        val res = client.ping(u, token)
        if (!res.ok) return@withContext ConnectionTest(false, res.error)
        val info = res.data!!
        ConnectionTest(true, "Connected to \"${info.sheetTitle}\" (${info.taskCount} tasks on the sheet). Nothing was changed.")
    }

    /** Stores URL + token. Does NOT enable sync — the user enables it explicitly. */
    fun saveConnection(url: String, token: String) {
        prefs.webAppUrl = url.trim()
        prefs.token = token
    }

    fun setSyncEnabled(enabled: Boolean) {
        prefs.syncEnabled = enabled && prefs.isConfigured
    }

    /** Stops sync and cancels future remote work. Local tasks are never touched. */
    fun disconnect(clearCredentials: Boolean) {
        prefs.syncEnabled = false
        if (clearCredentials) prefs.clearConnection()
    }

    data class FirstSyncPreview(
        val localCount: Int,
        val remoteCount: Int,
        val overlappingIds: Int,
        val onlyRemote: Int
    )

    /** Dry-run: fetches the remote snapshot and reports what a first sync would do. */
    suspend fun previewFirstSync(): ApiResult<FirstSyncPreview> = withContext(io) {
        if (!prefs.isConfigured) return@withContext ApiResult.failure("Connection not configured.")
        if (!isOnline()) return@withContext ApiResult.failure("No internet connection.")
        val snap = client.snapshot(prefs.webAppUrl, prefs.token)
        if (!snap.ok) return@withContext ApiResult.failure(snap.error)
        val remote = snap.data!!
        val localIds = repo.getAllActive().map { it.id }.toSet()
        val remoteIds = remote.map { it.id }.toSet()
        ApiResult.success(
            FirstSyncPreview(
                localCount = localIds.size,
                remoteCount = remoteIds.size,
                overlappingIds = localIds.intersect(remoteIds).size,
                onlyRemote = (remoteIds - localIds).size
            )
        )
    }

    enum class FirstSyncMode { MERGE, UPLOAD_FIRST, DOWNLOAD_FIRST }

    /**
     * First explicit sync after the user enables the integration.
     * MERGE: push local changes, pull remote, merge (local unsynced edits win).
     * UPLOAD_FIRST: push everything local first, then pull + merge.
     * DOWNLOAD_FIRST: pull remote tasks in first, then push local changes.
     */
    suspend fun firstSync(mode: FirstSyncMode): SyncReport = withContext(io) {
        mutex.withLock {
            if (!prefs.isConfigured) return@withContext SyncReport.failed("Connection not configured.")
            if (!isOnline()) return@withContext SyncReport.failed("No internet connection. Your tasks are safe locally and will sync later.")
            prefs.syncEnabled = true
            when (mode) {
                FirstSyncMode.UPLOAD_FIRST -> {
                    val pushRes = pushAll()
                    if (!pushRes.ok) return@withContext pushRes
                    pullAndMerge()
                }
                FirstSyncMode.DOWNLOAD_FIRST -> {
                    val pullRes = pullAndInsertMissing()
                    if (!pullRes.ok) return@withContext pullRes
                    val pushRes = pushAll()
                    if (!pushRes.ok) return@withContext pushRes
                    pullAndMerge()
                }
                FirstSyncMode.MERGE -> syncLocked()
            }
        }
    }

    data class SyncReport(
        val ok: Boolean,
        val pushed: Int,
        val pulled: Int,
        val remoteDeleted: Int,
        val conflicts: Int,
        val message: String
    ) {
        companion object {
            fun failed(message: String) = SyncReport(false, 0, 0, 0, 0, message)
        }
    }

    /** Manual or automatic sync. No-op unless the user enabled the integration. */
    suspend fun syncNow(): SyncReport = withContext(io) {
        mutex.withLock { syncLocked() }
    }

    // ---- internals (must hold mutex) ----

    private suspend fun syncLocked(): SyncReport {
        if (!isEnabled) return SyncReport.failed("Sync is not enabled.")
        if (!isOnline()) {
            prefs.lastSyncError = "No internet connection."
            return SyncReport.failed("No internet connection. Changes are saved locally and marked pending.")
        }
        val pushRes = pushAll()
        if (!pushRes.ok) return pushRes
        return pullAndMerge()
    }

    /** Pushes every unsynced row. Returns failed report on first hard failure. */
    private suspend fun pushAll(): SyncReport {
        val url = prefs.webAppUrl
        val token = prefs.token
        val unsynced = repo.getUnsynced()
        if (unsynced.isEmpty()) return SyncReport(true, 0, 0, 0, 0, "")

        var pushed = 0
        val tombstones = unsynced.filter { it.deleted }
        val upserts = unsynced.filter { !it.deleted }

        // Deletes first, one by one (each delete is idempotent).
        for (t in tombstones) {
            val res = client.delete(url, token, t.id)
            if (res.ok) {
                repo.purgeTombstone(t.id)
                pushed++
            } else {
                repo.markSyncError(t.id, res.error)
                return SyncReport.failed("Couldn't delete \"${t.taskName}\" on the sheet: ${res.error}. Will retry.")
            }
        }
        // Upserts in one batch; fall back to single calls on failure.
        if (upserts.isNotEmpty()) {
            val batch = client.batchUpsert(url, token, upserts)
            if (batch.ok) {
                upserts.forEach { repo.markSynced(it.id) }
                pushed += upserts.size
            } else {
                var anyOk = false
                for (t in upserts) {
                    val single = client.upsert(url, token, t)
                    if (single.ok) {
                        repo.markSynced(t.id); pushed++; anyOk = true
                    } else {
                        repo.markSyncError(t.id, single.error)
                    }
                }
                if (!anyOk) {
                    return SyncReport.failed("Couldn't upload changes: ${batch.error}. Will retry.")
                }
            }
        }
        return SyncReport(true, pushed, 0, 0, 0, "")
    }

    /** Pulls a snapshot and inserts remote tasks missing locally (used by DOWNLOAD_FIRST). */
    private suspend fun pullAndInsertMissing(): SyncReport {
        val snap = client.snapshot(prefs.webAppUrl, prefs.token)
        if (!snap.ok) return SyncReport.failed("Couldn't read the sheet: ${snap.error}")
        var inserted = 0
        for (remote in snap.data!!) {
            if (repo.getById(remote.id) == null) {
                repo.applyRemoteTask(remote)
                inserted++
            }
        }
        prefs.lastSyncedIds = snap.data!!.map { it.id }.toSet()
        return SyncReport(true, 0, inserted, 0, 0, "")
    }

    /**
     * Pulls a complete snapshot and merges it. Deletions are reconciled only
     * against IDs seen in the last successful snapshot.
     */
    private suspend fun pullAndMerge(): SyncReport {
        val snap = client.snapshot(prefs.webAppUrl, prefs.token)
        if (!snap.ok) {
            prefs.lastSyncError = snap.error
            return SyncReport.failed("Couldn't read the sheet: ${snap.error}. Local tasks are unchanged.")
        }
        val remote = snap.data!!
        val remoteById = remote.associateBy { it.id }
        var pulled = 0
        var conflicts = 0

        for (r in remote) {
            val local = repo.getById(r.id)
            if (local == null) {
                repo.applyRemoteTask(r)
                pulled++
            } else if (local.deleted) {
                continue // tombstone already pushed; purge happened in pushAll
            } else if (local.syncState == Task.SYNC_STATE_PENDING || local.syncState == Task.SYNC_STATE_ERROR) {
                // Local unsynced edit wins; count a conflict if remote also changed.
                val prevIds = prefs.lastSyncedIds
                if (r.id in prevIds) conflicts++
                // Re-push the local version so the sheet converges to it.
                val up = client.upsert(prefs.webAppUrl, prefs.token, local)
                if (up.ok) repo.markSynced(local.id) else repo.markSyncError(local.id, up.error)
            } else {
                // Both synced before: newer Last Updated wins.
                if (Dates.compareLastUpdated(r.lastUpdated, local.lastUpdated) > 0) {
                    repo.applyRemoteTask(r)
                    pulled++
                }
            }
        }

        // Remote deletion reconciliation — only with a complete validated snapshot.
        var remoteDeleted = 0
        val previousIds = prefs.lastSyncedIds
        if (previousIds.isNotEmpty()) {
            for (id in previousIds) {
                if (id !in remoteById) {
                    if (repo.applyRemoteDeletion(id)) remoteDeleted++
                }
            }
        }
        prefs.lastSyncedIds = remoteById.keys

        val now = Dates.nowString()
        prefs.lastSyncTime = now
        prefs.lastSyncError = ""
        val parts = mutableListOf<String>()
        if (pulled > 0) parts.add("$pulled from sheet")
        parts.add("synced just now")
        return SyncReport(
            ok = true,
            pushed = 0,
            pulled = pulled,
            remoteDeleted = remoteDeleted,
            conflicts = conflicts,
            message = "Sync complete" +
                (if (pulled > 0 || remoteDeleted > 0) " ($pulled new/updated, $remoteDeleted removed from sheet)" else "") +
                (if (conflicts > 0) ". $conflicts conflict(s): your unsynced edits were kept." else ".")
        )
    }
}
