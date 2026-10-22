package com.tasktracker.pro.data

/**
 * Builds the plain-text version of a task used by Share / WhatsApp share.
 * Only fields that actually have a value are included, so the message stays
 * short and readable in a chat.
 */
object TaskShare {

    fun format(task: Task): String {
        val lines = mutableListOf<String>()
        lines.add("📌 Task: ${task.taskName}")
        fun add(label: String, value: String) {
            if (value.isNotBlank()) lines.add("$label: $value")
        }
        add("Progress", task.progress)
        add("Priority", task.priority)
        add("Deadline", task.deadline)
        add("Vendor", task.vendor)
        add("Contact", task.contactNumber)
        if (task.estimatedCostInr.isNotBlank()) {
            add("Estimated Cost", "₹${task.estimatedCostInr}")
        }
        if (task.actualCostInr.isNotBlank()) {
            add("Actual Cost", "₹${task.actualCostInr}")
        }
        add("Payment Status", task.paymentStatus)
        add("Next Follow-up", task.nextFollowUp)
        add("Delivery Date", task.deliveryDate)
        add("Link", task.link)
        add("Notes", task.notes)
        lines.add("— Sent from Task Tracker Pro")
        return lines.joinToString("\n")
    }
}
