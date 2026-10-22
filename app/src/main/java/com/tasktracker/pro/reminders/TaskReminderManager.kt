package com.tasktracker.pro.reminders

import android.content.Context
import com.tasktracker.pro.data.ReminderPlanner
import com.tasktracker.pro.data.Task

/**
 * Keeps AlarmManager in sync with the tasks that should have an exact-time
 * reminder. Strategy: remember the set of task IDs that currently have an
 * alarm (in plain SharedPreferences — IDs only, no task data); on every
 * refresh, cancel alarms whose tasks are no longer eligible and (re)set
 * alarms for the eligible ones. Self-healing: any drift (edits, completes,
 * deletes, reboots handled by BootReceiver) is corrected on the next
 * refresh, which runs after every local task mutation and on app start.
 */
class TaskReminderManager(context: Context, private val scheduler: ReminderScheduler) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun sync(tasks: List<Task>, nowMillis: Long = System.currentTimeMillis()) {
        val planned = ReminderPlanner.eligible(tasks, nowMillis)
        val newIds = planned.map { it.task.id }.toSet()
        val oldIds = prefs.getStringSet(KEY_IDS, emptySet()) ?: emptySet()
        for (gone in oldIds - newIds) {
            scheduler.cancelTaskReminder(gone)
        }
        for (p in planned) {
            scheduler.scheduleTaskReminder(p.task.id, p.task.taskName, p.triggerAtMillis)
        }
        prefs.edit().putStringSet(KEY_IDS, newIds).apply()
    }

    /** Drops a task's alarm + bookkeeping (e.g. right after its reminder fires). */
    fun forget(taskId: String) {
        scheduler.cancelTaskReminder(taskId)
        val old = prefs.getStringSet(KEY_IDS, emptySet()) ?: emptySet()
        if (taskId in old) {
            prefs.edit().putStringSet(KEY_IDS, old - taskId).apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "task_reminder_alarms"
        private const val KEY_IDS = "scheduled_ids"
    }
}
