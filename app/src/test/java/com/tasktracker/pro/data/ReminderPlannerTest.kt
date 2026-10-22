package com.tasktracker.pro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderPlannerTest {

    private val nowMillis: Long = LocalDateTime.of(2026, 10, 22, 10, 0)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun task(
        id: String,
        reminderAt: String = "",
        progress: String = Task.PROGRESS_IN_PROGRESS,
        deleted: Boolean = false
    ) = Task(id = id, taskName = "Task $id", progress = progress, reminderAt = reminderAt, deleted = deleted)

    @Test
    fun futureReminderIsEligible() {
        val planned = ReminderPlanner.eligible(
            listOf(task("a", reminderAt = "2026-10-22T11:30:00")),
            nowMillis
        )
        assertEquals(listOf("a"), planned.map { it.task.id })
        assertTrue(planned[0].triggerAtMillis > nowMillis)
    }

    @Test
    fun pastOrBlankReminderIsNotEligible() {
        val planned = ReminderPlanner.eligible(
            listOf(
                task("past", reminderAt = "2026-10-22T09:00:00"),
                task("blank", reminderAt = ""),
                task("junk", reminderAt = "kal subah")
            ),
            nowMillis
        )
        assertTrue(planned.isEmpty())
    }

    @Test
    fun completedAndDeletedTasksAreNotEligible() {
        val planned = ReminderPlanner.eligible(
            listOf(
                task("done", reminderAt = "2026-10-23T09:00:00", progress = Task.PROGRESS_COMPLETED),
                task("gone", reminderAt = "2026-10-23T09:00:00", deleted = true)
            ),
            nowMillis
        )
        assertTrue(planned.isEmpty())
    }

    @Test
    fun plannedRemindersAreSortedByTime() {
        val planned = ReminderPlanner.eligible(
            listOf(
                task("later", reminderAt = "2026-10-25T09:00:00"),
                task("sooner", reminderAt = "2026-10-23T09:00:00")
            ),
            nowMillis
        )
        assertEquals(listOf("sooner", "later"), planned.map { it.task.id })
    }

    @Test
    fun idsAreStablePerTask() {
        assertEquals(
            ReminderPlanner.notificationIdFor("task-123"),
            ReminderPlanner.notificationIdFor("task-123")
        )
        assertEquals(
            ReminderPlanner.requestCodeFor("task-123"),
            ReminderPlanner.requestCodeFor("task-123")
        )
    }
}
