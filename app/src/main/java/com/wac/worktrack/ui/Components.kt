@file:OptIn(ExperimentalMaterial3Api::class)

package com.wac.worktrack.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.Money

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Button showing the current choice; tapping opens a menu of [options]. */
@Composable
fun <T> Choice(
    label: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    display: (T) -> String = { it.toString() },
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${display(selected)}", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach {
                DropdownMenuItem(text = { Text(display(it)) }, onClick = { onSelect(it); open = false })
            }
        }
    }
}

@Composable
fun TimePickerDialog(initialMinutes: Int?, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val start = initialMinutes ?: (9 * 60)
    val state = rememberTimePickerState(initialHour = start / 60, initialMinute = start % 60, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}

@Composable
fun DatePickerDialogFor(initialDay: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    // The Material date picker works in UTC midnights; epoch day * 1 day maps exactly.
    val state = rememberDatePickerState(initialSelectedDateMillis = initialDay * DAY_MILLIS)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPick(it / DAY_MILLIS) } }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}

private const val DAY_MILLIS = 86_400_000L

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Single amount field dialog (budgets, savings contributions). */
@Composable
fun AmountDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit, extraButton: (@Composable () -> Unit)? = null) {
    var text by remember { mutableStateOf(initial) }
    // Save stays off until the amount is a positive number, so nothing is silently dropped.
    val valid = Money.parse(text) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                text, { text = it }, label = { Text("Amount (₹)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = text.isNotBlank() && !valid,
                supportingText = if (text.isNotBlank() && !valid) ({ Text("Enter an amount above zero") }) else null,
            )
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = {
            Row {
                extraButton?.invoke()
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
fun ScoreRing(score: Int?, size: Dp = 88.dp, stroke: Dp = 9.dp) {
    val color = scoreColor(score)
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            val arcSize = Size(this.size.width - s, this.size.height - s)
            val topLeft = Offset(s / 2, s / 2)
            drawArc(track, -90f, 360f, false, topLeft, arcSize, style = Stroke(s))
            drawArc(color, -90f, 360f * (score ?: 0) / 100f, false, topLeft, arcSize, style = Stroke(s, cap = StrokeCap.Round))
        }
        Text(score?.let { "$it%" } ?: "–", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ProgressRow(label: String, value: String, fraction: Float, color: Color = MaterialTheme.colorScheme.primary) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) }, color = color,
            modifier = Modifier.fillMaxWidth().height(6.dp), drawStopIndicator = {},
        )
    }
}

@Composable
fun StatBox(label: String, value: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "‹ label ›" stepper used for day / week / month navigation. */
@Composable
fun Stepper(label: String, onPrev: () -> Unit, onNext: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onPrev) { Text("‹", style = MaterialTheme.typography.titleLarge) }
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        TextButton(onClick = { onNext?.invoke() }, enabled = onNext != null) { Text("›", style = MaterialTheme.typography.titleLarge) }
    }
}

fun daysLeftLabel(deadline: Long, today: Long): String {
    val left = deadline - today
    return when {
        left < 0 -> "ended ${Day.short(deadline)}"
        left == 0L -> "due today"
        else -> "by ${Day.short(deadline)} · $left days left"
    }
}

@Composable
fun Gap(width: Dp = 8.dp) = Spacer(Modifier.width(width))

/** Top bar with a back arrow, used by every screen that isn't a bottom-nav tab. */
@Composable
fun BackTopBar(title: String, onBack: () -> Unit, actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}) {
    androidx.compose.material3.TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            androidx.compose.material3.IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
        },
        actions = actions,
    )
}

/** Dialog with a scrolling form; [onDelete] adds a Delete button for existing items. */
@Composable
fun FormDialog(
    title: String,
    canSave: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        },
        confirmButton = { TextButton(enabled = canSave, onClick = { onSave(); onDismiss() }) { Text("Save") } },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(onClick = { it(); onDismiss() }) { Text("Delete", color = Bad) } }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
fun TextInput(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, modifier = modifier.fillMaxWidth())
}

/** Rupee amount field; shows an error while the text isn't a positive amount. */
@Composable
fun MoneyInput(value: String, onChange: (String) -> Unit, label: String, optional: Boolean = false) {
    val bad = value.isNotBlank() && Money.parse(value) == null
    OutlinedTextField(
        value, onChange, label = { Text("$label (₹)") }, singleLine = true, isError = bad,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        supportingText = when {
            bad -> ({ Text("Enter an amount above zero") })
            optional -> ({ Text("Optional") })
            else -> null
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Whole-number field (days, minutes); null while empty or not a number. */
@Composable
fun NumberInput(value: String, onChange: (String) -> Unit, label: String, hint: String? = null) {
    OutlinedTextField(
        value, { onChange(it.filter(Char::isDigit).take(4)) }, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = hint?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A tappable summary tile: small label over a big value. */
@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    sub: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
            sub?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (onClick != null) Card(onClick = onClick, modifier = modifier, content = content)
    else Card(modifier = modifier, content = content)
}

/** "Label    value" row inside a list, with an optional sub-line. */
@Composable
fun ItemRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            sub?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Gap()
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor)
        trailing?.let { Gap(4.dp); it() }
    }
}

@Composable
fun <T> rememberFlow(initial: T, vararg keys: Any?, flow: () -> kotlinx.coroutines.flow.Flow<T>): T {
    val f = remember(*keys) { flow() }
    return f.collectAsStateWithLifecycle(initial).value
}
