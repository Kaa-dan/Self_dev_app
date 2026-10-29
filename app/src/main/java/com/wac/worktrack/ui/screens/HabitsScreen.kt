package com.wac.worktrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.Streaks
import com.wac.worktrack.core.db
import com.wac.worktrack.core.durationLabel
import com.wac.worktrack.core.timeLabel
import com.wac.worktrack.core.write
import com.wac.worktrack.data.Habit
import com.wac.worktrack.data.HabitLog
import com.wac.worktrack.notify.HabitReminders
import com.wac.worktrack.ui.FormDialog
import com.wac.worktrack.ui.Gap
import com.wac.worktrack.ui.Good
import com.wac.worktrack.ui.Hint
import com.wac.worktrack.ui.LocalToday
import com.wac.worktrack.ui.NumberInput
import com.wac.worktrack.ui.ScoreRing
import com.wac.worktrack.ui.Stepper
import com.wac.worktrack.ui.TextInput
import com.wac.worktrack.ui.TimePickerDialog
import com.wac.worktrack.ui.rememberFlow

@Composable
fun HabitsScreen(modifier: Modifier) {
    val context = LocalContext.current
    val db = context.db
    val today = LocalToday.current
    val habits = rememberFlow(emptyList<Habit>()) { db.habits().habits() }
    val logs = rememberFlow(emptyList<HabitLog>()) { db.habits().logs() }
    var day by remember(today) { mutableLongStateOf(today) }
    var editing by remember { mutableStateOf<Habit?>(null) }
    var adding by remember { mutableStateOf(false) }

    val doneByHabit = remember(logs) { logs.groupBy({ it.habitId }, { it.date }).mapValues { it.value.toSet() } }
    val due = habits.filter { it.createdDay <= day }
    val doneCount = due.count { day in doneByHabit[it.id].orEmpty() }

    Box(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 88.dp)) {
            Card(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    ScoreRing(if (due.isEmpty()) null else doneCount * 100 / due.size, size = 72.dp, stroke = 8.dp)
                    Gap(16.dp)
                    Column {
                        Text("$doneCount of ${due.size} done", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        val streak = Streaks.dayStreak(habits, logs, today)
                        Text(
                            "🔥 $streak-day streak" + (Streaks.badgeFor(streak)?.let { " · ${it.emoji} ${it.name}" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // Past days can be ticked too, for habits done but not logged; the future can't.
            Stepper(Day.label(day, today), { day-- }, if (day < today) ({ day++ }) else null)
            if (habits.isEmpty()) Hint("Add the habits you want to do every day, with a time and how long each takes.")
            due.forEach { h ->
                val done = doneByHabit[h.id].orEmpty()
                HabitRow(
                    h, checked = day in done, streak = Streaks.habitStreak(done, today), doneDays = done, today = today,
                    onCheck = { on ->
                        context.write { if (on) habits().log(HabitLog(h.id, day)) else habits().unlog(HabitLog(h.id, day)) }
                    },
                    onClick = { editing = h },
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Habit") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (adding) HabitDialog(null) { adding = false }
    editing?.let { HabitDialog(it) { editing = null } }
}

@Composable
private fun HabitRow(
    habit: Habit,
    checked: Boolean,
    streak: Int,
    doneDays: Set<Long>,
    today: Long,
    onCheck: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, onCheck)
        Column(Modifier.weight(1f)) {
            Text(habit.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val details = listOfNotNull(
                habit.timeMinutes?.let(::timeLabel),
                habit.durationMinutes?.let(::durationLabel),
                if (streak > 0) "🔥 $streak" else null,
            )
            if (details.isNotEmpty()) {
                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Last 7 days, oldest first.
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (d in today - 6..today) {
                Box(
                    Modifier.size(9.dp).background(
                        when {
                            d in doneDays -> Good
                            d < habit.createdDay -> MaterialTheme.colorScheme.surface
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        CircleShape,
                    ),
                )
            }
        }
    }
}

@Composable
private fun HabitDialog(existing: Habit?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val today = LocalToday.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var time by remember { mutableStateOf(existing?.timeMinutes) }
    var duration by remember { mutableStateOf(existing?.durationMinutes?.toString() ?: "") }
    var pickTime by remember { mutableStateOf(false) }
    FormDialog(
        if (existing == null) "New habit" else "Edit habit",
        canSave = name.isNotBlank(), onDismiss = onDismiss,
        onSave = {
            val habit = (existing ?: Habit(name = "", timeMinutes = null, durationMinutes = null, createdDay = today))
                .copy(name = name.trim(), timeMinutes = time, durationMinutes = duration.toIntOrNull()?.takeIf { it > 0 })
            context.write {
                val id = habits().save(habit)
                HabitReminders.schedule(context, if (existing == null) habit.copy(id = id) else habit)
            }
        },
        onDelete = existing?.let {
            {
                HabitReminders.cancel(context, it.id)
                context.write { habits().delete(it) }
            }
        },
    ) {
        TextInput(name, { name = it }, "Habit (e.g. Morning walk)")
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.weight(1f)) {
                Text(time?.let { "At ${timeLabel(it)} · reminder on" } ?: "Time (optional)")
            }
            if (time != null) TextButton(onClick = { time = null }) { Text("Clear") }
        }
        NumberInput(duration, { duration = it }, "Duration in minutes", hint = "Optional, e.g. 30")
    }
    if (pickTime) TimePickerDialog(time, { pickTime = false }) { time = it; pickTime = false }
}
