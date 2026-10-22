package com.tasktracker.pro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CostsTest {

    private fun task(
        id: String,
        est: String = "",
        act: String = "",
        payment: String = "",
        vendor: String = "",
        deleted: Boolean = false
    ) = Task(
        id = id,
        taskName = "Task $id",
        estimatedCostInr = est,
        actualCostInr = act,
        paymentStatus = payment,
        vendor = vendor,
        deleted = deleted
    )

    @Test
    fun parseMoneyHandlesRupeeSymbolCommasAndGarbage() {
        assertEquals(1200.0, Costs.parseMoney("₹1,200"), 0.001)
        assertEquals(1200.5, Costs.parseMoney("1200.50"), 0.001)
        assertEquals(0.0, Costs.parseMoney(""), 0.001)
        assertEquals(0.0, Costs.parseMoney("abc"), 0.001)
    }

    @Test
    fun totalsIncludeAllNonDeletedTasks() {
        val s = Costs.summarize(
            listOf(
                task("a", est = "1000", act = "900", payment = "Paid"),
                task("b", est = "2000", act = "", payment = "Pending"),
                task("gone", est = "9999", act = "9999", deleted = true)
            )
        )
        assertEquals(3000.0, s.totalEstimatedInr, 0.001)
        assertEquals(900.0, s.totalActualInr, 0.001)
    }

    @Test
    fun dueUsesActualWhenGivenElseEstimated() {
        val s = Costs.summarize(
            listOf(
                task("a", est = "1000", act = "700", payment = ""),
                task("b", est = "500", act = "", payment = "Pending")
            )
        )
        assertEquals(1200.0, s.amountDueInr, 0.001)
        assertEquals(2, s.pendingPaymentTasks)
    }

    @Test
    fun paidTasksHaveNoDue_caseInsensitive() {
        val s = Costs.summarize(
            listOf(
                task("a", est = "1000", act = "1000", payment = "PAID"),
                task("b", est = "1000", act = "1000", payment = " paid ")
            )
        )
        assertEquals(0.0, s.amountDueInr, 0.001)
        assertEquals(0, s.pendingPaymentTasks)
    }

    @Test
    fun vendorDuesAreGroupedAndSortedByDueDescending() {
        val s = Costs.summarize(
            listOf(
                task("a", est = "1000", payment = "Pending", vendor = "Sharma"),
                task("b", est = "3000", payment = "Pending", vendor = "Verma"),
                task("c", est = "500", payment = "Pending", vendor = "Sharma"),
                task("d", est = "200", payment = "Pending", vendor = "")
            )
        )
        assertEquals(listOf("Verma", "Sharma", "No vendor"), s.vendorDues.map { it.vendor })
        assertEquals(3000.0, s.vendorDues[0].dueInr, 0.001)
        assertEquals(2, s.vendorDues[1].pendingTasks)
        assertEquals(1500.0, s.vendorDues[1].dueInr, 0.001)
    }

    @Test
    fun emptyTaskListHasNoMoney() {
        val s = Costs.summarize(emptyList())
        assertFalse(s.hasMoney)
        assertTrue(s.vendorDues.isEmpty())
    }

    @Test
    fun formatInrUsesIndianGrouping() {
        assertEquals("₹12,34,567", Costs.formatInr(1234567.0))
        assertEquals("₹1,200.50", Costs.formatInr(1200.5))
        assertEquals("₹0", Costs.formatInr(0.0))
    }
}
