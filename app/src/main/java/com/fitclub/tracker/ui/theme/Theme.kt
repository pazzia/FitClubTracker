@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.fitclub.tracker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitclub.tracker.R

/**
 * Iron & Lime. Dark: lime marks actions and completed work. Light: actions are ink with a lime
 * label, lime is a highlighter behind dark text and is never used as text on white.
 */
data class FcPalette(
    val light: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val line: Color,
    val line2: Color,
    val text: Color,
    val muted: Color,
    val dim: Color,
    /** Text, icons and outlines that mean "active"; also the fill of primary actions. */
    val accentInk: Color,
    /** Content placed on [accentInk]. */
    val onAccent: Color,
    val accentSoft: Color,
    val ember: Color,
    val emberSoft: Color,
    val danger: Color
)

val Lime = Color(0xFFC6FF3D)

fun palette(light: Boolean, accent: Color = Lime): FcPalette = if (light) FcPalette(
    light = true,
    bg = Color(0xFFF3F4EF), surface = Color.White, surface2 = Color(0xFFE8EAE3),
    line = Color(0xFFD5D9D0), line2 = Color(0xFFE3E6DE),
    text = Color(0xFF101315), muted = Color(0xFF59626A), dim = Color(0xFFA3AAA2),
    accentInk = Color(0xFF101315), onAccent = accent, accentSoft = accent.copy(alpha = .55f),
    ember = Color(0xFFB5400C), emberSoft = Color(0xFFB5400C).copy(alpha = .10f), danger = Color(0xFFC8102E)
) else FcPalette(
    light = false,
    bg = Color(0xFF0A0C0D), surface = Color(0xFF15181A), surface2 = Color(0xFF1E2226),
    line = Color(0xFF2A3035), line2 = Color(0xFF22272B),
    text = Color(0xFFF2F4EE), muted = Color(0xFF98A1A8), dim = Color(0xFF5F686F),
    accentInk = accent, onAccent = Color(0xFF0A0C0D), accentSoft = accent.copy(alpha = .14f),
    ember = Color(0xFFFF7A45), emberSoft = Color(0xFFFF7A45).copy(alpha = .14f), danger = Color(0xFFFF4D5E)
)

private val LocalPalette = staticCompositionLocalOf { palette(false) }

object Fc {
    private val p: FcPalette @Composable get() = LocalPalette.current
    val isLight: Boolean @Composable get() = p.light
    val Bg: Color @Composable get() = p.bg
    val Surface: Color @Composable get() = p.surface
    val Surface2: Color @Composable get() = p.surface2
    val AnatomyBackground: Color @Composable get() = p.surface
    val AnatomyLabel: Color @Composable get() = p.surface
    val Line: Color @Composable get() = p.line
    val Line2: Color @Composable get() = p.line2
    val Nav: Color @Composable get() = p.bg
    val Text: Color @Composable get() = p.text
    val Muted: Color @Composable get() = p.muted
    val Muted2: Color @Composable get() = p.muted
    val Dim: Color @Composable get() = p.dim
    /** Historical name: the accent ink. Lime in the dark theme, ink in the light one. */
    val Lime: Color @Composable get() = p.accentInk
    val OnAccent: Color @Composable get() = p.onAccent
    val LimeSoft: Color @Composable get() = p.accentSoft
    val Pink: Color @Composable get() = p.danger
    val Danger: Color @Composable get() = p.danger
    val Orange: Color @Composable get() = p.ember
    val Ember: Color @Composable get() = p.ember
    val EmberSoft: Color @Composable get() = p.emberSoft
    val TargetRed = Color(0xFFF0302F)
    val AuxOrange = Color(0xFFFF9A3C)
    val GlowBlue = Color(0xFF4D63FF)

    @Composable fun fit(value: Float): Color = when {
        value >= 0.8f -> Lime
        value >= 0.6f -> Orange
        else -> Pink
    }
    @Composable fun level(level: Int): Color = when (level) { 1 -> Lime; 2 -> Orange; else -> Pink }

    /**
     * Data colours: they tell measured series apart (body girths, charts) and never mark actions,
     * which stay lime. Paired per theme for contrast on white and on near-black.
     */
    val DataBlue: Color @Composable get() = if (p.light) Color(0xFF1668E3) else Color(0xFF3B8BFF)
    val DataPurple: Color @Composable get() = if (p.light) Color(0xFF8A2BE2) else Color(0xFFB068FF)
    val DataCyan: Color @Composable get() = if (p.light) Color(0xFF0E8A9E) else Color(0xFF22D3EE)
    /** Progress and setback in a measured value. */
    val Good: Color @Composable get() = if (p.light) Color(0xFF15803D) else Color(0xFF34D399)
    val Bad: Color @Composable get() = if (p.light) Color(0xFFDC2626) else Color(0xFFF87171)

    /** Content colour for a filled surface; the accent fill gets its paired colour. */
    @Composable fun on(fill: Color): Color = if (fill == Lime) OnAccent else foregroundFor(fill)
}

private fun oswald(weight: Int) = Font(R.font.oswald_variable, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
private fun onest(weight: Int) = Font(R.font.onest_variable, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Condensed display face for numbers, timers and capitalised headings. */
val Condensed = FontFamily(oswald(400), oswald(500), oswald(600), oswald(700))
/** Interface text. */
val Sans = FontFamily(onest(400), onest(500), onest(600), onest(700), onest(800))

/** Tabular figures keep weights and timers from jumping while they change. */
val NumberStyle = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 56.sp, lineHeight = 58.sp),
    displayMedium = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 46.sp),
    // Screen titles: heavy condensed headline, as on the Iron & Lime key frames.
    displaySmall = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 42.sp),
    headlineMedium = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 31.sp),
    headlineSmall = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp)
)

/** Iron & Lime shapes: 20dp cards, rounded fields; actions are pills (see components). */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(14.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Motion tokens of the animation pack (design_tokens.json). */
object FcMotion {
    const val SHORT_MS = 180
    const val MEDIUM_MS = 350
    const val LONG_MS = 850
    const val COUNTDOWN_STEP_MS = 900
}

@Composable
fun FitClubTheme(accent: Long = 0xFFC6FF3D, lightTheme: Boolean = false, content: @Composable () -> Unit) {
    val p = palette(lightTheme, Color(accent))
    val scheme = if (lightTheme) lightColorScheme(
        primary = p.accentInk, onPrimary = p.onAccent, primaryContainer = p.accentSoft, onPrimaryContainer = p.text,
        secondary = p.danger, onSecondary = Color.White,
        background = p.bg, onBackground = p.text, surface = p.surface, onSurface = p.text,
        surfaceVariant = p.surface2, onSurfaceVariant = p.muted, outline = p.line, outlineVariant = p.line2,
        error = p.danger, surfaceContainer = p.surface, surfaceContainerHigh = p.surface, surfaceContainerHighest = p.surface2
    ) else darkColorScheme(
        primary = p.accentInk, onPrimary = p.onAccent, primaryContainer = p.accentSoft, onPrimaryContainer = p.text,
        secondary = p.danger, onSecondary = p.bg,
        background = p.bg, onBackground = p.text, surface = p.surface, onSurface = p.text,
        surfaceVariant = p.surface2, onSurfaceVariant = p.muted, outline = p.line, outlineVariant = p.line2,
        error = p.danger, surfaceContainer = p.surface, surfaceContainerHigh = p.surface2, surfaceContainerHighest = p.surface2
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

/** Choose readable text over both bright program colours and dark accents. */
fun foregroundFor(color: Color): Color = if (color.luminance() > .179f) Color(0xFF17212B) else Color.White
