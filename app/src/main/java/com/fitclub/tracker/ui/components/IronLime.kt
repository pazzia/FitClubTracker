package com.fitclub.tracker.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitclub.tracker.ui.theme.Fc
import com.fitclub.tracker.ui.theme.Sans

/**
 * Iron & Lime building blocks from the animation design pack: soft lime glow behind primary
 * actions, pill buttons, glowing progress, stat rows with dividers and section headers.
 */

/** Pill radius for actions and chips. */
val PillShape = RoundedCornerShape(percent = 50)

/** Card radius of the pack (cardRadiusDp = 20). */
val CardShape = RoundedCornerShape(20.dp)

/**
 * Soft glow drawn behind the element: concentric rounded outlines that fade outwards (no blur
 * filter, so it renders the same on every device and in screenshots). Dark theme only: on a light
 * background a glow reads as a smudge.
 */
fun Modifier.glow(color: Color, enabled: Boolean, radius: Dp = 18.dp, alpha: Float = .30f, corner: Dp = 999.dp, spread: Dp = 0.dp): Modifier =
    if (!enabled || alpha <= 0f) this else drawBehind {
        val steps = 10
        val reach = radius.toPx()
        val base = spread.toPx()
        val r0 = corner.toPx().coerceAtMost(size.minDimension / 2)
        for (i in steps downTo 1) {
            val k = i / steps.toFloat()
            val grow = base + reach * k
            // Each ring adds a little light; together they fall off roughly like a gaussian.
            val a = alpha * (1f - k) * (1f - k) * 2.2f / steps + alpha * .02f
            drawRoundRect(
                color.copy(alpha = a.coerceIn(0f, 1f)),
                topLeft = Offset(-grow, -grow),
                size = androidx.compose.ui.geometry.Size(size.width + grow * 2, size.height + grow * 2),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r0 + grow)
            )
        }
    }

/** Ambient lime light at the top of a screen (dark theme only). */
fun Modifier.ambientGlow(enabled: Boolean, color: Color, centerX: Float = .78f, centerY: Float = .0f, alpha: Float = .10f): Modifier =
    if (!enabled) this else drawBehind {
        val center = Offset(size.width * centerX, size.height * centerY)
        val radius = size.width * 1.05f
        drawRect(Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center, radius))
    }

/** System «Remove animations» / animator scale 0: motion turns into plain fades. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

enum class PillKind { PRIMARY, SECONDARY, GHOST }

/**
 * Pill action of the new design. PRIMARY: lime with glow; SECONDARY: surface with a hairline;
 * GHOST: text only. At least 52dp tall; label can wrap.
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: PillKind = PillKind.PRIMARY,
    enabled: Boolean = true,
    icon: String? = null,
    height: Dp = 56.dp,
    filledIcon: Boolean = false
) {
    val dark = !Fc.isLight
    val bg = when {
        !enabled && kind == PillKind.PRIMARY -> Fc.Surface2
        kind == PillKind.PRIMARY -> Fc.Lime
        kind == PillKind.SECONDARY -> Fc.Surface
        else -> Color.Transparent
    }
    val fg = when {
        !enabled -> Fc.Muted
        kind == PillKind.PRIMARY -> Fc.OnAccent
        kind == PillKind.GHOST -> Fc.Muted
        else -> Fc.Text
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .glow(Fc.Lime, dark && enabled && kind == PillKind.PRIMARY, radius = 16.dp, alpha = .32f)
            .clip(PillShape)
            .background(bg)
            .then(if (kind == PillKind.SECONDARY) Modifier.border(1.dp, Fc.Line, PillShape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            if (filledIcon) FilledIcon(icon, fg, Modifier.size(18.dp)) else LineIcon(icon, fg, Modifier.size(20.dp), strokeWidth = 2.2f)
            Box(Modifier.width(10.dp))
        }
        Text(text, color = fg, textAlign = TextAlign.Center,
            style = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp))
    }
}

/** A filled glyph from a 24×24 path (play, flame). */
@Composable
fun FilledIcon(path: String, tint: Color, modifier: Modifier = Modifier.size(20.dp)) {
    val parsed = remember(path) { androidx.compose.ui.graphics.vector.PathParser().parsePathString(path).toPath() }
    androidx.compose.foundation.Canvas(modifier) {
        val k = size.minDimension / 24f
        scale(k, k, pivot = Offset.Zero) { drawPath(parsed, tint) }
    }
}

/** Thin progress line with a lime fill that glows in the dark theme. */
@Composable
fun GlowProgress(fraction: Float, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val f = fraction.coerceIn(0f, 1f)
    Box(modifier.height(height).clip(PillShape).background(Fc.Line)) {
        if (f > 0f) Box(
            Modifier.fillMaxWidth(f).fillMaxHeight()
                .glow(Fc.Lime, !Fc.isLight, radius = 6.dp, alpha = .55f)
                .clip(PillShape).background(Fc.Lime)
        )
    }
}

/** Section header: condensed title with an optional text link on the right. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp, lineHeight = 28.sp), color = Fc.Text, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction) {
            Text(action, color = Fc.Lime, style = MaterialTheme.typography.labelLarge)
            LineIcon(LineIcons.NEXT, Fc.Lime, Modifier.padding(start = 2.dp).size(16.dp))
        }
    }
}

/** Key numbers in one card separated by hairlines: icon, value, label (time · exercises · tonnage). */
@Composable
fun StatRow(items: List<StatItem>, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(CardShape).background(Fc.Surface).border(1.dp, Fc.Line, CardShape).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { i, item ->
            if (i > 0) Box(Modifier.width(1.dp).height(48.dp).background(Fc.Line))
            Column(Modifier.weight(1f).padding(horizontal = 6.dp).semantics(mergeDescendants = true) {},
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (item.icon != null) LineIcon(item.icon, Fc.Lime, Modifier.size(20.dp))
                NumberText(item.value, 24.sp, color = Fc.Text)
                Text(item.label, color = Fc.Muted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

data class StatItem(val value: String, val label: String, val icon: String? = null)

/** Round icon button of the new design: dark filled circle with a hairline. */
@Composable
fun RoundIconButton(description: String, icon: String, onClick: () -> Unit, modifier: Modifier = Modifier, dot: Boolean = false, size: Dp = 44.dp) {
    Box(
        modifier.size(size).clip(CircleShape).background(Fc.Surface).border(1.dp, Fc.Line, CircleShape)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        LineIcon(icon, Fc.Text, Modifier.size(20.dp), strokeWidth = 1.9f)
        if (dot) Box(Modifier.align(Alignment.TopEnd).padding(top = 9.dp, end = 10.dp).size(8.dp).clip(CircleShape).background(Fc.Lime))
    }
}

/** Extra glyphs of the pack (24×24 stroke paths). */
object PackIcons {
    const val HOME = "M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z"
    const val DUMBBELL = "M6.5 6.5v11M17.5 6.5v11M3 9v6M21 9v6M6.5 12h11"
    const val BOOK_OPEN = "M12 6.5C10 5 7 4.5 3 5v14c4-.5 7 0 9 1.5 2-1.5 5-2 9-1.5V5c-4-.5-7 0-9 1.5zM12 6.5V20"
    const val BARS = "M5 20v-6M10 20V9M15 20v-9M20 20V4"
    const val USER = "M12 4a4 4 0 1 0 .01 0zM4 21c1.5-4 4.5-6 8-6s6.5 2 8 6"
    const val GEAR = "M12 9a3 3 0 1 0 .01 0zM19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"
    const val FLAME = "M12 22a7 7 0 0 0 7-7c0-4-3-6-4-10-1 2-2 3-3 3 0-2-1-4-2-6-1 4-5 7-5 13a7 7 0 0 0 7 7z"
    const val FLAME_FILL = "M12 2c1 3.5 5 6 5 11a5 5 0 0 1-10 0c0-2.2 1-3.8 2.2-5 .2 1.6.9 2.6 1.8 3.2C11 8.5 11 5 12 2z"
    const val PLAY_FILL = "M7 4.5v15a1 1 0 0 0 1.5.86l12.5-7.5a1 1 0 0 0 0-1.72L8.5 3.64A1 1 0 0 0 7 4.5z"
    const val STOPWATCH = "M12 7a7 7 0 1 0 .01 0zM12 10v4M9.5 2.5h5M12 2.5V5"
    const val PLATES = "M7 6v12M4 8.5v7M17 6v12M20 8.5v7M7 12h10"
    const val SHARE = "M12 3v12M7 8l5-5 5 5M5 13v6a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-6"
    const val SOUND_ON = "M11 5 6 9H3v6h3l5 4zM15.5 8.5a5 5 0 0 1 0 7M18.5 5.5a9 9 0 0 1 0 13"
    const val SOUND_OFF = "M11 5 6 9H3v6h3l5 4zM16 9l5 6M21 9l-5 6"
    const val CALENDAR = "M6 5h12a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3zM3 10h18M8 3v4M16 3v4"
}
