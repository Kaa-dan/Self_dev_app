package com.wac.worktrack

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Local JSON persistence for finance transactions — no backend. */
object FinanceStore {
    private const val FILE = "finance.json"

    private fun file(context: Context) = File(context.filesDir, FILE)

    fun load(context: Context): MutableList<Transaction> {
        val f = file(context)
        if (!f.exists()) return mutableListOf()
        return try {
            val arr = JSONArray(f.readText())
            MutableList(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Transaction(
                    id = o.getInt("id"),
                    amount = o.getDouble("amount"),
                    isExpense = o.getBoolean("isExpense"),
                    category = o.getString("category"),
                    note = o.optString("note", ""),
                    timeMillis = o.getLong("timeMillis"),
                )
            }
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    fun save(context: Context, txns: List<Transaction>) {
        val arr = JSONArray()
        txns.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("amount", t.amount)
                    .put("isExpense", t.isExpense)
                    .put("category", t.category)
                    .put("note", t.note)
                    .put("timeMillis", t.timeMillis),
            )
        }
        file(context).writeText(arr.toString())
    }

    fun nextId(txns: List<Transaction>): Int = (txns.maxOfOrNull { it.id } ?: 0) + 1
}
