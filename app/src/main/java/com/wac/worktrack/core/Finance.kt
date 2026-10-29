package com.wac.worktrack.core

import com.wac.worktrack.data.Expense
import com.wac.worktrack.data.Loan
import com.wac.worktrack.data.Payable
import com.wac.worktrack.data.PersonDebt

object Finance {
    fun spent(expenses: List<Expense>, from: Long, to: Long): Long =
        expenses.filter { it.date in from..to }.sumOf { it.amountPaise }

    /** Loans still owed plus money I have to give people. */
    fun totalDebt(loans: List<Loan>, people: List<PersonDebt>): Long =
        loans.sumOf { it.outstandingPaise } + people.filter { it.toGive }.sumOf { it.remainingPaise }

    fun toReceive(people: List<PersonDebt>): Long = people.filterNot { it.toGive }.sumOf { it.remainingPaise }

    fun isPaid(payable: Payable, today: Long) = payable.lastPaidMonth == Day.monthKey(today)

    /** This month's payables not yet paid, soonest due first. */
    fun unpaid(payables: List<Payable>, today: Long): List<Payable> =
        payables.filterNot { isPaid(it, today) }.sortedBy { it.dueDay }

    /** What can still be spent each day (today included) to stay inside the monthly limit. */
    fun perDayLeft(limit: Long, spentThisMonth: Long, today: Long): Long {
        val daysLeft = Day.monthRange(today).last - today + 1
        return ((limit - spentThisMonth) / daysLeft).coerceAtLeast(0)
    }
}
