@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.wac.worktrack.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------- Interaction

/** Click that shrinks a little while pressed and gives a light haptic tick. */
@Composable
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "press")
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
        progress.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }
    return from + ((to - from) * progress.value).toLong()
}

@Composable
fun AnimatedAmount(paise: Long, style: TextStyle, color: Color = Color.Unspecified, modifier: Modifier = Modifier) {
    Text(Money.format(animatedPaise(paise)), modifier, style = style, color = color, maxLines = 1)
}

// ---------------------------------------------------------------- Cards

/** Rounded card; a [gradient] makes it a bright hero card with white text. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    gradient: List<Color>? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier
            .then(if (onClick != null) Modifier.bounceClick(onClick = onClick) else Modifier)
            .then(if (gradient != null) Modifier.shadow(14.dp, shape, ambientColor = gradient.first(), spotColor = gradient.first()) else Modifier)
            .clip(shape)
            .then(if (gradient != null) Modifier.background(gradient.brush()) else Modifier.background(color))
            .then(if (gradient == null) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Emoji in a soft tinted circle. */
@Composable
fun EmojiBubble(emoji: String, color: Color, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = (size.value * 0.48f).sp) }
}

/** Initial in a coloured circle, for people. */
@Composable
fun Avatar(name: String, size: Dp = 44.dp) {
    val color = Visuals.avatar(name)
    Box(Modifier.size(size).clip(CircleShape).background(listOf(color, color.copy(alpha = 0.6f)).brush()), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
    }
}

/** Small tile: emoji, label and an animated amount. */
@Composable
fun StatTile(
    emoji: String,
    label: String,
    paise: Long,
    tint: Color,
    modifier: Modifier = Modifier,
    sub: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
) {
    AppCard(modifier, onClick = onClick, padding = PaddingValues(16.dp)) {
        EmojiBubble(emoji, tint, 38.dp)
        Spacer(Modifier.height(12.dp))
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
            .clip(RoundedCornerShape(20.dp))
            .then(if (onClick != null) Modifier.bounceClick(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let { it(); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = subColor, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        trailing?.let { Spacer(Modifier.width(8.dp)); it() }
    }
}

@Composable
fun AmountText(paise: Long, color: Color = MaterialTheme.colorScheme.onSurface, prefix: String = "") {
    Text(prefix + Money.format(paise), style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
}

/** Coloured pill label ("Overdue", "Paid ✓"). */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text, modifier.clip(CircleShape).background(color.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp),
        color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1,
    )
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            subtitle?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(title, style = MaterialTheme.typography.headlineLarge)
        }
        trailing?.invoke()
    }
}

@Composable
fun EmptyState(emoji: String, title: String, text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 52.sp)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

// ---------------------------------------------------------------- Buttons and pickers

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    gradient: List<Color> = Gradients.Primary,
    small: Boolean = false,
) {
    Box(
        modifier
            .clip(CircleShape)
            .background(if (enabled) gradient.brush() else Brush.linearGradient(listOf(Color.Gray.copy(0.35f), Color.Gray.copy(0.35f))))
            .bounceClick(enabled, onClick)
            .padding(horizontal = if (small) 16.dp else 24.dp, vertical = if (small) 8.dp else 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, style = if (small) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium)
    }
}

/** Floating "+ Add" button with a gradient. */
@Composable
fun GradientFab(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, gradient: List<Color> = Gradients.Primary) {
    Row(
        modifier
            .shadow(16.dp, CircleShape, ambientColor = gradient.first(), spotColor = gradient.first())
            .clip(CircleShape)
            .background(gradient.brush())
            .bounceClick(onClick = onClick)
            .padding(start = 18.dp, end = 22.dp, top = 15.dp, bottom = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Add, null, tint = Color.White)
        Spacer(Modifier.width(6.dp))
        Text(text, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

/** Horizontally scrolling pills; the selected one is filled with the gradient. */
@Composable
fun <T> PillTabs(items: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            val on = item == selected
            val textColor by animateColorAsState(if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, label = "pill")
            Box(
                Modifier
                    .clip(CircleShape)
                    .then(
                        if (on) Modifier.background(Gradients.Primary.brush())
                        else Modifier.background(MaterialTheme.colorScheme.surfaceContainer).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    )
                    .bounceClick { onSelect(item) }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) { Text(label(item), color = textColor, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

/** Wrapping grid of selectable chips, optionally with an emoji each. */
@Composable
fun <T> ChipPicker(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    emoji: ((T) -> String)? = null,
    color: (T) -> Color = { Palette.Violet },
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            val on = item == selected
            val c = color(item)
            val bg by animateColorAsState(if (on) c.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHighest, label = "chip")
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(bg)
                    .border(1.5.dp, if (on) c else Color.Transparent, CircleShape)
                    .bounceClick { onSelect(item) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                emoji?.let { Text(it(item), fontSize = 16.sp); Spacer(Modifier.width(6.dp)) }
                Text(
                    label(item), style = MaterialTheme.typography.labelLarge,
                    color = if (on) c else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Big emoji grid for picking a habit / goal icon. */
@Composable
fun EmojiPicker(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { e ->
            val on = e == selected
            val scale by animateFloatAsState(if (on) 1.12f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "emoji")
            Box(
                Modifier
                    .size(46.dp)
                    .scale(scale)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(2.dp, if (on) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(14.dp))
                    .bounceClick { onSelect(e) },
                contentAlignment = Alignment.Center,
            ) { Text(e, fontSize = 22.sp) }
        }
    }
}

@Composable
fun CircleIconButton(icon: ImageVector, onClick: () -> Unit, enabled: Boolean = true, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = if (enabled) 1f else 0.4f))
            .bounceClick(enabled, onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint.copy(alpha = if (enabled) 1f else 0.3f)) }
}

/** "‹ label ›" stepper used for day / month navigation. */
@Composable
fun Stepper(label: String, onPrev: () -> Unit, onNext: (() -> Unit)?, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CircleIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, onPrev, tint = tint)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, color = tint)
        CircleIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, { onNext?.invoke() }, enabled = onNext != null, tint = tint)
    }
}

// ---------------------------------------------------------------- Charts

/** Ring that animates to [fraction], drawn with a sweep gradient. */
@Composable
fun ProgressRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    stroke: Dp = 10.dp,
    gradient: List<Color> = Gradients.Primary,
    track: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val anim by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val arc = Size(this.size.width - s, this.size.height - s)
            val tl = Offset(s / 2, s / 2)
            drawArc(track, 0f, 360f, false, tl, arc, style = Stroke(s))
            rotate(-90f) {
                drawArc(
                    Brush.sweepGradient(gradient + gradient.first()), 0f, 360f * anim, false, tl, arc,
                    style = Stroke(s, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

/** Animated gradient bar. */
@Composable
fun GradientBar(fraction: Float, modifier: Modifier = Modifier, gradient: List<Color> = Gradients.Primary, track: Color = MaterialTheme.colorScheme.surfaceVariant, height: Dp = 10.dp) {
    val anim by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing), label = "bar")
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(track)) {
        Box(Modifier.fillMaxWidth(anim).height(height).clip(CircleShape).background(gradient.brush()))
    }
}

/** Donut of (colour, value) slices that sweeps in. */
@Composable
fun DonutChart(slices: List<Pair<Color, Float>>, modifier: Modifier = Modifier, size: Dp = 140.dp, stroke: Dp = 22.dp, content: @Composable BoxScope.() -> Unit = {}) {
    val total = slices.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(0.0001f)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(slices) { progress.snapTo(0f); progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val arc = Size(this.size.width - s, this.size.height - s)
            val tl = Offset(s / 2, s / 2)
            var start = -90f
            val gap = if (slices.size > 1) 3f else 0f
            slices.forEach { (color, value) ->
                val sweep = 360f * value / total * progress.value
                drawArc(color, start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false, tl, arc, style = Stroke(s, cap = StrokeCap.Butt))
                start += sweep
            }
        }
        content()
    }
}

/** Burst of confetti each time [trigger] changes to a new non-zero value. */
@Composable
fun Confetti(trigger: Int, modifier: Modifier = Modifier) {
    if (trigger == 0) return
    val progress = remember(trigger) { Animatable(0f) }
    val pieces = remember(trigger) {
        val colors = listOf(Palette.Violet, Palette.Pink, Palette.Amber, Palette.Mint, Palette.Sky, Palette.Orange)
        List(90) {
            val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
            Triple(angle, 0.4f + Random.nextFloat() * 0.8f, colors[it % colors.size])
        }
    }
    LaunchedEffect(trigger) { progress.animateTo(1f, tween(1800, easing = LinearEasing)) }
    if (progress.value >= 1f) return
    Canvas(modifier.fillMaxSize()) {
        val origin = Offset(size.width / 2, size.height * 0.35f)
        val t = progress.value
        pieces.forEachIndexed { i, (angle, speed, color) ->
            val dist = size.minDimension * 0.75f * speed * t
            val gravity = size.height * 0.6f * t * t
            val p = Offset(origin.x + cos(angle) * dist, origin.y + sin(angle) * dist + gravity)
            val a = (1f - t).coerceIn(0f, 1f)
            rotate(t * 720f * (if (i % 2 == 0) 1 else -1), p) {
                drawRect(color.copy(alpha = a), Offset(p.x - 6f, p.y - 3f), Size(14f, 7f))
            }
        }
    }
}

// ---------------------------------------------------------------- Forms

/** Bottom sheet form with a gradient Save button and optional Delete. */
@Composable
fun SheetForm(
    title: String,
    canSave: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    saveLabel: String = "Save",
    onDelete: (() -> Unit)? = null,
    gradient: List<Color> = Gradients.Primary,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
                .padding(start = 22.dp, end = 22.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            content()
            Spacer(Modifier.height(4.dp))
            GradientButton(saveLabel, { onSave(); onDismiss() }, Modifier.fillMaxWidth(), enabled = canSave, gradient = gradient)
            onDelete?.let {
                TextButton(onClick = { it(); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("Delete", color = Bad, fontWeight = FontWeight.SemiBold) }
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
        shape = RoundedCornerShape(18.dp), colors = fieldColors(),
    )
}

/** Rupee amount; [big] makes it the hero field of a form. */
@Composable
fun MoneyInput(value: String, onChange: (String) -> Unit, label: String, optional: Boolean = false, big: Boolean = false) {
    val bad = value.isNotBlank() && Money.parse(value) == null
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true, isError = bad,
        prefix = { Text("₹ ", style = if (big) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyLarge) },
        textStyle = if (big) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        supportingText = when {
            bad -> ({ Text("Enter an amount above zero") })
            optional -> ({ Text("Optional") })
            else -> null
        },
        shape = RoundedCornerShape(18.dp), colors = fieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun NumberInput(value: String, onChange: (String) -> Unit, label: String, hint: String? = null) {
    OutlinedTextField(
        value, { onChange(it.filter(Char::isDigit).take(4)) }, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = hint?.let { { Text(it) } },
        shape = RoundedCornerShape(18.dp), colors = fieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Tappable field-like row (date, time). */
@Composable
fun PickerField(label: String, value: String, emoji: String, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .bounceClick(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 20.sp)
        Spacer(Modifier.width(12.dp))
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
        shape = RoundedCornerShape(28.dp),
    )
}

// ---------------------------------------------------------------- Bottom navigation

data class NavItem(val label: String, val icon: ImageVector)

/** Floating pill nav bar; the selected item grows to show its label. */
@Composable
fun FloatingNavBar(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .fillMaxWidth()
            .shadow(20.dp, CircleShape)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .padding(6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { i, item ->
            val on = i == selected
            val tint by animateColorAsState(if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, label = "nav")
            Row(
                Modifier
                    .clip(CircleShape)
                    .then(if (on) Modifier.background(Gradients.Primary.brush()) else Modifier)
                    .bounceClick { onSelect(i) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(item.icon, item.label, tint = tint)
                AnimatedVisibility(on, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                    Text(item.label, Modifier.padding(start = 8.dp), color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
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
