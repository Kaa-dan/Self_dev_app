package com.wac.worktrack.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.wac.worktrack.block.Blocks
import com.wac.worktrack.core.db
import com.wac.worktrack.core.durationLabel
import com.wac.worktrack.core.timeLabel
import com.wac.worktrack.core.write
import com.wac.worktrack.data.AppBlock
import com.wac.worktrack.ui.AppCard
import com.wac.worktrack.ui.ChipPicker
import com.wac.worktrack.ui.EmptyState
import com.wac.worktrack.ui.FieldLabel
import com.wac.worktrack.ui.GradientFab
import com.wac.worktrack.ui.Gradients
import com.wac.worktrack.ui.NumberInput
import com.wac.worktrack.ui.Palette
import com.wac.worktrack.ui.PickerField
import com.wac.worktrack.ui.ScreenTitle
import com.wac.worktrack.ui.SheetForm
import com.wac.worktrack.ui.Tag
import com.wac.worktrack.ui.TextInput
import com.wac.worktrack.ui.TimePickerDialog
import com.wac.worktrack.ui.bounceClick
import com.wac.worktrack.ui.rememberFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** An app on the phone that can be blocked. */
private class LaunchApp(val pkg: String, val label: String, val icon: ImageBitmap)

private suspend fun launchApps(context: Context): List<LaunchApp> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .distinctBy { it.activityInfo.packageName }
        .filter { it.activityInfo.packageName != context.packageName }
        .map { LaunchApp(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm).toBitmap(96, 96).asImageBitmap()) }
        .sortedBy { it.label.lowercase() }
}

@Composable
fun FocusScreen(modifier: Modifier) {
    val context = LocalContext.current
    val blocks = rememberFlow<List<AppBlock>?>(null) { context.db.blocks().blocks() }
    val apps by produceState(emptyList<LaunchApp>()) { value = launchApps(context) }
    // Re-checks which blocks are on as time passes.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(30_000)
        }
    }
    var editing by remember { mutableStateOf<AppBlock?>(null) }
    var adding by remember { mutableStateOf(false) }

    Box(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 110.dp)) {
            ScreenTitle("Focus", subtitle = "Block the apps that fog your brain")
            ServiceBanner()
            if (blocks != null && blocks.isEmpty()) {
                EmptyState("🧠", "Nothing blocked", "Pick the apps that pull you in and when to block them. A running block can't be changed or turned off.")
            }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                blocks.orEmpty().forEach { b ->
                    BlockCard(b, apps, Blocks.endsAt(b, now)) {
                        val until = Blocks.endsAt(b, System.currentTimeMillis())
                        if (until != null) {
                            Toast.makeText(context, "Locked until ${endLabel(until)}", Toast.LENGTH_SHORT).show()
                        } else {
                            editing = b
                        }
                    }
                }
            }
        }
        GradientFab("Block", { adding = true }, Modifier.align(Alignment.BottomEnd).padding(20.dp), Gradients.Grape)
    }

    if (adding) BlockSheet(null, apps) { adding = false }
    editing?.let { BlockSheet(it, apps) { editing = null } }
}

/** Asks to switch the blocker on in Accessibility settings until it is. */
@Composable
private fun ServiceBanner() {
    val context = LocalContext.current
    var check by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        check++
        onPauseOrDispose { }
    }
    val on = remember(check) { Blocks.serviceOn(context) }
    if (on) return
    AppCard(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), gradient = Gradients.Danger,
        onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } },
    ) {
        Text("🛡️ Turn on the blocker", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Text(
            "Tap, open “WorkTrack Lite app blocker” and switch it on. If it's greyed out: App info → ⋮ → Allow restricted settings, then try again.",
            color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun BlockCard(block: AppBlock, apps: List<LaunchApp>, activeUntil: Long?, onClick: () -> Unit) {
    val chosen = block.packageList.map { pkg -> apps.firstOrNull { it.pkg == pkg } }
    AppCard(
        Modifier.fillMaxWidth(), onClick = onClick, padding = PaddingValues(14.dp),
        gradient = if (activeUntil != null) Gradients.Grape else null,
    ) {
        val text = if (activeUntil != null) Color.White else MaterialTheme.colorScheme.onSurface
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (activeUntil != null) "🔒" else if (block.daily) "📅" else "⏳", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(scheduleLabel(block), color = text, style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        activeUntil != null -> "Blocking now · ${durationLabel(((activeUntil - System.currentTimeMillis()) / 60_000).toInt().coerceAtLeast(1))} left"
                        block.daily -> "Not blocking right now"
                        else -> "Ended · tap to change or delete"
                    },
                    color = if (activeUntil != null) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            chosen.take(6).forEach { app ->
                if (app != null) Image(app.icon, app.label, Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)))
            }
            if (chosen.size > 6) Text("+${chosen.size - 6}", color = text, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            chosen.mapIndexed { i, app -> app?.label ?: block.packageList[i] }.joinToString(", "),
            color = if (activeUntil != null) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun scheduleLabel(block: AppBlock) =
    if (block.daily) "Every day ${timeLabel(block.startMinutes)} – ${timeLabel(block.endMinutes)}"
    else "Until ${endLabel(block.untilMillis)}"

private fun endLabel(millis: Long): String {
    val at = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val day = at.toLocalDate()
    val today = LocalDate.now()
    return at.format(DateTimeFormatter.ofPattern("h:mm a")) + when (day) {
        today -> ""
        today.plusDays(1) -> " tomorrow"
        else -> at.format(DateTimeFormatter.ofPattern(", d MMM"))
    }
}

private val periods = listOf(15, 30, 60, 120, 180, 240, 480)

@Composable
private fun BlockSheet(existing: AppBlock?, apps: List<LaunchApp>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var daily by remember { mutableStateOf(existing?.daily ?: false) }
    var minutes by remember { mutableStateOf<Int?>(60) }
    var custom by remember { mutableStateOf("") }
    var start by remember { mutableIntStateOf(existing?.takeIf { it.daily }?.startMinutes ?: (9 * 60)) }
    var end by remember { mutableIntStateOf(existing?.takeIf { it.daily }?.endMinutes ?: (17 * 60)) }
    var picked by remember { mutableStateOf(existing?.packageList?.toSet() ?: emptySet()) }
    var search by remember { mutableStateOf("") }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    SheetForm(
        if (existing == null) "🧠 Block apps" else "🧠 Change block",
        canSave = picked.isNotEmpty() && if (daily) start != end else (minutes ?: 0) > 0,
        onDismiss = onDismiss, saveLabel = if (picked.isEmpty()) "Block" else "Block ${picked.size} app" + if (picked.size > 1) "s" else "",
        gradient = Gradients.Grape,
        onSave = {
            val block = AppBlock(
                id = existing?.id ?: 0, packages = picked.joinToString(","), daily = daily,
                startMinutes = start, endMinutes = end,
                untilMillis = if (daily) 0 else System.currentTimeMillis() + minutes!! * 60_000L,
            )
            context.write { blocks().save(block) }
        },
        onDelete = existing?.let { { context.write { blocks().delete(it) } } },
        pinSave = true,
    ) {
        ChipPicker(listOf(false, true), daily, { if (it) "📅 Every day" else "⏳ For a while" }, { daily = it }, color = { Palette.Purple })
        if (daily) {
            PickerField("From", timeLabel(start), "🌅", onClick = { pickStart = true })
            PickerField("To", timeLabel(end), "🌙", onClick = { pickEnd = true })
        } else {
            FieldLabel("Block for")
            ChipPicker(periods, minutes.takeIf { it in periods }, { durationLabel(it ?: 0) }, { minutes = it; custom = "" }, color = { Palette.Purple })
            NumberInput(custom, { custom = it; it.toIntOrNull()?.takeIf { m -> m > 0 }?.let { m -> minutes = m } }, "Custom minutes")
        }
        Text(
            "Once it's blocking you can't change or delete it until it ends.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FieldLabel("Apps · ${picked.size} chosen")
        TextInput(search, { search = it }, "Search apps")
        // Apps already in the block first; the order stays put while ticking.
        val first = remember(apps) { existing?.packageList?.toSet().orEmpty() }
        apps.filter { it.label.contains(search.trim(), ignoreCase = true) }
            .sortedBy { it.pkg !in first }
            .forEach { app ->
                val on = app.pkg in picked
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .bounceClick { picked = if (on) picked - app.pkg else picked + app.pkg }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(app.icon, null, Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)))
                    Spacer(Modifier.width(12.dp))
                    Text(app.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Checkbox(on, { picked = if (on) picked - app.pkg else picked + app.pkg })
                }
            }
        if (apps.isEmpty()) Tag("Loading apps…", Palette.Slate)
    }
    if (pickStart) TimePickerDialog(start, { pickStart = false }) { start = it; pickStart = false }
    if (pickEnd) TimePickerDialog(end, { pickEnd = false }) { end = it; pickEnd = false }
}
