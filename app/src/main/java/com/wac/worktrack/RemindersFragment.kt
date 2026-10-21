package com.wac.worktrack

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

class RemindersFragment : Fragment() {

    private val reminders = mutableListOf<Reminder>()
    private lateinit var adapter: ReminderAdapter
    private lateinit var empty: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_reminders, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        reminders.clear()
        reminders.addAll(ReminderStore.load(requireContext()).sortedBy { it.timeMillis })

        empty = view.findViewById(R.id.empty)
        val list = view.findViewById<RecyclerView>(R.id.list)
        adapter = ReminderAdapter(reminders) { remove(it) }
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        view.findViewById<FloatingActionButton>(R.id.add).setOnClickListener { addFlow() }
        updateEmpty()
    }

    private fun updateEmpty() {
        empty.visibility = if (reminders.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun addFlow() {
        val input = EditText(requireContext()).apply { hint = "Reminder title" }
        AlertDialog.Builder(requireContext())
            .setTitle("New reminder")
            .setView(input)
            .setPositiveButton("Next") { _, _ ->
                val title = input.text.toString().trim().ifEmpty { "Reminder" }
                pickDateTime(title)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickDateTime(title: String) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, y, m, d ->
                cal.set(Calendar.YEAR, y); cal.set(Calendar.MONTH, m); cal.set(Calendar.DAY_OF_MONTH, d)
                TimePickerDialog(
                    requireContext(),
                    { _, h, min ->
                        cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, min)
                        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                        save(title, cal.timeInMillis)
                    },
                    cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false,
                ).show()
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun save(title: String, timeMillis: Long) {
        if (timeMillis <= System.currentTimeMillis()) {
            Toast.makeText(requireContext(), "That time is in the past", Toast.LENGTH_SHORT).show()
            return
        }
        val reminder = Reminder(ReminderStore.nextId(reminders), title, timeMillis)
        reminders.add(reminder)
        reminders.sortBy { it.timeMillis }
        ReminderStore.save(requireContext(), reminders)
        ReminderScheduler.schedule(requireContext(), reminder)
        adapter.notifyDataSetChanged()
        updateEmpty()
        val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        Toast.makeText(requireContext(), "Set for ${fmt.format(Date(timeMillis))}", Toast.LENGTH_SHORT).show()
    }

    private fun remove(reminder: Reminder) {
        ReminderScheduler.cancel(requireContext(), reminder)
        reminders.remove(reminder)
        ReminderStore.save(requireContext(), reminders)
        adapter.notifyDataSetChanged()
        updateEmpty()
    }
}

private class ReminderAdapter(
    private val items: List<Reminder>,
    private val onDelete: (Reminder) -> Unit,
) : RecyclerView.Adapter<ReminderAdapter.VH>() {

    private val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.title)
        val time: TextView = v.findViewById(R.id.time)
        val delete: TextView = v.findViewById(R.id.delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_reminder, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]
        holder.title.text = r.title
        holder.time.text = fmt.format(Date(r.timeMillis))
        holder.delete.setOnClickListener { onDelete(r) }
    }

    override fun getItemCount(): Int = items.size
}
