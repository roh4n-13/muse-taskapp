package com.tasktracker.pro.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tasktracker.pro.appContainer
import com.tasktracker.pro.data.sync.SyncEngine
import com.tasktracker.pro.ui.formatTimestampDisplay
import kotlinx.coroutines.launch

private data class ConnState(
    val configured: Boolean,
    val enabled: Boolean,
    val lastSync: String,
    val lastError: String
)

private fun modeLabel(mode: SyncEngine.FirstSyncMode): String = when (mode) {
    SyncEngine.FirstSyncMode.MERGE ->
        "Merge — keep tasks from both sides"
    SyncEngine.FirstSyncMode.UPLOAD_FIRST ->
        "Upload first — send my tasks to the sheet, then pull"
    SyncEngine.FirstSyncMode.DOWNLOAD_FIRST ->
        "Download first — pull sheet tasks, then upload mine"
}

/**
 * "Connect Google Sheets" — optional integration. Everything here is explicit:
 * nothing connects, syncs, or changes tasks until the user taps the button.
 */
@Composable
fun SheetsScreen(snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val container = context.appContainer()
    val engine = container.syncEngine
    val prefs = container.securePrefs
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf(prefs.webAppUrl) }
    var token by remember { mutableStateOf(prefs.token) }
    var tokenVisible by remember { mutableStateOf(false) }
    var busyAction by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var showFirstSync by remember { mutableStateOf(false) }
    var showDisconnect by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<SyncEngine.FirstSyncPreview?>(null) }
    var firstSyncMode by remember { mutableStateOf(SyncEngine.FirstSyncMode.MERGE) }

    val pendingCount by container.repository.observePendingCount()
        .collectAsStateWithLifecycle(initialValue = 0)

    val conn = remember(tick) {
        ConnState(prefs.isConfigured, engine.isEnabled, prefs.lastSyncTime, prefs.lastSyncError)
    }

    fun say(msg: String, isError: Boolean = false) {
        statusMessage = msg
        statusIsError = isError
    }

    fun doDisconnect(clearCredentials: Boolean) {
        engine.disconnect(clearCredentials)
        container.syncPoller.stop()
        if (clearCredentials) {
            url = ""
            token = ""
        }
        showDisconnect = false
        tick++
        say(
            if (clearCredentials)
                "Disconnected and credentials cleared. Your local tasks are unchanged."
            else
                "Disconnected. Credentials kept — you can reconnect anytime. Your local tasks are unchanged."
        )
    }

    // ---- first-sync dialog ----
    val pv = preview
    if (showFirstSync && pv != null) {
        AlertDialog(
            onDismissRequest = { if (busyAction == null) showFirstSync = false },
            title = { Text("First sync") },
            text = {
                Column {
                    Text(
                        "Your phone has ${pv.localCount} task(s). " +
                            "The sheet has ${pv.remoteCount} task(s) " +
                            "(${pv.onlyRemote} only on the sheet, ${pv.overlappingIds} on both). " +
                            "Choose how to combine them:"
                    )
                    Spacer(Modifier.height(12.dp))
                    SyncEngine.FirstSyncMode.values().forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { firstSyncMode = mode }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = firstSyncMode == mode,
                                onClick = { firstSyncMode = mode }
                            )
                            Text(
                                modeLabel(mode),
                                modifier = Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    if (busyAction == "firstsync") {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Syncing…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = busyAction == null,
                    onClick = {
                        scope.launch {
                            busyAction = "firstsync"
                            val report = engine.firstSync(firstSyncMode)
                            busyAction = null
                            showFirstSync = false
                            tick++
                            if (report.ok) {
                                container.syncPoller.start(container.appScope)
                                say("Sync complete: ${report.message}")
                            } else {
                                say(report.message, isError = true)
                            }
                        }
                    }
                ) { Text("Start sync") }
            },
            dismissButton = {
                TextButton(
                    enabled = busyAction == null,
                    onClick = { showFirstSync = false }
                ) { Text("Cancel") }
            }
        )
    }

    // ---- disconnect dialog ----
    if (showDisconnect) {
        AlertDialog(
            onDismissRequest = { showDisconnect = false },
            title = { Text("Disconnect Google Sheets?") },
            text = {
                Text(
                    "Sync stops and no further requests are sent. " +
                        "Your local tasks stay exactly as they are."
                )
            },
            confirmButton = {
                TextButton(onClick = { doDisconnect(clearCredentials = true) }) {
                    Text("Clear credentials", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { doDisconnect(clearCredentials = false) }) {
                    Text("Keep credentials")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Connect Google Sheets",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Sync is completely optional — the app works fully without it. " +
                "Nothing is uploaded, downloaded, or changed until you explicitly connect and enable sync.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        Text(
            "On the sheet, pending tasks live in the \"Tasks\" tab and completed tasks " +
                "are stored separately in the \"Completed Tasks\" tab. Completing a task " +
                "moves it there automatically; reopening it moves it back. " +
                "The script also maintains a \"Summary\" tab with totals and pending payments.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )

        // ---- status card ----
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
                    if (conn.enabled) Icons.Filled.CheckCircle else Icons.Filled.CloudOff,
                    contentDescription = null,
                    tint = if (conn.enabled) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.secondary
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        when {
                            !conn.configured -> "Not connected"
                            conn.enabled -> "Connected — sync enabled"
                            else -> "Connected — sync off"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        when {
                            !conn.configured -> "No connection configured."
                            else -> buildString {
                                append("Pending changes: $pendingCount")
                                if (conn.lastSync.isBlank()) append("\nNot synced yet")
                                else append("\nLast synced: ${formatTimestampDisplay(conn.lastSync)}")
                                if (conn.lastError.isNotBlank()) append("\nLast error: ${conn.lastError}")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        // ---- credentials ----
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Apps Script Web App URL") },
            placeholder = { Text("https://script.google.com/…/exec") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Secret token") },
            singleLine = true,
            visualTransformation = if (tokenVisible) VisualTransformation.None
            else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { tokenVisible = !tokenVisible }) {
                    Icon(
                        if (tokenVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (tokenVisible) "Hide token" else "Show token"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = busyAction == null,
                onClick = {
                    scope.launch {
                        busyAction = "test"
                        statusMessage = null
                        val res = engine.testConnection(url, token)
                        busyAction = null
                        // A test never changes tasks or enables sync.
                        say(res.message, isError = !res.ok)
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                if (busyAction == "test") {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Test Connection")
                }
            }
            Button(
                enabled = busyAction == null,
                onClick = {
                    if (url.isBlank() || token.isBlank()) {
                        say("Enter both the Web App URL and the secret token first.", isError = true)
                    } else {
                        engine.saveConnection(url, token)
                        tick++
                        say("Connection saved. Sync stays OFF until you enable it with the switch below.")
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Save")
            }
        }

        // ---- enable switch ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Enable sync", fontWeight = FontWeight.SemiBold)
                Text(
                    "Two-way sync with your Google Sheet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            if (busyAction == "preview") {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Switch(
                    checked = conn.enabled,
                    enabled = busyAction == null,
                    onCheckedChange = { on ->
                        if (!on) {
                            engine.setSyncEnabled(false)
                            container.syncPoller.stop()
                            tick++
                            say("Sync turned off. Your local tasks are untouched.")
                        } else {
                            scope.launch {
                                busyAction = "preview"
                                statusMessage = null
                                val res = engine.previewFirstSync()
                                busyAction = null
                                if (res.ok && res.data != null) {
                                    preview = res.data
                                    firstSyncMode = SyncEngine.FirstSyncMode.MERGE
                                    showFirstSync = true
                                } else {
                                    say(res.error, isError = true)
                                }
                            }
                        }
                    }
                )
            }
        }

        // ---- sync now ----
        Button(
            enabled = conn.enabled && busyAction == null,
            onClick = {
                scope.launch {
                    busyAction = "syncnow"
                    statusMessage = null
                    val r = engine.syncNow()
                    busyAction = null
                    tick++
                    say(r.message.ifBlank { "Sync complete." }, isError = !r.ok)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            if (busyAction == "syncnow") {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("Sync Now")
            }
        }

        // ---- re-upload all (repair) ----
        if (conn.enabled) {
            OutlinedButton(
                enabled = busyAction == null,
                onClick = {
                    scope.launch {
                        busyAction = "reupload"
                        statusMessage = null
                        container.repository.markAllForUpload()
                        val r = engine.syncNow()
                        busyAction = null
                        tick++
                        say(
                            if (r.ok) "Re-uploaded all tasks: ${r.message}"
                            else "Re-upload failed: ${r.message}",
                            isError = !r.ok
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                if (busyAction == "reupload") {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Re-upload all tasks")
                }
            }
            Text(
                "Use this if tasks are missing from the sheet — it re-sends everything.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        // ---- disconnect ----
        if (conn.configured) {
            OutlinedButton(
                onClick = { showDisconnect = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Disconnect", color = MaterialTheme.colorScheme.error)
            }
        }

        // ---- status message ----
        statusMessage?.let { msg ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (statusIsError) Icons.Filled.Error else Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (statusIsError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (statusIsError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // ---- honest explainer ----
        Text(
            "How sync works: changes you make in the app upload to the sheet " +
                "within a couple of seconds, automatically. " +
                "Changes made directly in the sheet appear in the app within about " +
                "15 seconds while the app is open, when you return to the app, " +
                "or when you tap Sync Now. " +
                "Google Sheets cannot push edits instantly to the app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}
