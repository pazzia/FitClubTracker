package com.fitclub.tracker.ui.profile

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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

/** Colours of the measuring tape; lime on dark, deep olive on light so the tape stays readable on white. */
private class TapeColors(val tape: Color, val prev: Color, val lead: Color, val empty: Color, val good: Color, val bad: Color, val glow: Color)

@Composable
private fun tapeColors(): TapeColors = if (Fc.isLight)
    TapeColors(Color(0xFF4A6200), Color(0xFFB5400C), Color(0xFFB9BFB5), Color(0xFFA3AAA2), Color(0xFF3D5A00), Color(0xFFB5400C), Color(0x2E7FA300))
else TapeColors(Color(0xFFC6FF3D), Color(0xFFFF7A45), Color(0xFF3A4148), Color(0xFF5F686F), Color(0xFFC6FF3D), Color(0xFFFF7A45), Color(0x2EC6FF3D))

private val MONTHS = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
fun shortDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).let { "${it.dayOfMonth} ${MONTHS[it.monthValue - 1]}" }

/** The user's own front-view drawing, decolourised, on a transparent background. */
@Composable
private fun rememberFigure(female: Boolean, light: Boolean): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val key = "${if (female) "female" else "male"}_${if (light) "light" else "dark"}"
    val picture by produceState<ImageBitmap?>(null, key) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("bodymodel/$key.webp").use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
        }
    }
    return picture
}

private fun metricValueText(metric: BodyMetric, value: Float?, units: WeightUnits): String = when {
    value == null -> "—"
    metric == BodyMetric.WEIGHT -> units.format(value)
    metric == BodyMetric.FAT -> "${formatMeasure(value)} %"
    else -> "${formatMeasure(value)} см"
}

private fun metricDisplay(metric: BodyMetric, value: Float, units: WeightUnits) = if (metric == BodyMetric.WEIGHT) units.fromKg(value) else value
private fun metricUnit(metric: BodyMetric, units: WeightUnits) = if (metric == BodyMetric.WEIGHT) units.label else metric.unit

/**
 * Body model of the profile: the user's figure with a measuring tape on every girth. The tape
 * grows and shrinks with the girth; the dashed line under it is the date chosen as «было».
 */
@Composable
fun BodyModelSection(sex: Sex, rows: List<BodyMeasurementEntity>, onMeasure: (BodyMetric?) -> Unit, modifier: Modifier = Modifier) {
    val units = LocalWeightUnits.current
    val female = sex == Sex.FEMALE
    val colors = tapeColors()
    val figure = BodyFigure.of(female)
    val picture = rememberFigure(female, Fc.isLight)
    val empty = rows.isEmpty()
    var nowIndex by rememberSaveable(rows.size) { mutableIntStateOf(rows.lastIndex) }
    var prevIndex by rememberSaveable(rows.size) { mutableIntStateOf(if (rows.size > 1) 0 else -1) }
    var editingPrev by rememberSaveable { mutableStateOf(false) }
    val now = nowIndex.takeIf { it in rows.indices }
    val prev = prevIndex.takeIf { it in rows.indices && it != nowIndex }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("Модель тела", style = MaterialTheme.typography.headlineSmall, color = Fc.Text, modifier = Modifier.weight(1f).semantics { heading() })
            Text(when {
                now == null -> "нет замеров"
                prev != null -> "${shortDate(rows[prev].epochDay)} → ${shortDate(rows[now].epochDay)}"
                else -> shortDate(rows[now].epochDay)
            }, color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
        }

        FigureStage(figure, picture, rows, now, prev, colors, units, onMeasure)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            LegendItem(if (now == null) colors.empty else colors.tape, dashed = now == null, thick = true,
                text = if (now == null) "здесь появятся ваши обхваты" else "стало, ${shortDate(rows[now].epochDay)}")
            if (prev != null) LegendItem(colors.prev, dashed = true, thick = false, text = "было, ${shortDate(rows[prev].epochDay)}")
        }

        if (rows.size > 1 && now != null) {
            DatePicker(rows, now, prevIndex, editingPrev, colors,
                onEdit = { editingPrev = it },
                onPick = { i -> if (editingPrev) prevIndex = i else nowIndex = i })
        }
        if (now != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TrendTile(BodyMetric.WEIGHT, rows, now, prev, colors, units, Modifier.weight(1f))
                TrendTile(BodyMetric.FAT, rows, now, prev, colors, units, Modifier.weight(1f))
            }
        } else {
            Text("Замеров пока нет. Первый станет точкой отсчёта: дальше лента покажет, что ушло и что прибавилось.",
                color = Fc.Muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        PrimaryButton(if (empty) "Записать первый замер" else "Записать замер", { onMeasure(null) }, modifier = Modifier.testTag("record-measurement"))
    }
}

@Composable
private fun LegendItem(color: Color, dashed: Boolean, thick: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(22.dp, 4.dp)) {
            drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), (if (thick) 3.dp else 2.dp).toPx(),
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx())) else null)
        }
        Text(text, color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FigureStage(
    figure: BodyFigure, picture: ImageBitmap?, rows: List<BodyMeasurementEntity>, now: Int?, prev: Int?,
    colors: TapeColors, units: WeightUnits, onMeasure: (BodyMetric?) -> Unit
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stageW = maxWidth
        val railW = 80.dp
        val figH = min(540.dp, (stageW - railW * 2) / figure.aspect)
        val figW = figH * figure.aspect
        val figLeft = (stageW - figW) / 2
        val scale = figH.value / figure.height
        val labelH = 60.dp * fontScale.coerceAtLeast(1f)
        val stageH = figH + 8.dp

        data class Placed(val band: FigureBand, val value: Float?, val before: Float?, val ref: Float?, val top: Dp)
        val placed = remember(rows, now, prev, figure, figH, labelH) {
            fun side(right: Boolean): List<Placed> {
                val bands = figure.bands.filter { it.rightSide == right }
                val tops = railTops(bands.map { it.y * scale - 26f }, labelH.value, stageH.value)
                return bands.mapIndexed { i, b ->
                    Placed(b, now?.let { valueAsOf(rows, it, b.metric) }, prev?.let { valueAsOf(rows, it, b.metric) },
                        referenceValue(rows, b.metric), tops[i].dp)
                }
            }
            side(false) + side(true)
        }

        Box(Modifier.fillMaxWidth().height(stageH)) {
            if (picture != null) Image(picture, contentDescription = null, contentScale = ContentScale.FillBounds,
                modifier = Modifier.offset(x = figLeft).size(figW, figH))
            Canvas(Modifier.matchParentSize()) {
                val unit = scale * density
                val left = figLeft.toPx()
                val rail = railW.toPx()
                for (p in placed) {
                    val b = p.band
                    val y = b.y * unit
                    val cx = left + b.cx * unit
                    val len = tapeLength(b, p.value, p.ref) * unit
                    val x0 = cx - len / 2; val x1 = cx + len / 2
                    val has = p.value != null
                    val c = if (has) colors.tape else colors.empty
                    if (p.before != null && has) {
                        val pl = tapeLength(b, p.before, p.ref) * unit
                        drawLine(colors.prev, Offset(cx - pl / 2, y + 4.dp.toPx()), Offset(cx + pl / 2, y + 4.dp.toPx()), 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx())))
                    }
                    if (has) {
                        var x = x0
                        while (x <= x1) { drawLine(c.copy(alpha = .8f), Offset(x, y - 6.dp.toPx()), Offset(x, y - 2.dp.toPx()), 1.dp.toPx()); x += 6.dp.toPx() }
                    }
                    drawLine(c, Offset(x0, y), Offset(x1, y), 3.dp.toPx(), cap = StrokeCap.Round,
                        pathEffect = if (has) null else PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                    drawLine(c, Offset(x0, y - 6.dp.toPx()), Offset(x0, y + 6.dp.toPx()), 2.dp.toPx())
                    drawLine(c, Offset(x1, y - 6.dp.toPx()), Offset(x1, y + 6.dp.toPx()), 2.dp.toPx())
                    val railX = if (b.rightSide) size.width - rail else rail
                    val end = if (b.rightSide) x1 + 4.dp.toPx() else x0 - 4.dp.toPx()
                    val labelY = p.top.toPx() + 26.dp.toPx()
                    drawLine(colors.lead, Offset(end, y), Offset(railX, labelY), 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 3.dp.toPx())))
                }
            }
            for (p in placed) {
                val metric = p.band.metric
                val change = metricChange(metric, p.value, p.before)
                val delta = when {
                    p.value == null -> "нет замера"
                    change == null -> ""
                    change.isZero -> "без изменений"
                    else -> "${formatSignedMeasure(change.delta)} см"
                }
                val deltaColor = when { change == null || change.isZero -> Fc.Muted; change.good == true -> colors.good; else -> colors.bad }
                val valueText = metricValueText(metric, p.value, units)
                Column(
                    Modifier
                        .testTag("girth-${metric.key}")
                        .align(if (p.band.rightSide) Alignment.TopEnd else Alignment.TopStart)
                        .offset(y = p.top)
                        .width(railW - 6.dp)
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button, onClickLabel = "Записать замер") { onMeasure(metric) }
                        .clearAndSetSemantics { contentDescription = "${metric.title}: $valueText${if (delta.isNotEmpty()) ", $delta" else ""}. Записать замер" },
                    horizontalAlignment = if (p.band.rightSide) Alignment.End else Alignment.Start
                ) {
                    Text(metric.title, color = Fc.Muted, fontSize = 13.sp, lineHeight = 15.sp)
                    Text(valueText, color = Fc.Text, style = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 25.sp))
                    if (delta.isNotEmpty()) Text(delta, color = deltaColor, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun DatePicker(rows: List<BodyMeasurementEntity>, now: Int, prevIndex: Int, editingPrev: Boolean, colors: TapeColors,
    onEdit: (Boolean) -> Unit, onPick: (Int) -> Unit) {
    val prev = prevIndex.takeIf { it in rows.indices && it != now }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Fc.Surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HandleButton("Стало", shortDate(rows[now].epochDay), !editingPrev, colors.tape, Modifier.weight(1f)) { onEdit(false) }
            HandleButton("Было", prev?.let { shortDate(rows[it].epochDay) } ?: "не выбрано", editingPrev, colors.prev, Modifier.weight(1f)) { onEdit(true) }
        }
        val first = rows.first().epochDay
        val span = (rows.last().epochDay - first).coerceAtLeast(1)
        BoxWithConstraints(Modifier.fillMaxWidth().height(62.dp)) {
            val track = maxWidth - 44.dp
            fun xOf(i: Int): Dp = 22.dp + track * ((rows[i].epochDay - first).toFloat() / span)
            val lo = minOf(now, prev ?: now); val hi = maxOf(now, prev ?: now)
            val line = Fc.Line
            Canvas(Modifier.fillMaxWidth().height(44.dp)) {
                val y = size.height / 2
                drawLine(line, Offset(22.dp.toPx(), y), Offset(size.width - 22.dp.toPx(), y), 2.dp.toPx())
                drawLine(colors.tape, Offset(xOf(lo).toPx(), y), Offset(xOf(hi).toPx(), y), 2.dp.toPx())
            }
            rows.forEachIndexed { i, row ->
                val isNow = i == now; val isPrev = i == prev
                val label = shortDate(row.epochDay) + when { isNow -> ", выбрано как «стало»"; isPrev -> ", выбрано как «было»"; else -> "" }
                Box(
                    Modifier.testTag("measure-date-$i").offset(x = xOf(i) - 22.dp).size(44.dp)
                        .selectable(selected = isNow || isPrev, role = Role.RadioButton) { onPick(i) }
                        .clearAndSetSemantics { contentDescription = label },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isNow -> Box(Modifier.size(18.dp).background(colors.tape, CircleShape))
                        isPrev -> Box(Modifier.size(16.dp).background(Fc.Surface, CircleShape).border(3.dp, colors.prev, CircleShape))
                        else -> Box(Modifier.size(10.dp).background(Fc.Muted, CircleShape))
                    }
                }
            }
            Text(shortDate(rows.first().epochDay), color = Fc.Muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomStart))
            Text(shortDate(rows.last().epochDay), color = Fc.Muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomEnd))
        }
        Text(if (editingPrev) "Нажмите точку на шкале, чтобы выбрать дату «было»." else "Нажмите точку на шкале, чтобы выбрать дату «стало».",
            color = Fc.Muted, fontSize = 12.sp)
    }
}

@Composable
private fun HandleButton(caption: String, date: String, on: Boolean, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.heightIn(min = 56.dp).clip(shape)
            .border(if (on) 2.dp else 1.dp, if (on) color else Fc.Line, shape)
            .selectable(selected = on, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(caption, color = Fc.Muted, fontSize = 12.sp)
        Text(date, color = Fc.Text, style = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 18.sp))
    }
}

@Composable
private fun TrendTile(metric: BodyMetric, rows: List<BodyMeasurementEntity>, now: Int, prev: Int?, colors: TapeColors, units: WeightUnits, modifier: Modifier) {
    val value = valueAsOf(rows, now, metric)
    val before = prev?.let { valueAsOf(rows, it, metric) }
    val points = rows.mapIndexedNotNull { i, r -> metric.read(r)?.let { i to metricDisplay(metric, it, units) } }
    val change = metricChange(metric, value?.let { metricDisplay(metric, it, units) }, before?.let { metricDisplay(metric, it, units) })
    val unit = metricUnit(metric, units)
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(Fc.Surface).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(metric.title, color = Fc.Muted, fontSize = 13.sp)
        Text(metricValueText(metric, value, units), color = Fc.Text, style = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 24.sp))
        if (points.size > 1) {
            val first = rows.first().epochDay
            val span = (rows.last().epochDay - first).coerceAtLeast(1).toFloat()
            val lo = points.minOf { it.second }; val hi = points.maxOf { it.second }
            Canvas(Modifier.fillMaxWidth().height(36.dp)) {
                fun pt(i: Int, v: Float) = Offset(4.dp.toPx() + (rows[i].epochDay - first) / span * (size.width - 8.dp.toPx()),
                    size.height - 4.dp.toPx() - (if (hi == lo) .5f else (v - lo) / (hi - lo)) * (size.height - 8.dp.toPx()))
                val path = Path()
                points.forEachIndexed { k, (i, v) -> val o = pt(i, v); if (k == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
                drawPath(path, colors.tape, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                points.lastOrNull { it.first <= now }?.let { (i, v) -> drawCircle(colors.tape, 3.5.dp.toPx(), pt(i, v)) }
            }
        } else Spacer(Modifier.height(36.dp))
        Text(when {
            value == null -> "нет данных"
            change == null -> if (prev == null) "выберите дату «было»" else "не с чем сравнить"
            change.isZero -> "без изменений"
            else -> "${formatSignedMeasure(change.delta)} $unit"
        }, color = when { change == null || change.isZero -> Fc.Muted; change.good == true -> colors.good; else -> colors.bad },
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                    values[BodyMetric.ARM], values[BodyMetric.HIPS], values[BodyMetric.THIGH])
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
private fun MiniFigure(female: Boolean, metric: BodyMetric, colors: TapeColors) {
    val figure = BodyFigure.of(female)
    val picture = rememberFigure(female, Fc.isLight)
    val figH = 300.dp
    val figW = figH * figure.aspect
    val band = figure.bands.firstOrNull { it.metric == metric }
    Box(Modifier.size(figW, figH).semantics { contentDescription = if (band != null) "Где мерить: ${metric.title.lowercase()}" else "Замер всего тела" }) {
        if (picture != null) Image(picture, null, contentScale = ContentScale.FillBounds, modifier = Modifier.matchParentSize().alpha(if (band != null) .9f else .55f))
        if (band != null) Canvas(Modifier.matchParentSize()) {
            val unit = size.height / figure.height
            val y = band.y * unit; val cx = band.cx * unit; val len = band.half * 2 * unit
            drawRoundRect(colors.glow, Offset(cx - len / 2 - 6.dp.toPx(), y - 9.dp.toPx()), Size(len + 12.dp.toPx(), 18.dp.toPx()), CornerRadius(9.dp.toPx()))
            drawLine(colors.tape, Offset(cx - len / 2, y), Offset(cx + len / 2, y), 3.dp.toPx(), cap = StrokeCap.Round)
            drawLine(colors.tape, Offset(cx - len / 2, y - 6.dp.toPx()), Offset(cx - len / 2, y + 6.dp.toPx()), 2.dp.toPx())
            drawLine(colors.tape, Offset(cx + len / 2, y - 6.dp.toPx()), Offset(cx + len / 2, y + 6.dp.toPx()), 2.dp.toPx())
        }
    }
}
