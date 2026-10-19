package com.tasktracker.pro.ui.navigation

/** Bottom-nav destinations. */
object Routes {
    const val DASHBOARD = "dashboard"
    const val TASKS = "tasks"
    const val COMPLETED = "completed"
    const val REPORT = "report"
    const val SHEETS = "sheets"
    const val SETTINGS = "settings"

    const val TASK_DETAIL = "task/{taskId}"
    const val TASK_EDIT = "edit?taskId={taskId}"

    fun taskDetail(taskId: String) = "task/$taskId"
    fun taskEdit(taskId: String? = null) =
        if (taskId == null) "edit" else "edit?taskId=$taskId"
}
