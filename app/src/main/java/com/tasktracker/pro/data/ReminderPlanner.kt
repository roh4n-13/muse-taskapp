package com.tasktracker.pro.data

/**
 * Pure planning for exact-time per-task reminders. A task is eligible when
 * it is not deleted, not completed, has a valid `reminderAt`, and that time
 * is still in the future. Notification/request-code IDs are derived from
 * the task ID hash (String.hashCode is stable across runs), so the same
 * task always maps to the same alarm and the same notification.
 */
object ReminderPlanner {

    data class Planned(val task: Task, val triggerAtMillis: Long)

    fun eligible(tasks: List<Task>, nowMillis: Long): List<Planned> =
        tasks.mapNotNull { task ->
            if (task.deleted || task.isCompleted) return@mapNotNull null
            val trigger = Dates.reminderToMillis(task.reminderAt) ?: return@mapNotNull null
            if (trigger <= nowMillis) null else Planned(task, trigger)
        }.sortedBy { it.triggerAtMillis }

    fun notificationIdFor(taskId: String): Int =
        NOTIFICATION_BASE + (taskId.hashCode() and 0x1FFF)

    fun requestCodeFor(taskId: String): Int =
        REQUEST_BASE + (taskId.hashCode() and 0x7FFF)

    const val NOTIFICATION_BASE = 4000
    const val REQUEST_BASE = 20000
}
