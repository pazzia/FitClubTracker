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

/** Shared palette for dark themes and White Day. */
object Fc {
    private var light by mutableStateOf(false)
    val Bg get() = if (light) Color.White else Color(0xFF0E1317)
    val Surface get() = if (light) Color(0xFFF5F7F9) else Color(0xFF1B2228)
    val Surface2 get() = if (light) Color(0xFFE8EDF2) else Color(0xFF283239)
    val Line get() = if (light) Color(0xFFCCD4DC) else Color(0xFF303940)
    val Nav get() = if (light) Color.White else Color(0xFF11171C)
    val Text get() = if (light) Color(0xFF17212B) else Color(0xFFF5F5F7)
    val Muted get() = if (light) Color(0xFF53616E) else Color(0xFF8C99A4)
    val Muted2 get() = if (light) Color(0xFF5D6B78) else Color(0xFF81909C)
    var Lime by mutableStateOf(Color(0xFFD4FF3A))
        private set
    fun configure(argb: Long, lightTheme: Boolean) { light = lightTheme; Lime = Color(argb) }
    val Pink get() = if (light) Color(0xFFBF2450) else Color(0xFFFF3D6E)
    val Orange get() = if (light) Color(0xFF975B00) else Color(0xFFFFB23F)
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
fun FitClubTheme(accent: Long = 0xFFD4FF3A, lightTheme: Boolean = false, content: @Composable () -> Unit) {
    SideEffect { Fc.configure(accent, lightTheme) }
    MaterialTheme(
        colorScheme = if (lightTheme) lightColorScheme(
            primary = Color(accent), onPrimary = foregroundFor(Color(accent)),
            secondary = Color(0xFFBF2450), onSecondary = Color.White,
            background = Color.White, onBackground = Color(0xFF17212B),
            surface = Color(0xFFF5F7F9), onSurface = Color(0xFF17212B),
            surfaceVariant = Color(0xFFE8EDF2), onSurfaceVariant = Color(0xFF53616E),
            outline = Color(0xFFCCD4DC), error = Color(0xFFBF2450)
        ) else darkColorScheme(
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


/** Choose readable text over both bright program colours and dark accents. */
fun foregroundFor(color: Color): Color = if (color.luminance() > .179f) Color(0xFF17212B) else Color.White
