package com.tasktracker.pro.data

import java.time.LocalDate

/**
 * Pure logic for follow-up alerts: which tasks need a follow-up nudge today.
 * A task qualifies when it is not completed, not deleted, has a valid
 * "Next Follow-up" date, and that date is today or already past (an overdue
 * follow-up keeps nagging until the task is completed or the date moves).
 */
object FollowUps {

    /** Due follow-ups, oldest follow-up date first, then by task name. */
    fun dueFollowUps(tasks: List<Task>, today: LocalDate = Dates.today()): List<Task> =
        tasks
            .filter { !it.deleted && !it.isCompleted }
            .mapNotNull { task -> Dates.parseDate(task.nextFollowUp)?.let { date -> task to date } }
            .filter { (_, date) -> !date.isAfter(today) }
            .sortedWith(compareBy({ it.second }, { it.first.taskName.lowercase() }))
            .map { it.first }

    /** Short notification body for one due follow-up. */
    fun notificationText(task: Task, today: LocalDate = Dates.today()): String {
        val date = Dates.parseDate(task.nextFollowUp)
        val whenPart = when {
            date == null -> "Follow-up due"
            date.isBefore(today) -> "Follow-up overdue (was ${Dates.formatDateForDisplay(task.nextFollowUp)})"
            else -> "Follow-up due today"
        }
        val who = task.vendor.trim()
        return if (who.isNotEmpty()) "$whenPart — $who" else whenPart
    }
}
