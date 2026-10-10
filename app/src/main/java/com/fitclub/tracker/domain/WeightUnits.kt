package com.fitclub.tracker.domain

import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

/** Storage is always kilograms; changing display units never rewrites history. */
data class WeightUnits(val kg: Boolean = true) {
    val label: String get() = if (kg) "кг" else "фунт."
    fun fromKg(value: Float): Float = if (kg) value else value * POUNDS_PER_KG
    fun toKg(value: Float): Float = if (kg) value else value / POUNDS_PER_KG
    fun number(value: Float): String = decimal(value, 1)
    fun format(valueKg: Float): String = "${number(fromKg(valueKg))} $label"
    companion object { const val POUNDS_PER_KG = 2.2046226f }
}
val LocalWeightUnits = staticCompositionLocalOf { WeightUnits() }

fun com.fitclub.tracker.data.model.RepRange.defaultCount(): Int = when (this) {
    com.fitclub.tracker.data.model.RepRange.R1_3 -> 3
    com.fitclub.tracker.data.model.RepRange.R3_5 -> 5
    com.fitclub.tracker.data.model.RepRange.R6_8 -> 8
    com.fitclub.tracker.data.model.RepRange.R8_12 -> 10
    com.fitclub.tracker.data.model.RepRange.R12_15 -> 12
    com.fitclub.tracker.data.model.RepRange.R15_20 -> 15
    com.fitclub.tracker.data.model.RepRange.R20_PLUS -> 20
    com.fitclub.tracker.data.model.RepRange.TIME -> 45
}

/** Russian decimal comma, no trailing zeros: 17,5 · 81,25 · 80. Two decimals keep 1,25 kg plates exact. */
fun decimal(value: Float, decimals: Int = 2): String =
    String.format(Locale.US, "%.${decimals}f", value).trimEnd('0').trimEnd('.').replace('.', ',').let { if (it == "-0") "0" else it }

/** Accepts both "," and "." while typing and keeps a single comma. */
fun normalizeDecimalInput(text: String, maxLength: Int = 7): String {
    val cleaned = text.filter { it.isDigit() || it == '.' || it == ',' }.replace('.', ',')
    val first = cleaned.indexOf(',')
    val single = if (first < 0) cleaned else cleaned.substring(0, first + 1) + cleaned.substring(first + 1).replace(",", "")
    return single.take(maxLength)
}
