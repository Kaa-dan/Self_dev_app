package com.wac.worktrack

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Simple local JSON persistence for reminders — no backend. */
object ReminderStore {
    private const val FILE = "reminders.json"

    private fun file(context: Context) = File(context.filesDir, FILE)

    fun load(context: Context): MutableList<Reminder> {
        val f = file(context)
        if (!f.exists()) return mutableListOf()
        return try {
            val arr = JSONArray(f.readText())
            MutableList(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Reminder(
                    id = o.getInt("id"),
                    title = o.getString("title"),
                    timeMillis = o.getLong("timeMillis"),
                    repeatDaily = o.optBoolean("repeatDaily", false),
                )
            }
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    fun save(context: Context, reminders: List<Reminder>) {
        val arr = JSONArray()
        reminders.forEach { r ->
            arr.put(
                JSONObject()
                    .put("id", r.id)
                    .put("title", r.title)
                    .put("timeMillis", r.timeMillis)
                    .put("repeatDaily", r.repeatDaily),
            )
        }
        file(context).writeText(arr.toString())
    }

    fun nextId(reminders: List<Reminder>): Int = (reminders.maxOfOrNull { it.id } ?: 0) + 1
}
