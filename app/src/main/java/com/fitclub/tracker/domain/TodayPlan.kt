package com.fitclub.tracker.domain

import com.fitclub.tracker.data.repo.ProgramDraft
import com.fitclub.tracker.data.repo.WorkoutLog
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * The program day that follows the last one actually trained (A → B → C → A).
 * Days without exercises are skipped; null when the program has nothing to train.
 */
fun nextProgramDay(program: ProgramDraft, workouts: List<WorkoutLog>): Int? {
    val days = program.days.indices.filter { program.days[it].items.isNotEmpty() }
    if (days.isEmpty()) return null
    val last = workouts
        .filter { it.session.programId == program.id && it.session.dayPosition != null && it.completedSets > 0 }
        .maxByOrNull { it.session.startedAt }
        ?.session?.dayPosition ?: return days.first()
    return days.firstOrNull { it > last } ?: days.first()
}

/** Monday of the week that contains [date]. */
fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/**
 * Consecutive weeks with at least one completed workout. The current week counts once it has
 * a workout; an empty current week does not break a streak that ended last week.
 */
fun weekStreak(workouts: List<WorkoutLog>, today: LocalDate): Int {
    val weeks = workouts.filter { it.completedSets > 0 }
        .map { weekStart(LocalDate.ofEpochDay(it.session.epochDay)) }.toSet()
    var monday = weekStart(today)
    if (monday !in weeks) monday = monday.minusWeeks(1)
    var count = 0
    while (monday in weeks) { count++; monday = monday.minusWeeks(1) }
    return count
}

/** Completed workouts in the week of [today]. */
fun workoutsThisWeek(workouts: List<WorkoutLog>, today: LocalDate): Int {
    val start = weekStart(today).toEpochDay()
    return workouts.count { it.completedSets > 0 && it.session.epochDay in start..start + 6 }
}

/** Volume in kilograms for the week of [today]. */
fun volumeThisWeek(workouts: List<WorkoutLog>, today: LocalDate): Float {
    val start = weekStart(today).toEpochDay()
    return workouts.filter { it.session.epochDay in start..start + 6 }.sumOf { it.volumeKg.toDouble() }.toFloat()
}
