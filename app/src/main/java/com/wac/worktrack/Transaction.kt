package com.wac.worktrack

/**
 * A single money movement. [amount] is always positive; [isExpense] gives the direction.
 * [category] groups spending (Food, Transport, …); [note] is optional free text.
 */
data class Transaction(
    val id: Int,
    val amount: Double,
    val isExpense: Boolean,
    val category: String,
    val note: String,
    val timeMillis: Long,
) {
    /** Signed value: negative for expenses, positive for income. */
    val signed: Double get() = if (isExpense) -amount else amount
}
