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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.tasktracker.pro.appContainer
import com.tasktracker.pro.data.Dashboard
import com.tasktracker.pro.ui.components.TaskCard
import com.tasktracker.pro.ui.navigation.Routes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val filterLabels = mapOf(
    Dashboard.TaskFilter.ALL to "All",
    Dashboard.TaskFilter.PENDING to "Pending",
    Dashboard.TaskFilter.COMPLETED to "Completed",
    Dashboard.TaskFilter.OVERDUE to "Overdue",
    Dashboard.TaskFilter.TODAY to "Today",
    Dashboard.TaskFilter.UPCOMING to "Upcoming",
    Dashboard.TaskFilter.HIGH_PRIORITY to "High priority"
)

private val sortLabels = mapOf(
    Dashboard.TaskSort.DEADLINE_ASC to "Deadline",
    Dashboard.TaskSort.RECENTLY_UPDATED to "Recently updated",
    Dashboard.TaskSort.NAME_ASC to "Name",
    Dashboard.TaskSort.PRIORITY_FIRST to "Priority"
)

@Composable
fun TaskListScreen(navController: NavController, snackbarHostState: SnackbarHostState) {
    val container = LocalContext.current.appContainer()
    val repo = container.repository
    val scope = rememberCoroutineScope()
    val tasks by repo.observeActive().collectAsStateWithLifecycle(initialValue = emptyList())

    var query by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(Dashboard.TaskFilter.ALL) }
    var sort by remember { mutableStateOf(Dashboard.TaskSort.DEADLINE_ASC) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    // Debounce search input so filtering doesn't run on every keystroke.
    LaunchedEffect(query) {
        delay(300)
        debouncedQuery = query
    }

    val visible = remember(tasks, debouncedQuery, filter, sort) {
        Dashboard.filterAndSort(tasks, debouncedQuery, filter, sort)
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Routes.taskEdit()) }) {
                Icon(Icons.Filled.Add, contentDescription = "Add task")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search tasks") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Dashboard.TaskFilter.values().toList()) { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(filterLabels[f] ?: f.name) }
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${visible.size} task(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    sortLabels[sort] ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Box {
                    IconButton(onClick = { sortMenuOpen = true }) {
                        Icon(Icons.Filled.Sort, contentDescription = "Change sort order")
                    }
                    DropdownMenu(
                        expanded = sortMenuOpen,
                        onDismissRequest = { sortMenuOpen = false }
                    ) {
                        Dashboard.TaskSort.values().forEach { s ->
                            DropdownMenuItem(
                                text = { Text(sortLabels[s] ?: s.name) },
                                onClick = { sort = s; sortMenuOpen = false }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            when {
                tasks.isEmpty() -> ListEmptyState(
                    "No tasks yet.",
                    "Tap + to add your first task."
                )
                visible.isEmpty() -> ListEmptyState(
                    "No matches.",
                    "Try a different search or filter."
                )
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(visible, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            onClick = { navController.navigate(Routes.taskDetail(task.id)) },
                            onToggleComplete = {
                                scope.launch {
                                    val ok = if (task.isCompleted) repo.reopenTask(task.id)
                                    else repo.completeTask(task.id)
                                    if (!ok) snackbarHostState.showSnackbar("Couldn't update that task.")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ListEmptyState(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}
