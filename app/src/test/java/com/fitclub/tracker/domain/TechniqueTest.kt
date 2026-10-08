package com.fitclub.tracker.domain

import com.fitclub.tracker.data.db.SetLogEntity
import com.fitclub.tracker.data.db.WorkoutSessionEntity
import com.fitclub.tracker.data.model.RepRange
import com.fitclub.tracker.data.repo.ItemDraft
import com.fitclub.tracker.data.repo.WorkoutLog
import com.fitclub.tracker.ui.workout.supersetGroups
import com.fitclub.tracker.ui.workout.supersetLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TechniqueTest {
    private fun set(weight: Float, reps: Int, stages: String = "", session: Long = 1, warmup: Boolean = false) =
        SetLogEntity(sessionId = session, exerciseId = "squat", setIndex = 0, weightKg = weight, reps = reps, done = true, warmup = warmup, stages = stages)

    @Test fun stagesRoundTripAndCountTowardsVolumeButNotRecords() {
        val stages = listOf(Stage(62.5f, 6), Stage(50f, 6))
        val text = formatStages(stages)
        assertEquals("62.5×6;50×6", text)
        assertEquals(stages, parseStages(text))
        val drop = set(80f, 8, text)
        assertEquals(80f * 8 + 62.5f * 6 + 50f * 6, setVolumeKg(drop), 0.01f)
        assertEquals(drop, bestWorkingSet(listOf(drop, set(75f, 8)), "squat", false))
        assertEquals(emptyList<Stage>(), parseStages("мусор;×;-5×3"))
    }

    @Test fun lastSessionSkipsWarmupsAndKeepsOrder() {
        fun log(id: Long, started: Long, sets: List<SetLogEntity>) =
            WorkoutLog(WorkoutSessionEntity(id = id, programId = null, dayPosition = null, epochDay = 20000, startedAt = started, finishedAt = started + 1), sets)
        val old = log(1, 100, listOf(set(70f, 8, session = 1)))
        val recent = log(2, 200, listOf(set(40f, 12, session = 2, warmup = true), set(80f, 8, session = 2), set(80f, 7, session = 2)))
        val (found, sets) = lastSessionSets(listOf(old, recent), "squat", false)!!
        assertEquals(2L, found.session.id)
        assertEquals(listOf(8, 7), sets.map { it.reps })
        assertNull(lastSessionSets(listOf(old), "bench", false))
    }

    @Test fun linkedExercisesFormLabelledSupersets() {
        fun item(link: Boolean) = ItemDraft("x", 3, RepRange.R8_12, 90, linkNext = link)
        val items = listOf(item(false), item(true), item(true), item(false), item(true), item(true))
        assertEquals(listOf(listOf(1, 2, 3), listOf(4, 5)), supersetGroups(items))
        assertEquals(mapOf(1 to "A1", 2 to "A2", 3 to "A3", 4 to "B1", 5 to "B2"), supersetLabels(items))
    }

    @Test fun epleyEstimate() {
        assertEquals(96f, estimatedOneRepMax(80f, 6), 0.01f)
        assertEquals(100f, estimatedOneRepMax(100f, 1), 0.01f)
    }
}
