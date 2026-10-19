package com.tasktracker.pro.data

import java.time.LocalDate

/**
 * Pure domain logic: dashboard statistics, Smart Next Action, daily report,
 * search/filter/sort. No Android dependencies — fully unit-testable.
 */
object Dashboard {

    data class Stats(
        val total: Int,
        val pending: Int,
        val completed: Int,
        val overdue: Int,
        val dueToday: Int,
        /** 0..100, safe when there are no tasks. */
        val completionPct: Int,
        val upcoming: Int,
        val highPriorityPending: Int,
        val needsAttention: Int
    )

    fun computeStats(tasks: List<Task>, today: LocalDate = Dates.today()): Stats {
        val active = tasks.filter { !it.deleted }
        val completed = active.filter { it.isCompleted }
        val pending = active.filter { !it.isCompleted }
        val overdue = pending.filter { Dates.isOverdue(it.deadline, today) }
        val dueToday = pending.filter { Dates.isToday(it.deadline, today) }
        val upcoming = pending.filter { Dates.isUpcoming(it.deadline, today) && !Dates.isToday(it.deadline, today) }
        val highPriority = pending.filter { it.priority == Task.PRIORITY_HIGH }
        val needsAttention = pending.filter {
            Dates.isOverdue(it.deadline, today) || it.priority == Task.PRIORITY_HIGH
        }
        val pct = if (active.isEmpty()) 0 else (completed.size * 100 / active.size)
        return Stats(
            total = active.size,
            pending = pending.size,
            completed = completed.size,
            overdue = overdue.size,
            dueToday = dueToday.size,
            completionPct = pct,
            upcoming = upcoming.size,
            highPriorityPending = highPriority.size,
            needsAttention = needsAttention.size
        )
    }

    data class NextAction(val task: Task, val reason: String)

    /**
     * Deterministic rule-based recommendation. Priority order:
     * overdue → high-priority due soon → due today → upcoming → any other pending.
     * Never recommends completed or deleted tasks. Null when nothing is pending.
     */
    fun pickNextAction(tasks: List<Task>, today: LocalDate = Dates.today()): NextAction? {
        val pending = tasks.filter { !it.deleted && !it.isCompleted }
        if (pending.isEmpty()) return null

        fun earliestDeadline(list: List<Task>): Task? =
            list.minWithOrNull(
                compareBy({ Dates.parseDate(it.deadline) ?: LocalDate.MAX }, { it.taskName })
            )

        earliestDeadline(pending.filter { Dates.isOverdue(it.deadline, today) })?.let {
            return NextAction(it, "This task is overdue" + prioritySuffix(it))
        }
        earliestDeadline(
            pending.filter {
                it.priority == Task.PRIORITY_HIGH && Dates.isUpcoming(it.deadline, today)
            }
        )?.let {
            return NextAction(it, "High priority and due soon" + dueSuffix(it, today))
        }
        earliestDeadline(pending.filter { Dates.isToday(it.deadline, today) })?.let {
            return NextAction(it, "Due today" + prioritySuffix(it))
        }
        earliestDeadline(pending.filter { Dates.isUpcoming(it.deadline, today) })?.let {
            return NextAction(it, "Coming up soon" + dueSuffix(it, today))
        }
        earliestDeadline(pending)?.let {
            return NextAction(it, "Oldest pending task by deadline" + prioritySuffix(it))
        }
        return null
    }

    private fun prioritySuffix(t: Task): String =
        if (t.priority == Task.PRIORITY_HIGH) " and marked High Priority." else "."

    private fun dueSuffix(t: Task, today: LocalDate): String {
        val d = Dates.parseDate(t.deadline) ?: return "."
        return " (due ${Dates.formatDateForDisplay(t.deadline)})."
    }

    // ---------------- search / filter / sort ----------------

    enum class TaskFilter { ALL, PENDING, COMPLETED, OVERDUE, TODAY, UPCOMING, HIGH_PRIORITY }
    enum class TaskSort { DEADLINE_ASC, RECENTLY_UPDATED, NAME_ASC, PRIORITY_FIRST }

    fun filterAndSort(
        tasks: List<Task>,
        query: String = "",
        filter: TaskFilter = TaskFilter.ALL,
        sort: TaskSort = TaskSort.DEADLINE_ASC,
        today: LocalDate = Dates.today()
    ): List<Task> {
        var list = tasks.filter { !it.deleted }
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter {
                it.taskName.lowercase().contains(q) ||
                    it.notes.lowercase().contains(q) ||
                    it.vendor.lowercase().contains(q) ||
                    it.taskFocus.lowercase().contains(q)
            }
        }
        list = when (filter) {
            TaskFilter.ALL -> list
            TaskFilter.PENDING -> list.filter { !it.isCompleted }
            TaskFilter.COMPLETED -> list.filter { it.isCompleted }
            TaskFilter.OVERDUE -> list.filter { !it.isCompleted && Dates.isOverdue(it.deadline, today) }
            TaskFilter.TODAY -> list.filter { !it.isCompleted && Dates.isToday(it.deadline, today) }
            TaskFilter.UPCOMING -> list.filter { !it.isCompleted && Dates.isUpcoming(it.deadline, today) }
            TaskFilter.HIGH_PRIORITY -> list.filter { !it.isCompleted && it.priority == Task.PRIORITY_HIGH }
        }
        val priorityRank = mapOf(Task.PRIORITY_HIGH to 0, Task.PRIORITY_MEDIUM to 1, Task.PRIORITY_LOW to 2, "" to 3)
        list = when (sort) {
            TaskSort.DEADLINE_ASC -> list.sortedWith(
                compareBy({ Dates.parseDate(it.deadline) ?: LocalDate.MAX }, { it.taskName })
            )
            TaskSort.RECENTLY_UPDATED -> list.sortedWith { a, b ->
                -Dates.compareLastUpdated(a.lastUpdated, b.lastUpdated)
            }
            TaskSort.NAME_ASC -> list.sortedBy { it.taskName.lowercase() }
            TaskSort.PRIORITY_FIRST -> list.sortedWith(
                compareBy({ priorityRank[it.priority] ?: 3 },
                    { Dates.parseDate(it.deadline) ?: LocalDate.MAX })
            )
        }
        return list
    }

    // ---------------- daily report ----------------

    data class DailyReport(
        val date: LocalDate,
        val stats: Stats,
        val completedToday: List<Task>,
        val pendingImportant: List<Task>,
        val overdue: List<Task>,
        val dueToday: List<Task>
    )

    fun buildDailyReport(tasks: List<Task>, date: LocalDate = Dates.today()): DailyReport {
        val stats = computeStats(tasks, date)
        val active = tasks.filter { !it.deleted }
        val completedToday = active.filter {
            it.isCompleted && Dates.parseDateTime(it.taskCompleted)?.toLocalDate() == date
        }
        val pendingImportant = active
            .filter { !it.isCompleted && (it.priority == Task.PRIORITY_HIGH || Dates.isOverdue(it.deadline, date)) }
            .sortedWith(compareBy({ Dates.parseDate(it.deadline) ?: LocalDate.MAX }))
            .take(10)
        val overdue = active.filter { !it.isCompleted && Dates.isOverdue(it.deadline, date) }
        val dueToday = active.filter { !it.isCompleted && Dates.isToday(it.deadline, date) }
        return DailyReport(date, stats, completedToday, pendingImportant, overdue, dueToday)
    }

    fun formatDailyReport(report: DailyReport, appName: String = "Task Tracker Pro"): String {
        val s = report.stats
        val sb = StringBuilder()
        sb.appendLine("$appName — Daily Report")
        sb.appendLine(report.date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy")))
        sb.appendLine("────────────────────")
        sb.appendLine("Total: ${s.total} | Completed: ${s.completed} | Pending: ${s.pending}")
        sb.appendLine("Overdue: ${s.overdue} | Due today: ${s.dueToday} | Done: ${s.completionPct}%")
        if (report.completedToday.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("✅ Completed today:")
            report.completedToday.forEach { sb.appendLine("• ${it.taskName}") }
        }
        if (report.overdue.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("⚠️ Overdue:")
            report.overdue.forEach { sb.appendLine("• ${it.taskName} (was due ${it.deadline})") }
        }
        if (report.dueToday.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("📌 Due today:")
            report.dueToday.forEach { sb.appendLine("• ${it.taskName}") }
        }
        if (report.pendingImportant.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("🔥 Needs attention:")
            report.pendingImportant.forEach { sb.appendLine("• ${it.taskName}") }
        }
        if (s.total == 0) sb.appendLine("\nNo tasks yet. Add your first task to get started!")
        sb.appendLine()
        sb.append(summaryLine(s))
        return sb.toString()
    }

    private fun summaryLine(s: Stats): String = when {
        s.total == 0 -> "A fresh start — every finished task begins with one tap."
        s.overdue > 0 -> "Focus on the ${s.overdue} overdue task(s) first — small wins build momentum."
        s.pending == 0 -> "All clear! Everything is done. 🎉"
        s.completionPct >= 75 -> "Strong progress at ${s.completionPct}% — finish the last few."
        else -> "${s.pending} task(s) pending. Pick one and start."
    }
}
