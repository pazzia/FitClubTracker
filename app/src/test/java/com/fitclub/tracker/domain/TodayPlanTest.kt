package com.fitclub.tracker.domain

import com.fitclub.tracker.data.db.SetLogEntity
import com.fitclub.tracker.data.db.WorkoutSessionEntity
import com.fitclub.tracker.data.model.RepRange
import com.fitclub.tracker.data.repo.DayDraft
import com.fitclub.tracker.data.repo.ItemDraft
import com.fitclub.tracker.data.repo.ProgramDraft
import com.fitclub.tracker.data.repo.WorkoutLog
import com.fitclub.tracker.ui.workout.stepValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TodayPlanTest {
    private val item = ItemDraft("bench", 3, RepRange.R8_12, 90)
    private val program = ProgramDraft(id = 7, days = listOf(DayDraft(listOf(item)), DayDraft(), DayDraft(listOf(item)), DayDraft(listOf(item))))

    private fun log(day: Int?, startedAt: Long, epochDay: Long = 20000, programId: Long? = 7, sets: Int = 1) = WorkoutLog(
        WorkoutSessionEntity(id = startedAt, programId = programId, dayPosition = day, epochDay = epochDay, startedAt = startedAt, finishedAt = startedAt + 1),
        List(sets) { SetLogEntity(sessionId = startedAt, exerciseId = "bench", setIndex = it, weightKg = 50f, reps = 8, done = true) })

    @Test fun firstWorkoutStartsWithDayA() {
        assertEquals(0, nextProgramDay(program, emptyList()))
    }

    @Test fun rotationSkipsEmptyDaysAndWrapsAround() {
        assertEquals(2, nextProgramDay(program, listOf(log(0, 1))))
        assertEquals(3, nextProgramDay(program, listOf(log(0, 1), log(2, 2))))
        assertEquals(0, nextProgramDay(program, listOf(log(3, 3), log(0, 1))))
    }

    @Test fun emptySessionsAndOtherProgramsDoNotAdvanceTheRotation() {
        assertEquals(2, nextProgramDay(program, listOf(log(0, 1), log(2, 5, sets = 0), log(3, 6, programId = 99))))
    }

    @Test fun programWithoutExercisesHasNoNextDay() {
        assertNull(nextProgramDay(ProgramDraft(days = listOf(DayDraft(), DayDraft())), emptyList()))
    }

    @Test fun streakCountsConsecutiveWeeksAndToleratesAnEmptyCurrentWeek() {
        val monday = LocalDate.of(2026, 10, 5)
        val today = monday.plusDays(3)
        val logs = listOf(
            log(0, 1, monday.minusWeeks(1).toEpochDay()),
            log(0, 2, monday.minusWeeks(2).plusDays(4).toEpochDay()),
            log(0, 3, monday.minusWeeks(4).toEpochDay())
        )
        assertEquals(2, weekStreak(logs, today))
        assertEquals(3, weekStreak(logs + log(0, 4, today.toEpochDay()), today))
        assertEquals(1, workoutsThisWeek(logs + log(0, 4, today.toEpochDay()), today))
    }

    @Test fun steppersNeverGoBelowZeroAndKeepTenths() {
        assertEquals("75", stepValue("72.5", 2.5f, true))
        assertEquals("75", stepValue("72,5", 2.5f, true))
        assertEquals("72,5", stepValue("70", 2.5f, true))
        assertEquals("0", stepValue("1", -2.5f, true))
        assertEquals("9", stepValue("10", -1f, false))
        assertEquals("1", stepValue("", 1f, false))
        assertEquals("62,5", stepValue("60,0", 2.5f, true))
        assertEquals("17,56", com.fitclub.tracker.domain.decimal(17.56f))
        assertEquals("17,5", com.fitclub.tracker.domain.normalizeDecimalInput("17.5"))
        assertEquals("17,55", com.fitclub.tracker.domain.normalizeDecimalInput("17,5.5"))
    }
}
