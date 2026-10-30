package com.wac.worktrack.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Good = Color(0xFF12B886)
val Warn = Color(0xFFFFA62B)
val Bad = Color(0xFFFF5470)

/** Current epoch day; refreshed when the app resumes so screens roll over at midnight. */
val LocalToday = compositionLocalOf { 0L }

object Palette {
    val Violet = Color(0xFF7C5CFF)
    val Blue = Color(0xFF4F8BFF)
    val Sky = Color(0xFF2BC0E4)
    val Mint = Color(0xFF16C79A)
    val Teal = Color(0xFF0E9F8C)
    val Orange = Color(0xFFFF8A3D)
    val Pink = Color(0xFFFF4D8D)
    val Red = Color(0xFFFF5F6D)
    val Amber = Color(0xFFFFB648)
    val Purple = Color(0xFFB45CFF)
    val Slate = Color(0xFF8A8FB0)
}

/** Two-colour gradients for hero cards, buttons and rings. */
object Gradients {
    val Primary = listOf(Palette.Violet, Palette.Blue)
    val Fire = listOf(Palette.Orange, Palette.Pink)
    val Mint = listOf(Palette.Mint, Palette.Teal)
    val Ocean = listOf(Palette.Sky, Palette.Blue)
    val Danger = listOf(Palette.Red, Color(0xFFFF8A65))
    val Gold = listOf(Palette.Amber, Palette.Orange)
    val Grape = listOf(Palette.Purple, Palette.Pink)

    /** Bank cards cycle through these. */
    val cards = listOf(Primary, Ocean, Grape, Mint, Fire, Gold)
}

fun List<Color>.brush(): Brush = Brush.linearGradient(this)

/** Emoji + colour for each spending category / payable kind. */
object Visuals {
    private val category = mapOf(
        "Food" to ("🍔" to Palette.Orange),
        "Groceries" to ("🛒" to Palette.Mint),
        "Transport" to ("🚕" to Palette.Blue),
        "Shopping" to ("🛍️" to Palette.Pink),
        "Bills" to ("🧾" to Palette.Amber),
        "Health" to ("💊" to Palette.Red),
        "Entertainment" to ("🎬" to Palette.Purple),
        "Family" to ("👨‍👩‍👧" to Palette.Sky),
        "EMI" to ("🏦" to Palette.Violet),
        "Rent" to ("🏠" to Palette.Orange),
        "Credit card" to ("💳" to Palette.Pink),
        "Insurance" to ("🛡️" to Palette.Teal),
        "Subscription" to ("📺" to Palette.Purple),
        "Utility" to ("💡" to Palette.Amber),
    )

    fun emoji(name: String) = category[name]?.first ?: "✨"
    fun color(name: String) = category[name]?.second ?: Palette.Slate

    private val avatarColors = listOf(Palette.Violet, Palette.Pink, Palette.Mint, Palette.Orange, Palette.Sky, Palette.Purple)
    fun avatar(name: String) = avatarColors[Math.floorMod(name.lowercase().hashCode(), avatarColors.size)]

    val habitEmojis = listOf("✅", "🚶", "🏃", "🧘", "💪", "📚", "💧", "🛏️", "🍎", "🧠", "✍️", "🙏", "🎸", "🧹", "💊", "🚭", "📵", "☀️")
    val goalEmojis = listOf("🎯", "💰", "🏠", "🚗", "✈️", "📚", "💪", "💍", "🎓", "💻", "🏆", "❤️")
}

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9B83FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2C2560),
    onPrimaryContainer = Color(0xFFE3DCFF),
    secondary = Palette.Sky,
    tertiary = Palette.Pink,
    background = Color(0xFF0E0F1A),
    onBackground = Color(0xFFEDEEF7),
    surface = Color(0xFF0E0F1A),
    onSurface = Color(0xFFEDEEF7),
    surfaceVariant = Color(0xFF262A42),
    onSurfaceVariant = Color(0xFF9A9CB8),
    surfaceContainerLowest = Color(0xFF0A0B14),
    surfaceContainerLow = Color(0xFF141526),
    surfaceContainer = Color(0xFF181A2B),
    surfaceContainerHigh = Color(0xFF202338),
    surfaceContainerHighest = Color(0xFF282B44),
    outline = Color(0xFF3A3E60),
    outlineVariant = Color(0xFF2A2D47),
    error = Bad,
)

private val LightColors = lightColorScheme(
    primary = Palette.Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E3FF),
    onPrimaryContainer = Color(0xFF2A1A7A),
    secondary = Palette.Blue,
    tertiary = Palette.Pink,
    background = Color(0xFFF4F5FB),
    onBackground = Color(0xFF15172B),
    surface = Color(0xFFF4F5FB),
    onSurface = Color(0xFF15172B),
    surfaceVariant = Color(0xFFE9EAF4),
    onSurfaceVariant = Color(0xFF6B6E8A),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFE),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFF0F1F8),
    outline = Color(0xFFD5D7E6),
    outlineVariant = Color(0xFFE6E7F1),
    error = Bad,
)

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun WorkTrackTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
