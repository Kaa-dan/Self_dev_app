package com.wac.worktrack.block

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wac.worktrack.core.durationLabel
import com.wac.worktrack.ui.GradientButton
import com.wac.worktrack.ui.Gradients
import com.wac.worktrack.ui.WorkTrackTheme
import com.wac.worktrack.ui.brush
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Shown instead of a blocked app. Its only way out is the home screen. */
class BlockedActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val until = intent.getLongExtra(EXTRA_UNTIL, 0)
        val name = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }
            .getOrDefault("This app")
        val end = Instant.ofEpochMilli(until).atZone(ZoneId.systemDefault())
        val endLabel = end.format(DateTimeFormatter.ofPattern("h:mm a")) +
            if (end.toLocalDate() != LocalDate.now()) " tomorrow" else ""
        val left = ((until - System.currentTimeMillis()) / 60_000).toInt().coerceAtLeast(1)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        setContent {
            WorkTrackTheme {
                Column(
                    Modifier.fillMaxSize().background(Gradients.Primary.brush()).safeDrawingPadding().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("🧠", fontSize = 84.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("Stay focused", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "$name is blocked until $endLabel\n${durationLabel(left)} to go",
                        color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(36.dp))
                    GradientButton("Back to home", ::goHome, Modifier.fillMaxWidth(), gradient = Gradients.Mint)
                }
            }
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_UNTIL = "until"
    }
}
