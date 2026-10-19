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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fires the daily reminder: counts pending tasks and posts one notification.
 * Then schedules the next day. Never crashes the app — failures are swallowed.
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
                val stats = Dashboard.computeStats(tasks)
                if (stats.pending == 0) return@launch

                if (ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) return@launch

                val text = if (stats.overdue > 0) {
                    "${stats.pending} pending tasks (${stats.overdue} overdue). Tap to review."
                } else {
                    "${stats.pending} pending tasks. Tap to review."
                }
                val openIntent = context.packageManager
                    .getLaunchIntentForPackage(context.packageName)
                val contentPi = android.app.PendingIntent.getActivity(
                    context, 0, openIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                        android.app.PendingIntent.FLAG_IMMUTABLE
                )
                val notification = NotificationCompat.Builder(context, TaskTrackerApp.CHANNEL_REMINDERS)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Task Tracker Pro — daily reminder")
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setContentIntent(contentPi)
                    .setAutoCancel(true)
                    .build()
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)

                // Schedule tomorrow at the same configured time.
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

    companion object {
        private const val NOTIFICATION_ID = 2001
    }
}
