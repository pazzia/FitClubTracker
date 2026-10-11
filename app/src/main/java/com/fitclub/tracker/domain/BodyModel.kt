package com.fitclub.tracker.domain

import com.fitclub.tracker.data.db.BodyMeasurementEntity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** One step of the measurement flow; also the rows of the body model. */
enum class BodyMetric(
    val key: String,
    val title: String,
    val unit: String,
    val step: Float,
    /** For weight, waist, hips and fat a smaller number is progress. */
    val goodWhenDown: Boolean,
    val hint: String,
    val isGirth: Boolean,
    val read: (BodyMeasurementEntity) -> Float?
) {
    WEIGHT("weight", "Вес", "кг", 0.1f, true, "Утром, после туалета, до еды и воды. Те же весы и одежда.", false, { it.weightKg }),
    SHOULDERS("shoulders", "Плечи", "см", 0.5f, false, "Лента по самым широким точкам плеч, через дельты, руки свободно опущены.", true, { it.shouldersCm }),
    CHEST("chest", "Грудь", "см", 0.5f, false, "Лента по самой выступающей линии груди и под лопатками, на выдохе, руки опущены.", true, { it.chestCm }),
    WAIST("waist", "Талия", "см", 0.5f, true, "Самое узкое место над пупком, на спокойном выдохе, живот не втягивать.", true, { it.waistCm }),
    HIPS("hips", "Бёдра", "см", 0.5f, true, "Самая выступающая точка ягодиц, стопы вместе.", true, { it.hipsCm }),
    ARM("arm", "Бицепс", "см", 0.5f, false, "Середина плеча расслабленной руки — или в напряжении, но всегда одинаково.", true, { it.armCm }),
    THIGH("thigh", "Бедро", "см", 0.5f, false, "Чуть ниже ягодичной складки, вес на обе ноги.", true, { it.thighCm }),
    FAT("fat", "Жир", "%", 0.1f, true, "Значение с весов-анализатора или калипера. Можно пропустить.", false, { it.fatPercent });

    val minValue: Float get() = if (this == FAT) 0f else step
    val maxValue: Float get() = when (this) { FAT -> 100f; WEIGHT -> 400f; else -> 250f }

    companion object {
        fun byKey(key: String?): BodyMetric? = entries.firstOrNull { it.key == key }
        /** Starting values when there is no history yet; the user corrects them with the stepper. */
        fun typical(metric: BodyMetric, female: Boolean): Float = when (metric) {
            WEIGHT -> if (female) 62f else 80f
            SHOULDERS -> if (female) 92f else 115f
            CHEST -> if (female) 90f else 100f
            WAIST -> if (female) 70f else 85f
            HIPS -> if (female) 96f else 100f
            ARM -> if (female) 28f else 35f
            THIGH -> if (female) 55f else 58f
            FAT -> if (female) 25f else 18f
        }
    }
}

/** Colour family of a girth in the body measurements block. */
enum class BodyHue { BLUE, PURPLE, CYAN }

val BodyMetric.hue: BodyHue get() = when (this) {
    BodyMetric.SHOULDERS, BodyMetric.CHEST -> BodyHue.BLUE
    BodyMetric.ARM, BodyMetric.HIPS -> BodyHue.PURPLE
    else -> BodyHue.CYAN
}

/** Where a girth crosses the drawn figure, in source pixels of the drawing. */
data class FigureBand(val metric: BodyMetric, val y: Float, val cx: Float, val half: Float, val rightSide: Boolean)

/**
 * The four figures from the user's reference screens (male/female × light/dark), cleaned of the
 * annotations drawn over them. Bands sit where the reference drew its tapes.
 */
data class BodyFigure(val asset: String, val width: Float, val height: Float, val bands: List<FigureBand>) {
    val aspect: Float get() = width / height
    fun band(metric: BodyMetric) = bands.firstOrNull { it.metric == metric }

    companion object {
        private fun figure(asset: String, w: Float, h: Float, right: Set<BodyMetric>, vararg rows: Pair<BodyMetric, Triple<Float, Float, Float>>) =
            BodyFigure(asset, w, h, rows.map { (m, g) -> FigureBand(m, g.first, g.second, g.third, m in right) })

        private val MALE_RIGHT = setOf(BodyMetric.CHEST, BodyMetric.HIPS, BodyMetric.THIGH)
        // Cut whole, from the top of the hair to the soles, so the full figure always fits its card.
        val MALE_LIGHT = figure("male_light", 354f, 844f, MALE_RIGHT,
            BodyMetric.SHOULDERS to Triple(158f, 184f, 97f), BodyMetric.CHEST to Triple(248f, 179f, 80f), BodyMetric.ARM to Triple(251f, 73f, 18f),
            BodyMetric.WAIST to Triple(343f, 180f, 67f), BodyMetric.HIPS to Triple(448f, 180f, 90f), BodyMetric.THIGH to Triple(523f, 228f, 41f))
        val MALE_DARK = figure("male_dark", 351f, 805f, MALE_RIGHT,
            BodyMetric.SHOULDERS to Triple(159f, 178f, 100f), BodyMetric.CHEST to Triple(237f, 179f, 80f), BodyMetric.ARM to Triple(242f, 68f, 20f),
            BodyMetric.WAIST to Triple(335f, 181f, 67f), BodyMetric.HIPS to Triple(442f, 178f, 90f), BodyMetric.THIGH to Triple(515f, 234f, 44f))
        val FEMALE_DARK = figure("female_dark", 300f, 810f, setOf(BodyMetric.CHEST, BodyMetric.WAIST, BodyMetric.THIGH),
            BodyMetric.SHOULDERS to Triple(167f, 152f, 70f), BodyMetric.CHEST to Triple(237f, 155f, 55f), BodyMetric.ARM to Triple(254f, 68f, 16f),
            BodyMetric.WAIST to Triple(333f, 149f, 50f), BodyMetric.HIPS to Triple(427f, 155f, 78f), BodyMetric.THIGH to Triple(491f, 196f, 33f))
        val FEMALE_LIGHT = figure("female_light", 328f, 861f, setOf(BodyMetric.SHOULDERS, BodyMetric.ARM, BodyMetric.THIGH),
            BodyMetric.SHOULDERS to Triple(186f, 164f, 68f), BodyMetric.CHEST to Triple(266f, 170f, 63f), BodyMetric.ARM to Triple(284f, 253f, 18f),
            BodyMetric.WAIST to Triple(372f, 165f, 55f), BodyMetric.HIPS to Triple(464f, 165f, 85f), BodyMetric.THIGH to Triple(534f, 212f, 39f))

        fun of(female: Boolean, dark: Boolean) = when {
            female && dark -> FEMALE_DARK
            female -> FEMALE_LIGHT
            dark -> MALE_DARK
            else -> MALE_LIGHT
        }
        val ALL = listOf(MALE_LIGHT, MALE_DARK, FEMALE_LIGHT, FEMALE_DARK)
    }
}

/** Latest value of [metric] recorded on or before [epochDay]; rows are in date order. */
fun valueOnDay(rows: List<BodyMeasurementEntity>, epochDay: Long, metric: BodyMetric): Float? =
    rows.lastOrNull { it.epochDay <= epochDay && metric.read(it) != null }?.let(metric.read)

/** One point per week, the last one ending [today]: the body as it was at the end of that week. */
fun weeklySeries(rows: List<BodyMeasurementEntity>, metric: BodyMetric, today: Long, weeks: Int = 8): List<Float?> =
    (0 until weeks).map { i -> valueOnDay(rows, today - (weeks - 1 - i) * 7L, metric) }

data class PeriodChange(val delta: Float, val percent: Float, val good: Boolean?)

/** From the first known week to the last one of the period; null until two different weeks are known. */
fun periodChange(metric: BodyMetric, series: List<Float?>): PeriodChange? {
    val first = series.indexOfFirst { it != null }; val last = series.indexOfLast { it != null }
    if (first < 0 || last <= first) return null
    val a = series[first]!!; val b = series[last]!!
    val d = ((b - a) * 10).roundToInt() / 10f
    val pct = ((b - a) / a * 1000).roundToInt() / 10f
    return PeriodChange(d, pct, if (d == 0f) null else if (metric.goodWhenDown) d < 0 else d > 0)
}

/** The body as it was on the date of [index]: every metric takes its latest value recorded up to that day. */
fun valueAsOf(rows: List<BodyMeasurementEntity>, index: Int, metric: BodyMetric): Float? {
    if (index !in rows.indices) return null
    for (i in index downTo 0) metric.read(rows[i])?.let { return it }
    return null
}

data class MetricChange(val delta: Float, val good: Boolean?) {
    val isZero: Boolean get() = good == null
}

fun metricChange(metric: BodyMetric, now: Float?, before: Float?): MetricChange? {
    if (now == null || before == null) return null
    val d = ((now - before) * 10).roundToInt() / 10f
    if (d == 0f) return MetricChange(0f, null)
    return MetricChange(d, if (metric.goodWhenDown) d < 0 else d > 0)
}

/** Decimal with a comma and no trailing zero: 82 / 81,5. */
fun formatMeasure(v: Float): String {
    val r = (abs(v) * 10).roundToInt()
    val text = if (r % 10 == 0) "${r / 10}" else "${r / 10},${r % 10}"
    return if (v < 0 && r != 0) "-$text" else text
}

fun formatSignedMeasure(v: Float): String = (if (v > 0) "+" else if (v < 0) "−" else "") + formatMeasure(abs(v))

/**
 * Tops of the rail labels on one side. Each label wants to sit centred on its tape; labels that
 * would overlap are pushed down, and the column is pulled back up if it runs past [limit].
 */
fun railTops(wanted: List<Float>, labelHeight: Float, limit: Float): List<Float> {
    if (wanted.isEmpty()) return emptyList()
    val order = wanted.indices.sortedBy { wanted[it] }
    val tops = FloatArray(wanted.size)
    var floor = 0f
    for (i in order) { tops[i] = max(wanted[i], floor); floor = tops[i] + labelHeight }
    val overflow = floor - limit
    if (overflow > 0) {
        var ceiling = limit
        for (i in order.reversed()) { tops[i] = minOf(tops[i], ceiling - labelHeight).coerceAtLeast(0f); ceiling = tops[i] }
    }
    return tops.toList()
}

/** Snap a stepper value to its grid so 0.1 + 0.2 never shows as 0,30000001. */
fun snapMeasure(metric: BodyMetric, v: Float): Float =
    ((v / metric.step).roundToInt() * metric.step * 10).roundToInt().div(10f).coerceIn(metric.minValue, metric.maxValue)

/**
 * Height of the figure in its card: the whole figure always fits, up to [maxHeight]; a narrow
 * card scales it down so the callout columns of [labelWidth] on both sides keep their width.
 */
fun figureHeight(figure: BodyFigure, cardWidth: Float, labelWidth: Float = 86f, maxHeight: Float = 400f): Float =
    minOf(maxHeight, ((cardWidth - 2 * labelWidth) / figure.aspect).coerceAtLeast(0f))
