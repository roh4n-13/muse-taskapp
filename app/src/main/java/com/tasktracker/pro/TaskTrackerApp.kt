package com.tasktracker.pro

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.tasktracker.pro.data.BackupManager
import com.tasktracker.pro.data.SettingsStore
import com.tasktracker.pro.data.TaskDatabase
import com.tasktracker.pro.data.TaskRepository
import com.tasktracker.pro.data.sync.SecurePrefs
import com.tasktracker.pro.data.sync.SyncEngine
import com.tasktracker.pro.data.sync.SyncPoller
import com.tasktracker.pro.reminders.ReminderScheduler
import com.tasktracker.pro.reminders.TaskReminderManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
class TaskTrackerApp : Application() {
    val appScope = CoroutineScope(SupervisorJob())
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, appScope)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_REMINDERS,
            "Task reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Daily pending-task reminders and due-date alerts" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_REMINDERS = "reminders"
    }
}

fun Context.appContainer(): AppContainer =
    (applicationContext as TaskTrackerApp).container

/** Simple hand-rolled DI: one instance of each collaborator. */
class AppContainer(context: Context, val appScope: CoroutineScope) {
    private val appContext = context.applicationContext

    val database: TaskDatabase = TaskDatabase.get(appContext)
    val repository: TaskRepository = TaskRepository(database.taskDao())
    val securePrefs: SecurePrefs = SecurePrefs(appContext)
    val settings: SettingsStore = SettingsStore(appContext)
    val backup: BackupManager = BackupManager(repository)
    val reminders: ReminderScheduler = ReminderScheduler(appContext)
    val taskReminders: TaskReminderManager = TaskReminderManager(appContext, reminders)

    val syncEngine: SyncEngine = SyncEngine(
        repo = repository,
        prefs = securePrefs,
        isOnline = { isOnline(appContext) }
    )
    val syncPoller: SyncPoller = SyncPoller(syncEngine, intervalMs = 15_000L)

    init {
        // Instant upload: any local task change triggers a debounced sync (~2s),
        // so app → sheet no longer waits for the next poll tick. The same hook
        // re-plans exact-time task reminders so alarms always match the data.
        repository.onMutated = {
            syncEngine.notifyLocalChange(appScope)
            refreshTaskReminders()
        }
        // Heal any alarm drift (app update, restored data) on every start.
        refreshTaskReminders()
    }

    /** Recomputes which tasks should have an exact-time alarm set. */
    fun refreshTaskReminders() {
        appScope.launch {
            try {
                taskReminders.sync(repository.getAllActive())
            } catch (e: Exception) {
                // Best effort — the next mutation or start retries.
            }
        }
    }

    companion object {
        fun isOnline(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }
}
