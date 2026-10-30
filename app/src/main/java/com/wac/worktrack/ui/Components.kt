@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.wac.worktrack.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.Money

// ---------------------------------------------------------------- Interaction

/** Click that dips slightly while pressed and gives a light haptic tick. */
@Composable
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, tween(120), label = "press")
    val haptic = LocalHapticFeedback.current
    return this
        .scale(scale)
        .clickable(source, indication = null, enabled = enabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/** Animates an amount from its previous value (from 0 the first time) so totals count up. */
@Composable
fun animatedPaise(target: Long): Long {
    val progress = remember { Animatable(1f) }
    var from by remember { mutableLongStateOf(0L) }
    var to by remember { mutableLongStateOf(0L) }
    LaunchedEffect(target) {
        from += ((to - from) * progress.value).toLong()
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
    }
    return from + ((to - from) * progress.value).toLong()
}

@Composable
fun AnimatedAmount(paise: Long, style: TextStyle, color: Color = Color.Unspecified, modifier: Modifier = Modifier) {
    Text(Money.format(animatedPaise(paise)), modifier, style = style.tabular(), color = color, maxLines = 1)
}

/** Secondary text colour that works on both plain and [AppCard] hero backgrounds. */
@Composable
fun muted(): Color = LocalContentColor.current.copy(alpha = 0.62f)

// ---------------------------------------------------------------- Cards

/**
 * Rounded card with a hairline border. [hero] makes it the screen's one dark ink card;
 * text inside picks up the light ink colour, and [muted] the softer one.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    hero: Boolean = false,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val scheme = MaterialTheme.colorScheme
    CompositionLocalProvider(LocalContentColor provides if (hero) scheme.inverseOnSurface else scheme.onSurface) {
        Column(
            modifier
                .then(if (onClick != null) Modifier.bounceClick(onClick = onClick) else Modifier)
                .clip(shape)
                .background(if (hero) scheme.inverseSurface else color)
                .border(1.dp, if (hero) Color.White.copy(alpha = 0.06f) else scheme.outlineVariant, shape)
                .padding(padding),
            content = content,
        )
    }
}

/** Icon on a soft tinted square. */
@Composable
fun IconBadge(icon: ImageVector, color: Color, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = color, modifier = Modifier.size(size * 0.52f)) }
}

/** Initial in a coloured circle, for people. */
@Composable
fun Avatar(name: String, size: Dp = 40.dp) {
    val color = Visuals.avatar(name)
    Box(Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), color = color, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.42f).sp)
    }
}

/** Small tile: icon, label and an animated amount. */
@Composable
fun StatTile(
    icon: ImageVector,
    label: String,
    paise: Long,
    tint: Color,
    modifier: Modifier = Modifier,
    sub: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
) {
    AppCard(modifier, onClick = onClick, padding = PaddingValues(16.dp)) {
        IconBadge(icon, tint, 34.dp)
        Spacer(Modifier.height(14.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AnimatedAmount(paise, MaterialTheme.typography.titleLarge, valueColor)
        sub?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** List row: leading visual, title + sub, trailing value/content. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    subColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.bounceClick(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let { it(); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = subColor, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        trailing?.let { Spacer(Modifier.width(8.dp)); it() }
    }
}

/** Hairline between rows inside a card. */
@Composable
fun RowDivider() = HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

@Composable
fun AmountText(paise: Long, color: Color = MaterialTheme.colorScheme.onSurface, prefix: String = "") {
    Text(prefix + Money.format(paise), style = MaterialTheme.typography.titleSmall.tabular(), color = color)
}

/** Small tinted label ("Overdue", "Paid"), optionally with an icon. */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, null, tint = color, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(4.dp)) }
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** Quiet uppercase heading between sections, with an optional text action. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 24.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title.uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
        )
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            subtitle?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(title, style = MaterialTheme.typography.headlineLarge)
        }
        trailing?.invoke()
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

// ---------------------------------------------------------------- Buttons and pickers

/** Solid button; [outlined] gives the quieter variant for secondary actions. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
    small: Boolean = false,
    outlined: Boolean = false,
    icon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(if (small) 10.dp else 14.dp)
    val fg = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        outlined -> color
        else -> MaterialTheme.colorScheme.contentColorFor(color)
    }
    Row(
        modifier
            .clip(shape)
            .then(
                when {
                    !enabled -> Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                    outlined -> Modifier.border(1.dp, color.copy(alpha = 0.5f), shape)
                    else -> Modifier.background(color)
                },
            )
            .bounceClick(enabled, onClick)
            .padding(horizontal = if (small) 14.dp else 22.dp, vertical = if (small) 7.dp else 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, null, tint = fg, modifier = Modifier.size(if (small) 16.dp else 20.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, color = fg, style = if (small) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall)
    }
}

/** Text colour for a solid fill of [color]: the theme's own pair, else light ink. */
@Composable
private fun androidx.compose.material3.ColorScheme.contentColorFor(color: Color) = when (color) {
    primary -> onPrimary
    secondary -> onSecondary
    inverseSurface -> inverseOnSurface
    else -> Color.White
}

/** Floating "+ Add" button. */
@Composable
fun AddFab(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primary)
            .bounceClick(onClick = onClick)
            .padding(start = 16.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.onPrimary)
        Spacer(Modifier.width(6.dp))
        Text(text, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleSmall)
    }
}

/** Horizontally scrolling tabs; the selected one is filled with ink. */
@Composable
fun <T> PillTabs(items: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { item ->
            val on = item == selected
            val bg by animateColorAsState(if (on) MaterialTheme.colorScheme.onSurface else Color.Transparent, label = "tabBg")
            val fg by animateColorAsState(if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant, label = "tabFg")
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .then(if (on) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)))
                    .bounceClick { onSelect(item) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) { Text(label(item), color = fg, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

/** Wrapping grid of selectable chips, optionally with an icon each. */
@Composable
fun <T> ChipPicker(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    icon: ((T) -> ImageVector)? = null,
    color: ((T) -> Color)? = null,
) {
    val primary = MaterialTheme.colorScheme.primary
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            val on = item == selected
            val c = color?.invoke(item) ?: primary
            val bg by animateColorAsState(if (on) c.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHighest, label = "chip")
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .border(1.dp, if (on) c else Color.Transparent, RoundedCornerShape(10.dp))
                    .bounceClick { onSelect(item) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icon?.let {
                    Icon(it(item), null, tint = if (on) c else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(label(item), style = MaterialTheme.typography.labelLarge, color = if (on) c else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** Icon grid for picking a habit / goal icon; works on [Glyphs] keys. */
@Composable
fun IconPicker(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    val current = Glyphs.key(selected)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { key ->
            val on = key == current
            val primary = MaterialTheme.colorScheme.primary
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(1.dp, if (on) primary else Color.Transparent, RoundedCornerShape(12.dp))
                    .bounceClick { onSelect(key) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Glyphs.icon(key), key, tint = if (on) primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
fun CircleIconButton(icon: ImageVector, onClick: () -> Unit, enabled: Boolean = true, tint: Color = LocalContentColor.current) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).border(1.dp, tint.copy(alpha = if (enabled) 0.2f else 0.08f), CircleShape)
            .bounceClick(enabled, onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint.copy(alpha = if (enabled) 1f else 0.3f), modifier = Modifier.size(20.dp)) }
}

/** "‹ label ›" stepper used for day / month navigation. */
@Composable
fun Stepper(label: String, onPrev: () -> Unit, onNext: (() -> Unit)?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CircleIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, onPrev, tint = tint)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, color = tint)
        CircleIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, { onNext?.invoke() }, enabled = onNext != null, tint = tint)
    }
}

// ---------------------------------------------------------------- Charts

/** Ring that animates to [fraction]. */
@Composable
fun ProgressRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    stroke: Dp = 8.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val anim by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700, easing = FastOutSlowInEasing), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val arc = Size(this.size.width - s, this.size.height - s)
            val tl = Offset(s / 2, s / 2)
            drawArc(track, 0f, 360f, false, tl, arc, style = Stroke(s))
            rotate(-90f) {
                drawArc(color, 0f, 360f * anim, false, tl, arc, style = Stroke(s, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

/** Thin animated progress bar. */
@Composable
fun ProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.surfaceVariant,
    height: Dp = 6.dp,
) {
    val anim by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700, easing = FastOutSlowInEasing), label = "bar")
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(track)) {
        Box(Modifier.fillMaxWidth(anim).height(height).clip(CircleShape).background(color))
    }
}

/** Donut of (colour, value) slices that sweeps in. */
@Composable
fun DonutChart(slices: List<Pair<Color, Float>>, modifier: Modifier = Modifier, size: Dp = 140.dp, stroke: Dp = 18.dp, content: @Composable BoxScope.() -> Unit = {}) {
    val total = slices.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(0.0001f)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(slices) { progress.snapTo(0f); progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val arc = Size(this.size.width - s, this.size.height - s)
            val tl = Offset(s / 2, s / 2)
            var start = -90f
            val gap = if (slices.size > 1) 2f else 0f
            slices.forEach { (color, value) ->
                val sweep = 360f * value / total * progress.value
                drawArc(color, start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false, tl, arc, style = Stroke(s, cap = StrokeCap.Butt))
                start += sweep
            }
        }
        content()
    }
}

// ---------------------------------------------------------------- Forms

/** Bottom sheet form with a Save button and optional Delete. */
@Composable
fun SheetForm(
    title: String,
    canSave: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    saveLabel: String = "Save",
    onDelete: (() -> Unit)? = null,
    /** Keeps Save in view below a long form instead of after it. */
    pinSave: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    @Composable
    fun Actions() {
        PrimaryButton(saveLabel, { onSave(); onDismiss() }, Modifier.fillMaxWidth(), enabled = canSave)
        onDelete?.let {
            TextButton(onClick = { it(); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("Delete", color = Bad, style = MaterialTheme.typography.labelLarge) }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        if (pinSave) {
            Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(start = 22.dp, end = 22.dp, bottom = 20.dp)) {
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    content()
                }
                Spacer(Modifier.height(12.dp))
                Actions()
            }
        } else {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
                    .padding(start = 22.dp, end = 22.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                content()
                Spacer(Modifier.height(4.dp))
                Actions()
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    unfocusedBorderColor = Color.Transparent,
)

@Composable
fun TextInput(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true, modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp), colors = fieldColors(),
    )
}

/** Rupee amount; [big] makes it the hero field of a form. */
@Composable
fun MoneyInput(value: String, onChange: (String) -> Unit, label: String, optional: Boolean = false, big: Boolean = false) {
    val bad = value.isNotBlank() && Money.parse(value) == null
    val style = (if (big) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyLarge).tabular()
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true, isError = bad,
        prefix = { Text("₹ ", style = style) },
        textStyle = style,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        supportingText = when {
            bad -> ({ Text("Enter an amount above zero") })
            optional -> ({ Text("Optional") })
            else -> null
        },
        shape = RoundedCornerShape(14.dp), colors = fieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun NumberInput(value: String, onChange: (String) -> Unit, label: String, hint: String? = null) {
    OutlinedTextField(
        value, { onChange(it.filter(Char::isDigit).take(4)) }, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = hint?.let { { Text(it) } },
        shape = RoundedCornerShape(14.dp), colors = fieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Tappable field-like row (date, time). */
@Composable
fun PickerField(label: String, value: String, icon: ImageVector, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .bounceClick(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        trailing?.invoke()
    }
}

@Composable
fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ---------------------------------------------------------------- Dialogs

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
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    )
}

// ---------------------------------------------------------------- Bottom navigation

data class NavItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector = icon)

/** Flat bottom bar with a hairline on top; the selected tab is ink with a short accent bar. */
@Composable
fun BottomNav(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp)) {
            items.forEachIndexed { i, item ->
                val on = i == selected
                val tint by animateColorAsState(
                    if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), label = "nav",
                )
                val bar by animateFloatAsState(if (on) 1f else 0f, tween(200), label = "navBar")
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).bounceClick { onSelect(i) }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.width(18.dp * bar).height(2.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    Spacer(Modifier.height(6.dp))
                    Icon(if (on) item.selectedIcon else item.icon, item.label, tint = tint, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(item.label, color = tint, style = MaterialTheme.typography.labelSmall, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Misc

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

@Composable
fun <T> rememberFlow(initial: T, vararg keys: Any?, flow: () -> kotlinx.coroutines.flow.Flow<T>): T {
    val f = remember(*keys) { flow() }
    return f.collectAsStateWithLifecycle(initial).value
}
