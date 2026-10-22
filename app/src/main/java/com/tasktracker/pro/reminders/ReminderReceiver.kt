package com.tasktracker.pro.reminders

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tasktracker.pro.TaskTrackerApp
import com.tasktracker.pro.appContainer
import com.tasktracker.pro.data.Dashboard
import com.tasktracker.pro.data.FollowUps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fires the daily reminder: counts pending tasks and posts one summary
 * notification, plus one notification per task whose follow-up is due
 * (today or overdue). Then schedules the next day — always, so the chain
 * survives quiet days with nothing pending. Never crashes the app.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_DAILY_REMINDER) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val container = context.appContainer()
                val enabled = container.settings.reminderEnabled.first()
                if (!enabled) return@launch
                val tasks = container.repository.getAllActive()

                val hasPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    val stats = Dashboard.computeStats(tasks)
                    if (stats.pending > 0) {
                        val text = if (stats.overdue > 0) {
                            "${stats.pending} pending tasks (${stats.overdue} overdue). Tap to review."
                        } else {
                            "${stats.pending} pending tasks. Tap to review."
                        }
                        notify(context, NOTIFICATION_ID, "Task Tracker Pro — daily reminder", text)
                    }
                    // One alert per due follow-up, capped so a long-neglected
                    // list can't flood the notification shade in a single day.
                    // Each carries a "Done" action to complete the task in place.
                    FollowUps.dueFollowUps(tasks).take(MAX_FOLLOWUP_NOTIFICATIONS)
                        .forEach { task ->
                            notify(
                                context,
                                com.tasktracker.pro.data.ReminderPlanner.notificationIdFor(task.id),
                                "Follow-up: ${task.taskName}",
                                FollowUps.notificationText(task),
                                doneTaskId = task.id
                            )
                        }
                }

                // Schedule tomorrow at the same configured time — even when
                // nothing was pending today, otherwise the chain would stop.
                val hour = container.settings.reminderHour.first()
                val minute = container.settings.reminderMinute.first()
                container.reminders.scheduleNextDay(hour, minute)
            } catch (e: Exception) {
                // Never crash on a broadcast.
            } finally {
                pending.finish()
            }
        }
    }

    private fun notify(
        context: Context,
        id: Int,
        title: String,
        text: String,
        doneTaskId: String? = null
    ) {
        val openIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
        val contentPi = android.app.PendingIntent.getActivity(
            context, id, openIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, TaskTrackerApp.CHANNEL_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentPi)
            .setAutoCancel(true)
        if (doneTaskId != null) {
            val doneIntent = android.content.Intent(context, TaskActionReceiver::class.java).apply {
                action = TaskActionReceiver.ACTION_TASK_DONE
                putExtra(TaskActionReceiver.EXTRA_TASK_ID, doneTaskId)
                putExtra(TaskActionReceiver.EXTRA_NOTIFICATION_ID, id)
            }
            val donePi = android.app.PendingIntent.getBroadcast(
                context, id, doneIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Done", donePi)
        }
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    companion object {
        private const val NOTIFICATION_ID = 2001
        private const val MAX_FOLLOWUP_NOTIFICATIONS = 5
    }
}
