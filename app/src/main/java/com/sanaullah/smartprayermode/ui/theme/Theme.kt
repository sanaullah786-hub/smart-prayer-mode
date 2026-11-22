package com.sanaullah.smartprayermode.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val DeepGreen = Color(0xFF05241A)
val ForestGreen = Color(0xFF0A3B2A)
val CardGreen = Color(0xFF0F4D37)
val Emerald = Color(0xFF1FA05A)
val Gold = Color(0xFFE2B94A)
val GoldSoft = Color(0xFFF3DC95)
val Ivory = Color(0xFFF4EFDC)
val Danger = Color(0xFFB3261E)

val IslamicBackground = Brush.verticalGradient(
    listOf(Color(0xFF031A12), Color(0xFF0A3B2A), Color(0xFF05241A))
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 34.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 28.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
)

/** The app is intentionally always dark green + gold, whatever the system theme. */
@Composable
fun SmartPrayerTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_VARIABLE")
    val unused = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gold,
            onPrimary = DeepGreen,
            secondary = Emerald,
            background = DeepGreen,
            onBackground = Ivory,
            surface = CardGreen,
            onSurface = Ivory,
            surfaceVariant = ForestGreen,
            onSurfaceVariant = Ivory.copy(alpha = 0.75f),
            error = Danger,
        ),
        typography = AppTypography,
        content = content
    )
}
