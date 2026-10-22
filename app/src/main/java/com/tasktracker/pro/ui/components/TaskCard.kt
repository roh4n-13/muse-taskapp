package com.tasktracker.pro.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tasktracker.pro.data.Dates
import com.tasktracker.pro.data.Task
import com.tasktracker.pro.ui.formatDateField
import com.tasktracker.pro.ui.theme.TaskColors

/**
 * Reusable task row: title, priority chip, due date (red "Overdue" label when
 * overdue), progress chip, pending-sync indicator, tap to open details, and a
 * trailing checkbox button to complete/reopen. Optional delete action.
 */
@Composable
fun TaskCard(
    task: Task,
    onClick: () -> Unit,
    onToggleComplete: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null
) {
    val overdue = !task.isCompleted && Dates.isOverdue(task.deadline)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.taskName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PriorityChip(task.priority)
                    ProgressChip(task.progress)
                    if (task.deadline.isNotBlank()) {
                        DueChip(deadline = task.deadline, overdue = overdue)
                    }
                }
                if (task.syncState != Task.SYNC_STATE_SYNCED) {
                    Spacer(Modifier.height(6.dp))
                    SyncStateLabel(task)
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete task",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            IconButton(onClick = onToggleComplete) {
                if (task.isCompleted) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Reopen task",
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                } else {
                    Icon(
                        Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = "Mark task complete",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun PriorityChip(priority: String) {
    val color = when (priority) {
        Task.PRIORITY_HIGH -> TaskColors.HighPriority
        Task.PRIORITY_MEDIUM -> TaskColors.MediumPriority
        Task.PRIORITY_LOW -> TaskColors.LowPriority
        else -> null
    } ?: return
    Chip(text = priority, color = color)
}

@Composable
private fun ProgressChip(progress: String) {
    Chip(text = progress, color = MaterialTheme.colorScheme.secondary)
}

@Composable
private fun DueChip(deadline: String, overdue: Boolean) {
    val label = if (overdue) "Overdue · ${formatDateField(deadline)}" else formatDateField(deadline)
    Chip(
        text = label,
        color = if (overdue) TaskColors.Overdue else MaterialTheme.colorScheme.secondary
    )
}

@Composable
private fun SyncStateLabel(task: Task) {
    val (text, color) = when (task.syncState) {
        Task.SYNC_STATE_PENDING -> "Pending sync" to Color(0xFFB7791F)
        Task.SYNC_STATE_ERROR -> "Sync error" to MaterialTheme.colorScheme.error
        else -> return
    }
    Text(
        text = "● $text",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = color
    )
}

@Composable
private fun Chip(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1
        )
    }
}
