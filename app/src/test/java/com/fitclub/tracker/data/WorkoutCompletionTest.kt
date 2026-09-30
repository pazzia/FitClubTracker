package com.fitclub.tracker.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fitclub.tracker.data.db.AppDatabase
import com.fitclub.tracker.data.repo.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = android.app.Application::class)
class WorkoutCompletionTest {
    private lateinit var db: AppDatabase
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build() }
    @After fun close() = db.close()
    @Test fun earlyFinishSavesEmptySession() = runBlocking {
        val repo = FitnessRepository(db)
        repo.finishWorkout(null, null, 1000L, emptyList(), epochDay = 20000)
        val log = repo.observeWorkouts().first().single()
        assertEquals(0, log.completedSets)
        assertEquals(20000L, log.session.epochDay)
        assertNotNull(log.session.finishedAt)
    }
    @Test fun partialWorkoutKeepsExerciseTimingAndDoesNotMultiplySecondsIntoTonnage() = runBlocking {
        val repo = FitnessRepository(db)
        repo.finishWorkout(null, null, 1000L,
            listOf(CompletedSet("bench", 0, 60f, 8), CompletedSet("plank", 0, 10f, 45, isTimed = true)),
            exercises = listOf(CompletedExercise("bench", 0, 62000, true), CompletedExercise("plank", 1, 45000, false)))
        val log = repo.observeWorkouts().first().single()
        assertEquals(480f, log.volumeKg)
        assertEquals(2, log.completedSets)
        assertEquals(62000L, log.exercises[0].durationMillis)
        assertTrue(log.exercises[0].completed)
        assertFalse(log.exercises[1].completed)
        assertTrue(log.sets.single { it.exerciseId == "plank" }.isTimed)
    }
}
