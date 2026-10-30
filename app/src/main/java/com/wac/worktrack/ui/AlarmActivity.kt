package com.wac.worktrack.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wac.worktrack.core.db
import com.wac.worktrack.core.durationLabel
import com.wac.worktrack.core.timeLabel
import com.wac.worktrack.data.Habit
import com.wac.worktrack.notify.HabitAlarms
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** The ringing alarm, shown full screen (over the lock screen) at a habit's time. */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        val habitId = HabitAlarms.habitId(intent)
        setContent {
            WorkTrackTheme {
                val habit by produceState<Habit?>(null, habitId) { value = db.habits().habit(habitId) }
                habit?.let { AlarmScreen(it) { action -> act(action, habitId) } }
            }
        }
    }

    private fun act(action: String, habitId: Long) {
        HabitAlarms.perform(this, action, habitId)
        finish()
    }
}

@Composable
private fun AlarmScreen(habit: Habit, onAction: (String) -> Unit) {
    val pulse = rememberInfiniteTransition(label = "alarm")
    val scale by pulse.animateFloat(0.9f, 1.12f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "scale")
    val ring by pulse.animateFloat(0.15f, 0.35f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "ring")
    val timed = habit.durationMinutes != null
    Box(Modifier.fillMaxSize().background(Gradients.Fire.brush())) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(24.dp))
                Text(
                    LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm")),
                    color = Color.White, fontSize = 76.sp, fontWeight = FontWeight.ExtraBold,
                )
                Text("Habit time", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.titleMedium)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(190.dp).scale(scale).clip(CircleShape).background(Color.White.copy(alpha = ring)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(140.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                        Text(habit.emoji, fontSize = 72.sp)
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text(habit.name, color = Color.White, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                val details = listOfNotNull(habit.timeMinutes?.let(::timeLabel), habit.durationMinutes?.let { "for ${durationLabel(it)}" })
                if (details.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(details.joinToString(" · "), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.titleMedium)
                }
            }

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AlarmButton(
                    if (timed) "▶  Start ${durationLabel(habit.durationMinutes!!)} timer" else "✓  Mark done",
                    filled = true,
                ) { onAction(if (timed) HabitAlarms.START else HabitAlarms.DONE) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AlarmButton("😴  ${HabitAlarms.SNOOZE_MINUTES} min", Modifier.weight(1f)) { onAction(HabitAlarms.SNOOZE) }
                    AlarmButton("Dismiss", Modifier.weight(1f)) { onAction(HabitAlarms.DISMISS) }
                }
                if (timed) {
                    AlarmButton("✓  Already done") { onAction(HabitAlarms.DONE) }
                }
            }
        }
    }
}

@Composable
private fun AlarmButton(text: String, modifier: Modifier = Modifier.fillMaxWidth(), filled: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier
            .clip(CircleShape)
            .background(if (filled) Color.White else Color.White.copy(alpha = 0.18f))
            .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
            .bounceClick(onClick = onClick)
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = if (filled) Palette.Pink else Color.White,
            fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium,
        )
    }
}
