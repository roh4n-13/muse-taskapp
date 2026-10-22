package com.tasktracker.pro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskListVisibilityTest {

    private fun task(id: String, progress: String = Task.PROGRESS_NOT_STARTED, deleted: Boolean = false) =
        Task(id = id, taskName = "Task $id", progress = progress, deleted = deleted)

    @Test
    fun completedTasksAreNotInTheTaskList() {
        val visible = Dashboard.taskListVisible(
            listOf(
                task("pending"),
                task("doing", Task.PROGRESS_IN_PROGRESS),
                task("done", Task.PROGRESS_COMPLETED)
            )
        )
        assertEquals(listOf("pending", "doing"), visible.map { it.id })
    }

    @Test
    fun deletedTasksAreNotInTheTaskList() {
        val visible = Dashboard.taskListVisible(
            listOf(task("a"), task("b", deleted = true))
        )
        assertEquals(listOf("a"), visible.map { it.id })
    }

    @Test
    fun allCompletedMeansEmptyList() {
        val visible = Dashboard.taskListVisible(
            listOf(task("a", Task.PROGRESS_COMPLETED), task("b", Task.PROGRESS_COMPLETED))
        )
        assertTrue(visible.isEmpty())
    }
}
