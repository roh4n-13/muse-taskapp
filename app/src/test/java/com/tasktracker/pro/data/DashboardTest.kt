package com.tasktracker.pro.data

import com.tasktracker.pro.data.Dashboard.TaskFilter
import com.tasktracker.pro.data.Dashboard.TaskSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DashboardTest {

    private val today = LocalDate.of(2026, 10, 20)

    private fun task(
        id: String,
        name: String,
        progress: String = Task.PROGRESS_NOT_STARTED,
        priority: String = "",
        deadline: String = "",
        deleted: Boolean = false,
        lastUpdated: String = "",
        taskCompleted: String = "",
        notes: String = "",
        vendor: String = "",
        taskFocus: String = ""
    ) = Task(
        id = id,
        taskName = name,
        progress = progress,
        priority = priority,
        deadline = deadline,
        deleted = deleted,
        lastUpdated = lastUpdated,
        taskCompleted = taskCompleted,
        notes = notes,
        vendor = vendor,
        taskFocus = taskFocus
    )

    // ---------- computeStats ----------

    @Test
    fun `computeStats on empty list is all zeros with pct 0 and no crash`() {
        val s = Dashboard.computeStats(emptyList(), today)
        assertEquals(0, s.total)
        assertEquals(0, s.pending)
        assertEquals(0, s.completed)
        assertEquals(0, s.overdue)
        assertEquals(0, s.dueToday)
        assertEquals(0, s.completionPct)
        assertEquals(0, s.upcoming)
        assertEquals(0, s.highPriorityPending)
        assertEquals(0, s.needsAttention)
    }

    @Test
    fun `computeStats derives every metric from real tasks`() {
        val tasks = listOf(
            task("c1", "Done thing", progress = Task.PROGRESS_COMPLETED),
            task("o1", "Overdue thing", deadline = "2026-10-19"),
            task("t1", "Today thing", deadline = "2026-10-20", priority = Task.PRIORITY_HIGH),
            task("u1", "Upcoming thing", deadline = "2026-10-22")
        )
        val s = Dashboard.computeStats(tasks, today)
        assertEquals(4, s.total)
        assertEquals(3, s.pending)
        assertEquals(1, s.completed)
        assertEquals(1, s.overdue)
        assertEquals(1, s.dueToday)
        assertEquals(25, s.completionPct) // 1 of 4
        assertEquals(1, s.upcoming) // u1 only; t1 is due today, not "upcoming"
        assertEquals(1, s.highPriorityPending)
        assertEquals(2, s.needsAttention) // overdue o1 + high-priority t1
    }

    @Test
    fun `computeStats ignores deleted tasks`() {
        val tasks = listOf(
            task("k1", "Kept", progress = Task.PROGRESS_COMPLETED),
            task("d1", "Deleted", deleted = true)
        )
        val s = Dashboard.computeStats(tasks, today)
        assertEquals(1, s.total)
        assertEquals(1, s.completed)
        assertEquals(100, s.completionPct)
    }

    // ---------- pickNextAction ----------

    private fun actionCandidates() = listOf(
        task("up", "Upcoming", deadline = "2026-10-25"),
        task("td", "Due today", deadline = "2026-10-20"),
        task("hp", "High priority soon", deadline = "2026-10-22", priority = Task.PRIORITY_HIGH),
        task("od", "Overdue", deadline = "2026-10-15"),
        task("done", "Finished", progress = Task.PROGRESS_COMPLETED, deadline = "2026-10-01"),
        task("del", "Gone", deleted = true, deadline = "2026-10-01")
    )

    @Test
    fun `pickNextAction chooses overdue first`() {
        val next = Dashboard.pickNextAction(actionCandidates(), today)
        assertNotNull(next)
        assertEquals("od", next!!.task.id)
        assertTrue(next.reason.contains("overdue", ignoreCase = true))
    }

    @Test
    fun `pickNextAction chooses high priority due soon when no overdue`() {
        val tasks = actionCandidates().filter { it.id != "od" }
        val next = Dashboard.pickNextAction(tasks, today)
        assertNotNull(next)
        assertEquals("hp", next!!.task.id)
        assertTrue(next.reason.contains("High priority", ignoreCase = true))
    }

    @Test
    fun `pickNextAction chooses due today over upcoming`() {
        val tasks = actionCandidates().filter { it.id == "td" || it.id == "up" }
        val next = Dashboard.pickNextAction(tasks, today)
        assertNotNull(next)
        assertEquals("td", next!!.task.id)
    }

    @Test
    fun `pickNextAction falls back to upcoming then oldest pending`() {
        val onlyUpcoming = listOf(task("up", "Upcoming", deadline = "2026-10-25"))
        assertEquals("up", Dashboard.pickNextAction(onlyUpcoming, today)!!.task.id)

        // No deadlines at all: deterministic tie-break by name.
        val noDeadlines = listOf(task("b", "Beta"), task("a", "Alpha"))
        val next = Dashboard.pickNextAction(noDeadlines, today)
        assertNotNull(next)
        assertEquals("a", next!!.task.id)
    }

    @Test
    fun `pickNextAction returns null when nothing is pending`() {
        assertNull(Dashboard.pickNextAction(emptyList(), today))
        assertNull(
            Dashboard.pickNextAction(
                listOf(task("c1", "Done", progress = Task.PROGRESS_COMPLETED)),
                today
            )
        )
        assertNull(
            Dashboard.pickNextAction(
                listOf(task("d1", "Gone", deleted = true)),
                today
            )
        )
    }

    @Test
    fun `pickNextAction never recommends completed or deleted tasks`() {
        val tasks = listOf(
            task("c1", "Done overdue", progress = Task.PROGRESS_COMPLETED, deadline = "2026-10-01"),
            task("d1", "Deleted overdue", deleted = true, deadline = "2026-10-01")
        )
        assertNull(Dashboard.pickNextAction(tasks, today))
    }

    @Test
    fun `pickNextAction explains the reason for an overdue high priority task`() {
        val t = task("x", "Urgent", deadline = "2026-10-10", priority = Task.PRIORITY_HIGH)
        val next = Dashboard.pickNextAction(listOf(t), today)!!
        assertTrue(next.reason.contains("overdue", ignoreCase = true))
        assertTrue(next.reason.contains("High Priority", ignoreCase = true))
    }

    // ---------- filterAndSort ----------

    private fun mixedTasks() = listOf(
        task("p1", "Buy groceries", deadline = "2026-10-19", notes = "milk and eggs"),
        task("p2", "Call vendor", deadline = "2026-10-20", priority = Task.PRIORITY_HIGH, vendor = "Acme Traders"),
        task("p3", "Gym session", deadline = "2026-10-25", priority = Task.PRIORITY_MEDIUM, taskFocus = "Health"),
        task("p4", "Read book"),
        task("c1", "Old chore", progress = Task.PROGRESS_COMPLETED, deadline = "2026-10-18"),
        task("d1", "Deleted task", deleted = true, deadline = "2026-10-10")
    )

    @Test
    fun `filterAndSort matches query across name notes vendor and focus`() {
        val tasks = mixedTasks()
        assertEquals(listOf("p1"), Dashboard.filterAndSort(tasks, query = "milk", today = today).map { it.id })
        assertEquals(listOf("p2"), Dashboard.filterAndSort(tasks, query = "acme", today = today).map { it.id })
        assertEquals(listOf("p3"), Dashboard.filterAndSort(tasks, query = "health", today = today).map { it.id })
        assertEquals(listOf("p1"), Dashboard.filterAndSort(tasks, query = "BUY", today = today).map { it.id })
        assertTrue(Dashboard.filterAndSort(tasks, query = "zzz", today = today).isEmpty())
    }

    @Test
    fun `filterAndSort applies every TaskFilter`() {
        val tasks = mixedTasks()
        fun ids(filter: TaskFilter) =
            Dashboard.filterAndSort(tasks, filter = filter, today = today).map { it.id }.toSet()

        assertEquals(setOf("p1", "p2", "p3", "p4", "c1"), ids(TaskFilter.ALL))
        assertEquals(setOf("p1", "p2", "p3", "p4"), ids(TaskFilter.PENDING))
        assertEquals(setOf("c1"), ids(TaskFilter.COMPLETED))
        assertEquals(setOf("p1"), ids(TaskFilter.OVERDUE))
        assertEquals(setOf("p2"), ids(TaskFilter.TODAY))
        assertEquals(setOf("p2", "p3"), ids(TaskFilter.UPCOMING)) // today counts as upcoming
        assertEquals(setOf("p2"), ids(TaskFilter.HIGH_PRIORITY))
    }

    @Test
    fun `filterAndSort excludes deleted tasks from every view`() {
        val tasks = mixedTasks()
        val all = Dashboard.filterAndSort(tasks, query = "deleted", today = today)
        assertTrue(all.isEmpty())
    }

    @Test
    fun `filterAndSort sorts by deadline with unset deadlines last`() {
        val tasks = listOf(
            task("a", "A", deadline = "2026-10-25"),
            task("b", "B", deadline = "2026-10-19"),
            task("c", "C")
        )
        val ids = Dashboard.filterAndSort(tasks, sort = TaskSort.DEADLINE_ASC, today = today).map { it.id }
        assertEquals(listOf("b", "a", "c"), ids)
    }

    @Test
    fun `filterAndSort sorts by name case-insensitively`() {
        val tasks = listOf(task("1", "charlie"), task("2", "Alpha"), task("3", "bravo"))
        val ids = Dashboard.filterAndSort(tasks, sort = TaskSort.NAME_ASC, today = today).map { it.id }
        assertEquals(listOf("2", "3", "1"), ids)
    }

    @Test
    fun `filterAndSort sorts high priority first`() {
        val tasks = listOf(
            task("low", "Low", priority = Task.PRIORITY_LOW, deadline = "2026-10-19"),
            task("none", "None", deadline = "2026-10-19"),
            task("med", "Med", priority = Task.PRIORITY_MEDIUM, deadline = "2026-10-19"),
            task("high", "High", priority = Task.PRIORITY_HIGH, deadline = "2026-10-30")
        )
        val ids = Dashboard.filterAndSort(tasks, sort = TaskSort.PRIORITY_FIRST, today = today).map { it.id }
        assertEquals(listOf("high", "med", "low", "none"), ids)
    }

    @Test
    fun `filterAndSort sorts by most recently updated`() {
        val tasks = listOf(
            task("old", "Old", lastUpdated = "2026-10-18T10:00:00"),
            task("new", "New", lastUpdated = "2026-10-20T10:00:00"),
            task("mid", "Mid", lastUpdated = "2026-10-19T10:00:00")
        )
        val ids = Dashboard.filterAndSort(tasks, sort = TaskSort.RECENTLY_UPDATED, today = today).map { it.id }
        assertEquals(listOf("new", "mid", "old"), ids)
    }

    // ---------- daily report ----------

    @Test
    fun `buildDailyReport groups tasks by date correctly`() {
        val tasks = listOf(
            task("done-today", "Finished report", progress = Task.PROGRESS_COMPLETED, taskCompleted = "2026-10-20T12:00:00"),
            task("done-old", "Old win", progress = Task.PROGRESS_COMPLETED, taskCompleted = "2026-10-19T12:00:00"),
            task("od", "Late bill", deadline = "2026-10-18"),
            task("td", "Call bank", deadline = "2026-10-20"),
            task("hp", "Big launch", deadline = "2026-10-28", priority = Task.PRIORITY_HIGH)
        )
        val report = Dashboard.buildDailyReport(tasks, today)
        assertEquals(today, report.date)
        assertEquals(listOf("done-today"), report.completedToday.map { it.id })
        assertEquals(listOf("od"), report.overdue.map { it.id })
        assertEquals(listOf("td"), report.dueToday.map { it.id })
        assertEquals(setOf("od", "hp"), report.pendingImportant.map { it.id }.toSet())
        assertEquals(5, report.stats.total)
        assertEquals(2, report.stats.completed)
    }

    @Test
    fun `formatDailyReport contains real task names and correct counts`() {
        val tasks = listOf(
            task("done-today", "Finished report", progress = Task.PROGRESS_COMPLETED, taskCompleted = "2026-10-20T12:00:00"),
            task("od", "Late bill", deadline = "2026-10-18"),
            task("td", "Call bank", deadline = "2026-10-20")
        )
        val text = Dashboard.formatDailyReport(Dashboard.buildDailyReport(tasks, today))
        assertTrue(text.contains("Finished report"))
        assertTrue(text.contains("Late bill"))
        assertTrue(text.contains("Call bank"))
        assertTrue(text.contains("Total: 3"))
        assertTrue(text.contains("Completed: 1"))
        assertTrue(text.contains("Overdue: 1"))
        assertTrue(text.contains("Done: 33%"))
        assertTrue(text.contains("Completed today:"))
        assertTrue(text.contains("Overdue:"))
        assertTrue(text.contains("Due today:"))
    }

    @Test
    fun `formatDailyReport handles a day with no tasks gracefully`() {
        val text = Dashboard.formatDailyReport(Dashboard.buildDailyReport(emptyList(), today))
        assertTrue(text.contains("Total: 0"))
        assertTrue(text.contains("No tasks yet"))
        assertFalse(text.contains("null"))
    }
}
