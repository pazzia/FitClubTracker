package com.fitclub.tracker.ui.theme

import android.graphics.Typeface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Палитра Volt Night. Светлая и сливовая темы подключаются на этапе «Личный кабинет». */
object Fc {
    val Bg = Color(0xFF0E0E12)
    val Surface = Color(0xFF18181F)
    val Surface2 = Color(0xFF26262F)
    val Line = Color(0xFF2C2C36)
    val Nav = Color(0xFF121217)
    val Text = Color(0xFFF5F5F7)
    val Muted = Color(0xFFA1A1AD)
    val Muted2 = Color(0xFF8B8B97)
    val Lime = Color(0xFFD4FF3A)
    val Pink = Color(0xFFFF3D6E)
    val Orange = Color(0xFFFFB23F)
    val TargetRed = Color(0xFFF0302F)
    val AuxOrange = Color(0xFFFF9A3C)
    val GlowBlue = Color(0xFF4D63FF)
    val LimeSoft = Color(0x24D4FF3A)

    fun fit(value: Float): Color = when {
        value >= 0.8f -> Lime
        value >= 0.6f -> Orange
        else -> Pink
    }

    fun level(level: Int): Color = when (level) { 1 -> Lime; 2 -> Orange; else -> Color(0xFFFF8FAE) }
}

/** Узкий гротеск для заголовков. Oswald можно подключить через Downloadable Fonts. */
val Condensed = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 30.sp),
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
fun FitClubTheme(content: @Composable () -> Unit) {
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
