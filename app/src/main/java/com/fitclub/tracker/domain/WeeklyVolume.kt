package com.fitclub.tracker.domain

import com.fitclub.tracker.data.repo.WorkoutLog
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class WeekVolume(val monday: LocalDate, val volumeKg: Float)

fun weeklyVolume(workouts: List<WorkoutLog>, referenceDate: LocalDate, count: Int): List<WeekVolume> {
    require(count in 1..52)
    val monday = referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return (count - 1 downTo 0).map { offset ->
        val start = monday.minusWeeks(offset.toLong())
        WeekVolume(start, workouts.filter { it.session.epochDay in start.toEpochDay()..start.plusDays(6).toEpochDay() }
            .sumOf { it.volumeKg.toDouble() }.toFloat())
    }
}
