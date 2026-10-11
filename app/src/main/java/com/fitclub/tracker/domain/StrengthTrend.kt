package com.fitclub.tracker.domain

import com.fitclub.tracker.data.repo.WorkoutLog
import java.time.LocalDate

data class StrengthPoint(val monday: LocalDate, val oneRepMaxKg: Float)

/**
 * Best estimated one-repetition maximum per week for one exercise, oldest first. Warm-up and
 * assisted sets do not count; weeks without the exercise are left out.
 */
fun weeklyOneRepMax(workouts: List<WorkoutLog>, exerciseId: String, today: LocalDate, weeks: Int = 12): List<StrengthPoint> {
    val first = weekStart(today).minusWeeks((weeks - 1).toLong())
    return (0 until weeks).mapNotNull { w ->
        val monday = first.plusWeeks(w.toLong())
        val range = monday.toEpochDay()..monday.plusDays(6).toEpochDay()
        workouts.filter { it.session.epochDay in range }.flatMap { it.sets }
            .filter { it.exerciseId == exerciseId && it.done && !it.isTimed && !it.warmup && !it.assisted && it.reps > 0 }
            .maxOfOrNull { estimatedOneRepMax(it.weightKg, it.reps) }
            ?.let { StrengthPoint(monday, it) }
    }
}
