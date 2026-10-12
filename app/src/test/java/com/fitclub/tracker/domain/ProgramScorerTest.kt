package com.fitclub.tracker.domain

import com.fitclub.tracker.data.model.Goal
import com.fitclub.tracker.data.repo.ProgramDraft
import com.fitclub.tracker.data.repo.SamplePrograms
import com.fitclub.tracker.data.seed.ExerciseSeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramScorerTest {
    private val catalog = ExerciseSeed.all.associateBy { it.id }

    @Test
    fun seedPreservesMvpCoverageAndIncludesNewAdductorGroup() {
        assertEquals(ExerciseSeed.all.size, catalog.size)
        com.fitclub.tracker.data.model.MuscleGroup.entries.forEach { group ->
            val minimum = if (group == com.fitclub.tracker.data.model.MuscleGroup.ADDUCTORS) 1 else 20
            assertTrue(group.title, ExerciseSeed.all.count { it.group == group } >= minimum)
        }
    }

    @Test
    fun samplesReferenceExistingExercises() {
        SamplePrograms.all.flatMap { p -> p.days.flatMap { it.items } }.forEach {
            assertTrue("Нет упражнения ${it.exerciseId}", it.exerciseId in catalog)
        }
    }

    @Test
    fun balancedMassProgramScoresHigh() {
        val result = ProgramScorer.score(SamplePrograms.all[0], catalog)
        assertTrue("Фулбоди: ${result.percent}", result.percent >= 80)
    }

    @Test
    fun heavyProgramForEnduranceScoresLowWithAdvice() {
        val result = ProgramScorer.score(SamplePrograms.all[2], catalog)
        assertTrue("Летний рельеф: ${result.percent}", result.percent < 60)
        assertTrue(result.recommendations.any { it.title.startsWith("Повторения") })
        assertTrue(result.recommendations.any { it.title == "Тип отягощения" })
    }

    @Test
    fun sameProgramFitsStrengthBetter() {
        val endurance = SamplePrograms.all[2]
        val strength = endurance.copy(goal = Goal.STRENGTH)
        assertTrue(ProgramScorer.score(strength, catalog).percent > ProgramScorer.score(endurance, catalog).percent)
    }

    @Test
    fun emptyProgramGivesZero() {
        assertEquals(0, ProgramScorer.score(ProgramDraft(), catalog).percent)
    }
}
