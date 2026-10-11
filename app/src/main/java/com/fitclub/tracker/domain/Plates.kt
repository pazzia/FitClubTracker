package com.fitclub.tracker.domain

import kotlin.math.abs
import kotlin.math.roundToInt

/** Standard gym plates, kg, heaviest first. */
val STANDARD_PLATES = listOf(25f, 20f, 15f, 10f, 5f, 2.5f, 1.25f)

/**
 * Plates for ONE side of the bar to reach [totalKg], heaviest first, or null when the weight is
 * below the bar or cannot be made exactly from [plates].
 */
fun platesPerSide(totalKg: Float, barKg: Float = 20f, plates: List<Float> = STANDARD_PLATES): List<Float>? {
    if (!totalKg.isFinite() || totalKg < barKg - 0.001f) return null
    var side = ((totalKg - barKg) / 2f * 100).roundToInt() / 100f
    val out = ArrayList<Float>()
    for (p in plates) while (side + 0.001f >= p) { out += p; side -= p }
    return if (abs(side) < 0.01f) out else null
}

/** «Гриф 20 + на сторону 25 + 10 + 2,5»; null when the hint would be wrong or empty. */
fun plateHint(totalKg: Float, barKg: Float = 20f): String? {
    val side = platesPerSide(totalKg, barKg) ?: return null
    if (side.isEmpty()) return "Пустой гриф ${formatMeasure(barKg)} кг"
    return "Гриф ${formatMeasure(barKg)} + на сторону ${side.joinToString(" + ") { formatMeasure(it) }}"
}

/** Bar weight for an exercise's equipment, or null when plate maths does not apply. */
fun barFor(equipment: String?): Float? = when (equipment) {
    "Штанга" -> 20f
    "EZ-гриф" -> 10f
    else -> null
}
