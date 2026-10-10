package com.fitclub.tracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitclub.tracker.data.repo.ProgramIcons
import com.fitclub.tracker.data.repo.ProgramPhotos
import com.fitclub.tracker.ui.theme.Condensed
import com.fitclub.tracker.ui.theme.Fc
import com.fitclub.tracker.ui.theme.NumberStyle
import com.fitclub.tracker.ui.theme.foregroundFor

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.displaySmall, color = Fc.Text, modifier = modifier)
}

/** Small capitalised label above a title or a section. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Fc.Muted) {
    Text(text.uppercase(), modifier = modifier, color = color,
        style = TextStyle(fontFamily = Condensed, fontSize = 13.sp, letterSpacing = 1.4.sp, lineHeight = 16.sp))
}

/** Weights, repetitions and timers: condensed face with tabular figures. */
@Composable
fun NumberText(text: String, size: TextUnit, modifier: Modifier = Modifier, color: Color = Fc.Text) {
    Text(text, modifier = modifier, color = color, style = NumberStyle.copy(fontSize = size, lineHeight = size * 1.1f))
}

@Composable
fun BackRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Fc.Text)
        Text(label, style = MaterialTheme.typography.labelLarge, color = Fc.Text, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
fun FcCard(
    modifier: Modifier = Modifier,
    border: Color = Fc.Line2,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .clip(shape)
            .background(Fc.Surface)
            .border(if (border == Fc.Lime) 1.5.dp else 1.dp, border, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(16.dp),
        content = content
    )
}

@Composable
fun Pill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.labelMedium, color = fg,
        modifier = modifier.clip(RoundedCornerShape(999.dp)).background(bg).padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

/** Selectable chip, at least 44dp tall. */
@Composable
fun FcChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: String? = null) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .then(if (selected) Modifier.background(Fc.Lime) else Modifier.border(1.dp, Fc.Line, shape))
            .semantics { this.selected = selected }
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val fg = if (selected) Fc.OnAccent else Fc.Text
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
        if (trailing != null) Text(trailing, style = NumberStyle.copy(fontSize = 15.sp), color = fg.copy(alpha = .7f))
    }
}

/** Segmented control across the full width without horizontal scrolling. */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    selectedBg: Color = Fc.Text,
    selectedFg: Color = Fc.on(selectedBg),
    leading: @Composable RowScope.(T, Boolean) -> Unit = { _, _ -> },
    trailing: @Composable RowScope.(T, Boolean) -> Unit = { _, _ -> }
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (options.size > 3) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
            .clip(RoundedCornerShape(16.dp))
            .background(Fc.Surface)
            .border(1.dp, Fc.Line2, RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { opt ->
            val on = opt == selected
            Row(
                Modifier
                    .then(if (options.size > 3) Modifier.width(96.dp) else Modifier.weight(1f))
                    .heightIn(min = height.coerceAtLeast(44.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) selectedBg else Color.Transparent)
                    .semantics { this.selected = on }
                    .clickable(role = Role.RadioButton) { onSelect(opt) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                leading(opt, on)
                Text(
                    label(opt), style = MaterialTheme.typography.labelLarge,
                    color = if (on) selectedFg else Fc.Muted, textAlign = TextAlign.Center
                )
                trailing(opt, on)
            }
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Fc.Lime, textColor: Color = Fc.on(color)) {
    Surface(
        onClick = onClick, color = color, shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = 60.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = textColor, textAlign = TextAlign.Center,
                style = TextStyle(fontFamily = Condensed, fontSize = 20.sp, letterSpacing = 0.5.sp))
        }
    }
}

@Composable
fun DashedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val line = Fc.Line
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawRoundRect(
                color = line, style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx())
            )
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = Fc.Text)
    }
}

/** Round icon button with a 44dp target. */
@Composable
fun CircleIconButton(description: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = false,
    content: @Composable () -> Unit) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .then(if (filled) Modifier.background(Fc.Surface2) else Modifier.border(1.dp, Fc.Line, CircleShape))
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

object IconPaths {
    val map = mapOf(
        ProgramIcons.DUMBBELL to "M6.5 6.5v11M3.5 9v6M17.5 6.5v11M20.5 9v6M6.5 12h11",
        ProgramIcons.BOLT to "M13 2 4 14h7l-1 8 9-12h-7z",
        ProgramIcons.FLAME to "M12 22a7 7 0 0 0 7-7c0-4-3-6-4-10-1 2-2 3-3 3 0-2-1-4-2-6-1 4-5 7-5 13a7 7 0 0 0 7 7z",
        ProgramIcons.HEART to "M12 21s-8-5-8-11a4.5 4.5 0 0 1 8-2.8A4.5 4.5 0 0 1 20 10c0 6-8 11-8 11z",
        ProgramIcons.MOUNTAIN to "M3 20l6-10 4 6 3-4 5 8z",
        ProgramIcons.TARGET to "M12 3a9 9 0 1 0 .01 0zM12 7a5 5 0 1 0 .01 0zM12 11a1 1 0 1 0 .01 0z"
    )
    val titles = mapOf(
        ProgramIcons.DUMBBELL to "Гантель", ProgramIcons.BOLT to "Молния", ProgramIcons.FLAME to "Пламя",
        ProgramIcons.HEART to "Сердце", ProgramIcons.MOUNTAIN to "Гора", ProgramIcons.TARGET to "Мишень"
    )
}

/** Line icons drawn from 24×24 path strings. */
object LineIcons {
    const val BOLT = "M13 2 4 14h7l-1 8 9-12h-7z"
    const val LIST = "M9 6h12M9 12h12M9 18h12M4 6h.01M4 12h.01M4 18h.01"
    const val BOOK = "M4 5.5A2.5 2.5 0 0 1 6.5 3H20v15H6.5A2.5 2.5 0 0 0 4 20.5zM4 20.5A2.5 2.5 0 0 0 6.5 23H20v-5M9 8h7M9 12h5"
    const val CHART = "M5 20V11M12 20V4M19 20v-6"
    const val CHECK = "M5 12.5l4.5 4.5L19 7.5"
    const val PLUS = "M12 5v14M5 12h14"
    const val MINUS = "M5 12h14"
    const val BACK = "M15 5l-7 7 7 7"
    const val NEXT = "M9 5l7 7-7 7"
    const val DOWN = "M6 15l6-6 6 6"
    const val SEARCH = "M11 4a7 7 0 1 0 .01 0zM20 20l-3.5-3.5"
    const val TIMER = "M12 5a8 8 0 1 0 .01 0zM12 9v4l2.5 2M9 2h6"
    const val PLAY = "M7 4.5v15l12.5-7.5z"
    const val TROPHY = "M8 21h8M12 17v4M7 4h10v5a5 5 0 0 1-10 0zM7 6H4a3 3 0 0 0 3 4M17 6h3a3 3 0 0 1-3 4"
    const val CALENDAR = "M6 5h12a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3zM3 10h18M8 3v4M16 3v4"
    const val FILTER = "M4 7h10M18 7h2M4 17h4M12 17h8M16 5a2 2 0 1 0 .01 0zM10 15a2 2 0 1 0 .01 0z"
    const val UP = "M12 19V5M6 11l6-6 6 6"
    const val CLOSE = "M6 6l12 12M18 6L6 18"
}

@Composable
fun LineIcon(path: String, tint: Color, modifier: Modifier = Modifier.size(22.dp), strokeWidth: Float = 2f) {
    val parsed = remember(path) { PathParser().parsePathString(path).toPath() }
    Canvas(modifier) {
        val k = size.minDimension / 24f
        scale(k, k, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(parsed, tint, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Иконка-обводка из 24×24 path-строки. */
@Composable
fun StrokeIcon(icon: String, tint: Color, modifier: Modifier = Modifier, strokeWidth: Float = 2.2f) {
    LineIcon(IconPaths.map[icon] ?: IconPaths.map.getValue(ProgramIcons.TARGET), tint, modifier, strokeWidth)
}

@Composable
fun ProgramCover(icon: String, color: Color, size: Dp, modifier: Modifier = Modifier, photo: String? = null) {
    val context = LocalContext.current
    val bitmap = remember(photo) { ProgramPhotos.thumbnail(context, photo) }
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(color)
            .semantics { contentDescription = IconPaths.titles[icon] ?: "" },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) Image(bitmap.asImageBitmap(), "Своя обложка программы", Modifier.size(size), contentScale = ContentScale.Crop)
        else StrokeIcon(icon, foregroundFor(color), Modifier.size(size * 0.48f))
    }
}

val DefaultBorder = BorderStroke(1.dp, Color(0xFF26262F))

/** Common primary/secondary action dimensions, including multiline labels. */
@Composable
fun ActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    outlined: Boolean = false, enabled: Boolean = true) {
    val shape = RoundedCornerShape(18.dp)
    val size = modifier.fillMaxWidth().heightIn(min = 56.dp)
    if (outlined) androidx.compose.material3.OutlinedButton(onClick = onClick, enabled = enabled,
        modifier = size, shape = shape, border = BorderStroke(1.dp, Fc.Line),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Fc.Text)) {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    } else androidx.compose.material3.Button(onClick = onClick, enabled = enabled, modifier = size, shape = shape,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Fc.Lime, contentColor = Fc.OnAccent,
            disabledContainerColor = Fc.Surface2, disabledContentColor = Fc.Muted)) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), textAlign = TextAlign.Center)
    }
}
