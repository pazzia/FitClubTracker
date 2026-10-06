package com.fitclub.tracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.Image
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.fitclub.tracker.data.repo.ProgramPhotos
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fitclub.tracker.data.repo.ProgramIcons
import com.fitclub.tracker.ui.theme.Fc
import com.fitclub.tracker.ui.theme.foregroundFor

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.displaySmall, color = Fc.Text, modifier = modifier)
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
    border: Color = Fc.Line,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .background(Fc.Surface)
            .border(1.dp, border, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(17.dp),
        content = content
    )
}

@Composable
fun Pill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.labelMedium, color = fg,
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

/** Сегментный переключатель на всю ширину — без горизонтальной прокрутки. */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    selectedBg: Color = Fc.Text,
    selectedFg: Color = foregroundFor(selectedBg),
    leading: @Composable RowScope.(T, Boolean) -> Unit = { _, _ -> },
    trailing: @Composable RowScope.(T, Boolean) -> Unit = { _, _ -> }
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Fc.Bg)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { opt ->
            val on = opt == selected
            Row(
                Modifier
                    .weight(1f)
                    .height(height)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) selectedBg else Color.Transparent)
                    .semantics { this.selected = on }
                    .clickable(role = Role.RadioButton) { onSelect(opt) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                leading(opt, on)
                Text(
                    label(opt), style = MaterialTheme.typography.labelLarge, maxLines = 1,
                    color = if (on) selectedFg else Fc.Muted, textAlign = TextAlign.Center
                )
                trailing(opt, on)
            }
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Fc.Lime, textColor: Color = foregroundFor(color)) {
    Surface(
        onClick = onClick, color = color, shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth().height(56.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.titleMedium, color = textColor)
        }
    }
}

@Composable
fun DashedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val line = Fc.Line
    Box(
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawRoundRect(
                color = line, style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())
            )
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = Fc.Text)
    }
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

/** Иконка-обводка из 24×24 path-строки. */
@Composable
fun StrokeIcon(icon: String, tint: Color, modifier: Modifier = Modifier, strokeWidth: Float = 2.2f) {
    val path = PathParser().parsePathString(IconPaths.map[icon] ?: IconPaths.map.getValue(ProgramIcons.TARGET)).toPath()
    Canvas(modifier) {
        val k = size.minDimension / 24f
        scale(k, k, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(path, tint, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
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
    val shape = RoundedCornerShape(16.dp)
    val size = modifier.fillMaxWidth().height(56.dp)
    if (outlined) androidx.compose.material3.OutlinedButton(onClick = onClick, enabled = enabled,
        modifier = size, shape = shape, border = BorderStroke(1.dp, Fc.Line),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Fc.Lime)) {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2)
    } else androidx.compose.material3.Button(onClick = onClick, enabled = enabled, modifier = size, shape = shape,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Fc.Lime, contentColor = foregroundFor(Fc.Lime))) {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2)
    }
}
