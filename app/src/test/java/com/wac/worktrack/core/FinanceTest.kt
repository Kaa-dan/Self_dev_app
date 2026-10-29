package com.wac.worktrack.core

import com.wac.worktrack.data.Loan
import com.wac.worktrack.data.Payable
import com.wac.worktrack.data.PersonDebt
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class FinanceTest {
    private val today = LocalDate.of(2026, 10, 29).toEpochDay()

    @Test
    fun debtCountsLoansAndMoneyToGive() {
        val loans = listOf(Loan(1, "Car", 500_000, 300_000, 10_000, ""))
        val people = listOf(
            PersonDebt(1, "A", 20_000, paidPaise = 5_000, toGive = true, dueDate = null, note = ""),
            PersonDebt(2, "B", 7_000, toGive = false, dueDate = null, note = ""),
        )
        assertEquals(315_000, Finance.totalDebt(loans, people))
        assertEquals(7_000, Finance.toReceive(people))
    }

    @Test
    fun unpaidSkipsThisMonthsPayments() {
        val rent = Payable(1, "Rent", "Rent", 100, 5, null, lastPaidMonth = "2026-10")
        val emi = Payable(2, "EMI", "EMI", 100, 10, null, lastPaidMonth = "2026-09")
        assertEquals(listOf(emi), Finance.unpaid(listOf(rent, emi), today))
    }

    @Test
    fun perDayLeftSplitsRemainingLimit() {
        // 29 Oct to 31 Oct is 3 days.
        assertEquals(1_000, Finance.perDayLeft(10_000, 7_000, today))
        assertEquals(0, Finance.perDayLeft(10_000, 12_000, today))
    }

    @Test
    fun dueDayClampsToShortMonths() {
        val feb = LocalDate.of(2026, 2, 10).toEpochDay()
        assertEquals(LocalDate.of(2026, 2, 28).toEpochDay(), Day.inMonth(feb, 31))
    }
}
