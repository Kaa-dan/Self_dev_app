package com.wac.worktrack.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val Good = Color(0xFF2E9E5B)
val Warn = Color(0xFFE08A00)
val Bad = Color(0xFFD64545)

fun scoreColor(score: Int?): Color = when {
    score == null -> Color.Gray
    score >= 80 -> Good
    score >= 50 -> Warn
    else -> Bad
}

/** Current epoch day; refreshed when the app resumes so screens roll over at midnight. */
val LocalToday = compositionLocalOf { 0L }

private val Indigo = Color(0xFF4F5BD5)
private val IndigoLight = Color(0xFF8B93E3)

/** Wallpaper colours on Android 12+, indigo before that. */
@Composable
fun WorkTrackTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = IndigoLight, secondary = IndigoLight, tertiary = IndigoLight)
        else -> lightColorScheme(primary = Indigo, secondary = Indigo, tertiary = Indigo)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
