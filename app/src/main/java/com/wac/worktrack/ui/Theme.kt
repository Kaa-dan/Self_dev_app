package com.wac.worktrack.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhonelinkErase
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SmokeFree
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Mid-tones that read on both the paper (light) and the charcoal (dark) background.
val Good = Color(0xFF5E8C5A)
val Warn = Color(0xFFC08A2E)
val Bad = Color(0xFFC0513F)

/** Current epoch day; refreshed when the app resumes so screens roll over at midnight. */
val LocalToday = compositionLocalOf { 0L }

/** Muted, earthy tints for categories, habits and accounts. */
object Palette {
    val Ember = Color(0xFFC0602F)
    val Sage = Color(0xFF6B8F5E)
    val Ochre = Color(0xFFC49A3A)
    val Slate = Color(0xFF5E7A96)
    val Plum = Color(0xFF8E5E86)
    val Teal = Color(0xFF3F8C88)
    val Clay = Color(0xFFA86F55)
    val Rose = Color(0xFFB8606A)
    val Stone = Color(0xFF8A8174)

    /** Habits, savings pots and bank cards cycle through these. */
    val cycle = listOf(Ember, Sage, Slate, Ochre, Plum, Teal, Clay)

    /** Deep fills for bank cards, each dark enough for cream text. */
    val cards = listOf(Color(0xFF2B241D), Color(0xFF2F4A3A), Color(0xFF2C3E52), Color(0xFF6A3520), Color(0xFF4A3550))
}

/** Icon + colour for each spending category / payable kind. */
object Visuals {
    private val category = mapOf(
        "Food" to (Icons.Rounded.Restaurant to Palette.Ember),
        "Groceries" to (Icons.Rounded.ShoppingCart to Palette.Sage),
        "Transport" to (Icons.Rounded.LocalTaxi to Palette.Slate),
        "Shopping" to (Icons.Rounded.ShoppingBag to Palette.Rose),
        "Bills" to (Icons.Rounded.Receipt to Palette.Ochre),
        "Health" to (Icons.Rounded.LocalHospital to Bad),
        "Entertainment" to (Icons.Rounded.Movie to Palette.Plum),
        "Family" to (Icons.Rounded.FamilyRestroom to Palette.Teal),
        "EMI" to (Icons.Rounded.AccountBalance to Palette.Slate),
        "Rent" to (Icons.Rounded.Home to Palette.Clay),
        "Credit card" to (Icons.Rounded.CreditCard to Palette.Rose),
        "Insurance" to (Icons.Rounded.HealthAndSafety to Palette.Teal),
        "Subscription" to (Icons.Rounded.Subscriptions to Palette.Plum),
        "Utility" to (Icons.Rounded.Lightbulb to Palette.Ochre),
    )

    fun icon(name: String) = category[name]?.first ?: Icons.Rounded.MoreHoriz
    fun color(name: String) = category[name]?.second ?: Palette.Stone

    fun avatar(name: String) = Palette.cycle[Math.floorMod(name.lowercase().hashCode(), Palette.cycle.size)]
}

/**
 * Icons for habits and goals. The key is what's stored in the `emoji` column; rows saved
 * before icons existed hold an emoji, which [icon] maps to the closest icon.
 */
object Glyphs {
    private val icons = linkedMapOf(
        "check" to Icons.Rounded.TaskAlt,
        "walk" to Icons.AutoMirrored.Rounded.DirectionsWalk,
        "run" to Icons.AutoMirrored.Rounded.DirectionsRun,
        "meditate" to Icons.Rounded.SelfImprovement,
        "gym" to Icons.Rounded.FitnessCenter,
        "read" to Icons.AutoMirrored.Rounded.MenuBook,
        "water" to Icons.Rounded.WaterDrop,
        "sleep" to Icons.Rounded.Bedtime,
        "eat" to Icons.Rounded.Spa,
        "mind" to Icons.Rounded.Psychology,
        "write" to Icons.Rounded.EditNote,
        "gratitude" to Icons.Rounded.VolunteerActivism,
        "music" to Icons.Rounded.MusicNote,
        "clean" to Icons.Rounded.CleaningServices,
        "medicine" to Icons.Rounded.Medication,
        "nosmoke" to Icons.Rounded.SmokeFree,
        "nophone" to Icons.Rounded.PhonelinkErase,
        "sun" to Icons.Rounded.WbSunny,
        "flag" to Icons.Rounded.Flag,
        "money" to Icons.Rounded.Savings,
        "home" to Icons.Rounded.Home,
        "car" to Icons.Rounded.DirectionsCar,
        "travel" to Icons.Rounded.Flight,
        "study" to Icons.Rounded.School,
        "ring" to Icons.Rounded.Diamond,
        "work" to Icons.Rounded.Laptop,
        "trophy" to Icons.Rounded.EmojiEvents,
        "heart" to Icons.Rounded.Favorite,
    )

    private val legacy = mapOf(
        "✅" to "check", "🚶" to "walk", "🏃" to "run", "🧘" to "meditate", "💪" to "gym", "📚" to "read",
        "💧" to "water", "🛏️" to "sleep", "🍎" to "eat", "🧠" to "mind", "✍️" to "write", "🙏" to "gratitude",
        "🎸" to "music", "🧹" to "clean", "💊" to "medicine", "🚭" to "nosmoke", "📵" to "nophone", "☀️" to "sun",
        "🎯" to "flag", "💰" to "money", "🏠" to "home", "🚗" to "car", "✈️" to "travel", "💍" to "ring",
        "🎓" to "study", "💻" to "work", "🏆" to "trophy", "❤️" to "heart",
    )

    val habit = listOf(
        "check", "walk", "run", "meditate", "gym", "read", "water", "sleep", "eat",
        "mind", "write", "gratitude", "music", "clean", "medicine", "nosmoke", "nophone", "sun",
    )
    val goal = listOf("flag", "money", "home", "car", "travel", "read", "gym", "ring", "study", "work", "trophy", "heart")

    /** Stored key (or legacy emoji) → the key used today. */
    fun key(stored: String) = legacy[stored] ?: stored.takeIf { it in icons } ?: "check"
    fun icon(stored: String): ImageVector = icons.getValue(key(stored))
}

private val LightColors = lightColorScheme(
    primary = Color(0xFFB4532A),
    onPrimary = Color(0xFFFFF8F2),
    primaryContainer = Color(0xFFF6E3D6),
    onPrimaryContainer = Color(0xFF5A2410),
    secondary = Color(0xFF5D7A55),
    onSecondary = Color.White,
    tertiary = Palette.Ochre,
    background = Color(0xFFF5F1EA),
    onBackground = Color(0xFF1E1A15),
    surface = Color(0xFFF5F1EA),
    onSurface = Color(0xFF1E1A15),
    surfaceVariant = Color(0xFFE9E2D6),
    onSurfaceVariant = Color(0xFF6E6558),
    surfaceContainerLowest = Color(0xFFFFFDF9),
    surfaceContainerLow = Color(0xFFFAF6F0),
    surfaceContainer = Color(0xFFFFFCF7),
    surfaceContainerHigh = Color(0xFFFFFCF7),
    surfaceContainerHighest = Color(0xFFEFE9DF),
    inverseSurface = Color(0xFF2B241D),
    inverseOnSurface = Color(0xFFF4EDE2),
    outline = Color(0xFFCFC5B5),
    outlineVariant = Color(0xFFE6DED1),
    error = Bad,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE08A5E),
    onPrimary = Color(0xFF2A1408),
    primaryContainer = Color(0xFF4A2A1A),
    onPrimaryContainer = Color(0xFFF7DCCB),
    secondary = Color(0xFF8DB583),
    onSecondary = Color(0xFF14210F),
    tertiary = Palette.Ochre,
    background = Color(0xFF141210),
    onBackground = Color(0xFFEEE7DC),
    surface = Color(0xFF141210),
    onSurface = Color(0xFFEEE7DC),
    surfaceVariant = Color(0xFF2E2A24),
    onSurfaceVariant = Color(0xFFA39A8C),
    surfaceContainerLowest = Color(0xFF0F0D0B),
    surfaceContainerLow = Color(0xFF1A1714),
    surfaceContainer = Color(0xFF1E1B17),
    surfaceContainerHigh = Color(0xFF24201B),
    surfaceContainerHighest = Color(0xFF2A2620),
    inverseSurface = Color(0xFF332A21),
    inverseOnSurface = Color(0xFFF1E9DD),
    outline = Color(0xFF4A443B),
    outlineVariant = Color(0xFF2E2A24),
    error = Bad,
)

/** Tabular figures, so amounts and timers don't jiggle as digits change. */
fun TextStyle.tabular() = copy(fontFeatureSettings = "tnum")

private val AppTypography = Typography().let { t ->
    val serif = FontFamily.Serif
    t.copy(
        displaySmall = t.displaySmall.copy(fontFamily = serif, fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp, fontFeatureSettings = "tnum"),
        headlineLarge = t.headlineLarge.copy(fontFamily = serif, fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontFamily = serif, fontWeight = FontWeight.Medium, letterSpacing = (-0.25).sp, fontFeatureSettings = "tnum"),
        headlineSmall = t.headlineSmall.copy(fontFamily = serif, fontWeight = FontWeight.Medium),
        titleLarge = t.titleLarge.copy(fontFamily = serif, fontWeight = FontWeight.Medium),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = t.labelMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
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
