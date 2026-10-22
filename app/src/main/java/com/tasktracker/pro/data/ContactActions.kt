package com.tasktracker.pro.data

/**
 * Pure helpers for the Call / WhatsApp actions on a task's contact number.
 * Numbers in this app are mostly Indian mobile numbers, so a bare 10-digit
 * number is treated as India (+91) for WhatsApp. The dial URI keeps the
 * number essentially as typed (spaces/dashes removed), so unusual formats
 * still reach the dialer untouched in meaning.
 */
object ContactActions {

    /** Digits only (drops +, spaces, dashes, brackets). */
    fun digitsOnly(raw: String): String = raw.filter { it.isDigit() }

    /**
     * International digits for wa.me links, or null when the number is too
     * short to be a real phone number:
     *  - "0091…" → "91…"
     *  - "098765…" (11 digits, leading 0) → "91…"
     *  - "98765…" (10 digits) → "91" + number
     *  - anything already carrying a country code is kept as-is.
     */
    fun whatsAppDigits(raw: String): String? {
        var d = digitsOnly(raw)
        if (d.startsWith("00")) d = d.drop(2)
        return when {
            d.length == 10 -> "91$d"
            d.length == 11 && d.startsWith("0") -> "91" + d.drop(1)
            d.length in 11..15 -> d
            else -> null
        }
    }

    /** https://wa.me/<digits> link, or null when the number looks unusable. */
    fun whatsAppUrl(raw: String): String? = whatsAppDigits(raw)?.let { "https://wa.me/$it" }

    /** tel: URI for the dialer (ACTION_DIAL — no call permission needed). */
    fun dialUri(raw: String): String? {
        val trimmed = raw.trim()
        if (digitsOnly(trimmed).length < 5) return null
        val cleaned = trimmed.filter { it.isDigit() || it == '+' }
        return "tel:$cleaned"
    }
}
