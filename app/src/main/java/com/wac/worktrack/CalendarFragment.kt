package com.wac.worktrack

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Monthly calendar. Days with a one-off reminder show a dot. Tapping a day lists that day's
 * reminders (one-off that fall on it, plus every daily reminder) and lets you add more —
 * either just that day, or repeating every day.
 */
class CalendarFragment : Fragment() {

    private val reminders = mutableListOf<Reminder>()
    private val cells = mutableListOf<Int?>()        // null = blank leading slot, else day-of-month
    private val month = Calendar.getInstance()       // first-of-month being displayed
    private lateinit var adapter: DayAdapter
    private lateinit var monthLabel: TextView
    private val monthFmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val timeFmt = DateFormat.getTimeInstance(DateFormat.SHORT)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_calendar, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        loadReminders()
        ReminderScheduler.rescheduleAll(requireContext())

        month.set(Calendar.DAY_OF_MONTH, 1)
        monthLabel = view.findViewById(R.id.month_label)
        val grid = view.findViewById<RecyclerView>(R.id.grid)
        adapter = DayAdapter(cells, month, ::hasDatedReminder) { day -> openDay(day) }
        grid.layoutManager = GridLayoutManager(requireContext(), 7)
        grid.adapter = adapter

        view.findViewById<View>(R.id.prev).setOnClickListener { shiftMonth(-1) }
        view.findViewById<View>(R.id.next).setOnClickListener { shiftMonth(1) }
        rebuild()
    }

    private fun loadReminders() {
        reminders.clear()
        reminders.addAll(ReminderStore.load(requireContext()))
    }

    private fun shiftMonth(delta: Int) {
        month.add(Calendar.MONTH, delta)
        rebuild()
    }

    private fun rebuild() {
        monthLabel.text = monthFmt.format(month.time)
        cells.clear()
        // Leading blanks so the 1st lands under its weekday column (week starts Sunday).
        val lead = month.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY   // SUN=1 -> 0 ... SAT=7 -> 6
        repeat(lead) { cells.add(null) }
        val daysInMonth = month.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (d in 1..daysInMonth) cells.add(d)
        adapter.notifyDataSetChanged()
    }

    /** True if a one-off reminder falls on [day] of the displayed month (drives the dot). */
    private fun hasDatedReminder(day: Int): Boolean {
        val start = dayStart(day)
        val end = start + DAY_MS
        return reminders.any { !it.repeatDaily && it.timeMillis in start until end }
    }

    private fun dayStart(day: Int): Long {
        val c = month.clone() as Calendar
        c.set(Calendar.DAY_OF_MONTH, day)
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun remindersFor(day: Int): List<Reminder> {
        val start = dayStart(day)
        val end = start + DAY_MS
        return reminders
            .filter { it.repeatDaily || it.timeMillis in start until end }
            .sortedBy { it.minuteOfDay }
    }

    private fun openDay(day: Int) {
        val dayList = remindersFor(day)
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        if (dayList.isEmpty()) {
            container.addView(TextView(requireContext()).apply {
                text = "Nothing scheduled."; alpha = 0.7f
            })
        } else {
            dayList.forEach { r -> container.addView(reminderRow(r, day)) }
        }
        val dateLabel = DateFormat.getDateInstance(DateFormat.FULL).format(Date(dayStart(day)))
        AlertDialog.Builder(requireContext())
            .setTitle(dateLabel)
            .setView(ScrollView(requireContext()).apply { addView(container) })
            .setPositiveButton("Add") { _, _ -> addFlow(day) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun reminderRow(r: Reminder, day: Int): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 16, 0, 16)
        }
        val label = TextView(requireContext()).apply {
            val tod = timeFmt.format(Date(r.timeMillis))
            text = if (r.repeatDaily) "$tod  ·  ${r.title}  (daily)" else "$tod  ·  ${r.title}"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val del = TextView(requireContext()).apply {
            text = "Delete"; setPadding(16, 0, 0, 0); setTextColor(0xFFF38BA8.toInt())
            setOnClickListener { delete(r, day) }
        }
        row.addView(label); row.addView(del)
        return row
    }

    private fun addFlow(day: Int) {
        val input = EditText(requireContext()).apply { hint = "Reminder title" }
        AlertDialog.Builder(requireContext())
            .setTitle("New reminder")
            .setView(input)
            .setPositiveButton("Next") { _, _ ->
                val title = input.text.toString().trim().ifEmpty { "Reminder" }
                chooseRepeat(day, title)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun chooseRepeat(day: Int, title: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage("How often?")
            .setPositiveButton("Just this day") { _, _ -> pickTime(day, title, repeat = false) }
            .setNegativeButton("Every day") { _, _ -> pickTime(day, title, repeat = true) }
            .show()
    }

    private fun pickTime(day: Int, title: String, repeat: Boolean) {
        val now = Calendar.getInstance()
        TimePickerDialog(
            requireContext(),
            { _, h, min ->
                val c = month.clone() as Calendar
                c.set(Calendar.DAY_OF_MONTH, day)
                c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, min)
                c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
                save(title, c.timeInMillis, repeat, day)
            },
            now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false,
        ).show()
    }

    private fun save(title: String, timeMillis: Long, repeat: Boolean, day: Int) {
        if (!repeat && timeMillis <= System.currentTimeMillis()) {
            Toast.makeText(requireContext(), "That time is in the past", Toast.LENGTH_SHORT).show()
            return
        }
        val reminder = Reminder(ReminderStore.nextId(reminders), title, timeMillis, repeat)
        reminders.add(reminder)
        ReminderStore.save(requireContext(), reminders)
        ReminderScheduler.schedule(requireContext(), reminder)
        rebuild()
        openDay(day)
    }

    private fun delete(r: Reminder, day: Int) {
        ReminderScheduler.cancel(requireContext(), r)
        reminders.removeAll { it.id == r.id }
        ReminderStore.save(requireContext(), reminders)
        rebuild()
        openDay(day)
    }

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

private class DayAdapter(
    private val cells: List<Int?>,
    private val month: Calendar,
    private val hasDot: (Int) -> Boolean,
    private val onClick: (Int) -> Unit,
) : RecyclerView.Adapter<DayAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val day: TextView = v.findViewById(R.id.day)
        val dot: View = v.findViewById(R.id.dot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_day, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val d = cells[position]
        if (d == null) {
            holder.day.text = ""
            holder.dot.visibility = View.INVISIBLE
            holder.itemView.setOnClickListener(null)
            holder.itemView.isClickable = false
            return
        }
        holder.day.text = d.toString()
        holder.dot.visibility = if (hasDot(d)) View.VISIBLE else View.INVISIBLE
        holder.day.setTypeface(null, if (isToday(d)) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        holder.itemView.setOnClickListener { onClick(d) }
    }

    private fun isToday(day: Int): Boolean {
        val t = Calendar.getInstance()
        return t.get(Calendar.YEAR) == month.get(Calendar.YEAR) &&
            t.get(Calendar.MONTH) == month.get(Calendar.MONTH) &&
            t.get(Calendar.DAY_OF_MONTH) == day
    }

    override fun getItemCount(): Int = cells.size
}
