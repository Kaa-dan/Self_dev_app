package com.wac.worktrack.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.wac.worktrack.core.db
import com.wac.worktrack.notify.HabitAlarms
import com.wac.worktrack.widget.TaskWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** The whole database as JSON rows per table, for the cloud backup. */
object Snapshot {
    val TABLES = listOf(
        "settings", "banks", "expenses", "incomes", "pending_txns", "loans", "person_debts", "payables",
        "savings", "habits", "habit_timers", "habit_logs", "goals", "app_blocks",
    )

    /** Bumped when the database version changes; older backups are still accepted. */
    const val FORMAT = 1

    fun dump(context: Context): Map<String, List<JSONObject>> {
        val sql = context.db.openHelper.readableDatabase
        // One transaction, so a write landing mid-dump can't leave the backup half old, half new.
        return context.db.runInTransaction<Map<String, List<JSONObject>>> {
            TABLES.associateWith { table -> sql.query("SELECT * FROM $table").use { it.toJsonRows() } }
        }
    }

    /** Replaces all local data with [data]; tables missing from [data] are emptied. */
    suspend fun load(context: Context, data: Map<String, List<JSONObject>>): Int = withContext(Dispatchers.IO) {
        val sql = context.db.openHelper.writableDatabase
        var rows = 0
        sql.beginTransaction()
        try {
            for (table in TABLES) {
                sql.delete(table, null, null)
                for (row in data[table].orEmpty()) {
                    sql.insert(table, SQLiteDatabase.CONFLICT_REPLACE, row.toContentValues())
                    rows++
                }
            }
            sql.setTransactionSuccessful()
        } finally {
            sql.endTransaction()
        }
        // Raw writes bypass Room's DAOs; tell observers (open screens) to re-query.
        context.db.invalidationTracker.refreshAsync()
        HabitAlarms.rescheduleAll(context)
        TaskWidget.refresh(context)
        rows
    }

    private fun Cursor.toJsonRows(): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        while (moveToNext()) {
            val o = JSONObject()
            for (i in 0 until columnCount) {
                o.put(
                    getColumnName(i),
                    when (getType(i)) {
                        Cursor.FIELD_TYPE_INTEGER -> getLong(i)
                        Cursor.FIELD_TYPE_FLOAT -> getDouble(i)
                        Cursor.FIELD_TYPE_STRING -> getString(i)
                        else -> JSONObject.NULL
                    },
                )
            }
            out += o
        }
        return out
    }

    private fun JSONObject.toContentValues() = ContentValues().also { cv ->
        keys().forEach { key ->
            when (val v = get(key)) {
                JSONObject.NULL -> cv.putNull(key)
                is Int -> cv.put(key, v.toLong())
                is Long -> cv.put(key, v)
                is Double -> cv.put(key, v)
                is Boolean -> cv.put(key, if (v) 1L else 0L)
                else -> cv.put(key, v.toString())
            }
        }
    }
}
