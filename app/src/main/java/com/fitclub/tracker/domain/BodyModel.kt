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
    CHEST("chest", "Грудь", "см", 0.5f, false, "Лента по самой выступающей линии груди и под лопатками, на выдохе, руки опущены.", true, { it.chestCm }),
    WAIST("waist", "Талия", "см", 0.5f, true, "Самое узкое место над пупком, на спокойном выдохе, живот не втягивать.", true, { it.waistCm }),
    HIPS("hips", "Бёдра", "см", 0.5f, true, "Самая выступающая точка ягодиц, стопы вместе.", true, { it.hipsCm }),
    ARM("arm", "Рука", "см", 0.5f, false, "Середина плеча расслабленной руки — или в напряжении, но всегда одинаково.", true, { it.armCm }),
    THIGH("thigh", "Бедро", "см", 0.5f, false, "Чуть ниже ягодичной складки, вес на обе ноги.", true, { it.thighCm }),
    FAT("fat", "Жир", "%", 0.1f, true, "Значение с весов-анализатора или калипера. Можно пропустить.", false, { it.fatPercent });

    val minValue: Float get() = if (this == FAT) 0f else step
    val maxValue: Float get() = when (this) { FAT -> 100f; WEIGHT -> 400f; else -> 250f }

    companion object {
        fun byKey(key: String?): BodyMetric? = entries.firstOrNull { it.key == key }
        /** Starting values when there is no history yet; the user corrects them with the stepper. */
        fun typical(metric: BodyMetric, female: Boolean): Float = when (metric) {
            WEIGHT -> if (female) 62f else 80f
            CHEST -> if (female) 90f else 100f
            WAIST -> if (female) 70f else 85f
            HIPS -> if (female) 96f else 100f
            ARM -> if (female) 28f else 35f
            THIGH -> if (female) 55f else 58f
            FAT -> if (female) 25f else 18f
        }
    }
}

/** Where a girth crosses the drawn figure, in source pixels of the front-view drawing. */
data class FigureBand(val metric: BodyMetric, val y: Float, val cx: Float, val half: Float, val rightSide: Boolean)

data class BodyFigure(val width: Float, val height: Float, val bands: List<FigureBand>) {
    val aspect: Float get() = width / height

    companion object {
        // Levels and widths measured on the user's own front-view drawings (male 298×837, female 270×837).
        val MALE = BodyFigure(298f, 837f, listOf(
            FigureBand(BodyMetric.CHEST, 225f, 149f, 79f, true),
            FigureBand(BodyMetric.WAIST, 310f, 148f, 64f, false),
            FigureBand(BodyMetric.ARM, 300f, 266f, 24f, true),
            FigureBand(BodyMetric.HIPS, 405f, 148.5f, 82f, false),
            FigureBand(BodyMetric.THIGH, 490f, 201f, 39f, true)
        ))
        val FEMALE = BodyFigure(270f, 837f, listOf(
            FigureBand(BodyMetric.CHEST, 232f, 136f, 64f, true),
            FigureBand(BodyMetric.WAIST, 305f, 136.5f, 50f, false),
            FigureBand(BodyMetric.ARM, 322f, 234f, 20f, true),
            FigureBand(BodyMetric.HIPS, 420f, 136f, 80f, false),
            FigureBand(BodyMetric.THIGH, 492f, 180f, 35f, true)
        ))
        fun of(female: Boolean) = if (female) FEMALE else MALE
    }
}

/** The body as it was on the date of [index]: every metric takes its latest value recorded up to that day. */
fun valueAsOf(rows: List<BodyMeasurementEntity>, index: Int, metric: BodyMetric): Float? {
    if (index !in rows.indices) return null
    for (i in index downTo 0) metric.read(rows[i])?.let { return it }
    return null
}

/** The value the tape is scaled against: the first time this girth was measured. */
fun referenceValue(rows: List<BodyMeasurementEntity>, metric: BodyMetric): Float? = rows.firstNotNullOfOrNull { metric.read(it) }

/**
 * Tape length in source pixels. The first measurement fits the drawn body exactly; later ones
 * grow or shrink in proportion to the girth itself, kept within a sane range so a typo cannot
 * throw the tape off the screen.
 */
fun tapeLength(band: FigureBand, value: Float?, reference: Float?): Float {
    val drawn = band.half * 2
    if (value == null || reference == null || reference <= 0f) return drawn
    return drawn * (value / reference).coerceIn(0.6f, 1.5f)
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
