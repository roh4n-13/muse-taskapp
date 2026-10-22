package com.tasktracker.pro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.tasktracker.pro.appContainer
import com.tasktracker.pro.data.Costs
import com.tasktracker.pro.data.Dashboard
import com.tasktracker.pro.ui.formatTimestampDisplay
import com.tasktracker.pro.ui.navigation.Routes
import kotlinx.coroutines.launch

private data class SyncInfo(
    val configured: Boolean,
    val enabled: Boolean,
    val lastSync: String,
    val lastError: String
)

@Composable
fun DashboardScreen(navController: NavController, snackbarHostState: SnackbarHostState) {
    val container = LocalContext.current.appContainer()
    val scope = rememberCoroutineScope()
    val tasks by container.repository.observeActive()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val pendingSync by container.repository.observePendingCount()
        .collectAsStateWithLifecycle(initialValue = 0)

    // SecurePrefs are plain vars — re-read them whenever we return to this screen.
    var prefsTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) prefsTick++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    val syncInfo = remember(prefsTick) {
        val p = container.securePrefs
        SyncInfo(p.isConfigured, container.syncEngine.isEnabled, p.lastSyncTime, p.lastSyncError)
    }

    var syncing by remember { mutableStateOf(false) }

    val stats = remember(tasks) { Dashboard.computeStats(tasks) }
    val nextAction = remember(tasks) { Dashboard.pickNextAction(tasks) }
    val costs = remember(tasks) { Costs.summarize(tasks) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Dashboard",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            SyncStatusCard(
                info = syncInfo,
                pendingSync = pendingSync,
                onOpenSheets = { navController.navigate(Routes.SHEETS) }
            )
        }
        item {
            Text(
                "Overview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        item { StatsGrid(stats) }
        item {
            Text(
                "Kharcha",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        item { KharchaCard(costs) }
        item {
            NextActionCard(
                nextAction = nextAction,
                onOpenTask = { id -> navController.navigate(Routes.taskDetail(id)) },
                onAddTask = { navController.navigate(Routes.taskEdit()) }
            )
        }
        item {
            Text(
                "Quick actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickAction(
                        label = "Add Task",
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        onClick = { navController.navigate(Routes.taskEdit()) },
                        modifier = Modifier.weight(1f)
                    )
                    QuickAction(
                        label = "All Tasks",
                        icon = { Icon(Icons.Filled.List, contentDescription = null) },
                        onClick = { navController.navigate(Routes.TASKS) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickAction(
                        label = "Daily Report",
                        icon = { Icon(Icons.Filled.Description, contentDescription = null) },
                        onClick = { navController.navigate(Routes.REPORT) },
                        modifier = Modifier.weight(1f)
                    )
                    if (syncInfo.enabled) {
                        QuickAction(
                            label = if (syncing) "Syncing…" else "Sync Now",
                            icon = {
                                if (syncing) CircularProgressIndicator(
                                    modifier = Modifier.padding(2.dp),
                                    strokeWidth = 2.dp
                                )
                                else Icon(Icons.Filled.Sync, contentDescription = null)
                            },
                            onClick = {
                                if (syncing) return@QuickAction
                                scope.launch {
                                    syncing = true
                                    val report = container.syncEngine.syncNow()
                                    syncing = false
                                    prefsTick++
                                    snackbarHostState.showSnackbar(
                                        if (report.ok) report.message.ifBlank { "Sync complete." }
                                        else report.message
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        QuickAction(
                            label = "Connect Sheets",
                            icon = { Icon(Icons.Filled.Cloud, contentDescription = null) },
                            onClick = { navController.navigate(Routes.SHEETS) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncStatusCard(
    info: SyncInfo,
    pendingSync: Int,
    onOpenSheets: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (info.enabled) Icons.Filled.Cloud else Icons.Filled.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                val title = when {
                    !info.configured -> "Not connected"
                    info.enabled -> "Sync enabled"
                    else -> "Sync off"
                }
                Text(title, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when {
                        !info.configured ->
                            "Your tasks stay on this device. Google Sheets sync is optional."
                        info.enabled -> buildString {
                            append("Pending changes: $pendingSync")
                            if (info.lastSync.isBlank()) append("\nNot synced yet")
                            else append("\nLast synced: ${formatTimestampDisplay(info.lastSync)}")
                            if (info.lastError.isNotBlank()) append("\nLast error: ${info.lastError}")
                        }
                        else -> "Connection saved. Sync is off — enable it from the Sheets tab."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            if (!info.enabled) {
                TextButton(onClick = onOpenSheets) { Text("Open Sheets") }
            }
        }
    }
}

@Composable
private fun StatsGrid(stats: Dashboard.Stats) {
    val items = listOf(
        "Total tasks" to stats.total.toString(),
        "Pending" to stats.pending.toString(),
        "Completed" to stats.completed.toString(),
        "Overdue" to stats.overdue.toString(),
        "Due today" to stats.dueToday.toString(),
        "Upcoming" to stats.upcoming.toString(),
        "High priority" to stats.highPriorityPending.toString(),
        "Completion" to "${stats.completionPct}%"
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { (label, value) -> StatCard(label, value) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RowScope.StatCard(label: String, value: String) {
    Card(modifier = Modifier.weight(1f)) {
        Column(Modifier.padding(12.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun KharchaCard(costs: Costs.CostSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (!costs.hasMoney) {
                Text(
                    "No costs recorded yet. Add an Estimated or Actual Cost to any task and the totals will show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                return@Column
            }
            MoneyRow("Estimated total", Costs.formatInr(costs.totalEstimatedInr))
            MoneyRow("Actual total", Costs.formatInr(costs.totalActualInr))
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Amount due",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    Costs.formatInr(costs.amountDueInr),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            if (costs.amountDueInr > 0) {
                Text(
                    "${costs.pendingPaymentTasks} task(s) with payment pending",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(8.dp))
                costs.vendorDues.take(5).forEach { v ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${v.vendor} (${v.pendingTasks})",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            Costs.formatInr(v.dueInr),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                Text(
                    "All payments are clear 🎉",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun MoneyRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun NextActionCard(
    nextAction: Dashboard.NextAction?,
    onOpenTask: (String) -> Unit,
    onAddTask: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Smart Next Action",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            if (nextAction == null) {
                Text(
                    "All clear! Nothing is pending. Add a task whenever you're ready.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onAddTask) { Text("Add a task") }
            } else {
                Text(
                    nextAction.task.taskName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    nextAction.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { onOpenTask(nextAction.task.id) }) {
                    Text("Open task")
                }
            }
        }
    }
}

@Composable
private fun QuickAction(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp)
    ) {
        icon()
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1)
    }
}
