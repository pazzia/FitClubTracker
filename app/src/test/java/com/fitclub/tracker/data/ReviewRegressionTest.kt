package com.fitclub.tracker.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fitclub.tracker.data.db.*
import com.fitclub.tracker.data.repo.*
import com.fitclub.tracker.domain.WeightUnits
import com.fitclub.tracker.domain.defaultCount
import com.fitclub.tracker.data.model.RepRange
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class ReviewRegressionTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: FitnessRepository
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        repo = FitnessRepository(db)
    }
    @After fun close() = db.close()
    @Test fun poundsRoundTripAndDisplayDoNotRewriteHistory() = runBlocking {
        repo.finishWorkout(null, null, 1, listOf(CompletedSet("bench", 0, 60f, 8)))
        val before = repo.observeWorkouts().first().single().sets.single().weightKg
        val pounds = WeightUnits(false)
        assertEquals(132.27736f, pounds.fromKg(before), .0001f)
        assertEquals(before, pounds.toKg(pounds.fromKg(before)), .0001f)
        assertEquals("1058,2 фунт.", pounds.format(repo.observeWorkouts().first().single().volumeKg))
        assertEquals(before, repo.observeWorkouts().first().single().sets.single().weightKg, 0f)
        assertTrue(ProfileSettings().unitsKg)
    }
    @Test fun duplicateBlocksRetainIdentityAndTitleSurvivesDeletingPlan() = runBlocking {
        val schedule = ScheduleRepository(db)
        val planId = schedule.save(ScheduledDraft(epochDay = 30000, title = "План", items = listOf(ItemDraft("bench", 1, RepRange.R8_12, 90))))
        repo.finishWorkout(null, null, 1, listOf(CompletedSet("bench", 0, 60f, 8, exercisePosition = 0), CompletedSet("bench", 1, 65f, 6, exercisePosition = 1)),
            scheduleId = planId, epochDay = 20000, exercises = listOf(CompletedExercise("bench", 0, 100, true), CompletedExercise("bench", 1, 200, true)), title = "Снимок", plannedEpochDay = 30000)
        schedule.delete(planId)
        val log = repo.observeWorkouts().first().single()
        assertEquals("Снимок", log.session.title)
        assertEquals(20000L, log.session.epochDay)
        assertEquals(30000L, log.session.plannedEpochDay)
        assertEquals(listOf(0, 1), log.sets.map { it.exercisePosition })
    }
    @Test fun finishingAtomicallyRemovesDraftAndFailureKeepsIt() = runBlocking {
        repo.saveActive(10, "План", "draft")
        try { repo.finishWorkout(null, null, 1, listOf(CompletedSet("bench", 0, Float.NaN, 8)), scheduleId = 10); fail("Invalid weight") } catch (_: IllegalArgumentException) { }
        assertEquals("draft", repo.getActive(10)?.snapshot)
        assertTrue(repo.observeWorkouts().first().isEmpty())
        repo.finishWorkout(null, null, 1, emptyList(), scheduleId = 10)
        assertNull(repo.getActive(10))
        assertEquals("Без выполненных подходов", repo.observeWorkouts().first().single().status)
    }
    @Test fun editorRemovesGhostExerciseLogsAndKeepsTimingOfExistingBlock() = runBlocking {
        val id = repo.finishWorkout(null, null, 1, listOf(CompletedSet("bench", 0, 60f, 8, exercisePosition = 0), CompletedSet("plank", 0, 0f, 45, true, 1)),
            exercises = listOf(CompletedExercise("bench", 0, 1000, true), CompletedExercise("plank", 1, 2000, true)))
        val log = repo.observeWorkouts().first().single()
        repo.updateWorkout(id, 1, 10000, listOf(log.sets.first { it.exerciseId == "bench" }, SetLogEntity(sessionId = id, exerciseId = "bbrow", setIndex = 0, weightKg = 50f, reps = 10, done = true, exercisePosition = 2)))
        val updated = repo.observeWorkouts().first().single()
        assertEquals(listOf("bench", "bbrow"), updated.exercises.map { it.exerciseId })
        assertEquals(1000L, updated.exercises.first().durationMillis)
        assertEquals(0L, updated.exercises.last().durationMillis)
    }
    @Test fun editingLegacyGroupedSetsRetainsAggregatedExerciseTime() = runBlocking {
        val id = repo.finishWorkout(null, null, 1, listOf(CompletedSet("bench", 0, 60f, 8)),
            exercises = listOf(CompletedExercise("bench", 0, 1000, true), CompletedExercise("bench", 2, 2000, true)))
        val log = repo.observeWorkouts().first().single()
        repo.updateWorkout(id, 1, 10000, log.sets)
        assertEquals(3000L, repo.observeWorkouts().first().single().exercises.single().durationMillis)
    }
    @Test fun timedAndAssistedSetsAreExcludedFromTonnageButWarmupIsRecorded() = runBlocking {
        repo.finishWorkout(null, null, 1, listOf(CompletedSet("bench", 0, 20f, 10, warmup = true, rir = 5), CompletedSet("pullup", 0, 40f, 10, assisted = true), CompletedSet("plank", 0, 10f, 60, true)))
        val log = repo.observeWorkouts().first().single()
        assertEquals(200f, log.volumeKg, 0f)
        assertEquals(5, log.sets.single { it.exerciseId == "bench" }.rir)
        assertTrue(log.sets.single { it.exerciseId == "bench" }.warmup)
        assertTrue(log.sets.single { it.exerciseId == "pullup" }.assisted)
    }
    @Test fun invalidMeasurementsAreRejectedWithoutWriting() = runBlocking {
        for (values in listOf(listOf<Float?>(null, null, null, null, null), listOf(-1f, null, null, null, null), listOf(null, null, 101f, null, null))) {
            try { repo.addMeasurement(values[0], values[1], values[2], values[3], values[4]); fail("Invalid measurement") } catch (_: IllegalArgumentException) { }
        }
        assertTrue(repo.observeMeasurements().first().isEmpty())
        repo.addMeasurement(80f, null, 0f, null, null)
        assertEquals(0f, repo.observeMeasurements().first().single().fatPercent!!, 0f)
    }
    @Test fun weeklyGraphSeparatesYearsAndKeepsChronologicalEmptyWeeks() = runBlocking {
        suspend fun record(date: String, weight: Float) = repo.finishWorkout(null, null, 1,
            listOf(CompletedSet("bench", 0, weight, 10)), epochDay = java.time.LocalDate.parse(date).toEpochDay())
        record("2021-12-31", 90f); record("2022-12-31", 10f); record("2023-01-01", 20f); record("2023-01-02", 30f)
        val series = com.fitclub.tracker.domain.weeklyVolume(repo.observeWorkouts().first(), java.time.LocalDate.parse("2023-01-16"), 4)
        assertEquals(listOf("2022-12-26", "2023-01-02", "2023-01-09", "2023-01-16"), series.map { it.monday.toString() })
        assertEquals(listOf(300f, 300f, 0f, 0f), series.map { it.volumeKg })
    }
    @Test fun recordedMonotonicDurationTakesPrecedenceOverWallClockDifference() = runBlocking {
        repo.finishWorkout(null, null, 1, emptyList(), elapsedMillis = 120000)
        val log = repo.observeWorkouts().first().single()
        assertEquals(2, log.durationMin)
        repo.updateWorkout(log.session.id, log.session.startedAt, log.session.finishedAt!!, emptyList())
        assertEquals(2, repo.observeWorkouts().first().single().durationMin)
    }
    @Test fun weightSuggestionsDoNotMixAssistanceWarmupAndTimedResults() = runBlocking {
        repo.finishWorkout(null, null, 1000, listOf(CompletedSet("bench", 0, 20f, 10, warmup = true), CompletedSet("bench", 1, 60f, 8)))
        repo.finishWorkout(null, null, 2000, listOf(CompletedSet("bench", 0, 80f, 8, assisted = true)))
        repo.finishWorkout(null, null, 3000, listOf(CompletedSet("bench", 0, 30f, 45, isTimed = true)))
        val history = repo.observeWorkouts().first()
        assertEquals(60f, com.fitclub.tracker.domain.previousWorkingSet(history, "bench", false, 0)!!.weightKg, 0f)
        assertEquals(30f, com.fitclub.tracker.domain.previousWorkingSet(history, "bench", true, 0)!!.weightKg, 0f)
        assertNull(com.fitclub.tracker.domain.previousWorkingSet(history, "bench", false, 1))
    }
    @Test fun repetitionDefaultsFollowPlan() {
        assertEquals(3, RepRange.R1_3.defaultCount()); assertEquals(5, RepRange.R3_5.defaultCount())
        assertEquals(12, RepRange.R12_15.defaultCount()); assertEquals(45, RepRange.TIME.defaultCount())
    }
}
