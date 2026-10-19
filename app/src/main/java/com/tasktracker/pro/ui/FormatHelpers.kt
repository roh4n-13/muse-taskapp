package com.tasktracker.pro.ui

import com.tasktracker.pro.data.Dates
import java.time.format.DateTimeFormatter

private val TIMESTAMP_DISPLAY = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")

/** Formats an ISO offset date-time for display; "—" when blank/invalid. */
fun formatTimestampDisplay(value: String): String {
    if (value.isBlank()) return "—"
    val odt = Dates.parseDateTime(value) ?: return value
    return odt.format(TIMESTAMP_DISPLAY)
}

/** Formats a "yyyy-MM-dd" date for display; "—" when blank/invalid. */
fun formatDateField(value: String): String {
    if (value.isBlank()) return "—"
    return Dates.formatDateForDisplay(value)
}
