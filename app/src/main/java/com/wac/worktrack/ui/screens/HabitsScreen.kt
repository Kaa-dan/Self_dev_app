package com.wac.worktrack.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.Streaks
import com.wac.worktrack.core.db
import com.wac.worktrack.core.durationLabel
import com.wac.worktrack.core.timeLabel
import com.wac.worktrack.core.write
import com.wac.worktrack.data.Habit
import com.wac.worktrack.data.HabitLog
import com.wac.worktrack.data.HabitTimer
import com.wac.worktrack.notify.HabitAlarms
import com.wac.worktrack.ui.AddFab
import com.wac.worktrack.ui.AppCard
import com.wac.worktrack.ui.Bad
import com.wac.worktrack.ui.ChipPicker
import com.wac.worktrack.ui.EmptyState
import com.wac.worktrack.ui.FieldLabel
import com.wac.worktrack.ui.Glyphs
import com.wac.worktrack.ui.Good
import com.wac.worktrack.ui.IconBadge
import com.wac.worktrack.ui.IconPicker
import com.wac.worktrack.ui.LocalToday
import com.wac.worktrack.ui.NumberInput
import com.wac.worktrack.ui.PickerField
import com.wac.worktrack.ui.PrimaryButton
import com.wac.worktrack.ui.ProgressBar
import com.wac.worktrack.ui.ProgressRing
import com.wac.worktrack.ui.ScreenTitle
import com.wac.worktrack.ui.SheetForm
import com.wac.worktrack.ui.Tag
import com.wac.worktrack.ui.TextInput
import com.wac.worktrack.ui.TimePickerDialog
import com.wac.worktrack.ui.bounceClick
import com.wac.worktrack.ui.muted
import com.wac.worktrack.ui.rememberFlow
import com.wac.worktrack.ui.tabular
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HabitsScreen(modifier: Modifier) {
    val context = LocalContext.current
    val db = context.db
    val today = LocalToday.current
    val habitsOrNull = rememberFlow<List<Habit>?>(null) { db.habits().habits() }
    val logs = rememberFlow<List<HabitLog>?>(null) { db.habits().logs() }.orEmpty()
    val habits = habitsOrNull.orEmpty()
    var day by remember(today) { mutableLongStateOf(today) }
    var editing by remember { mutableStateOf<Habit?>(null) }
    var adding by remember { mutableStateOf(false) }
    val timers = rememberFlow(emptyList<HabitTimer>(), today) { db.habits().timers(today) }

    val doneByHabit = remember(logs) { logs.groupBy({ it.habitId }, { it.date }).mapValues { it.value.toSet() } }
    val doneByDay = remember(logs) { Streaks.byDay(logs) }
    val due = habits.filter { it.createdDay <= day }
    val doneCount = due.count { day in doneByHabit[it.id].orEmpty() }
    val streak = Streaks.dayStreak(habits, logs, today)

    Box(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 110.dp)) {
            ScreenTitle("Habits", subtitle = "Show up, every day")

            AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), hero = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val fraction = if (due.isEmpty()) 0f else doneCount.toFloat() / due.size
                    val ink = androidx.compose.material3.LocalContentColor.current
                    ProgressRing(fraction, size = 88.dp, stroke = 6.dp, color = MaterialTheme.colorScheme.primary, track = ink.copy(alpha = 0.12f)) {
                        Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.titleLarge.tabular())
                    }
                    Spacer(Modifier.width(20.dp))
                    Column {
                        Text(Day.label(day, today), color = muted(), style = MaterialTheme.typography.labelLarge)
                        Text("$doneCount of ${due.size} done", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (streak == 1) "1 day in a row" else "$streak days in a row" +
                                (Streaks.badgeFor(streak)?.let { " · ${it.name}" } ?: ""),
                            color = muted(), style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            AlarmPermissionBanner(habits)

            WeekStrip(habits, doneByDay, today, day) { day = it }

            if (habitsOrNull != null && habits.isEmpty()) {
                EmptyState(Icons.Rounded.Eco, "No habits yet", "Add the things you want to do every day, with a time and how long each takes.")
            }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                due.forEach { h ->
                    val done = doneByHabit[h.id].orEmpty()
                    HabitCard(
                        h, habitColor(habits.indexOf(h)), checked = day in done, streak = Streaks.habitStreak(done, today),
                        doneDays = done, today = today,
                        // Timers run for today only.
                        timer = if (day == today) timers.firstOrNull { it.habitId == h.id } ?: HabitTimer(h.id, today) else null,
                        // A habit with a duration is only ticked off by running its timer.
                        onCheck = if (h.durationMinutes == null) ({ toggleHabit(context, h.id, day, day !in done) }) else null,
                        onClick = { editing = h },
                    )
                }
            }
        }
        AddFab("Habit", { adding = true }, Modifier.align(Alignment.BottomEnd).padding(20.dp))
    }

    if (adding) HabitSheet(null) { adding = false }
    editing?.let { HabitSheet(it) { editing = null } }
}

/** Last 7 days; each shows how much of that day was done. Tap to view / tick that day. */
@Composable
private fun WeekStrip(habits: List<Habit>, doneByDay: Map<Long, Set<Long>>, today: Long, selected: Long, onSelect: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (d in today - 6..today) {
            val on = d == selected
            val due = habits.filter { it.createdDay <= d }
            val fraction = if (due.isEmpty()) 0f else due.count { it.id in doneByDay[d].orEmpty() }.toFloat() / due.size
            val bg by animateColorAsState(if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainer, label = "day")
            val fg = if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
            val shape = RoundedCornerShape(12.dp)
            Column(
                Modifier.weight(1f).clip(shape).background(bg)
                    .then(if (on) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape))
                    .bounceClick { onSelect(d) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    Day.date(d).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    color = fg.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall,
                )
                Text("${Day.date(d).dayOfMonth}", color = fg, style = MaterialTheme.typography.titleSmall.tabular())
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier.size(5.dp).clip(CircleShape).background(
                        when {
                            fraction >= 1f -> Good
                            fraction > 0f -> MaterialTheme.colorScheme.primary
                            else -> fg.copy(alpha = 0.15f)
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun HabitCard(
    habit: Habit,
    color: Color,
    checked: Boolean,
    streak: Int,
    doneDays: Set<Long>,
    today: Long,
    timer: HabitTimer?,
    onCheck: (() -> Unit)?,
    onClick: () -> Unit,
) {
    AppCard(Modifier.fillMaxWidth(), onClick = onClick, padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Glyphs.icon(habit.emoji), color, 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(habit.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                val bits = listOfNotNull(
                    habit.timeMinutes?.let(::timeLabel),
                    habit.durationMinutes?.let(::durationLabel),
                    if (streak > 0) "$streak-day streak" else null,
                )
                if (bits.isNotEmpty()) {
                    Text(bits.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Spacer(Modifier.height(8.dp))
                }
                // The last seven days, oldest first.
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (d in today - 6..today) {
                        Box(
                            Modifier.size(width = 16.dp, height = 4.dp).clip(CircleShape).background(
                                if (d in doneDays) color else MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        )
                    }
                }
            }
            // No check circle for a habit with a duration: its timer completes it.
            if (onCheck != null) {
                Spacer(Modifier.width(8.dp))
                CheckCircle(checked, onCheck, size = 40.dp)
            }
        }
        val target = habit.durationMinutes
        if (target != null && timer != null) TimerRow(habit, target, timer, color, checked)
    }
}

/** Start / pause timer for a habit with a duration, with live elapsed time. */
@Composable
fun TimerRow(habit: Habit, targetMinutes: Int, timer: HabitTimer, color: Color, done: Boolean) {
    val context = LocalContext.current
    val running = timer.runningSince != null
    // Ticks once a second while the timer runs.
    val now by produceState(System.currentTimeMillis(), timer) {
        value = System.currentTimeMillis()
        while (running) {
            delay(1000)
            value = System.currentTimeMillis()
        }
    }
    val elapsed = timer.elapsed(now)
    val target = targetMinutes * 60_000L
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row {
                Text(
                    clock(elapsed), style = MaterialTheme.typography.titleMedium.tabular(),
                    color = if (running) color else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    " / ${clock(target)}", style = MaterialTheme.typography.titleMedium.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            ProgressBar(elapsed.toFloat() / target, color = color, height = 4.dp)
        }
        Spacer(Modifier.width(12.dp))
        when {
            running -> PrimaryButton("Pause", { HabitAlarms.perform(context, HabitAlarms.PAUSE, habit.id) }, small = true, outlined = true, icon = Icons.Rounded.Pause)
            done && elapsed >= target -> Tag("Time done", Good, icon = Icons.Rounded.Check)
            else -> PrimaryButton(
                if (elapsed > 0) "Resume" else "Start",
                { HabitAlarms.perform(context, HabitAlarms.START, habit.id) },
                small = true, icon = Icons.Rounded.PlayArrow,
            )
        }
    }
}

/** "1:05:09" / "12:30". */
private fun clock(millis: Long): String {
    val total = millis / 1000
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Warns when the phone won't let habit alarms ring on time or full screen. */
@Composable
private fun AlarmPermissionBanner(habits: List<Habit>) {
    val context = LocalContext.current
    var check by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        check++
        onPauseOrDispose { }
    }
    if (habits.none { it.timeMinutes != null && it.ringAlarm }) return
    val fix = remember(check) { HabitAlarms.permissionSettings(context) } ?: return
    WarningCard(
        "Alarms need permission",
        "Tap to allow exact and full-screen alarms so habits ring on time.",
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) { runCatching { context.startActivity(fix) } }
}

/** Tinted card asking the user to fix something. */
@Composable
fun WarningCard(title: String, text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    AppCard(modifier.fillMaxWidth(), color = Bad.copy(alpha = 0.08f), onClick = onClick, padding = PaddingValues(16.dp)) {
        Row {
            Icon(Icons.Rounded.WarningAmber, null, tint = Bad, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Bad)
                Spacer(Modifier.height(2.dp))
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val durations = listOf(null, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240)

@Composable
private fun HabitSheet(existing: Habit?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val today = LocalToday.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var icon by remember { mutableStateOf(Glyphs.key(existing?.emoji ?: "check")) }
    var time by remember { mutableStateOf(existing?.timeMinutes) }
    var duration by remember { mutableStateOf(existing?.durationMinutes) }
    var ringAlarm by remember { mutableStateOf(existing?.ringAlarm ?: true) }
    var custom by remember { mutableStateOf(existing?.durationMinutes?.takeIf { it !in durations }?.toString() ?: "") }
    var pickTime by remember { mutableStateOf(false) }
    SheetForm(
        if (existing == null) "New habit" else "Edit habit",
        canSave = name.isNotBlank(), onDismiss = onDismiss,
        onSave = {
            val habit = (existing ?: Habit(name = "", timeMinutes = null, durationMinutes = null, createdDay = today))
                .copy(name = name.trim(), emoji = icon, timeMinutes = time, durationMinutes = duration, ringAlarm = ringAlarm)
            context.write {
                val id = habits().save(habit)
                HabitAlarms.schedule(context, if (existing == null) habit.copy(id = id) else habit)
            }
        },
        onDelete = existing?.let {
            {
                HabitAlarms.cancel(context, it.id)
                context.write { habits().delete(it) }
            }
        },
    ) {
        TextInput(name, { name = it }, "Habit name")
        FieldLabel("Icon")
        IconPicker(Glyphs.habit, icon) { icon = it }
        FieldLabel("When")
        PickerField(
            "Time", time?.let { timeLabel(it) } ?: "Any time today", Icons.Rounded.Schedule,
            onClick = { pickTime = true },
            trailing = if (time != null) ({ TextButton(onClick = { time = null }) { Text("Clear") } }) else null,
        )
        if (time != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Alarm, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (ringAlarm) "Ring as alarm" else "Quiet notification", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (ringAlarm) "Rings with the alarm sound until you start, snooze or dismiss" else "A normal notification, no ringing",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(ringAlarm, { ringAlarm = it })
            }
        }
        FieldLabel("How long · gives you a start/stop timer")
        ChipPicker(
            durations, duration.takeIf { it in durations }, { it?.let(::durationLabel) ?: "No timer" }, { duration = it; custom = "" },
            icon = { if (it == null) Icons.Rounded.Check else Icons.Rounded.Timer },
        )
        NumberInput(
            custom, { custom = it; it.toIntOrNull()?.takeIf { m -> m > 0 }?.let { m -> duration = m } },
            "Custom minutes", hint = "e.g. 150 for 2 h 30 min",
        )
    }
    if (pickTime) TimePickerDialog(time, { pickTime = false }) { time = it; pickTime = false }
}
