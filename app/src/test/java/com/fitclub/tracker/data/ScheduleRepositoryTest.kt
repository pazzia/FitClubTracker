package com.fitclub.tracker.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fitclub.tracker.data.db.*
import com.fitclub.tracker.data.model.RepRange
import com.fitclub.tracker.data.model.Sex
import com.fitclub.tracker.data.repo.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = android.app.Application::class)
class ScheduleRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ScheduleRepository
    private val date = LocalDate.of(2026, 9, 30).toEpochDay()
    private val items = listOf(ItemDraft("bench", 4, RepRange.R8_12, 90), ItemDraft("bbrow", 3, RepRange.R6_8, 120))
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = ScheduleRepository(db)
    }
    @After fun close() = db.close()

    @Test fun savedPlansAreIsolatedByFullDate() = runBlocking {
        repo.save(ScheduledDraft(epochDay = date, title = "Сентябрь", items = items))
        val otherDate = LocalDate.of(2026, 10, 30).toEpochDay()
        repo.save(ScheduledDraft(epochDay = otherDate, title = "Октябрь", items = items.take(1)))
        assertEquals("Сентябрь", repo.getForDate(date)!!.title)
        assertEquals("Октябрь", repo.getForDate(otherDate)!!.title)
        assertNull(repo.getForDate(date - 1))
        assertEquals(2, repo.observeAll().first().size)
    }
    @Test fun editingReplacesItemsWithoutCreatingAnotherPlan() = runBlocking {
        val id = repo.save(ScheduledDraft(epochDay = date, items = items))
        repo.save(repo.getForDate(date)!!.copy(title = "Грудь", startMinute = 540, items = items.take(1).map { it.copy(sets = 5) }))
        val loaded = repo.getForDate(date)!!
        assertEquals(id, loaded.id)
        assertEquals(540, loaded.startMinute)
        assertEquals(5, loaded.items.single().sets)
        assertEquals(1, repo.observeAll().first().size)
    }
    @Test fun scheduledWorkoutKeepsSnapshotWhenProgramChangesOrIsDeleted() = runBlocking {
        val programs = ProgramRepository(db)
        val programId = programs.save(ProgramDraft(name = "План", days = listOf(DayDraft(items))))
        val original = programs.get(programId)!!
        repo.save(ScheduledDraft(epochDay = date).fromProgram(original, 0))
        programs.save(original.copy(days = listOf(DayDraft(items.take(1)))))
        programs.delete(programId)
        assertEquals(items, repo.getForDate(date)!!.items)
    }
    @Test fun completedWorkoutUsesScheduledDateAndSurvivesPlanDeletion() = runBlocking {
        val id = repo.save(ScheduledDraft(epochDay = date, items = items))
        val fitness = FitnessRepository(db)
        fitness.finishWorkout(null, null, System.currentTimeMillis() - 61_000,
            listOf(CompletedSet("bench", 0, 60f, 8)), scheduleId = id, epochDay = date)
        repo.delete(id)
        assertNull(repo.getForDate(date))
        val log = fitness.observeWorkouts().first().single()
        assertEquals(date, log.session.epochDay)
        assertEquals(id, log.session.scheduleId)
        assertEquals(480f, log.volumeKg)
    }
    @Test fun twoWorkoutsOnSameDateAreIndependent() = runBlocking {
        val first = repo.save(ScheduledDraft(epochDay = date, title = "Утро", startMinute = 480, items = items))
        val second = repo.save(ScheduledDraft(epochDay = date, title = "Вечер", startMinute = 1200, items = items.take(1)))
        assertNotEquals(first, second)
        assertEquals(listOf(first, second), repo.getAllForDate(date).map { it.id })
        repo.save(repo.get(second)!!.copy(title = "Вечер изменён"))
        repo.delete(first)
        assertEquals("Вечер изменён", repo.getAllForDate(date).single().title)
    }
    @Test fun editedResultsRecalculateVolumeAndDeleteOnlySelectedWorkout() = runBlocking {
        val fitness = FitnessRepository(db)
        val firstPlan = repo.save(ScheduledDraft(epochDay = date, items = items))
        val otherPlan = repo.save(ScheduledDraft(epochDay = date, items = items))
        val first = fitness.finishWorkout(null, null, 1000, listOf(CompletedSet("bench", 0, 60f, 8)), firstPlan, date)
        val other = fitness.finishWorkout(null, null, 1000, listOf(CompletedSet("bench", 0, 50f, 10)), otherPlan, date)
        fitness.updateWorkout(first, 1000, 61000, listOf(SetLogEntity(sessionId = first, exerciseId = "bench", setIndex = 0, weightKg = 70f, reps = 10, done = true)))
        assertEquals(700f, fitness.observeWorkouts().first().first { it.session.id == first }.volumeKg)
        fitness.deleteWorkout(first)
        assertEquals(other, fitness.observeWorkouts().first().single().session.id)
        assertNull(repo.get(firstPlan))
        assertNotNull(repo.get(otherPlan))
    }
    @Test fun invalidPlansAreRejected() = runBlocking {
        for (plan in listOf(ScheduledDraft(epochDay = date), ScheduledDraft(epochDay = date, items = items, startMinute = 1440))) {
            try { repo.save(plan); fail("Invalid plan was saved") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun sexPersistsInProfileAfterRepositoryRecreation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("profile", Context.MODE_PRIVATE).edit().clear().commit()
        val profile = ProfileRepository(context)
        profile.update(profile.settings.value.copy(sex = Sex.FEMALE))
        assertEquals(Sex.FEMALE, ProfileRepository(context).settings.value.sex)
    }
}

