package com.fitclub.tracker.domain

import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

/** Storage is always kilograms; changing display units never rewrites history. */
data class WeightUnits(val kg: Boolean = true) {
    val label: String get() = if (kg) "кг" else "фунт."
    fun fromKg(value: Float): Float = if (kg) value else value * POUNDS_PER_KG
    fun toKg(value: Float): Float = if (kg) value else value / POUNDS_PER_KG
    fun number(value: Float): String = String.format(Locale.US, "%.1f", value).removeSuffix(".0")
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
