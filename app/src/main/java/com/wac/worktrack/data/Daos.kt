package com.wac.worktrack.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MoneyDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    fun settings(): Flow<Settings?>

    @Upsert
    suspend fun saveSettings(settings: Settings)

    // Banks
    @Query("SELECT * FROM banks ORDER BY name")
    fun banks(): Flow<List<Bank>>

    @Upsert
    suspend fun saveBank(bank: Bank)

    @Delete
    suspend fun deleteBank(bank: Bank)

    @Query("UPDATE banks SET balancePaise = balancePaise + :deltaPaise WHERE id = :id")
    suspend fun adjustBank(id: Long, deltaPaise: Long)

    // Expenses
    @Query("SELECT * FROM expenses WHERE date BETWEEN :from AND :to ORDER BY date DESC, id DESC")
    fun expenses(from: Long, to: Long): Flow<List<Expense>>

    @Insert
    suspend fun insertExpense(expense: Expense)

    @Delete
    suspend fun deleteExpenseRow(expense: Expense)

    /** Spending leaves the bank it came from. */
    @Transaction
    suspend fun addExpense(expense: Expense) {
        insertExpense(expense)
        expense.bankId?.let { adjustBank(it, -expense.amountPaise) }
    }

    /** Deleting a spend puts the money back in its bank. */
    @Transaction
    suspend fun deleteExpense(expense: Expense) {
        deleteExpenseRow(expense)
        expense.bankId?.let { adjustBank(it, expense.amountPaise) }
    }

    @Transaction
    suspend fun receiveSalary(settings: Settings, bankId: Long?, month: String) {
        bankId?.let { adjustBank(it, settings.salaryPaise) }
        saveSettings(settings.copy(lastSalaryMonth = month))
    }

    // Loans
    @Query("SELECT * FROM loans ORDER BY outstandingPaise DESC")
    fun loans(): Flow<List<Loan>>

    @Upsert
    suspend fun saveLoan(loan: Loan): Long

    @Query("UPDATE loans SET outstandingPaise = MAX(0, MIN(totalPaise, outstandingPaise + :deltaPaise)) WHERE id = :id")
    suspend fun adjustLoan(id: Long, deltaPaise: Long)

    @Delete
    suspend fun deleteLoanRow(loan: Loan)

    @Query("UPDATE payables SET loanId = NULL WHERE loanId = :loanId")
    suspend fun unlinkLoan(loanId: Long)

    /** The loan's EMI stays in monthly payables, just no longer tied to a loan. */
    @Transaction
    suspend fun deleteLoan(loan: Loan) {
        unlinkLoan(loan.id)
        deleteLoanRow(loan)
    }

    // People
    @Query("SELECT * FROM person_debts ORDER BY dueDate IS NULL, dueDate, person")
    fun personDebts(): Flow<List<PersonDebt>>

    @Upsert
    suspend fun savePersonDebt(debt: PersonDebt)

    @Delete
    suspend fun deletePersonDebt(debt: PersonDebt)

    // Monthly payables
    @Query("SELECT * FROM payables ORDER BY dueDay, title")
    fun payables(): Flow<List<Payable>>

    @Upsert
    suspend fun savePayable(payable: Payable)

    @Delete
    suspend fun deletePayable(payable: Payable)

    @Transaction
    suspend fun payPayable(payable: Payable, bankId: Long?, month: String) {
        bankId?.let { adjustBank(it, -payable.amountPaise) }
        payable.loanId?.let { adjustLoan(it, -payable.amountPaise) }
        savePayable(payable.copy(lastPaidMonth = month, lastPaidBankId = bankId))
    }

    /** Reverses [payPayable]: money back in the bank, back on the loan. */
    @Transaction
    suspend fun undoPayable(payable: Payable) {
        payable.lastPaidBankId?.let { adjustBank(it, payable.amountPaise) }
        payable.loanId?.let { adjustLoan(it, payable.amountPaise) }
        savePayable(payable.copy(lastPaidMonth = null, lastPaidBankId = null))
    }

    // Savings
    @Query("SELECT * FROM savings ORDER BY amountPaise DESC")
    fun savings(): Flow<List<Saving>>

    @Upsert
    suspend fun saveSaving(saving: Saving)

    @Delete
    suspend fun deleteSaving(saving: Saving)
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY timeMinutes IS NULL, timeMinutes, name")
    fun habits(): Flow<List<Habit>>

    @Query("SELECT * FROM habits")
    suspend fun habitsNow(): List<Habit>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun habit(id: Long): Habit?

    @Query("SELECT * FROM habit_logs")
    fun logs(): Flow<List<HabitLog>>

    @Query("SELECT EXISTS(SELECT 1 FROM habit_logs WHERE habitId = :habitId AND date = :date)")
    suspend fun isLogged(habitId: Long, date: Long): Boolean

    @Upsert
    suspend fun save(habit: Habit): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun log(log: HabitLog)

    @Delete
    suspend fun unlog(log: HabitLog)

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId")
    suspend fun deleteLogs(habitId: Long)

    // Timers
    @Query("SELECT * FROM habit_timers WHERE date = :date")
    fun timers(date: Long): Flow<List<HabitTimer>>

    @Query("SELECT * FROM habit_timers WHERE habitId = :habitId AND date = :date")
    suspend fun timer(habitId: Long, date: Long): HabitTimer?

    @Query("SELECT * FROM habit_timers WHERE runningSince IS NOT NULL")
    suspend fun runningTimers(): List<HabitTimer>

    @Upsert
    suspend fun saveTimer(timer: HabitTimer)

    @Query("DELETE FROM habit_timers WHERE habitId = :habitId")
    suspend fun deleteTimers(habitId: Long)

    @Delete
    suspend fun deleteRow(habit: Habit)

    @Transaction
    suspend fun delete(habit: Habit) {
        deleteLogs(habit.id)
        deleteTimers(habit.id)
        deleteRow(habit)
    }
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY doneDay IS NOT NULL, endDate")
    fun goals(): Flow<List<Goal>>

    @Upsert
    suspend fun save(goal: Goal)

    @Delete
    suspend fun delete(goal: Goal)
}
