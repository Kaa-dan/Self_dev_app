package com.wac.worktrack

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/** The daily routine timeline: recurring blocks from wake-up to sleep, each with an optional alert. */
class ScheduleFragment : Fragment() {

    private val blocks = mutableListOf<DayBlock>()
    private lateinit var adapter: BlockAdapter
    private lateinit var empty: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_schedule, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        reload()
        // Make sure stored blocks are armed (e.g. first run after install, or re-open).
        ScheduleAlarm.rescheduleAll(requireContext())

        empty = view.findViewById(R.id.empty)
        val list = view.findViewById<RecyclerView>(R.id.list)
        adapter = BlockAdapter(blocks, onToggle = { toggleNotify(it) }, onDelete = { remove(it) })
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        view.findViewById<FloatingActionButton>(R.id.add).setOnClickListener { addFlow() }
        updateEmpty()
    }

    private fun reload() {
        blocks.clear()
        blocks.addAll(ScheduleStore.load(requireContext()).sortedBy { it.minuteOfDay })
    }

    private fun updateEmpty() {
        empty.visibility = if (blocks.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun addFlow() {
        val input = EditText(requireContext()).apply { hint = "e.g. Wake up, Gym, Lunch" }
        AlertDialog.Builder(requireContext())
            .setTitle("New block")
            .setView(input)
            .setPositiveButton("Pick time") { _, _ ->
                val title = input.text.toString().trim().ifEmpty { "Block" }
                pickTime(title)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickTime(title: String) {
        val now = Calendar.getInstance()
        TimePickerDialog(
            requireContext(),
            { _, h, min -> save(title, h * 60 + min) },
            now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false,
        ).show()
    }

    private fun save(title: String, minuteOfDay: Int) {
        val block = DayBlock(ScheduleStore.nextId(blocks), title, minuteOfDay, notify = true)
        blocks.add(block)
        blocks.sortBy { it.minuteOfDay }
        ScheduleStore.save(requireContext(), blocks)
        ScheduleAlarm.schedule(requireContext(), block)
        adapter.notifyDataSetChanged()
        updateEmpty()
    }

    private fun toggleNotify(block: DayBlock) {
        val updated = block.copy(notify = !block.notify)
        val i = blocks.indexOfFirst { it.id == block.id }
        if (i < 0) return
        blocks[i] = updated
        ScheduleStore.save(requireContext(), blocks)
        ScheduleAlarm.schedule(requireContext(), updated)   // re-arms or cancels based on notify
        adapter.notifyItemChanged(i)
    }

    private fun remove(block: DayBlock) {
        ScheduleAlarm.cancel(requireContext(), block)
        blocks.removeAll { it.id == block.id }
        ScheduleStore.save(requireContext(), blocks)
        adapter.notifyDataSetChanged()
        updateEmpty()
    }
}

private class BlockAdapter(
    private val items: List<DayBlock>,
    private val onToggle: (DayBlock) -> Unit,
    private val onDelete: (DayBlock) -> Unit,
) : RecyclerView.Adapter<BlockAdapter.VH>() {

    private val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.title)
        val time: TextView = v.findViewById(R.id.time)
        val bell: TextView = v.findViewById(R.id.bell)
        val delete: TextView = v.findViewById(R.id.delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_block, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val b = items[position]
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, b.hour); set(Calendar.MINUTE, b.minute)
        }
        holder.title.text = b.title
        holder.time.text = fmt.format(Date(cal.timeInMillis))
        holder.bell.text = if (b.notify) "🔔" else "🔕"
        holder.bell.setOnClickListener { onToggle(b) }
        holder.delete.setOnClickListener { onDelete(b) }
    }

    override fun getItemCount(): Int = items.size
}
