package com.tasktracker.pro.reminders

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tasktracker.pro.TaskTrackerApp
import com.tasktracker.pro.appContainer
import com.tasktracker.pro.data.Dates
import com.tasktracker.pro.data.ReminderPlanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Fires one task's exact-time reminder. Re-reads the task from the database
 * before notifying, so a task that was completed, deleted, or had its
 * reminder moved never produces a stale alert. The notification carries a
 * "Done" action (see TaskActionReceiver) to complete the task in place.
 */
class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_TASK_REMINDER) return
        val taskId = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_ID) ?: return
        val scheduledFor = intent.getLongExtra(ReminderScheduler.EXTRA_TRIGGER_AT, 0L)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val container = context.appContainer()
                val task = container.repository.getById(taskId)
                // The alarm has fired either way — drop its bookkeeping.
                container.taskReminders.forget(taskId)
                if (task == null || task.deleted || task.isCompleted) return@launch
                // Stale alarm (reminder was edited after this alarm was set)?
                val current = Dates.reminderToMillis(task.reminderAt) ?: return@launch
                if (abs(current - scheduledFor) > STALE_TOLERANCE_MS) return@launch

                if (ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) return@launch

                val notificationId = ReminderPlanner.notificationIdFor(taskId)
                val openIntent = context.packageManager
                    .getLaunchIntentForPackage(context.packageName)
                val contentPi = PendingIntent.getActivity(
                    context, notificationId, openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val doneIntent = Intent(context, TaskActionReceiver::class.java).apply {
                    action = TaskActionReceiver.ACTION_TASK_DONE
                    putExtra(TaskActionReceiver.EXTRA_TASK_ID, taskId)
                    putExtra(TaskActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                }
                val donePi = PendingIntent.getBroadcast(
                    context, notificationId, doneIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val text = buildString {
                    append("Time to do this task")
                    if (task.vendor.isNotBlank()) append(" — ${task.vendor}")
                    if (task.deadline.isNotBlank()) append(" (deadline ${task.deadline})")
                    append(".")
                }
                val notification = NotificationCompat.Builder(context, TaskTrackerApp.CHANNEL_REMINDERS)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("⏰ ${task.taskName}")
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setContentIntent(contentPi)
                    .addAction(0, "Done", donePi)
                    .setAutoCancel(true)
                    .build()
                NotificationManagerCompat.from(context).notify(notificationId, notification)
            } catch (e: Exception) {
                // Never crash on a broadcast.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val STALE_TOLERANCE_MS = 60_000L
    }
}
