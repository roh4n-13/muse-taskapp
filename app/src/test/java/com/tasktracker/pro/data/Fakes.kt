package com.tasktracker.pro.data

import com.tasktracker.pro.data.sync.ApiResult
import com.tasktracker.pro.data.sync.PingInfo
import com.tasktracker.pro.data.sync.SheetsApi
import com.tasktracker.pro.data.sync.SyncPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory fake of [TaskDao] backed by a HashMap. Flows are driven by a
 * MutableStateFlow so they behave like Room's observable queries.
 */
class FakeTaskDao : TaskDao {
    private val store = HashMap<String, Task>()
    private val state = MutableStateFlow<List<Task>>(emptyList())

    private fun emit() {
        state.value = store.values.toList()
    }

    override fun observeActive(): Flow<List<Task>> =
        state.map { list -> list.filter { !it.deleted }.sortedByDescending { it.lastUpdated } }

    override fun observeCompleted(): Flow<List<Task>> =
        state.map { list ->
            list.filter { !it.deleted && it.isCompleted }.sortedByDescending { it.taskCompleted }
        }

    override fun observeById(id: String): Flow<Task?> =
        state.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun getById(id: String): Task? = store[id]

    override suspend fun getAllActive(): List<Task> = store.values.filter { !it.deleted }

    override suspend fun getUnsynced(): List<Task> =
        store.values.filter {
            it.syncState == Task.SYNC_STATE_PENDING || it.syncState == Task.SYNC_STATE_ERROR
        }

    override fun observePendingCount(): Flow<Int> =
        state.map { list ->
            list.count {
                !it.deleted &&
                    (it.syncState == Task.SYNC_STATE_PENDING || it.syncState == Task.SYNC_STATE_ERROR)
            }
        }

    override suspend fun upsert(task: Task) {
        store[task.id] = task
        emit()
    }

    override suspend fun insertAll(tasks: List<Task>) {
        tasks.forEach { store.putIfAbsent(it.id, it) }
        emit()
    }

    override suspend fun update(task: Task) {
        store[task.id] = task
        emit()
    }

    override suspend fun hardDelete(id: String) {
        store.remove(id)
        emit()
    }

    override suspend fun countActive(): Int = store.values.count { !it.deleted }
}

/**
 * Scriptable fake of the Apps Script backend. Every call is recorded so tests
 * can assert exactly what the sync engine attempted.
 */
class FakeSheetsApi : SheetsApi {
    var pingResult: ApiResult<PingInfo> = ApiResult.failure("ping not stubbed")
    var snapshotResult: ApiResult<List<Task>> = ApiResult.success(emptyList())
    var batchResult: ApiResult<Int> = ApiResult.success(0)
    var upsertHandler: (Task) -> ApiResult<String> = { ApiResult.success(it.id) }
    var deleteHandler: (String) -> ApiResult<Boolean> = { ApiResult.success(true) }

    /** Same URL rules as AppsScriptClient: https + script.google + /exec. */
    var urlValidator: (String) -> Boolean = { url ->
        val t = url.trim()
        t.startsWith("https://") &&
            t.contains("script.google") &&
            t.substringBefore("?").trimEnd('/').endsWith("/exec")
    }

    val pingCalls = mutableListOf<Pair<String, String>>()
    val snapshotCalls = mutableListOf<Pair<String, String>>()
    val upsertCalls = mutableListOf<Task>()
    val deleteCalls = mutableListOf<String>()
    val batchCalls = mutableListOf<List<Task>>()

    fun resetCalls() {
        pingCalls.clear()
        snapshotCalls.clear()
        upsertCalls.clear()
        deleteCalls.clear()
        batchCalls.clear()
    }

    override fun isValidWebAppUrl(url: String): Boolean = urlValidator(url)

    override fun ping(url: String, token: String): ApiResult<PingInfo> {
        pingCalls += url to token
        return pingResult
    }

    override fun snapshot(url: String, token: String): ApiResult<List<Task>> {
        snapshotCalls += url to token
        return snapshotResult
    }

    override fun upsert(url: String, token: String, task: Task): ApiResult<String> {
        upsertCalls += task
        return upsertHandler(task)
    }

    override fun delete(url: String, token: String, id: String): ApiResult<Boolean> {
        deleteCalls += id
        return deleteHandler(id)
    }

    override fun batchUpsert(url: String, token: String, tasks: List<Task>): ApiResult<Int> {
        batchCalls += tasks
        return batchResult
    }
}

/** In-memory fake of [SyncPrefs]; no Android storage involved. */
class FakeSyncPrefs : SyncPrefs {
    override var webAppUrl: String = ""
    override var token: String = ""
    override var syncEnabled: Boolean = false
    override var lastSyncTime: String = ""
    override var lastSyncError: String = ""
    override var lastSyncedIds: Set<String> = emptySet()

    override val isConfigured: Boolean
        get() = webAppUrl.isNotBlank() && token.isNotBlank()

    override fun clearConnection() {
        webAppUrl = ""
        token = ""
        syncEnabled = false
        lastSyncedIds = emptySet()
    }
}
