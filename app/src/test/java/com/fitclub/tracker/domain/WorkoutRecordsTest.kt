package com.fitclub.tracker.domain

import com.fitclub.tracker.data.db.SetLogEntity
import org.junit.Assert.*
import org.junit.Test

class WorkoutRecordsTest {
    private fun set(weight: Float = 40f, reps: Int = 8, timed: Boolean = false) =
        SetLogEntity(sessionId = 1, exerciseId = "bench", setIndex = 0, weightKg = weight, reps = reps, done = true, isTimed = timed)

    @Test fun mixedHistoryHasIndependentRecords() {
        val weighted = set()
        val timed = set(reps = 60, timed = true)
        val history = listOf(weighted, timed)
        assertEquals(weighted, bestWorkingSet(history, "bench", false))
        assertEquals(timed, bestWorkingSet(history, "bench", true))
    }

    @Test fun weightRecordUsesRepsToBreakTiesAndExcludesOtherExercises() {
        val best = set(weight = 60f, reps = 8)
        val history = listOf(set(weight = 40f, reps = 60), set(weight = 60f, reps = 5), best,
            set(weight = 100f).copy(exerciseId = "bbrow"))
        assertEquals(best, bestWorkingSet(history, "bench", false))
    }

    @Test fun timedRecordUsesSecondsRegardlessOfExternalWeight() {
        val best = set(weight = 0f, reps = 90, timed = true)
        assertEquals(best, bestWorkingSet(listOf(set(weight = 100f, reps = 60, timed = true), best), "bench", true))
    }

    @Test fun warmupAssistanceAndUncheckedSetsNeverBecomeRecords() {
        for (timed in listOf(false, true)) {
            val regular = set(reps = 8, timed = timed)
            val higher = set(weight = 100f, reps = 120, timed = timed)
            assertEquals(regular, bestWorkingSet(listOf(regular, higher.copy(warmup = true),
                higher.copy(assisted = true), higher.copy(done = false)), "bench", timed))
        }
    }

    @Test fun absentModeHasNoRecord() {
        assertNull(bestWorkingSet(listOf(set()), "bench", true))
        assertNull(bestWorkingSet(listOf(set(timed = true)), "bench", false))
        assertNull(bestWorkingSet(emptyList(), "bench", false))
        assertNull(bestWorkingSet(listOf(set().copy(warmup = true)), "bench", false))
    }

    @Test fun poundsConversionDoesNotChangeRecordIdentityOrTimedValues() {
        val history = listOf(set(), set(reps = 60, timed = true))
        val weighted = bestWorkingSet(history, "bench", false)!!
        assertEquals("40 кг", WeightUnits(true).format(weighted.weightKg))
        assertEquals("88.2 фунт.", WeightUnits(false).format(weighted.weightKg))
        assertEquals(60, bestWorkingSet(history, "bench", true)!!.reps)
        assertEquals(40f, weighted.weightKg, 0f)
    }
}
