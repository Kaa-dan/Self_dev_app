package com.wac.worktrack.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.sp
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
import com.wac.worktrack.ui.AppCard
import com.wac.worktrack.ui.ChipPicker
import com.wac.worktrack.ui.Confetti
import com.wac.worktrack.ui.EmojiBubble
import com.wac.worktrack.ui.EmojiPicker
import com.wac.worktrack.ui.EmptyState
import com.wac.worktrack.ui.FieldLabel
import com.wac.worktrack.ui.Good
import com.wac.worktrack.ui.GradientBar
import com.wac.worktrack.ui.GradientButton
import com.wac.worktrack.ui.GradientFab
import com.wac.worktrack.ui.Gradients
import com.wac.worktrack.ui.LocalToday
import com.wac.worktrack.ui.NumberInput
import com.wac.worktrack.ui.Palette
import com.wac.worktrack.ui.PickerField
import com.wac.worktrack.ui.ProgressRing
import com.wac.worktrack.ui.ScreenTitle
import com.wac.worktrack.ui.SheetForm
import com.wac.worktrack.ui.Tag
import com.wac.worktrack.ui.TextInput
import com.wac.worktrack.ui.TimePickerDialog
import com.wac.worktrack.ui.Visuals
import com.wac.worktrack.ui.bounceClick
import com.wac.worktrack.ui.brush
import com.wac.worktrack.ui.rememberFlow
import java.time.format.TextStyle
import java.util.Locale

val habitColors = listOf(Palette.Violet, Palette.Mint, Palette.Orange, Palette.Sky, Palette.Pink, Palette.Amber, Palette.Purple)

@Composable
fun HabitsScreen(modifier: Modifier) {
    val context = LocalContext.current
    val db = context.db
    val today = LocalToday.current
    val habitsOrNull = rememberFlow<List<Habit>?>(null) { db.habits().habits() }
    val logsOrNull = rememberFlow<List<HabitLog>?>(null) { db.habits().logs() }
    val habits = habitsOrNull.orEmpty()
    val logs = logsOrNull.orEmpty()
    var day by remember(today) { mutableLongStateOf(today) }
    var editing by remember { mutableStateOf<Habit?>(null) }
    var adding by remember { mutableStateOf(false) }
    val timers = rememberFlow(emptyList<HabitTimer>(), today) { db.habits().timers(today) }

    val doneByHabit = remember(logs) { logs.groupBy({ it.habitId }, { it.date }).mapValues { it.value.toSet() } }
    val doneByDay = remember(logs) { Streaks.byDay(logs) }
    val due = habits.filter { it.createdDay <= day }
    val doneCount = due.count { day in doneByHabit[it.id].orEmpty() }
    val todayAllDone = habits.isNotEmpty() && Streaks.allDone(habits, doneByDay, today)
    val celebrate = rememberCelebration(todayAllDone, habitsOrNull != null && logsOrNull != null)
    val streak = Streaks.dayStreak(habits, logs, today)

    Box(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 110.dp)) {
            ScreenTitle("Habits", subtitle = "Small steps, every day")

            AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), gradient = Gradients.Primary) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val fraction = if (due.isEmpty()) 0f else doneCount.toFloat() / due.size
                    ProgressRing(fraction, size = 104.dp, stroke = 11.dp, gradient = listOf(Color.White, Color(0xFFD9D2FF)), track = Color.White.copy(alpha = 0.22f)) {
                        Text("${(fraction * 100).toInt()}%", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.width(18.dp))
                    Column {
                        Text(Day.label(day, today), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
                        Text("$doneCount of ${due.size} done", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "🔥 $streak-day streak" + (Streaks.badgeFor(streak)?.let { "  ${it.emoji}" } ?: ""),
                            Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 12.dp, vertical = 5.dp),
                            color = Color.White, style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }

            AlarmPermissionBanner(habits)

            WeekStrip(habits, doneByDay, today, day) { day = it }

            if (habitsOrNull != null && habits.isEmpty()) {
                EmptyState("🌱", "No habits yet", "Add the things you want to do every day — with a time and how long each takes.")
            }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                due.forEachIndexed { i, h ->
                    val done = doneByHabit[h.id].orEmpty()
                    HabitCard(
                        h, habitColors[i % habitColors.size], checked = day in done, streak = Streaks.habitStreak(done, today),
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
        GradientFab("Habit", { adding = true }, Modifier.align(Alignment.BottomEnd).padding(20.dp))
        Confetti(celebrate)
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
            val text by animateColorAsState(if (on) Color.White else MaterialTheme.colorScheme.onSurface, label = "day")
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                    .then(if (on) Modifier.background(Gradients.Fire.brush()) else Modifier.background(MaterialTheme.colorScheme.surfaceContainer))
                    .bounceClick { onSelect(d) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    Day.date(d).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    color = if (on) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text("${Day.date(d).dayOfMonth}", color = text, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.size(8.dp).clip(CircleShape).background(
                        when {
                            fraction >= 1f -> if (on) Color.White else Good
                            fraction > 0f -> Palette.Amber
                            else -> if (on) Color.White.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant
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
            EmojiBubble(habit.emoji, color, 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(habit.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    habit.timeMinutes?.let { Tag("⏰ ${timeLabel(it)}", color) }
                    habit.durationMinutes?.let { Tag("⏱ ${durationLabel(it)}", Palette.Slate) }
                    if (streak > 0) Tag("🔥 $streak", Palette.Orange)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (d in today - 6..today) {
                        Box(
                            Modifier.size(width = 18.dp, height = 6.dp).clip(CircleShape).background(
                                if (d in doneDays) color else MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        )
                    }
                }
            }
            // No check circle for a habit with a duration: its timer completes it.
            if (onCheck != null) {
                Spacer(Modifier.width(8.dp))
                CheckCircle(checked, onCheck, size = 44.dp)
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
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row {
                Text(
                    clock(elapsed), style = MaterialTheme.typography.titleMedium,
                    color = if (running) color else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    " / ${clock(target)}", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            GradientBar(elapsed.toFloat() / target, gradient = listOf(color, color.copy(alpha = 0.6f)), height = 8.dp)
        }
        Spacer(Modifier.width(12.dp))
        when {
            running -> GradientButton("⏸ Pause", { HabitAlarms.perform(context, HabitAlarms.PAUSE, habit.id) }, gradient = Gradients.Gold, small = true)
            done && elapsed >= target -> Tag("✓ Time done", Good)
            else -> GradientButton(
                if (elapsed > 0) "▶ Resume" else "▶ Start",
                { HabitAlarms.perform(context, HabitAlarms.START, habit.id) },
                gradient = Gradients.Mint, small = true,
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
    AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), gradient = Gradients.Danger, onClick = {
        runCatching { context.startActivity(fix) }
    }) {
        Text("⏰ Alarms need permission", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Text(
            "Tap to allow exact alarms and full-screen alarms so habits ring on time.",
            color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private val durations = listOf(null, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240)

@Composable
private fun HabitSheet(existing: Habit?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val today = LocalToday.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var emoji by remember { mutableStateOf(existing?.emoji ?: "✅") }
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
                .copy(name = name.trim(), emoji = emoji, timeMinutes = time, durationMinutes = duration, ringAlarm = ringAlarm)
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBubble(emoji, Palette.Violet, 60.dp)
            Spacer(Modifier.width(14.dp))
            TextInput(name, { name = it }, "Habit name", Modifier.weight(1f))
        }
        FieldLabel("Icon")
        EmojiPicker(Visuals.habitEmojis, emoji) { emoji = it }
        FieldLabel("When")
        PickerField(
            "Time", time?.let { timeLabel(it) } ?: "Any time today", "⏰",
            onClick = { pickTime = true },
            trailing = if (time != null) ({ TextButton(onClick = { time = null }) { Text("Clear") } }) else null,
        )
        if (time != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (ringAlarm) "🔔 Ring as alarm" else "🔕 Quiet notification", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (ringAlarm) "Rings with the alarm sound until you start, snooze or dismiss" else "A normal notification, no ringing",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(ringAlarm, { ringAlarm = it })
            }
        }
        FieldLabel("How long · gives you a start/stop timer")
        ChipPicker(durations, duration.takeIf { it in durations }, { it?.let(::durationLabel) ?: "No timer" }, { duration = it; custom = "" })
        NumberInput(
            custom, { custom = it; it.toIntOrNull()?.takeIf { m -> m > 0 }?.let { m -> duration = m } },
            "Custom minutes", hint = "e.g. 150 for 2 h 30 min",
        )
    }
    if (pickTime) TimePickerDialog(time, { pickTime = false }) { time = it; pickTime = false }
}
