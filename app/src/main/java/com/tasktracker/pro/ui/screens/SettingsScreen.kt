package com.tasktracker.pro.ui.screens

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tasktracker.pro.appContainer
import com.tasktracker.pro.data.Dates
import com.tasktracker.pro.data.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val container = context.appContainer()
    val settings = container.settings
    val scope = rememberCoroutineScope()
    val lifecycleOwner2 = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val themeMode by settings.theme.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    val reminderEnabled by settings.reminderEnabled.collectAsStateWithLifecycle(initialValue = false)
    val reminderHour by settings.reminderHour.collectAsStateWithLifecycle(initialValue = 9)
    val reminderMinute by settings.reminderMinute.collectAsStateWithLifecycle(initialValue = 0)

    var notice by remember { mutableStateOf<String?>(null) }
    var showRationale by remember { mutableStateOf(false) }
    var pendingImportText by remember { mutableStateOf<String?>(null) }
    var showImportConfirm by remember { mutableStateOf(false) }

    val activity = context as? Activity

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            scope.launch {
                if (granted) {
                    settings.setReminderEnabled(true)
                    container.reminders.scheduleDaily(reminderHour, reminderMinute)
                    notice = "Daily reminder turned on."
                } else {
                    notice = "Notification permission denied — reminders stay off. " +
                        "You can enable them later in Android Settings."
                }
            }
        }

    fun setReminder(on: Boolean) {
        if (!on) {
            scope.launch {
                settings.setReminderEnabled(false)
                container.reminders.cancelDaily()
                notice = "Daily reminder turned off."
            }
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (activity != null && ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            ) {
                showRationale = true
            } else {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            scope.launch {
                settings.setReminderEnabled(true)
                container.reminders.scheduleDaily(reminderHour, reminderMinute)
                notice = "Daily reminder turned on."
            }
        }
    }

    fun pickTime() {
        val enabledNow = reminderEnabled
        TimePickerDialog(
            context,
            { _, h, m ->
                scope.launch {
                    settings.setReminderTime(h, m)
                    if (enabledNow) container.reminders.scheduleDaily(h, m)
                    notice = "Reminder time updated."
                }
            },
            reminderHour,
            reminderMinute,
            false
        ).show()
    }

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val json = container.backup.exportToJson()
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(json.toByteArray())
                        } ?: throw IOException("Couldn't open the file for writing.")
                    }
                    notice = "Backup exported."
                } catch (e: Exception) {
                    notice = "Export failed: ${e.message}"
                }
            }
        }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                try {
                    val text = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                    }
                    if (text.isNullOrBlank()) {
                        notice = "Couldn't read that file."
                    } else {
                        pendingImportText = text
                        showImportConfirm = true
                    }
                } catch (e: Exception) {
                    notice = "Couldn't read that file: ${e.message}"
                }
            }
        }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("Allow notifications?") },
            text = {
                Text(
                    "Task Tracker Pro sends one daily reminder about your pending tasks. " +
                        "Without notification permission, reminders can't appear."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRationale = false
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                ) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) { Text("Not now") }
            }
        )
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text("Import backup?") },
            text = {
                Text(
                    "Merge import: tasks from the file are added to your existing tasks. " +
                        "Existing tasks are never replaced or deleted."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val res = withContext(Dispatchers.IO) {
                                container.backup.importFromJson(pendingImportText.orEmpty())
                            }
                            showImportConfirm = false
                            pendingImportText = null
                            notice = buildString {
                                append("Import done: ${res.imported} added, ${res.skipped} skipped.")
                                if (res.errors.isNotEmpty()) {
                                    append("\n")
                                    append(res.errors.take(3).joinToString("\n"))
                                }
                            }
                        }
                    }
                ) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false }) { Text("Cancel") }
            }
        )
    }

    val timeLabel = remember(reminderHour, reminderMinute) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, reminderHour)
            set(Calendar.MINUTE, reminderMinute)
        }
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(cal.time)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        notice?.let {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(it, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }

        // ---- appearance ----
        SectionTitle("Appearance")
        ThemeOption(
            label = "System default",
            selected = themeMode == ThemeMode.SYSTEM,
            onSelect = { scope.launch { settings.setTheme(ThemeMode.SYSTEM) } }
        )
        ThemeOption(
            label = "Light",
            selected = themeMode == ThemeMode.LIGHT,
            onSelect = { scope.launch { settings.setTheme(ThemeMode.LIGHT) } }
        )
        ThemeOption(
            label = "Dark",
            selected = themeMode == ThemeMode.DARK,
            onSelect = { scope.launch { settings.setTheme(ThemeMode.DARK) } }
        )

        HorizontalDivider()

        // ---- reminders ----
        SectionTitle("Daily reminder")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Remind me about pending tasks", fontWeight = FontWeight.SemiBold)
                Text(
                    "One notification a day with your pending tasks, plus a separate alert for each task whose follow-up is due that day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Switch(checked = reminderEnabled, onCheckedChange = { setReminder(it) })
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.width(8.dp))
            Text(timeLabel, modifier = Modifier.weight(1f))
            TextButton(onClick = { pickTime() }) { Text("Change time") }
        }
        if (!container.reminders.canScheduleExact()) {
            Text(
                "Note: exact alarms aren't allowed on this device, so reminders may arrive " +
                    "a few minutes late. To fix this, allow \"Alarms & reminders\" for " +
                    "Task Tracker Pro in Android Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        // Battery optimization is the other big cause of late reminders on
        // many phones (Xiaomi/Oppo/Vivo/Samsung): the system puts the app to
        // sleep and delivers its alarm minutes late. Offer the exemption.
        var batteryExempt by remember {
            mutableStateOf(isIgnoringBatteryOptimizations(context))
        }
        androidx.compose.runtime.DisposableEffect(lifecycleOwner2) {
            val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
                if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    batteryExempt = isIgnoringBatteryOptimizations(context)
                }
            }
            lifecycleOwner2.lifecycle.addObserver(obs)
            onDispose { lifecycleOwner2.lifecycle.removeObserver(obs) }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Reminders on exact time", fontWeight = FontWeight.SemiBold)
                Text(
                    if (batteryExempt)
                        "Battery optimization is OFF for this app — reminders will arrive on time."
                    else
                        "Battery saving can delay reminders by 10–15 minutes on many phones. Turn it off for this app so exact-time reminders arrive on time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            if (!batteryExempt) {
                TextButton(onClick = { requestIgnoreBatteryOptimizations(context) }) {
                    Text("Allow")
                }
            }
        }

        HorizontalDivider()

        // ---- backup ----
        SectionTitle("Backup")
        Text(
            "Export your tasks to a JSON file, or import a previous backup. " +
                "Importing merges — it never replaces or deletes existing tasks. " +
                "Backups contain tasks only, never your Google Sheets credentials.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    exportLauncher.launch("tasktracker-backup-${Dates.todayString()}.json")
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.FileUpload, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Export")
            }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json")) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.FileDownload, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Import")
            }
        }

        HorizontalDivider()

        // ---- about ----
        SectionTitle("About")
        Text("Task Tracker Pro", fontWeight = FontWeight.SemiBold)
        Text(
            "Version 1.0.0\nPackage: ${context.packageName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )

        HorizontalDivider()

        // ---- privacy ----
        SectionTitle("Privacy")
        Text(
            "Local-first: your tasks live on this device and work fully offline. " +
                "The Google Sheets URL and secret token are kept in encrypted " +
                "storage and are only used to talk to your own sheet. " +
                "Nothing is uploaded anywhere unless you explicitly enable sync.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}

/** True when the system lets this app run without battery-optimization delays. */
private fun isIgnoringBatteryOptimizations(context: android.content.Context): Boolean {
    val pm = context.getSystemService(android.os.PowerManager::class.java)
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

/** Opens the system dialog asking to exempt this app from battery optimization. */
private fun requestIgnoreBatteryOptimizations(context: android.content.Context) {
    val direct = android.content.Intent(
        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
    ).apply {
        data = android.net.Uri.parse("package:${context.packageName}")
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val opened = runCatching { context.startActivity(direct) }.isSuccess
    if (!opened) {
        runCatching {
            context.startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

@Composable
private fun ThemeOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
