package com.fitclub.tracker.ui.theme

import android.graphics.Typeface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Shared palette for light and dark themes. */
private val LocalLightTheme = staticCompositionLocalOf { false }

object Fc {
    val Bg: Color @Composable get() = MaterialTheme.colorScheme.background
    val Surface: Color @Composable get() = MaterialTheme.colorScheme.surface
    val Surface2: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
    val Line: Color @Composable get() = MaterialTheme.colorScheme.outline
    val Nav: Color @Composable get() = if (LocalLightTheme.current) Color.White else Color(0xFF11171C)
    val Text: Color @Composable get() = MaterialTheme.colorScheme.onBackground
    val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val Muted2: Color @Composable get() = if (LocalLightTheme.current) Color(0xFF5D6B78) else Color(0xFF81909C)
    val Lime: Color @Composable get() = MaterialTheme.colorScheme.primary
    val Pink: Color @Composable get() = MaterialTheme.colorScheme.secondary
    val Orange: Color @Composable get() = if (LocalLightTheme.current) Color(0xFF975B00) else Color(0xFFFFB23F)
    val TargetRed = Color(0xFFF0302F)
    val AuxOrange = Color(0xFFFF9A3C)
    val GlowBlue = Color(0xFF4D63FF)
    val LimeSoft: Color @Composable get() = Lime.copy(alpha = .14f)

    @Composable fun fit(value: Float): Color = when {
        value >= 0.8f -> Lime
        value >= 0.6f -> Orange
        else -> Pink
    }
    @Composable fun level(level: Int): Color = when (level) { 1 -> Lime; 2 -> Orange; else -> Color(0xFFFF8FAE) }
}

/** Узкий гротеск для заголовков. Oswald можно подключить через Downloadable Fonts. */
val Condensed = FontFamily.SansSerif

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 35.sp),
    headlineMedium = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 31.sp),
    headlineSmall = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 15.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp)
)

@Composable
fun FitClubTheme(accent: Long = 0xFF69B7FF, lightTheme: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLightTheme provides lightTheme) {
    MaterialTheme(
        colorScheme = if (lightTheme) lightColorScheme(
            primary = Color(accent), onPrimary = foregroundFor(Color(accent)),
            secondary = Color(0xFFBF2450), onSecondary = Color.White,
            background = Color.White, onBackground = Color(0xFF17212B),
            surface = Color(0xFFF5F7F9), onSurface = Color(0xFF17212B),
            surfaceVariant = Color(0xFFE8EDF2), onSurfaceVariant = Color(0xFF53616E),
            outline = Color(0xFFCCD4DC), error = Color(0xFFBF2450)
        ) else darkColorScheme(
            primary = Color(accent), onPrimary = foregroundFor(Color(accent)),
            secondary = Color(0xFFFF3D6E), onSecondary = Color(0xFF0E1317),
            background = Color(0xFF0E1317), onBackground = Color(0xFFF5F5F7),
            surface = Color(0xFF1B2228), onSurface = Color(0xFFF5F5F7),
            surfaceVariant = Color(0xFF283239), onSurfaceVariant = Color(0xFF8C99A4),
            outline = Color(0xFF303940), error = Color(0xFFFF3D6E)
        ),
        typography = AppTypography,
        content = content
    )
    }
}


/** Choose readable text over both bright program colours and dark accents. */
fun foregroundFor(color: Color): Color = if (color.luminance() > .179f) Color(0xFF17212B) else Color.White
