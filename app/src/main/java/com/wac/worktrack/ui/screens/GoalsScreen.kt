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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.db
import com.wac.worktrack.core.write
import com.wac.worktrack.data.Goal
import com.wac.worktrack.ui.AppCard
import com.wac.worktrack.ui.Bad
import com.wac.worktrack.ui.ChipPicker
import com.wac.worktrack.ui.Confetti
import com.wac.worktrack.ui.DatePickerDialogFor
import com.wac.worktrack.ui.EmojiBubble
import com.wac.worktrack.ui.EmojiPicker
import com.wac.worktrack.ui.EmptyState
import com.wac.worktrack.ui.FieldLabel
import com.wac.worktrack.ui.Good
import com.wac.worktrack.ui.GradientFab
import com.wac.worktrack.ui.Gradients
import com.wac.worktrack.ui.LocalToday
import com.wac.worktrack.ui.Palette
import com.wac.worktrack.ui.PickerField
import com.wac.worktrack.ui.ProgressRing
import com.wac.worktrack.ui.ScreenTitle
import com.wac.worktrack.ui.SectionHeader
import com.wac.worktrack.ui.SheetForm
import com.wac.worktrack.ui.Tag
import com.wac.worktrack.ui.TextInput
import com.wac.worktrack.ui.Visuals
import com.wac.worktrack.ui.Warn
import com.wac.worktrack.ui.rememberFlow

@Composable
fun GoalsScreen(modifier: Modifier) {
    val context = LocalContext.current
    val today = LocalToday.current
    val goalsOrNull = rememberFlow<List<Goal>?>(null) { context.db.goals().goals() }
    val goals = goalsOrNull.orEmpty()
    var editing by remember { mutableStateOf<Goal?>(null) }
    var adding by remember { mutableStateOf(false) }
    var celebrate by remember { mutableIntStateOf(0) }
    val active = goals.filter { it.doneDay == null }
    val done = goals.filter { it.doneDay != null }

    Box(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 110.dp)) {
            ScreenTitle("Goals", subtitle = "Dream it, date it, do it")

            AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), gradient = Gradients.Grape) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("In progress", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
                        Text("${active.size}", color = Color.White, style = MaterialTheme.typography.displaySmall)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Achieved", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
                        Text("${done.size} 🏆", color = Color.White, style = MaterialTheme.typography.displaySmall)
                    }
                }
                active.minByOrNull { it.endDate }?.let {
                    Spacer(Modifier.height(10.dp))
                    Text("Next up: ${it.emoji} ${it.title} · ${daysText(it.endDate - today)}", color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            if (goalsOrNull != null && goals.isEmpty()) {
                EmptyState("🎯", "No goals yet", "Add something you want to achieve and the date you want it done by.")
            }
            if (active.isNotEmpty()) SectionHeader("In progress")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                active.forEach { g ->
                    GoalCard(g, today, { editing = g }) {
                        celebrate++
                        context.write { goals().save(g.copy(doneDay = today)) }
                    }
                }
            }
            if (done.isNotEmpty()) {
                SectionHeader("Achieved 🏆")
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    done.forEach { g -> GoalCard(g, today, { editing = g }) { context.write { goals().save(g.copy(doneDay = null)) } } }
                }
            }
        }
        GradientFab("Goal", { adding = true }, Modifier.align(Alignment.BottomEnd).padding(20.dp), Gradients.Grape)
        Confetti(celebrate)
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
    val (gradient, color) = when {
        achieved -> Gradients.Mint to Good
        left < 0 -> Gradients.Danger to Bad
        left <= 7 -> Gradients.Gold to Warn
        else -> Gradients.Primary to Palette.Violet
    }
    AppCard(Modifier.fillMaxWidth(), onClick = onClick, padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (achieved) {
                EmojiBubble(goal.emoji, Good, 60.dp)
            } else {
                ProgressRing(goalTimeFraction(goal, today), size = 60.dp, stroke = 6.dp, gradient = gradient) {
                    Text(goal.emoji, fontSize = 24.sp)
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
                if (achieved) Tag("🏆 Done ${Day.short(goal.doneDay!!)}", Good)
                else Tag("📅 ${Day.short(goal.endDate)} · ${daysText(left)}", color)
            }
            Spacer(Modifier.width(8.dp))
            CheckCircle(achieved, onToggle, size = 40.dp, gradient = Gradients.Mint)
        }
    }
}

@Composable
private fun GoalSheet(existing: Goal?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val today = LocalToday.current
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var emoji by remember { mutableStateOf(existing?.emoji ?: "🎯") }
    var end by remember { mutableLongStateOf(existing?.endDate ?: (today + 30)) }
    var pickDate by remember { mutableStateOf(false) }
    val quick = listOf("1 week" to 7L, "1 month" to 30L, "3 months" to 91L, "6 months" to 182L, "1 year" to 365L)
    SheetForm(
        if (existing == null) "New goal" else "Edit goal",
        canSave = title.isNotBlank(), onDismiss = onDismiss, gradient = Gradients.Grape,
        onSave = {
            val goal = (existing ?: Goal(title = "", note = "", endDate = end, startDay = today))
                .copy(title = title.trim(), note = note.trim(), endDate = end, emoji = emoji)
            context.write { goals().save(goal) }
        },
        onDelete = existing?.let { { context.write { goals().delete(it) } } },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBubble(emoji, Palette.Purple, 60.dp)
            Spacer(Modifier.width(14.dp))
            TextInput(title, { title = it }, "What do you want to achieve?", Modifier.weight(1f))
        }
        FieldLabel("Icon")
        EmojiPicker(Visuals.goalEmojis, emoji) { emoji = it }
        TextInput(note, { note = it }, "Note (optional)")
        FieldLabel("Finish by")
        ChipPicker(quick.map { it.first }, quick.firstOrNull { today + it.second == end }?.first, { it ?: "" }, { label ->
            end = today + quick.first { it.first == label }.second
        })
        PickerField("End date", "${Day.long(end)} · ${daysText(end - today)}", "📅", onClick = { pickDate = true })
    }
    if (pickDate) DatePickerDialogFor(end, { pickDate = false }) { end = it; pickDate = false }
}
