package com.wac.worktrack.ui.screens

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wac.worktrack.core.write
import com.wac.worktrack.data.HabitLog
import com.wac.worktrack.notify.HabitAlarms
import com.wac.worktrack.ui.Gradients
import com.wac.worktrack.ui.bounceClick
import com.wac.worktrack.ui.brush

fun toggleHabit(context: Context, habitId: Long, day: Long, done: Boolean) {
    context.write {
        if (done) {
            habits().log(HabitLog(habitId, day))
            // Done: stop its alarm and timer.
            HabitAlarms.onMarkedDone(context, habitId)
        } else {
            habits().unlog(HabitLog(habitId, day))
        }
    }
}

/** Ticks up each time [allDone] turns true after the data has loaded, to fire confetti. */
@Composable
fun rememberCelebration(allDone: Boolean, loaded: Boolean): Int {
    var trigger by remember { mutableIntStateOf(0) }
    var previous by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(allDone, loaded) {
        if (!loaded) return@LaunchedEffect
        if (previous == false && allDone) trigger++
        previous = allDone
    }
    return trigger
}

/** Round check that pops and fills with the gradient when done. */
@Composable
fun CheckCircle(checked: Boolean, onToggle: () -> Unit, size: Dp = 40.dp, gradient: List<Color> = Gradients.Mint) {
    val scale by animateFloatAsState(if (checked) 1f else 0.9f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMedium), label = "check")
    val border by animateColorAsState(if (checked) Color.Transparent else MaterialTheme.colorScheme.outline, label = "checkBorder")
    Box(
        Modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .then(if (checked) Modifier.background(gradient.brush()) else Modifier)
            .border(2.dp, border, CircleShape)
            .bounceClick(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Default.Check, "Done", tint = Color.White, modifier = Modifier.size(size * 0.6f))
    }
}
