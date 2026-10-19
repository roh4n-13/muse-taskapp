package com.tasktracker.pro.ui.screens

import android.app.DatePickerDialog
import android.content.Intent
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tasktracker.pro.appContainer
import com.tasktracker.pro.data.Dashboard
import com.tasktracker.pro.data.Dates
import com.tasktracker.pro.ui.formatDateField
import java.time.LocalDate

/** One-tap daily report, generated from on-device data — works fully offline. */
@Composable
fun ReportScreen() {
    val context = LocalContext.current
    val container = context.appContainer()
    val tasks by container.repository.observeActive()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val clipboard = LocalClipboardManager.current

    var dateStr by remember { mutableStateOf(Dates.todayString()) }
    var reportText by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Daily Report",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = formatDateField(dateStr),
                onValueChange = {},
                readOnly = true,
                label = { Text("Report date") },
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    val base = Dates.parseDate(dateStr) ?: LocalDate.now()
                    DatePickerDialog(
                        context,
                        { _, y, m, d ->
                            dateStr = "%04d-%02d-%02d".format(y, m + 1, d)
                            reportText = null
                        },
                        base.year,
                        base.monthValue - 1,
                        base.dayOfMonth
                    ).show()
                }
            ) {
                Icon(Icons.Filled.DateRange, contentDescription = "Pick report date")
            }
        }
        Button(
            onClick = {
                val date = Dates.parseDate(dateStr) ?: LocalDate.now()
                val report = Dashboard.buildDailyReport(tasks, date)
                reportText = Dashboard.formatDailyReport(report)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Generate report")
        }

        val text = reportText
        if (text != null) {
            Card(modifier = Modifier
                .fillMaxWidth()
                .weight(1f)) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(text)) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Copy")
                }
                OutlinedButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(send, "Share daily report"))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Share")
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                "Tap Generate to build today's report from your tasks.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Text(
            "Generated from on-device data — works fully offline.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}
