package com.fitclub.tracker.ui.theme

import android.graphics.Typeface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Палитра Volt Night. Светлая и сливовая темы подключаются на этапе «Личный кабинет». */
object Fc {
    val Bg = Color(0xFF0E1317)
    val Surface = Color(0xFF1B2228)
    val Surface2 = Color(0xFF283239)
    val Line = Color(0xFF303940)
    val Nav = Color(0xFF11171C)
    val Text = Color(0xFFF5F5F7)
    val Muted = Color(0xFF8C99A4)
    val Muted2 = Color(0xFF81909C)
    var Lime by mutableStateOf(Color(0xFFD4FF3A))
        private set
    fun accent(argb: Long) { Lime = Color(argb) }
    val Pink = Color(0xFFFF3D6E)
    val Orange = Color(0xFFFFB23F)
    val TargetRed = Color(0xFFF0302F)
    val AuxOrange = Color(0xFFFF9A3C)
    val GlowBlue = Color(0xFF4D63FF)
    val LimeSoft get() = Lime.copy(alpha = .14f)

    fun fit(value: Float): Color = when {
        value >= 0.8f -> Lime
        value >= 0.6f -> Orange
        else -> Pink
    }

    fun level(level: Int): Color = when (level) { 1 -> Lime; 2 -> Orange; else -> Color(0xFFFF8FAE) }
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
fun FitClubTheme(accent: Long = 0xFFD4FF3A, content: @Composable () -> Unit) {
    SideEffect { Fc.accent(accent) }
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Fc.Lime, onPrimary = Fc.Bg,
            secondary = Fc.Pink, onSecondary = Fc.Bg,
            background = Fc.Bg, onBackground = Fc.Text,
            surface = Fc.Surface, onSurface = Fc.Text,
            surfaceVariant = Fc.Surface2, onSurfaceVariant = Fc.Muted,
            outline = Fc.Line, error = Fc.Pink
        ),
        typography = AppTypography,
        content = content
    )
}
