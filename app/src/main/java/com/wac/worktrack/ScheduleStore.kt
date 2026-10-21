package com.wac.worktrack

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Local JSON persistence for the daily schedule blocks — no backend. */
object ScheduleStore {
    private const val FILE = "schedule.json"

    private fun file(context: Context) = File(context.filesDir, FILE)

    fun load(context: Context): MutableList<DayBlock> {
        val f = file(context)
        if (!f.exists()) return mutableListOf()
        return try {
            val arr = JSONArray(f.readText())
            MutableList(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                DayBlock(
                    id = o.getInt("id"),
                    title = o.getString("title"),
                    minuteOfDay = o.getInt("minuteOfDay"),
                    notify = o.optBoolean("notify", true),
                )
            }
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    fun save(context: Context, blocks: List<DayBlock>) {
        val arr = JSONArray()
        blocks.forEach { b ->
            arr.put(
                JSONObject()
                    .put("id", b.id)
                    .put("title", b.title)
                    .put("minuteOfDay", b.minuteOfDay)
                    .put("notify", b.notify),
            )
        }
        file(context).writeText(arr.toString())
    }

    fun nextId(blocks: List<DayBlock>): Int = (blocks.maxOfOrNull { it.id } ?: 0) + 1
}
