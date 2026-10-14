package com.fitclub.tracker.ui.profile

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitclub.tracker.AppContainer
import com.fitclub.tracker.data.db.BodyMeasurementEntity
import com.fitclub.tracker.data.model.Sex
import com.fitclub.tracker.domain.*
import com.fitclub.tracker.ui.components.PrimaryButton
import com.fitclub.tracker.ui.navigation.appViewModel
import com.fitclub.tracker.ui.theme.Condensed
import com.fitclub.tracker.ui.theme.Fc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Body measurements block, v2 (the user's reference screens): blue family palette inside this
 * block only, so the figure, tapes and charts read as one piece on both themes.
 */
private class BlockColors(
    val blue: Color, val purple: Color, val cyan: Color, val good: Color, val bad: Color,
    val surface: Color, val line: Color, val labelName: Color, val segBg: Color, val segOn: Color,
    val grid: Color, val tipLine: Color, val areaAlpha: Float, val addBorder: Color, val addText: Color
) {
    val tape: Color get() = blue
    val glow: Color get() = blue.copy(alpha = .18f)
    fun of(h: BodyHue) = when (h) { BodyHue.BLUE -> blue; BodyHue.PURPLE -> purple; BodyHue.CYAN -> cyan }
}

/** The block's colours come from the theme: data colours for girths, lime for actions. */
@Composable
private fun blockColors(): BlockColors = BlockColors(
    blue = Fc.DataBlue, purple = Fc.DataPurple, cyan = Fc.DataCyan, good = Fc.Good, bad = Fc.Bad,
    surface = Fc.Surface, line = Fc.Line, labelName = Fc.Text, segBg = Fc.Surface2, segOn = Fc.Lime,
    grid = Fc.Line2, tipLine = Fc.Line, areaAlpha = if (Fc.isLight) .12f else .22f, addBorder = Fc.Line, addText = Fc.OnAccent
)

@Composable
private fun tapeColors(): BlockColors = blockColors()

/** Background the figure was drawn on, so the picture blends into its card. */
private fun figureBackground(figure: BodyFigure): Color = when (figure.asset) {
    "female_dark" -> Color(0xFF0F1821)
    "male_dark" -> Color(0xFF0B121B)
    else -> Color(0xFFFDFDFE)
}

private val MONTHS = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
fun shortDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).let { "${it.dayOfMonth} ${MONTHS[it.monthValue - 1]}" }

/** One of the four figures from the user's reference screens, cleaned of the drawn annotations. */
@Composable
private fun rememberFigure(figure: BodyFigure): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val picture by produceState<ImageBitmap?>(null, figure.asset) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("bodymodel/${figure.asset}.webp").use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
        }
    }
    return picture
}

private fun metricDisplay(metric: BodyMetric, value: Float, units: WeightUnits) = if (metric == BodyMetric.WEIGHT) units.fromKg(value) else value
private fun metricUnit(metric: BodyMetric, units: WeightUnits) = if (metric == BodyMetric.WEIGHT) units.label else metric.unit

/** Girths in the order of the chart tabs. */
private val GIRTHS = listOf(BodyMetric.SHOULDERS, BodyMetric.CHEST, BodyMetric.ARM, BodyMetric.WAIST, BodyMetric.HIPS, BodyMetric.THIGH)
/** Chart tabs: weight first, then the girths top to bottom, body fat last. */
private val TABS = listOf(BodyMetric.WEIGHT) + GIRTHS + BodyMetric.FAT
/** Change cards lead with what people watch most. */
private val CARD_ORDER = listOf(BodyMetric.WEIGHT, BodyMetric.WAIST, BodyMetric.HIPS, BodyMetric.CHEST, BodyMetric.ARM, BodyMetric.THIGH, BodyMetric.SHOULDERS, BodyMetric.FAT)

private fun unitOf(metric: BodyMetric, units: WeightUnits) = when (metric) { BodyMetric.WEIGHT -> units.label; BodyMetric.FAT -> "%"; else -> "см" }
private const val WEEKS = 8

/**
 * «Замеры тела»: the user's figure with a dashed tape and a callout on every girth, change cards
 * for the last eight weeks and a weekly chart. Tapping a callout or a card shows that girth on
 * the chart; tapping a week shows the body as it was at the end of that week.
 */
@Composable
fun BodyModelSection(
    sex: Sex, rows: List<BodyMeasurementEntity>, onMeasure: (BodyMetric?) -> Unit,
    modifier: Modifier = Modifier, onSex: (Sex) -> Unit = {}, today: Long = LocalDate.now().toEpochDay()
) {
    val female = sex == Sex.FEMALE
    val colors = blockColors()
    val figure = BodyFigure.of(female, dark = !Fc.isLight)
    var metricKey by rememberSaveable { mutableStateOf(BodyMetric.WAIST.key) }
    val metric = BodyMetric.byKey(metricKey) ?: BodyMetric.WAIST
    var week by rememberSaveable { mutableIntStateOf(WEEKS - 1) }
    val units = LocalWeightUnits.current
    // Weight is shown in the profile's units; the rest are centimetres and percent.
    val series = remember(rows, today, units) {
        TABS.associateWith { m -> weeklySeries(rows, m, today, WEEKS).map { v -> v?.let { if (m == BodyMetric.WEIGHT) units.fromKg(it) else it } } }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Замеры тела", color = Fc.Text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Text("Вес, жир и обхваты за 8 недель", color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
            }
            Surface(
                onClick = { onMeasure(null) }, shape = RoundedCornerShape(20.dp), color = Fc.Lime,
                modifier = Modifier.heightIn(min = 44.dp).testTag("record-measurement")
            ) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text(if (rows.isEmpty()) "Первый замер" else "Добавить замер", color = Fc.OnAccent, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        FigureCard(figure, colors, series, week, metric, onPick = { metricKey = it.key })

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CARD_ORDER.forEach { m -> ChangeCard(m, series.getValue(m), m == metric, colors, unitOf(m, units)) { metricKey = m.key } }
        }

        ProgressCard(metric, series.getValue(metric), week, today, colors, unitOf(metric, units), onMetric = { metricKey = it.key }, onWeek = { week = it })
    }
}

@Composable
private fun FigureCard(figure: BodyFigure, colors: BlockColors, series: Map<BodyMetric, List<Float?>>, week: Int, selected: BodyMetric, onPick: (BodyMetric) -> Unit) {
    val picture = rememberFigure(figure)
    val bg = figureBackground(figure)
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(bg).border(1.dp, colors.line, RoundedCornerShape(22.dp))
    ) {
        val cardW = maxWidth
        val labelW = 80.dp
        val imgH = figureHeight(figure, cardW.value, labelWidth = labelW.value + 6f).dp
        val imgW = imgH * figure.aspect
        val imgLeft = (cardW - imgW) / 2
        val imgTop = 10.dp
        val cardH = imgH + 20.dp
        val scale = imgH.value / figure.height
        val gap = 46f * fontScale

        val tops = remember(figure, imgH, fontScale) {
            val result = HashMap<BodyMetric, Float>()
            for (right in listOf(false, true)) {
                val side = figure.bands.filter { it.rightSide == right }
                railTops(side.map { imgTop.value + it.y * scale - 30f }, gap, cardH.value - 6f).forEachIndexed { i, t -> result[side[i].metric] = t + 6f }
            }
            result
        }

        Box(Modifier.fillMaxWidth().height(cardH)) {
            if (picture != null) Image(picture, contentDescription = null, contentScale = ContentScale.FillBounds,
                modifier = Modifier.offset(x = imgLeft, y = imgTop).size(imgW, imgH))
            Canvas(Modifier.matchParentSize()) {
                val u = scale * density
                for (b in figure.bands) {
                    val color = colors.of(b.metric.hue)
                    val on = b.metric == selected
                    val y = imgTop.toPx() + b.y * u
                    val cx = imgLeft.toPx() + b.cx * u
                    val half = b.half * u
                    val px = if (b.rightSide) cx + half else cx - half
                    val top = tops.getValue(b.metric).dp.toPx()
                    val ax = if (b.rightSide) size.width - labelW.toPx() - 8.dp.toPx() else labelW.toPx() + 6.dp.toPx()
                    val ay = top + 30.dp.toPx()
                    val mx = (px + ax) / 2
                    val leader = Path().apply { moveTo(px, y); cubicTo(mx, y, mx, ay, ax, ay) }
                    if (on) drawLine(color.copy(alpha = .28f), Offset(cx - half, y), Offset(cx + half, y), 9.dp.toPx(), cap = StrokeCap.Round)
                    drawPath(leader, color, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
                    drawLine(color, Offset(cx - half, y), Offset(cx + half, y), (if (on) 3f else 2f).dp.toPx(), cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                    drawCircle(bg, 5.5.dp.toPx(), Offset(px, y))
                    drawCircle(color, 5.5.dp.toPx(), Offset(px, y), style = Stroke(2.6.dp.toPx()))
                }
            }
            for (b in figure.bands) {
                val color = colors.of(b.metric.hue)
                val value = series[b.metric]?.getOrNull(week)
                val valueText = value?.let { "${formatMeasure(it)} см" } ?: "—"
                Column(
                    Modifier
                        .testTag("girth-${b.metric.key}")
                        .align(if (b.rightSide) Alignment.TopEnd else Alignment.TopStart)
                        .offset(x = if (b.rightSide) (-8).dp else 8.dp, y = tops.getValue(b.metric).dp)
                        .width(labelW).heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button, onClickLabel = "Показать на графике") { onPick(b.metric) }
                        .clearAndSetSemantics { contentDescription = "${b.metric.title}: $valueText" + (if (b.metric == selected) ", выбрано" else "") }
                ) {
                    Text(b.metric.title, color = colors.labelName, fontSize = 12.sp, lineHeight = 14.sp)
                    Text(valueText, color = color, fontSize = 19.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Line icons of the zones; dashed parts mark where the tape goes. */
private fun zoneIcon(metric: BodyMetric): List<Pair<String, Boolean>> = when (metric) {
    BodyMetric.SHOULDERS -> listOf("M2.5 13c2-4.5 5.5-6.5 9.5-6.5s7.5 2 9.5 6.5" to false, "M12 6.5V10" to false, "M6 13.5h12" to true)
    BodyMetric.CHEST -> listOf("M3 9c3-1 4.5-4 9-4s6 3 9 4" to false, "M6.5 9v11M17.5 9v11" to false, "M6.5 12.5c2 2 4 2 5.5 0 1.5 2 3.5 2 5.5 0" to false)
    BodyMetric.ARM -> listOf("M4 20c0-6 3-12 8-12 3 0 4.5 2.5 3.5 5" to false, "M15.5 13c-1 2.5-4.5 2-6 5.5" to false, "M9 12.5h7" to true)
    BodyMetric.WAIST -> listOf("M7 3c0 5 2 6.5 2 9s-2 4-2 9" to false, "M17 3c0 5-2 6.5-2 9s2 4 2 9" to false, "M8.5 12h7" to true)
    BodyMetric.HIPS -> listOf("M6 3c-.5 5-.5 9 2.5 12.5L10 21" to false, "M18 3c.5 5 .5 9-2.5 12.5L14 21" to false, "M10.5 15.5c.5-2 2.5-2 3 0" to false, "M6.5 9h11" to true)
    BodyMetric.WEIGHT -> listOf("M5 20h14l-2-12H7z" to false, "M9.5 11.5a2.5 2.5 0 0 1 5 0" to false, "M12 11.5l1.2-1.6" to false)
    BodyMetric.FAT -> listOf("M12 3c3 4 6 7.2 6 11a6 6 0 0 1-12 0c0-3.8 3-7 6-11z" to false, "M9 15a3 3 0 0 0 3 3" to true)
    else -> listOf("M7 3v7c0 4 1.5 7.5 3 11" to false, "M17 3v7c0 4-1.5 7.5-3 11" to false, "M7.5 9h9" to true)
}

@Composable
private fun ZoneIcon(metric: BodyMetric, color: Color, modifier: Modifier) {
    val paths = remember(metric) { zoneIcon(metric).map { (d, dashed) -> PathParser().parsePathString(d).toPath() to dashed } }
    Canvas(modifier) {
        val k = size.minDimension / 24f
        scale(k, k, pivot = Offset.Zero) {
            for ((path, dashed) in paths) drawPath(path, color, style = Stroke(1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(2f, 2f)) else null))
        }
    }
}

@Composable
private fun ChangeCard(metric: BodyMetric, series: List<Float?>, selected: Boolean, colors: BlockColors, unit: String, onClick: () -> Unit) {
    val color = colors.of(metric.hue)
    val change = periodChange(metric, series)
    val pctColor = when (change?.good) { true -> colors.good; false -> colors.bad; null -> Fc.Muted }
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.testTag("change-${metric.key}").width(116.dp).clip(shape).background(colors.surface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) color else colors.line, shape)
            .clickable(role = Role.Button, onClickLabel = "Показать на графике", onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = metric.title + " за 8 недель: " +
                    (change?.let { "${formatSignedMeasure(it.delta)} $unit, ${formatSignedMeasure(it.percent)} %" } ?: "пока не с чем сравнить")
            }
            .padding(horizontal = 10.dp, vertical = 12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(color.copy(alpha = if (Fc.isLight) .08f else .14f)), contentAlignment = Alignment.Center) {
                ZoneIcon(metric, color, Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(metric.title, color = Fc.Muted, fontSize = 12.sp, maxLines = 1)
                Text(change?.let { "${formatSignedMeasure(it.delta)} $unit" } ?: "—", color = Fc.Text, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            }
        }
        Row(Modifier.padding(top = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(change?.let { (if (it.delta > 0) "▲ " else if (it.delta < 0) "▼ " else "") + formatSignedMeasure(it.percent) + " %" } ?: "",
                color = pctColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            val known = series.mapIndexedNotNull { i, v -> v?.let { i to it } }
            if (known.size > 1) Canvas(Modifier.weight(1f).height(22.dp)) {
                val lo = known.minOf { it.second }; val hi = known.maxOf { it.second }
                fun pt(i: Int, v: Float) = Offset(2f + i * (size.width - 4f) / (series.size - 1),
                    size.height - 3f - (if (hi == lo) .5f else (v - lo) / (hi - lo)) * (size.height - 6f))
                val line = Path(); val area = Path()
                known.forEachIndexed { n, (i, v) -> val o = pt(i, v); if (n == 0) { line.moveTo(o.x, o.y); area.moveTo(o.x, size.height); area.lineTo(o.x, o.y) } else { line.lineTo(o.x, o.y); area.lineTo(o.x, o.y) } }
                area.lineTo(pt(known.last().first, 0f).x, size.height); area.close()
                drawPath(area, color.copy(alpha = .14f))
                drawPath(line, color, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

@Composable
private fun ProgressCard(metric: BodyMetric, series: List<Float?>, week: Int, today: Long, colors: BlockColors, unit: String, onMetric: (BodyMetric) -> Unit, onWeek: (Int) -> Unit) {
    val color = colors.of(metric.hue)
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(colors.surface).border(1.dp, colors.line, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Прогресс за 8 недель", color = Fc.Text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.segBg).horizontalScroll(rememberScrollState()).padding(3.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            TABS.forEach { m ->
                val on = m == metric
                Box(
                    Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(17.dp)).background(if (on) colors.segOn else Color.Transparent)
                        .selectable(selected = on, role = Role.Tab) { onMetric(m) }.testTag("tab-${m.key}").padding(horizontal = 13.dp),
                    contentAlignment = Alignment.Center
                ) { Text(m.title, color = if (on) Fc.OnAccent else colors.labelName, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium) }
            }
        }

        val known = series.mapIndexedNotNull { i, v -> v?.let { i to it } }
        if (known.isEmpty()) {
            Text("За последние 8 недель замеров нет. Запишите замер — здесь появится динамика по неделям.", color = Fc.Muted, fontSize = 13.sp)
            return@Column
        }
        val lo0 = known.minOf { it.second }; val hi0 = known.maxOf { it.second }
        val lo = kotlin.math.floor(lo0).toInt() - 1
        val hi = (kotlin.math.ceil(hi0).toInt() + 1).let { if ((it - lo) % 2 != 0) it + 1 else it }
        val mid = (lo + hi) / 2
        val selected = week.takeIf { series.getOrNull(it) != null } ?: known.last().first
        BoxWithConstraints(Modifier.fillMaxWidth().height(176.dp)) {
            val w = maxWidth
            val left = 40.dp; val right = 14.dp; val top = 18.dp; val bottom = 148.dp
            fun x(i: Int): Dp = left + (w - left - right) * (i / (WEEKS - 1f))
            fun y(v: Float): Dp = bottom - (bottom - top) * ((v - lo) / (hi - lo).toFloat())
            val muted = Fc.Muted; val grid = colors.grid; val surface = colors.surface
            Canvas(Modifier.matchParentSize()) {
                for (g in listOf(hi, mid, lo)) drawLine(grid, Offset(left.toPx() - 6.dp.toPx(), y(g.toFloat()).toPx()), Offset(size.width, y(g.toFloat()).toPx()), 1.dp.toPx())
                val line = Path(); val area = Path()
                known.forEachIndexed { n, (i, v) ->
                    val o = Offset(x(i).toPx(), y(v).toPx())
                    if (n == 0) { line.moveTo(o.x, o.y); area.moveTo(o.x, bottom.toPx()); area.lineTo(o.x, o.y) } else { line.lineTo(o.x, o.y); area.lineTo(o.x, o.y) }
                }
                area.lineTo(x(known.last().first).toPx(), bottom.toPx()); area.close()
                drawPath(area, color.copy(alpha = colors.areaAlpha))
                drawPath(line, color, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                val sx = x(selected).toPx()
                drawLine(color, Offset(sx, y(series[selected]!!).toPx() + 8.dp.toPx()), Offset(sx, bottom.toPx()), 1.4.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
                for ((i, v) in known) {
                    val o = Offset(x(i).toPx(), y(v).toPx())
                    if (i == selected) { drawCircle(color, 9.dp.toPx(), o); drawCircle(surface, 6.dp.toPx(), o); drawCircle(color, 4.dp.toPx(), o) }
                    else { drawCircle(surface, 4.5.dp.toPx(), o); drawCircle(color, 4.5.dp.toPx(), o, style = Stroke(2.dp.toPx())) }
                }
            }
            for (g in listOf(hi, mid, lo)) Text("$g", color = muted, fontSize = 11.sp, modifier = Modifier.offset(y = y(g.toFloat()) - 8.dp))
            for (i in 0 until WEEKS) {
                val v = series[i]
                if (v != null) Box(
                    Modifier.testTag("week-$i").offset(x = x(i) - 20.dp, y = y(v) - 20.dp).size(40.dp)
                        .selectable(selected = i == selected, role = Role.RadioButton) { onWeek(i) }
                        .clearAndSetSemantics { contentDescription = "Неделя ${i + 1}, до ${shortDate(today - (WEEKS - 1 - i) * 7L)}: ${formatMeasure(v)} $unit" }
                )
                Text("Нед ${i + 1}", color = if (i == selected) Fc.Text else muted, fontSize = 11.sp, fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center, modifier = Modifier.offset(x = x(i) - 22.dp, y = 156.dp).width(44.dp))
            }
            val first = known.first()
            val value = series[selected]!!
            val change = if (selected > first.first) metricChange(metric, value, first.second) else null
            val tipW = 92.dp
            val tipX = (x(selected) - tipW / 2).coerceIn(30.dp, w - tipW)
            val tipY = (y(value) - 62.dp).coerceAtLeast(0.dp)
            Column(
                Modifier.offset(x = tipX, y = tipY).width(tipW).clip(RoundedCornerShape(12.dp)).background(colors.surface)
                    .border(1.dp, colors.tipLine, RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 6.dp)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }.testTag("chart-tip"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("${formatMeasure(value)} $unit", color = Fc.Text, style = MaterialTheme.typography.titleMedium)
                Text(when { change == null -> "старт"; change.isZero -> "без изменений"; else -> "${formatSignedMeasure(change.delta)} $unit с нед ${first.first + 1}" },
                    color = when (change?.good) { true -> colors.good; false -> colors.bad; null -> muted }, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        }
    }
}

/** «Тело» opened from the profile: the same block as in «Прогресс → Тело». */
class BodyViewModel(c: AppContainer) : ViewModel() {
    val settings = c.profile.settings
    val rows = c.fitness.observeMeasurements().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun BodyScreen(onBack: () -> Unit, onMeasure: (String?) -> Unit) {
    val vm = appViewModel { BodyViewModel(it) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()
    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize().background(Fc.Bg), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { com.fitclub.tracker.ui.components.BackRow("Профиль", onBack) }
        item { BodyModelSection(settings.sex, rows, onMeasure = { onMeasure(it?.key) }) }
    }
}

/* ---------------------------------------------------------------- Measurement flow */

class MeasureViewModel(private val c: AppContainer) : ViewModel() {
    val settings = c.profile.settings
    val measurements = c.fitness.observeMeasurements().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    /** [values] are canonical: weight in kilograms. */
    fun save(values: Map<BodyMetric, Float>, onSaved: () -> Unit) {
        if (saving) return
        saving = true
        viewModelScope.launch {
            runCatching {
                c.fitness.addMeasurement(values[BodyMetric.WEIGHT], values[BodyMetric.WAIST], values[BodyMetric.FAT], values[BodyMetric.CHEST],
                    values[BodyMetric.ARM], values[BodyMetric.HIPS], values[BodyMetric.THIGH], shoulders = values[BodyMetric.SHOULDERS])
            }.onSuccess { error = null; onSaved() }.onFailure { error = "Не удалось сохранить замер: ${it.message}" }
            saving = false
        }
    }
}

private fun encode(map: Map<BodyMetric, Float>) = map.entries.joinToString(";") { "${it.key.key}=${it.value}" }
private fun decode(s: String): Map<BodyMetric, Float> = s.split(';').mapNotNull { part ->
    val (k, v) = part.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
    BodyMetric.byKey(k)?.let { m -> v.toFloatOrNull()?.let { m to it } }
}.toMap()
private fun encodeSet(set: Set<BodyMetric>) = set.joinToString(";") { it.key }
private fun decodeSet(s: String) = s.split(';').mapNotNull { BodyMetric.byKey(it) }.toSet()

/**
 * Seven short steps, one girth at a time: where to put the tape, the previous value already in
 * the field, ± to correct it. Opening it from a girth label starts at that girth.
 */
@Composable
fun MeasureScreen(startKey: String?, onClose: () -> Unit) {
    val vm = appViewModel { MeasureViewModel(it) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val loaded by vm.measurements.collectAsStateWithLifecycle()
    val rows = loaded ?: return
    val units = LocalWeightUnits.current
    val female = settings.sex == Sex.FEMALE
    val metrics = BodyMetric.entries
    val start = BodyMetric.byKey(startKey)?.ordinal ?: 0
    var step by rememberSaveable { mutableIntStateOf(start) }
    var enteredRaw by rememberSaveable { mutableStateOf("") }
    var skippedRaw by rememberSaveable { mutableStateOf(encodeSet(metrics.take(start).toSet())) }
    var done by rememberSaveable { mutableStateOf(false) }
    val entered = decode(enteredRaw)
    val skipped = decodeSet(skippedRaw)
    val lastRow = rows.lastIndex
    fun last(m: BodyMetric): Float? = valueAsOf(rows, lastRow, m)?.let { metricDisplay(m, it, units) }
    fun initial(m: BodyMetric): Float = entered[m] ?: last(m) ?: metricDisplay(m, BodyMetric.typical(m, female), units)
    val lastDate = rows.lastOrNull()?.let { shortDate(it.epochDay) }
    val colors = tapeColors()

    Column(
        Modifier.fillMaxSize().background(Fc.Bg).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text(if (done) "Закрыть" else "Отмена", color = Fc.Text, fontSize = 15.sp) }
            Text("Новый замер, ${shortDate(LocalDate.now().toEpochDay())}", color = Fc.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Text(if (done) "готово" else "${step + 1} из ${metrics.size}", color = Fc.Muted, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 56.dp))
        }
        Row(
            Modifier.fillMaxWidth().semantics { contentDescription = "Шаг ${step + 1} из ${metrics.size}"; progressBarRangeInfo = ProgressBarRangeInfo((step + 1).toFloat(), 1f..metrics.size.toFloat()) },
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            metrics.forEachIndexed { k, m ->
                val c = when {
                    done || k < step -> if (m in skipped) Fc.Muted.copy(alpha = .55f) else colors.tape.copy(alpha = .55f)
                    k == step -> colors.tape
                    else -> Fc.Line
                }
                Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c))
            }
        }

        if (!done) {
            val metric = metrics[step]
            val unit = metricUnit(metric, units)
            var draft by rememberSaveable(step) { mutableStateOf(formatMeasure(initial(metric))) }
            val parsed = draft.replace(',', '.').toFloatOrNull()
            val canonical = parsed?.let { if (metric == BodyMetric.WEIGHT) units.toKg(it) else it }
            val valid = canonical != null && canonical.isFinite() && canonical >= metric.minValue && canonical <= metric.maxValue
            val previous = last(metric)
            fun commit(): Float? = parsed?.takeIf { valid }
            fun goNext(skip: Boolean) {
                val map = entered.toMutableMap(); val sk = skipped.toMutableSet()
                if (skip) { map.remove(metric); sk += metric } else { map[metric] = commit() ?: return; sk -= metric }
                enteredRaw = encode(map); skippedRaw = encodeSet(sk)
                if (step < metrics.lastIndex) step++ else {
                    val toSave = metrics.filter { it !in sk && map[it] != null }.associateWith { m ->
                        val v = map.getValue(m); if (m == BodyMetric.WEIGHT) units.toKg(v) else v
                    }
                    if (toSave.isEmpty()) vm.error = "Запишите хотя бы одно значение — все шаги пропущены."
                    else vm.save(toSave) { done = true }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                MiniFigure(female, metric, colors)
                Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(metric.title, color = Fc.Text, style = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 36.sp),
                        modifier = Modifier.semantics { heading() })
                    Text(metric.hint, color = Fc.Muted, style = MaterialTheme.typography.bodyMedium)
                    Text(if (previous != null) "В прошлый раз: ${formatMeasure(previous)} $unit, $lastDate" else "Это первый замер. Подставлено среднее значение — поправьте под себя.",
                        color = Fc.Muted, fontSize = 13.sp)
                }
            }

            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Fc.Surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StepButton("−", "Уменьшить на ${formatMeasure(metric.step)} $unit") {
                        draft = formatMeasure(snapMeasure(metric, (parsed ?: initial(metric)) - metric.step))
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                        BasicTextField(
                            draft, { draft = normalizeDecimalInput(it) }, singleLine = true,
                            textStyle = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 56.sp, color = if (valid) Fc.Text else Fc.Danger, textAlign = TextAlign.Center),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            cursorBrush = SolidColor(colors.tape),
                            modifier = Modifier.widthIn(min = 64.dp, max = 160.dp).testTag("measure-value")
                                .semantics { contentDescription = "${metric.title}, $unit"; liveRegion = LiveRegionMode.Polite }
                        )
                        Text(unit, color = Fc.Muted, fontSize = 18.sp, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
                    }
                    StepButton("+", "Увеличить на ${formatMeasure(metric.step)} $unit") {
                        draft = formatMeasure(snapMeasure(metric, (parsed ?: initial(metric)) + metric.step))
                    }
                }
                val change = if (valid && previous != null) metricChange(metric, parsed, previous) else null
                Text(when {
                    !valid -> "Введите число от ${formatMeasure(metricDisplay(metric, metric.minValue, units))} до ${formatMeasure(metricDisplay(metric, metric.maxValue, units))}"
                    change == null -> " "
                    change.isZero -> "как в прошлый раз"
                    else -> "${formatSignedMeasure(change.delta)} $unit к $lastDate"
                }, color = when { !valid -> Fc.Danger; change == null || change.isZero -> Fc.Muted; change.good == true -> colors.good; else -> colors.bad },
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Text(if (previous != null) "Шаг ±${formatMeasure(metric.step)} $unit. Подставлено прошлое значение, поправьте его." else "Шаг ±${formatMeasure(metric.step)} $unit.",
                    color = Fc.Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
            }

            vm.error?.let { Text(it, color = Fc.Danger) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(onClick = { vm.error = null; goNext(skip = true) }, shape = RoundedCornerShape(18.dp), color = Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Fc.Line), modifier = Modifier.weight(1f).heightIn(min = 60.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text("Пропустить", color = Fc.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                }
                PrimaryButton(
                    when { vm.saving -> "Сохраняю…"; step == metrics.lastIndex -> "Сохранить замер"; else -> "Дальше" },
                    { if (valid && !vm.saving) { vm.error = null; goNext(skip = false) } },
                    modifier = Modifier.weight(2f).alpha(if (valid) 1f else .5f).testTag("measure-next")
                )
            }
            if (step > 0) TextButton(onClick = { step-- }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Назад к «${metrics[step - 1].title}»", color = Fc.Muted)
            }
        } else {
            Text("Замер записан", color = Fc.Text, style = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 34.sp),
                modifier = Modifier.padding(top = 12.dp).semantics { heading() })
            Text("Лента на модели уже показывает новые обхваты. Следующий замер удобнее делать через 2 недели в то же время суток.",
                color = Fc.Muted, style = MaterialTheme.typography.bodyMedium)
            // Summary compares with the measurement before today's, so a correction pass still shows progress.
            val before = rows.indexOfLast { it.epochDay < LocalDate.now().toEpochDay() }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Fc.Surface).padding(horizontal = 16.dp, vertical = 4.dp)) {
                metrics.forEachIndexed { k, m ->
                    val v = entered[m]?.takeIf { m !in skipped }
                    val was = if (before >= 0) valueAsOf(rows, before, m)?.let { metricDisplay(m, it, units) } else null
                    val change = metricChange(m, v, was)
                    if (k > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Fc.Line))
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(m.title, color = Fc.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(if (v == null) "пропущено" else "${formatMeasure(v)} ${metricUnit(m, units)}", color = if (v == null) Fc.Muted else Fc.Text,
                                style = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 20.sp))
                            if (change != null && !change.isZero) Text("${formatSignedMeasure(change.delta)} ${metricUnit(m, units)}",
                                color = if (change.good == true) colors.good else colors.bad, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            PrimaryButton("Открыть модель тела", onClose, modifier = Modifier.testTag("measure-done"))
            TextButton(onClick = { step = 0; done = false }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Исправить значения", color = Fc.Muted) }
        }
    }
}

@Composable
private fun StepButton(symbol: String, label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = Fc.Bg, border = androidx.compose.foundation.BorderStroke(1.dp, Fc.Line),
        modifier = Modifier.size(64.dp).semantics { contentDescription = label }) {
        Box(contentAlignment = Alignment.Center) { Text(symbol, color = Fc.Text, fontSize = 30.sp) }
    }
}

/** Small copy of the figure with the girth being measured lit up. */
@Composable
private fun MiniFigure(female: Boolean, metric: BodyMetric, colors: BlockColors) {
    val figure = BodyFigure.of(female, dark = !Fc.isLight)
    val picture = rememberFigure(figure)
    val figH = 300.dp
    val figW = figH * figure.aspect
    val band = figure.band(metric)
    val color = band?.let { colors.of(it.metric.hue) } ?: colors.blue
    Box(
        Modifier.size(figW + 12.dp, figH + 12.dp).clip(RoundedCornerShape(18.dp)).background(figureBackground(figure))
            .semantics { contentDescription = if (band != null) "Где мерить: ${metric.title.lowercase()}" else "Замер всего тела" },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(figW, figH)) {
            if (picture != null) Image(picture, null, contentScale = ContentScale.FillBounds, modifier = Modifier.matchParentSize().alpha(if (band != null) 1f else .55f))
            if (band != null) Canvas(Modifier.matchParentSize()) {
                val unit = size.height / figure.height
                val y = band.y * unit; val cx = band.cx * unit; val len = band.half * 2 * unit
                drawRoundRect(color.copy(alpha = .2f), Offset(cx - len / 2 - 6.dp.toPx(), y - 9.dp.toPx()), Size(len + 12.dp.toPx(), 18.dp.toPx()), CornerRadius(9.dp.toPx()))
                drawLine(color, Offset(cx - len / 2, y), Offset(cx + len / 2, y), 3.dp.toPx(), cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                val px = if (band.rightSide) cx + len / 2 else cx - len / 2
                drawCircle(figureBackground(figure), 5.dp.toPx(), Offset(px, y))
                drawCircle(color, 5.dp.toPx(), Offset(px, y), style = Stroke(2.4.dp.toPx()))
            }
        }
    }
}
