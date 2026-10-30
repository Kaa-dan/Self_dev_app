package com.wac.worktrack.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// All money is stored in paise; all dates are epoch days.

/** Salary and the monthly spending limit. A single row with id 1. */
@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey val id: Int = 1,
    val salaryPaise: Long = 0,
    val salaryDay: Int = 1,
    val monthlyLimitPaise: Long = 0,
    /** Month ("2026-10") whose salary was last marked received. */
    val lastSalaryMonth: String? = null,
)

@Entity(tableName = "banks")
data class Bank(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val balancePaise: Long,
)

/** Money spent. When [bankId] is set, the amount was taken out of that bank's balance. */
@Entity(tableName = "expenses", indices = [Index("date")])
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountPaise: Long,
    val category: String,
    val note: String,
    val date: Long,
    val bankId: Long?,
)

/** Money received, added to [bankId]'s balance when set. */
@Entity(tableName = "incomes", indices = [Index("date")])
data class Income(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountPaise: Long,
    val source: String,
    val date: Long,
    val bankId: Long?,
)

/**
 * A money sent / received bank SMS waiting to be checked. Kept after it is verified or ignored
 * ([status]) so the same SMS is never picked up twice ([smsKey]).
 */
@Entity(tableName = "pending_txns", indices = [Index("smsKey", unique = true)])
data class PendingTxn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val smsKey: String,
    val amountPaise: Long,
    /** Money received rather than sent. */
    val credit: Boolean,
    /** Who it was paid to / received from, as the SMS says. */
    val party: String,
    val bankId: Long?,
    /** Last digits of the account or card, when the SMS gives them. */
    val account: String,
    val date: Long,
    val body: String,
    val status: Int = PENDING,
) {
    companion object {
        const val PENDING = 0
        const val VERIFIED = 1
        const val IGNORED = 2
    }
}

/** A bank / app loan. [outstandingPaise] drops as linked EMIs are paid. */
@Entity(tableName = "loans")
data class Loan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val totalPaise: Long,
    val outstandingPaise: Long,
    val emiPaise: Long,
    val note: String,
)

/** Money between me and a person: [toGive] = I owe them, otherwise they owe me. */
@Entity(tableName = "person_debts")
data class PersonDebt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val person: String,
    val amountPaise: Long,
    val paidPaise: Long = 0,
    val toGive: Boolean,
    val dueDate: Long?,
    val note: String,
) {
    val remainingPaise get() = (amountPaise - paidPaise).coerceAtLeast(0)
    val settled get() = remainingPaise == 0L
}

/** Something paid every month: EMI, rent, credit card bill… */
@Entity(tableName = "payables")
data class Payable(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val kind: String,
    val amountPaise: Long,
    val dueDay: Int,
    /** Paying an EMI reduces this loan's outstanding amount. */
    val loanId: Long?,
    /** Month ("2026-10") last marked paid, and the bank it was paid from, so it can be undone. */
    val lastPaidMonth: String? = null,
    val lastPaidBankId: Long? = null,
)

@Entity(tableName = "savings")
data class Saving(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amountPaise: Long,
)

/** A daily habit, optionally at a time of day ([timeMinutes] past midnight) and for a duration. */
@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val timeMinutes: Int?,
    val durationMinutes: Int?,
    val createdDay: Long,
    @ColumnInfo(defaultValue = "✅") val emoji: String = "✅",
    /** At [timeMinutes]: ring like an alarm clock (true) or post a quiet notification. */
    @ColumnInfo(defaultValue = "1") val ringAlarm: Boolean = true,
)

/** Time spent on a timed habit on [date]; [runningSince] is set while the timer runs. */
@Entity(tableName = "habit_timers", primaryKeys = ["habitId", "date"])
data class HabitTimer(
    val habitId: Long,
    val date: Long,
    val accumulatedMillis: Long = 0,
    val runningSince: Long? = null,
) {
    fun elapsed(now: Long) = accumulatedMillis + (runningSince?.let { (now - it).coerceAtLeast(0) } ?: 0)
}

@Entity(tableName = "habit_logs", primaryKeys = ["habitId", "date"], indices = [Index("date")])
data class HabitLog(
    val habitId: Long,
    val date: Long,
)

@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String,
    val endDate: Long,
    val doneDay: Long? = null,
    @ColumnInfo(defaultValue = "🎯") val emoji: String = "🎯",
    /** When the goal was set, for the time-elapsed ring. */
    @ColumnInfo(defaultValue = "0") val startDay: Long = 0,
)

/**
 * Apps that can't be opened for a while: every day from [startMinutes] to [endMinutes] when [daily],
 * otherwise until [untilMillis].
 */
@Entity(tableName = "app_blocks")
data class AppBlock(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Package names, comma separated. */
    val packages: String,
    val daily: Boolean,
    val startMinutes: Int = 0,
    val endMinutes: Int = 0,
    val untilMillis: Long = 0,
) {
    val packageList get() = packages.split(',').filter { it.isNotBlank() }
}
