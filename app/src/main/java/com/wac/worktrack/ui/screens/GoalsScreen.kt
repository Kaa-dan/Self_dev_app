package com.wac.worktrack.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.db
import com.wac.worktrack.core.write
import com.wac.worktrack.data.Goal
import com.wac.worktrack.ui.Bad
import com.wac.worktrack.ui.DatePickerDialogFor
import com.wac.worktrack.ui.FormDialog
import com.wac.worktrack.ui.Good
import com.wac.worktrack.ui.Hint
import com.wac.worktrack.ui.LocalToday
import com.wac.worktrack.ui.SectionTitle
import com.wac.worktrack.ui.TextInput
import com.wac.worktrack.ui.Warn
import com.wac.worktrack.ui.daysLeftLabel
import com.wac.worktrack.ui.rememberFlow

@Composable
fun GoalsScreen(modifier: Modifier) {
    val context = LocalContext.current
    val today = LocalToday.current
    val goals = rememberFlow(emptyList<Goal>()) { context.db.goals().goals() }
    var editing by remember { mutableStateOf<Goal?>(null) }
    var adding by remember { mutableStateOf(false) }
    val active = goals.filter { it.doneDay == null }
    val done = goals.filter { it.doneDay != null }

    Box(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 88.dp)) {
            SectionTitle("Active · ${active.size}")
            if (active.isEmpty()) Hint("Add a goal and the date you want it done by.")
            active.forEach { g -> GoalRow(g, today, { editing = g }) { context.write { goals().save(g.copy(doneDay = today)) } } }
            if (done.isNotEmpty()) {
                SectionTitle("Achieved · ${done.size}")
                done.forEach { g -> GoalRow(g, today, { editing = g }) { context.write { goals().save(g.copy(doneDay = null)) } } }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Goal") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (adding) GoalDialog(null) { adding = false }
    editing?.let { GoalDialog(it) { editing = null } }
}

@Composable
private fun GoalRow(goal: Goal, today: Long, onClick: () -> Unit, onToggle: () -> Unit) {
    val achieved = goal.doneDay != null
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(achieved, { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(
                goal.title, style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (achieved) TextDecoration.LineThrough else null,
            )
            if (goal.note.isNotBlank()) {
                Text(goal.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            val left = goal.endDate - today
            Text(
                if (achieved) "Done ${Day.short(goal.doneDay!!)} · target ${Day.short(goal.endDate)}" else daysLeftLabel(goal.endDate, today),
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    achieved -> Good
                    left < 0 -> Bad
                    left <= 7 -> Warn
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun GoalDialog(existing: Goal?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val today = LocalToday.current
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var end by remember { mutableLongStateOf(existing?.endDate ?: (today + 30)) }
    var pickDate by remember { mutableStateOf(false) }
    FormDialog(
        if (existing == null) "New goal" else "Edit goal",
        canSave = title.isNotBlank(), onDismiss = onDismiss,
        onSave = {
            val goal = (existing ?: Goal(title = "", note = "", endDate = end)).copy(title = title.trim(), note = note.trim(), endDate = end)
            context.write { goals().save(goal) }
        },
        onDelete = existing?.let { { context.write { goals().delete(it) } } },
    ) {
        TextInput(title, { title = it }, "Goal")
        TextInput(note, { note = it }, "Note (optional)")
        OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
            Text("End date: ${Day.label(end, today)} (${end - today} days)")
        }
    }
    if (pickDate) DatePickerDialogFor(end, { pickDate = false }) { end = it; pickDate = false }
}
