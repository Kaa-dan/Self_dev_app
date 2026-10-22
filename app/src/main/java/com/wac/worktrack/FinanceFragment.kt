package com.wac.worktrack

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.DateFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Finance tracker: log income/expenses, see this month's totals, a category breakdown and tips. */
class FinanceFragment : Fragment() {

    private val txns = mutableListOf<Transaction>()
    private lateinit var adapter: TxnAdapter
    private lateinit var summary: TextView
    private lateinit var breakdown: TextView
    private lateinit var insight: TextView
    private val money: NumberFormat = NumberFormat.getCurrencyInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_finance, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        txns.clear()
        txns.addAll(FinanceStore.load(requireContext()).sortedByDescending { it.timeMillis })

        summary = view.findViewById(R.id.summary)
        breakdown = view.findViewById(R.id.breakdown)
        insight = view.findViewById(R.id.insight)
        val list = view.findViewById<RecyclerView>(R.id.list)
        adapter = TxnAdapter(txns, money) { remove(it) }
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        view.findViewById<FloatingActionButton>(R.id.add).setOnClickListener { addFlow() }
        refreshHeader()
    }

    /** Transactions in the current calendar month. */
    private fun thisMonth(): List<Transaction> {
        val c = Calendar.getInstance()
        val y = c.get(Calendar.YEAR); val m = c.get(Calendar.MONTH)
        return txns.filter {
            val t = Calendar.getInstance().apply { timeInMillis = it.timeMillis }
            t.get(Calendar.YEAR) == y && t.get(Calendar.MONTH) == m
        }
    }

    private fun refreshHeader() {
        val month = thisMonth()
        val income = month.filter { !it.isExpense }.sumOf { it.amount }
        val expense = month.filter { it.isExpense }.sumOf { it.amount }
        val net = income - expense
        val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())

        summary.text = "$monthName\nIn  ${money.format(income)}    Out  ${money.format(expense)}\nNet  ${money.format(net)}"
        breakdown.text = buildBreakdown(month, expense)
        insight.text = buildInsight(month, expense)
    }

    private fun buildBreakdown(month: List<Transaction>, expenseTotal: Double): String {
        val byCat = month.filter { it.isExpense }
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }
        if (byCat.isEmpty()) return "No spending yet this month."
        return "Spending by category\n" + byCat.joinToString("\n") { (cat, amt) ->
            val pct = if (expenseTotal > 0) (amt / expenseTotal * 100).toInt() else 0
            "• $cat — ${money.format(amt)} ($pct%)"
        }
    }

    private fun buildInsight(month: List<Transaction>, expenseTotal: Double): String {
        if (expenseTotal <= 0) return "💡 Add a few expenses and I'll surface spending tips here."
        val dayOfMonth = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        val perDay = expenseTotal / dayOfMonth
        val top = month.filter { it.isExpense }
            .groupBy { it.category }
            .mapValues { (_, l) -> l.sumOf { it.amount } }
            .maxByOrNull { it.value }
        val income = month.filter { !it.isExpense }.sumOf { it.amount }
        val tips = buildList {
            add("≈ ${money.format(perDay)}/day so far this month")
            if (top != null) add("biggest category: ${top.key} (${money.format(top.value)})")
            if (income > 0 && expenseTotal > income) add("⚠️ spending exceeds income this month")
            else if (income > 0) add("✅ you're net positive — ${money.format(income - expenseTotal)} saved")
        }
        return "💡 " + tips.joinToString(" · ")
    }

    private fun addFlow() {
        val pad = (resources.displayMetrics.density * 20).toInt()
        val box = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        val amount = EditText(requireContext()).apply {
            hint = "Amount"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val type = RadioGroup(requireContext()).apply { orientation = RadioGroup.HORIZONTAL }
        val expense = RadioButton(requireContext()).apply { text = "Expense"; id = View.generateViewId(); isChecked = true }
        val incomeRb = RadioButton(requireContext()).apply { text = "Income"; id = View.generateViewId() }
        type.addView(expense); type.addView(incomeRb)
        val category = EditText(requireContext()).apply { hint = "Category (Food, Transport, Bills…)" }
        val note = EditText(requireContext()).apply { hint = "Note (optional)" }
        listOf(amount, type, category, note).forEach { box.addView(it) }

        AlertDialog.Builder(requireContext())
            .setTitle("Add transaction")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val value = amount.text.toString().toDoubleOrNull()
                if (value == null || value <= 0) {
                    Toast.makeText(requireContext(), "Enter a valid amount", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val cat = category.text.toString().trim().ifEmpty { "Other" }
                save(value, expense.isChecked, cat, note.text.toString().trim())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun save(amount: Double, isExpense: Boolean, category: String, note: String) {
        val t = Transaction(
            id = FinanceStore.nextId(txns),
            amount = amount,
            isExpense = isExpense,
            category = category,
            note = note,
            timeMillis = System.currentTimeMillis(),
        )
        txns.add(0, t)
        FinanceStore.save(requireContext(), txns)
        adapter.notifyItemInserted(0)
        refreshHeader()
    }

    private fun remove(t: Transaction) {
        val i = txns.indexOfFirst { it.id == t.id }
        if (i < 0) return
        txns.removeAt(i)
        FinanceStore.save(requireContext(), txns)
        adapter.notifyItemRemoved(i)
        refreshHeader()
    }
}

private class TxnAdapter(
    private val items: List<Transaction>,
    private val money: NumberFormat,
    private val onDelete: (Transaction) -> Unit,
) : RecyclerView.Adapter<TxnAdapter.VH>() {

    private val fmt = DateFormat.getDateInstance(DateFormat.MEDIUM)

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.title)
        val meta: TextView = v.findViewById(R.id.meta)
        val amount: TextView = v.findViewById(R.id.amount)
        val delete: TextView = v.findViewById(R.id.delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_txn, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val t = items[position]
        holder.title.text = if (t.note.isEmpty()) t.category else "${t.category} · ${t.note}"
        holder.meta.text = fmt.format(Date(t.timeMillis))
        holder.amount.text = (if (t.isExpense) "-" else "+") + money.format(t.amount)
        holder.amount.setTextColor(if (t.isExpense) 0xFFF38BA8.toInt() else 0xFFA6E3A1.toInt())
        holder.delete.setOnClickListener { onDelete(t) }
    }

    override fun getItemCount(): Int = items.size
}
