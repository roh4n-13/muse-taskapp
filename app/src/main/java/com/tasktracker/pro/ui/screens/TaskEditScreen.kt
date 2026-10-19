@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.tasktracker.pro.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tasktracker.pro.appContainer
import com.tasktracker.pro.data.Dates
import com.tasktracker.pro.data.Task
import com.tasktracker.pro.ui.formatDateField
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(
    taskId: String?,
    navController: NavController,
    snackbarHostState: SnackbarHostState
) {
    val repo = LocalContext.current.appContainer().repository
    val isNew = taskId == null
    var existing by remember { mutableStateOf<Task?>(null) }
    var loading by remember { mutableStateOf(!isNew) }

    LaunchedEffect(taskId) {
        existing = if (taskId != null) repo.getById(taskId) else null
        loading = false
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (!isNew && existing == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Task not found.", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { navController.popBackStack() }) { Text("Back") }
        }
        return
    }

    // Key on the loaded task so form state initializes from it exactly once.
    key(existing?.id ?: "new") {
        TaskForm(
            initial = existing,
            navController = navController,
            snackbarHostState = snackbarHostState
        )
    }
}

@Composable
private fun TaskForm(
    initial: Task?,
    navController: NavController,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val repo = context.appContainer().repository
    val scope = rememberCoroutineScope()
    val isNew = initial == null

    var name by remember { mutableStateOf(initial?.taskName ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: "") }
    var progress by remember { mutableStateOf(initial?.progress ?: Task.PROGRESS_NOT_STARTED) }
    var priority by remember { mutableStateOf(initial?.priority ?: "") }
    var deadline by remember { mutableStateOf(initial?.deadline ?: "") }
    var vendor by remember { mutableStateOf(initial?.vendor ?: "") }
    var contact by remember { mutableStateOf(initial?.contactNumber ?: "") }
    var estCost by remember { mutableStateOf(initial?.estimatedCostInr ?: "") }
    var actCost by remember { mutableStateOf(initial?.actualCostInr ?: "") }
    var followUp by remember { mutableStateOf(initial?.nextFollowUp ?: "") }
    var delivery by remember { mutableStateOf(initial?.deliveryDate ?: "") }
    var payment by remember { mutableStateOf(initial?.paymentStatus ?: "") }
    var link by remember { mutableStateOf(initial?.link ?: "") }
    var focus by remember { mutableStateOf(initial?.taskFocus ?: "") }
    var valueAdd by remember { mutableStateOf(initial?.valueAdd ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }

    var nameError by remember { mutableStateOf<String?>(null) }
    var dateError by remember { mutableStateOf<String?>(null) }
    var costError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    fun validate(): Boolean {
        nameError = if (name.isBlank()) "Task name is required." else null
        dateError = listOf(
            "Deadline" to deadline,
            "Next follow-up" to followUp,
            "Delivery date" to delivery
        ).firstOrNull { (_, v) -> v.isNotBlank() && Dates.parseDate(v) == null }
            ?.let { (label, _) -> "$label is not a valid date." }
        costError = when {
            estCost.isNotBlank() && estCost.toDoubleOrNull() == null ->
                "Estimated cost must be a number."
            actCost.isNotBlank() && actCost.toDoubleOrNull() == null ->
                "Actual cost must be a number."
            else -> null
        }
        return nameError == null && dateError == null && costError == null
    }

    fun onSave() {
        if (saving || !validate()) return
        saving = true
        scope.launch {
            val ok = if (initial == null) {
                repo.createTask(
                    taskName = name,
                    type = type,
                    progress = progress,
                    priority = priority,
                    deadline = deadline,
                    vendor = vendor,
                    contactNumber = contact,
                    estimatedCostInr = estCost,
                    actualCostInr = actCost,
                    nextFollowUp = followUp,
                    deliveryDate = delivery,
                    paymentStatus = payment,
                    link = link,
                    taskFocus = focus,
                    valueAdd = valueAdd,
                    notes = notes
                ) != null
            } else {
                repo.updateTask(
                    initial.copy(
                        taskName = name,
                        type = type,
                        progress = progress,
                        priority = priority,
                        deadline = deadline,
                        vendor = vendor,
                        contactNumber = contact,
                        estimatedCostInr = estCost,
                        actualCostInr = actCost,
                        nextFollowUp = followUp,
                        deliveryDate = delivery,
                        paymentStatus = payment,
                        link = link,
                        taskFocus = focus,
                        valueAdd = valueAdd,
                        notes = notes
                    )
                )
            }
            saving = false
            if (ok) {
                navController.popBackStack()
            } else {
                snackbarHostState.showSnackbar("Couldn't save the task. Please check the fields and try again.")
            }
        }
    }

    if (showDeleteDialog && initial != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete task?") },
            text = { Text("\"${initial.taskName}\" will be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        scope.launch {
                            repo.deleteTask(initial.id)
                            navController.popBackStack()
                            val r = snackbarHostState.showSnackbar(
                                "Task deleted",
                                actionLabel = "Undo"
                            )
                            if (r == SnackbarResult.ActionPerformed) {
                                repo.restoreTask(initial.id)
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
                title = { Text(if (isNew) "New task" else "Edit task") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete task")
                        }
                    }
                    TextButton(onClick = { onSave() }, enabled = !saving) {
                        Text(if (saving) "Saving…" else "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = null },
                label = { Text("Task Name *") },
                isError = nameError != null,
                supportingText = { nameError?.let { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = type,
                onValueChange = { type = it },
                label = { Text("Type") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            DropdownField(
                label = "Progress",
                options = Task.PROGRESS_VALUES,
                selected = progress,
                onSelect = { progress = it }
            )
            DropdownField(
                label = "Priority",
                options = Task.PRIORITY_VALUES,
                selected = priority,
                display = { if (it.isBlank()) "None" else it },
                onSelect = { priority = it }
            )
            DateField(label = "Deadline", value = deadline, onChange = { deadline = it })
            OutlinedTextField(
                value = vendor,
                onValueChange = { vendor = it },
                label = { Text("Vendor / Craftsman") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = contact,
                onValueChange = { contact = it },
                label = { Text("Contact Number") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = estCost,
                    onValueChange = { estCost = it; costError = null },
                    label = { Text("Est. Cost (INR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = costError != null,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = actCost,
                    onValueChange = { actCost = it; costError = null },
                    label = { Text("Actual Cost (INR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = costError != null,
                    modifier = Modifier.weight(1f)
                )
            }
            costError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            DateField(label = "Next Follow-up", value = followUp, onChange = { followUp = it })
            DateField(label = "Delivery Date", value = delivery, onChange = { delivery = it })
            OutlinedTextField(
                value = payment,
                onValueChange = { payment = it },
                label = { Text("Payment Status") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                label = { Text("Link") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = focus,
                onValueChange = { focus = it },
                label = { Text("Task Focus") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = valueAdd,
                onValueChange = { valueAdd = it },
                label = { Text("Value Add") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            dateError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = { onSave() },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (saving) "Saving…" else "Save task")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DropdownField(
    label: String,
    options: List<String>,
    selected: String,
    display: (String) -> String = { it },
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = display(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )
        // Transparent overlay so tapping anywhere on the field opens the menu.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(display(opt)) },
                    onClick = { onSelect(opt); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun DateField(
    label: String,
    value: String,
    onChange: (String) -> Unit
) {
    val context = LocalContext.current
    OutlinedTextField(
        value = if (value.isBlank()) "" else formatDateField(value),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        placeholder = { Text("Not set") },
        trailingIcon = {
            Row {
                if (value.isNotBlank()) {
                    IconButton(onClick = { onChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear date")
                    }
                }
                IconButton(
                    onClick = {
                        val base = Dates.parseDate(value) ?: LocalDate.now()
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                onChange("%04d-%02d-%02d".format(y, m + 1, d))
                            },
                            base.year,
                            base.monthValue - 1,
                            base.dayOfMonth
                        ).show()
                    }
                ) {
                    Icon(Icons.Filled.DateRange, contentDescription = "Pick date")
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}
