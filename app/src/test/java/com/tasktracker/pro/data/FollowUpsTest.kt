package com.tasktracker.pro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FollowUpsTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 22)

    private fun task(
        id: String,
        progress: String = Task.PROGRESS_IN_PROGRESS,
        followUp: String = "",
        deleted: Boolean = false,
        vendor: String = ""
    ) = Task(
        id = id,
        taskName = "Task $id",
        progress = progress,
        nextFollowUp = followUp,
        deleted = deleted,
        vendor = vendor
    )

    @Test
    fun followUpDueTodayIsIncluded() {
        val due = FollowUps.dueFollowUps(listOf(task("a", followUp = "2026-10-22")), today)
        assertEquals(listOf("a"), due.map { it.id })
    }

    @Test
    fun overdueFollowUpIsIncluded() {
        val due = FollowUps.dueFollowUps(listOf(task("a", followUp = "2026-10-20")), today)
        assertEquals(listOf("a"), due.map { it.id })
    }

    @Test
    fun futureFollowUpIsNotDue() {
        val due = FollowUps.dueFollowUps(listOf(task("a", followUp = "2026-10-23")), today)
        assertTrue(due.isEmpty())
    }

    @Test
    fun completedAndDeletedTasksAreExcluded() {
        val due = FollowUps.dueFollowUps(
            listOf(
                task("done", progress = Task.PROGRESS_COMPLETED, followUp = "2026-10-22"),
                task("gone", followUp = "2026-10-22", deleted = true)
            ),
            today
        )
        assertTrue(due.isEmpty())
    }

    @Test
    fun blankOrInvalidFollowUpDateIsIgnored() {
        val due = FollowUps.dueFollowUps(
            listOf(task("a", followUp = ""), task("b", followUp = "next week")),
            today
        )
        assertTrue(due.isEmpty())
    }

    @Test
    fun oldestFollowUpComesFirst() {
        val due = FollowUps.dueFollowUps(
            listOf(
                task("newer", followUp = "2026-10-22"),
                task("older", followUp = "2026-10-19")
            ),
            today
        )
        assertEquals(listOf("older", "newer"), due.map { it.id })
    }

    @Test
    fun notificationTextMentionsVendorAndOverdue() {
        val text = FollowUps.notificationText(
            task("a", followUp = "2026-10-20", vendor = "Sharma Furniture"),
            today
        )
        assertTrue(text.contains("Sharma Furniture"))
        assertTrue(text.contains("overdue", ignoreCase = true))
    }
}
