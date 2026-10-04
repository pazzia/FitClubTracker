package com.fitclub.tracker.data

import com.fitclub.tracker.data.seed.ExerciseSeed
import com.fitclub.tracker.ui.library.matchesExercise
import org.junit.Assert.*
import org.junit.Test

class ExerciseSearchTest {
    private val bench = ExerciseSeed.all.first { it.id == "bench" }
    @Test fun searchNormalizesCaseYoAndMatchesEveryWord() {
        assertTrue(matchesExercise(bench, "  ЖИМ   ЛЕЖА "))
        assertTrue(matchesExercise(bench, "штанга грудь"))
        assertFalse(matchesExercise(bench, "жим ноги"))
        assertTrue(matchesExercise(bench, ""))
    }
    @Test fun queryReturnsMultipleMatchingExercises() {
        val matches = ExerciseSeed.all.filter { matchesExercise(it, "жим") }
        assertTrue(matches.size > 1)
        assertTrue(matches.any { it.id == "bench" })
        assertTrue(matches.all { matchesExercise(it, "жим") })
    }
}
