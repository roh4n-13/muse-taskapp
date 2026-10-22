package com.tasktracker.pro.reminders

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tasktracker.pro.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles the "Done" action on task notifications (exact-time reminders and
 * follow-up alerts): completes the task through the repository — which also
 * triggers the usual instant sync and reminder re-planning — and dismisses
 * the notification.
 */
class TaskActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TASK_DONE) return
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                context.appContainer().repository.completeTask(taskId)
                if (notificationId >= 0) {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.cancel(notificationId)
                }
            } catch (e: Exception) {
                // Never crash on a broadcast.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TASK_DONE = "com.tasktracker.pro.TASK_DONE"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
