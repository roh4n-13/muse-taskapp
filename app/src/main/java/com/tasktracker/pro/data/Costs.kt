package com.tasktracker.pro.data

/**
 * Money logic for the in-app Kharcha (cost) summary. Rules intentionally
 * match the Google Sheet "Summary" tab so both places always agree:
 *  - Totals include every non-deleted task (completed ones included).
 *  - Amount due: for tasks whose Payment Status is not "Paid",
 *    due = Actual Cost when given, otherwise Estimated Cost.
 *  - Unparseable amounts count as 0.
 */
object Costs {

    data class VendorDue(val vendor: String, val pendingTasks: Int, val dueInr: Double)

    data class CostSummary(
        val totalEstimatedInr: Double,
        val totalActualInr: Double,
        val amountDueInr: Double,
        val pendingPaymentTasks: Int,
        val vendorDues: List<VendorDue>
    ) {
        val hasMoney: Boolean
            get() = totalEstimatedInr > 0 || totalActualInr > 0 || amountDueInr > 0
    }

    /** "₹1,200" / "1,200.50" / "" -> number (0 when unparseable). */
    fun parseMoney(raw: String): Double {
        val cleaned = raw.filter { it.isDigit() || it == '.' }
        if (cleaned.isBlank()) return 0.0
        return cleaned.toDoubleOrNull() ?: 0.0
    }

    fun summarize(tasks: List<Task>): CostSummary {
        var totalEstimated = 0.0
        var totalActual = 0.0
        var totalDue = 0.0
        var pendingPaymentTasks = 0
        val byVendor = mutableMapOf<String, VendorDue>()
        for (task in tasks) {
            if (task.deleted) continue
            val est = parseMoney(task.estimatedCostInr)
            val act = parseMoney(task.actualCostInr)
            totalEstimated += est
            totalActual += act
            val paid = task.paymentStatus.trim().equals("paid", ignoreCase = true)
            if (!paid) {
                val due = if (act > 0) act else est
                if (due > 0) {
                    totalDue += due
                    pendingPaymentTasks++
                    val name = task.vendor.trim().ifEmpty { "No vendor" }
                    val prev = byVendor[name]
                    byVendor[name] = VendorDue(
                        vendor = name,
                        pendingTasks = (prev?.pendingTasks ?: 0) + 1,
                        dueInr = (prev?.dueInr ?: 0.0) + due
                    )
                }
            }
        }
        return CostSummary(
            totalEstimatedInr = totalEstimated,
            totalActualInr = totalActual,
            amountDueInr = totalDue,
            pendingPaymentTasks = pendingPaymentTasks,
            vendorDues = byVendor.values.sortedWith(
                compareByDescending<VendorDue> { it.dueInr }.thenBy { it.vendor.lowercase() }
            )
        )
    }

    /**
     * ₹ formatting with Indian digit grouping: 1234567 -> "₹12,34,567".
     * Implemented manually (not via NumberFormat) because platform locale
     * data differs between devices/JVMs — the result must be identical
     * everywhere: last 3 digits, then groups of 2.
     */
    fun formatInr(amount: Double): String {
        val negative = amount < 0
        val abs = kotlin.math.abs(amount)
        val rounded = Math.round(abs * 100.0) / 100.0
        val intPart = rounded.toLong()
        val fracPaise = Math.round((rounded - intPart) * 100).toInt()

        val digits = intPart.toString()
        val grouped = if (digits.length <= 3) {
            digits
        } else {
            val head = digits.dropLast(3)
            val tail = digits.takeLast(3)
            val headGrouped = head.reversed().chunked(2).joinToString(",") { it }.reversed()
            "$headGrouped,$tail"
        }
        val fracPart = if (fracPaise > 0) "." + fracPaise.toString().padStart(2, '0') else ""
        return (if (negative) "-" else "") + "₹" + grouped + fracPart
    }
}
