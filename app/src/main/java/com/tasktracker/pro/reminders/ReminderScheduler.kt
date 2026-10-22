package com.tasktracker.pro.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Schedules reminders with AlarmManager.
 *
 * Per-task exact-time reminders use [AlarmManager.setAlarmClock] — the same
 * mechanism as the phone's alarm-clock app. It is the most punctual API on
 * Android: it is exempt from Doze batching (which delays ordinary alarms by
 * up to ~15 minutes) and does not depend on the exact-alarm special access.
 * If it ever throws, we fall back to exact, then inexact, alarms.
 *
 * The daily summary reminder uses exact-when-permitted, else inexact —
 * a few minutes of drift there is harmless.
 */
class ReminderScheduler(private val context: Context) {

    fun canScheduleExact(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    fun scheduleDaily(hour: Int, minute: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = nextTrigger(hour, minute)
        val pi = pendingIntent()
        // Cancel any previous schedule first: exactly one daily alarm may exist.
        am.cancel(pi)
        if (canScheduleExact()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancelDaily() {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent())
    }

    /** Schedules tomorrow's alarm after today's fires (exact alarms don't repeat). */
    fun scheduleNextDay(hour: Int, minute: Int) = scheduleDaily(hour, minute)

    // ---- exact-time per-task reminders ----

    /**
     * Sets (or replaces) the one-shot exact-time alarm for a single task.
     * Primary path: setAlarmClock (alarm-clock grade punctuality — this is
     * the fix for reminders arriving ~10 minutes late when Android batched
     * an inexact alarm). Fallbacks: exact alarm, then inexact.
     */
    fun scheduleTaskReminder(taskId: String, taskName: String, triggerAtMillis: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = taskPendingIntent(taskId, taskName, triggerAtMillis)
        am.cancel(pi)
        try {
            val showIntent = context.packageManager
                .getLaunchIntentForPackage(context.packageName)
            val showPi = if (showIntent != null) {
                PendingIntent.getActivity(
                    context,
                    com.tasktracker.pro.data.ReminderPlanner.requestCodeFor(taskId),
                    showIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else null
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, showPi), pi)
            return
        } catch (e: Exception) {
            // Fall through to the exact/inexact chain below.
        }
        if (canScheduleExact()) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                return
            } catch (e: SecurityException) {
                // Permission revoked between check and call — use inexact.
            }
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
    }

    fun cancelTaskReminder(taskId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(taskPendingIntent(taskId, "", 0L))
    }

    private fun taskPendingIntent(taskId: String, taskName: String, triggerAtMillis: Long): PendingIntent {
        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = ACTION_TASK_REMINDER
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_NAME, taskName)
            putExtra(EXTRA_TRIGGER_AT, triggerAtMillis)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getBroadcast(
            context,
            com.tasktracker.pro.data.ReminderPlanner.requestCodeFor(taskId),
            intent,
            flags
        )
    }

    private fun nextTrigger(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_DAILY_REMINDER
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    companion object {
        const val ACTION_DAILY_REMINDER = "com.tasktracker.pro.DAILY_REMINDER"
        const val ACTION_TASK_REMINDER = "com.tasktracker.pro.TASK_REMINDER"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_NAME = "extra_task_name"
        const val EXTRA_TRIGGER_AT = "extra_trigger_at"
        private const val REQUEST_CODE = 1001
    }
}
