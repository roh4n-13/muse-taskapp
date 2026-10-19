@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.tasktracker.pro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tasktracker.pro.appContainer
import com.tasktracker.pro.data.Task
import com.tasktracker.pro.ui.formatDateField
import com.tasktracker.pro.ui.formatTimestampDisplay
import com.tasktracker.pro.ui.navigation.Routes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    taskId: String,
    navController: NavController,
    snackbarHostState: SnackbarHostState
) {
    val container = LocalContext.current.appContainer()
    val repo = container.repository
    val scope = rememberCoroutineScope()

    var task by remember { mutableStateOf<Task?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(taskId) {
        repo.observeById(taskId).collect {
            task = it
            loaded = true
        }
    }

    if (!loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val t = task
    if (t == null || t.deleted) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                if (t?.deleted == true) "This task was deleted." else "Task not found.",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { navController.popBackStack() }) { Text("Back") }
        }
        return
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete task?") },
            text = { Text("\"${t.taskName}\" will be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        scope.launch {
                            repo.deleteTask(t.id)
                            navController.popBackStack()
                            val r = snackbarHostState.showSnackbar(
                                "Task deleted",
                                actionLabel = "Undo"
                            )
                            if (r == SnackbarResult.ActionPerformed) {
                                repo.restoreTask(t.id)
                            }
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Task details") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Routes.taskEdit(t.id)) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit task")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete task")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text(
                    t.taskName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
            }
            items(fieldRows(t)) { (label, value) ->
                FieldRow(label, value)
            }
            if (t.syncState != Task.SYNC_STATE_SYNCED) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "Sync status",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                when (t.syncState) {
                                    Task.SYNC_STATE_PENDING ->
                                        "Pending — will sync when Google Sheets sync is enabled."
                                    Task.SYNC_STATE_ERROR ->
                                        "Error: ${t.lastSyncError.ifBlank { "unknown error" }}"
                                    else -> "Synced"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                if (t.isCompleted) repo.reopenTask(t.id)
                                else repo.completeTask(t.id)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (t.isCompleted) "Reopen" else "Mark complete")
                    }
                    OutlinedButton(
                        onClick = { navController.navigate(Routes.taskEdit(t.id)) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Edit")
                    }
                }
            }
        }
    }
}

/** All 21 sheet fields in order; blank values are dropped by the caller. */
private fun fieldRows(t: Task): List<Pair<String, String>> = listOf(
    "ID" to t.id,
    "Task Name" to t.taskName,
    "Type" to t.type,
    "Progress" to t.progress,
    "Priority" to t.priority,
    "Deadline" to formatDateField(t.deadline),
    "Vendor / Craftsman" to t.vendor,
    "Contact Number" to t.contactNumber,
    "Estimated Cost (INR)" to t.estimatedCostInr.let { if (it.isBlank()) "" else "₹$it" },
    "Actual Cost (INR)" to t.actualCostInr.let { if (it.isBlank()) "" else "₹$it" },
    "Next Follow-up" to formatDateField(t.nextFollowUp),
    "Delivery Date" to formatDateField(t.deliveryDate),
    "Payment Status" to t.paymentStatus,
    "Link" to t.link,
    "Task Focus" to t.taskFocus,
    "Value Add" to t.valueAdd,
    "Notes" to t.notes,
    "Task Added" to formatTimestampDisplay(t.taskAdded),
    "Task Started" to formatTimestampDisplay(t.taskStarted),
    "Task Completed" to formatTimestampDisplay(t.taskCompleted),
    "Last Updated" to formatTimestampDisplay(t.lastUpdated)
).filter { (_, v) -> v.isNotBlank() && v != "—" }

@Composable
private fun FieldRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
