package com.wac.worktrack.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.db
import com.wac.worktrack.core.write
import com.wac.worktrack.data.Goal
import com.wac.worktrack.ui.AppCard
import com.wac.worktrack.ui.Bad
import com.wac.worktrack.ui.ChipPicker
import com.wac.worktrack.ui.DatePickerDialogFor
import com.wac.worktrack.ui.Glyphs
import com.wac.worktrack.ui.IconBadge
import com.wac.worktrack.ui.IconPicker
import com.wac.worktrack.ui.EmptyState
import com.wac.worktrack.ui.FieldLabel
import com.wac.worktrack.ui.Good
import com.wac.worktrack.ui.AddFab
import com.wac.worktrack.ui.LocalToday
import com.wac.worktrack.ui.PickerField
import com.wac.worktrack.ui.ProgressRing
import com.wac.worktrack.ui.ScreenTitle
import com.wac.worktrack.ui.SectionHeader
import com.wac.worktrack.ui.SheetForm
import com.wac.worktrack.ui.Tag
import com.wac.worktrack.ui.TextInput
import com.wac.worktrack.ui.Warn
import com.wac.worktrack.ui.muted
import com.wac.worktrack.ui.tabular
import com.wac.worktrack.ui.rememberFlow

@Composable
fun GoalsScreen(modifier: Modifier) {
    val context = LocalContext.current
    val today = LocalToday.current
    val goalsOrNull = rememberFlow<List<Goal>?>(null) { context.db.goals().goals() }
    val goals = goalsOrNull.orEmpty()
    var editing by remember { mutableStateOf<Goal?>(null) }
    var adding by remember { mutableStateOf(false) }
    val active = goals.filter { it.doneDay == null }
    val done = goals.filter { it.doneDay != null }

    Box(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 110.dp)) {
            ScreenTitle("Goals", subtitle = "Decide it, date it, do it")

            AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), hero = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("In progress", color = muted(), style = MaterialTheme.typography.labelLarge)
                        Text("${active.size}", style = MaterialTheme.typography.displaySmall)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Achieved", color = muted(), style = MaterialTheme.typography.labelLarge)
                        Text("${done.size}", style = MaterialTheme.typography.displaySmall)
                    }
                }
                active.minByOrNull { it.endDate }?.let {
                    Spacer(Modifier.height(10.dp))
                    Text("Next up: ${it.title} · ${daysText(it.endDate - today)}", color = muted(), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            if (goalsOrNull != null && goals.isEmpty()) {
                EmptyState(Icons.Rounded.Flag, "No goals yet", "Add something you want to achieve and the date you want it done by.")
            }
            if (active.isNotEmpty()) SectionHeader("In progress")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                active.forEach { g ->
                    GoalCard(g, today, { editing = g }) {
                        context.write { goals().save(g.copy(doneDay = today)) }
                    }
                }
            }
            if (done.isNotEmpty()) {
                SectionHeader("Achieved")
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    done.forEach { g -> GoalCard(g, today, { editing = g }) { context.write { goals().save(g.copy(doneDay = null)) } } }
                }
            }
        }
        AddFab("Goal", { adding = true }, Modifier.align(Alignment.BottomEnd).padding(20.dp))
    }

    if (adding) GoalSheet(null) { adding = false }
    editing?.let { GoalSheet(it) { editing = null } }
}

private fun daysText(left: Long) = when {
    left < 0 -> "${-left} days over"
    left == 0L -> "due today"
    left == 1L -> "1 day left"
    else -> "$left days left"
}

@Composable
private fun GoalCard(goal: Goal, today: Long, onClick: () -> Unit, onToggle: () -> Unit) {
    val achieved = goal.doneDay != null
    val left = goal.endDate - today
    val color = when {
        achieved -> Good
        left < 0 -> Bad
        left <= 7 -> Warn
        else -> MaterialTheme.colorScheme.primary
    }
    AppCard(Modifier.fillMaxWidth(), onClick = onClick, padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (achieved) {
                IconBadge(Glyphs.icon(goal.emoji), Good, 52.dp)
            } else {
                ProgressRing(goalTimeFraction(goal, today), size = 52.dp, stroke = 4.dp, color = color) {
                    androidx.compose.material3.Icon(Glyphs.icon(goal.emoji), null, tint = color, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    goal.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (achieved) TextDecoration.LineThrough else null,
                )
                if (goal.note.isNotBlank()) {
                    Text(goal.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(6.dp))
                if (achieved) Tag("Done ${Day.short(goal.doneDay!!)}", Good, icon = Icons.Rounded.EmojiEvents)
                else Tag("${Day.short(goal.endDate)} · ${daysText(left)}", color, icon = Icons.Rounded.Event)
            }
            Spacer(Modifier.width(8.dp))
            CheckCircle(achieved, onToggle, size = 36.dp)
        }
    }
}

@Composable
private fun GoalSheet(existing: Goal?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val today = LocalToday.current
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var icon by remember { mutableStateOf(Glyphs.key(existing?.emoji ?: "flag")) }
    var end by remember { mutableLongStateOf(existing?.endDate ?: (today + 30)) }
    var pickDate by remember { mutableStateOf(false) }
    val quick = listOf("1 week" to 7L, "1 month" to 30L, "3 months" to 91L, "6 months" to 182L, "1 year" to 365L)
    SheetForm(
        if (existing == null) "New goal" else "Edit goal",
        canSave = title.isNotBlank(), onDismiss = onDismiss,
        onSave = {
            val goal = (existing ?: Goal(title = "", note = "", endDate = end, startDay = today))
                .copy(title = title.trim(), note = note.trim(), endDate = end, emoji = icon)
            context.write { goals().save(goal) }
        },
        onDelete = existing?.let { { context.write { goals().delete(it) } } },
    ) {
        TextInput(title, { title = it }, "What do you want to achieve?")
        FieldLabel("Icon")
        IconPicker(Glyphs.goal, icon) { icon = it }
        TextInput(note, { note = it }, "Note (optional)")
        FieldLabel("Finish by")
        ChipPicker(quick.map { it.first }, quick.firstOrNull { today + it.second == end }?.first, { it ?: "" }, { label ->
            end = today + quick.first { it.first == label }.second
        })
        PickerField("End date", "${Day.long(end)} · ${daysText(end - today)}", Icons.Rounded.CalendarMonth, onClick = { pickDate = true })
    }
    if (pickDate) DatePickerDialogFor(end, { pickDate = false }) { end = it; pickDate = false }
}
