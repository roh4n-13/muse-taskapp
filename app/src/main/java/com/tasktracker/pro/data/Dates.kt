package com.tasktracker.pro.data

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** Central date/time helpers. All comparisons use the device default zone. */
object Dates {
    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val DATE_TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun today(): LocalDate = LocalDate.now()

    fun todayString(): String = today().format(DATE)

    fun nowString(): String = OffsetDateTime.now().format(DATE_TIME)

    /** Parses "yyyy-MM-dd", returns null when blank/invalid — never throws. */
    fun parseDate(value: String): LocalDate? {
        if (value.isBlank()) return null
        return try {
            LocalDate.parse(value.trim(), DATE)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    /** Parses ISO offset date-time, falling back to a plain local date-time
     *  (assumed in the device zone — the Apps Script backend writes timestamps
     *  without an offset). Returns null when blank/invalid — never throws. */
    fun parseDateTime(value: String): OffsetDateTime? {
        val v = value.trim()
        if (v.isBlank()) return null
        try {
            return OffsetDateTime.parse(v, DATE_TIME)
        } catch (e: DateTimeParseException) {
            // fall through to local format
        }
        return try {
            java.time.LocalDateTime.parse(v, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(java.time.ZoneId.systemDefault())
                .toOffsetDateTime()
        } catch (e: DateTimeParseException) {
            null
        }
    }

    /** True when the deadline is a valid date before today. */
    fun isOverdue(deadline: String, today: LocalDate = today()): Boolean {
        val d = parseDate(deadline) ?: return false
        return d.isBefore(today)
    }

    fun isToday(deadline: String, today: LocalDate = today()): Boolean =
        parseDate(deadline) == today

    fun isUpcoming(deadline: String, today: LocalDate = today(), daysAhead: Long = 7): Boolean {
        val d = parseDate(deadline) ?: return false
        return !d.isBefore(today) && !d.isAfter(today.plusDays(daysAhead))
    }

    /** Compares two "Last Updated" values; invalid/blank sorts as oldest. */
    fun compareLastUpdated(a: String, b: String): Int {
        val ai = parseDateTime(a)
        val bi = parseDateTime(b)
        return when {
            ai == null && bi == null -> 0
            ai == null -> -1
            bi == null -> 1
            else -> ai.compareTo(bi)
        }
    }

    fun formatDateForDisplay(value: String): String {
        val d = parseDate(value) ?: return value
        return d.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
    }
}
