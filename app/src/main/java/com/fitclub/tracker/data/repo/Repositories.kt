package com.fitclub.tracker.data.repo

import androidx.room.withTransaction
import com.fitclub.tracker.data.db.AppDatabase
import com.fitclub.tracker.data.db.ExerciseEntity
import com.fitclub.tracker.data.db.ProgramDayEntity
import com.fitclub.tracker.data.db.ProgramEntity
import com.fitclub.tracker.data.db.ProgramExerciseEntity
import com.fitclub.tracker.data.db.ProgramWithDays
import com.fitclub.tracker.data.db.BodyMeasurementEntity
import com.fitclub.tracker.data.db.ExerciseLogEntity
import com.fitclub.tracker.data.db.SetLogEntity
import com.fitclub.tracker.data.db.WorkoutSessionEntity
import com.fitclub.tracker.data.model.Goal
import com.fitclub.tracker.data.model.RepRange
import com.fitclub.tracker.data.seed.ExerciseSeed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** Программа в удобном для редактирования виде. */
data class ProgramDraft(
    val id: Long = 0,
    val name: String = "Новая программа",
    val goal: Goal = Goal.MASS,
    val colorArgb: Long = 0xFFD4FF3A,
    val icon: String = ProgramIcons.TARGET,
    val freqPerWeek: Int = 3,
    val isActive: Boolean = false,
    val days: List<DayDraft> = listOf(DayDraft()),
    val coverPhoto: String? = null
) {
    val exerciseCount: Int get() = days.sumOf { it.items.size }
}

data class DayDraft(val items: List<ItemDraft> = emptyList())

data class ItemDraft(
    val exerciseId: String,
    val sets: Int,
    val reps: RepRange,
    val restSec: Int,
    val comment: String = ""
)

object ProgramIcons {
    const val DUMBBELL = "dumbbell"
    const val BOLT = "bolt"
    const val FLAME = "flame"
    const val HEART = "heart"
    const val MOUNTAIN = "mountain"
    const val TARGET = "target"
    val all = listOf(DUMBBELL, BOLT, FLAME, HEART, MOUNTAIN, TARGET)
}

fun ProgramWithDays.toDraft() = ProgramDraft(
    id = program.id,
    name = program.name,
    goal = program.goal,
    colorArgb = program.colorArgb,
    icon = program.icon,
    freqPerWeek = program.freqPerWeek,
    isActive = program.isActive,
    coverPhoto = program.coverPhoto,
    days = days.sortedBy { it.day.position }.map { d ->
        DayDraft(d.items.sortedBy { it.position }.map { ItemDraft(it.exerciseId, it.sets, it.reps, it.restSec, it.comment) })
    }
)

class ExerciseRepository(private val db: AppDatabase) {
    fun observeAll(): Flow<List<ExerciseEntity>> = db.exerciseDao().observeAll()
    fun observe(id: String): Flow<ExerciseEntity?> = db.exerciseDao().observe(id)

    suspend fun seedIfEmpty() {
        // Additive and idempotent on upgrades: keep user programs and recorded exercise IDs.
        db.exerciseDao().insertMissing(ExerciseSeed.all)
    }
}

class ProgramRepository(private val db: AppDatabase) {
    private val dao = db.programDao()

    fun observeAll(): Flow<List<ProgramDraft>> = dao.observeAll().map { list -> list.map { it.toDraft() } }
    fun observeActive(): Flow<ProgramDraft?> = dao.observeActive().map { it?.toDraft() }

    suspend fun get(id: Long): ProgramDraft? = dao.get(id)?.toDraft()

    /** Сохраняет программу целиком: шапку, дни и упражнения. Возвращает id. */
    suspend fun save(draft: ProgramDraft): Long = db.withTransaction {
        val entity = ProgramEntity(
            id = draft.id, name = draft.name.ifBlank { "Без названия" }, goal = draft.goal,
            colorArgb = draft.colorArgb, icon = draft.icon, freqPerWeek = draft.freqPerWeek,
            isActive = draft.isActive, coverPhoto = draft.coverPhoto
        )
        val id = if (draft.id == 0L) dao.insertProgram(entity) else { dao.updateProgram(entity); draft.id }
        dao.deleteDays(id)
        draft.days.forEachIndexed { index, day ->
            val dayId = dao.insertDay(ProgramDayEntity(programId = id, position = index))
            dao.insertItems(day.items.mapIndexed { pos, it ->
                ProgramExerciseEntity(dayId = dayId, exerciseId = it.exerciseId, position = pos, sets = it.sets, reps = it.reps, restSec = it.restSec, comment = it.comment)
            })
        }
        if (draft.isActive) dao.setActive(id)
        id
    }

    suspend fun delete(id: Long) = dao.deleteProgram(id)

    suspend fun setActive(id: Long) = dao.setActive(id)

    suspend fun seedIfEmpty() {
        if (dao.count() > 0) return
        SamplePrograms.all.forEach { save(it) }
    }
}

data class WorkoutLog(
    val session: WorkoutSessionEntity,
    val sets: List<SetLogEntity>,
    val exercises: List<ExerciseLogEntity> = emptyList()
) {
    val volumeKg: Float get() = sets.filter { it.done && !it.isTimed }.sumOf { (it.weightKg * it.reps).toDouble() }.toFloat()
    val completedSets: Int get() = sets.count { it.done }
    val durationMin: Int get() = (((session.finishedAt ?: session.startedAt) - session.startedAt) / 60_000L).toInt().coerceAtLeast(1)
}

data class CompletedExercise(val exerciseId: String, val position: Int, val durationMillis: Long, val completed: Boolean)

data class CompletedSet(val exerciseId: String, val setIndex: Int, val weightKg: Float, val reps: Int, val isTimed: Boolean = false)

class FitnessRepository(private val db: AppDatabase) {
    fun observeMeasurements(): Flow<List<BodyMeasurementEntity>> = db.measurementDao().observeAll()

    fun observeWorkouts(): Flow<List<WorkoutLog>> = combine(
        db.workoutDao().observeSessions(), db.workoutDao().observeSets(), db.workoutDao().observeExercises()
    ) { sessions, sets, exercises ->
        sessions.map { session -> WorkoutLog(session, sets.filter { it.sessionId == session.id }, exercises.filter { it.sessionId == session.id }) }
    }

    suspend fun deleteWorkout(id: Long, deleteLinkedPlan: Boolean = true) = db.withTransaction {
        val session = db.workoutDao().getSession(id) ?: return@withTransaction
        db.workoutDao().deleteSession(id)
        if (deleteLinkedPlan && session.scheduleId != null &&
            db.workoutDao().observeSessions().first().none { it.scheduleId == session.scheduleId }) {
            db.scheduleDao().delete(session.scheduleId)
        }
    }

    suspend fun updateWorkout(sessionId: Long, startedAt: Long, finishedAt: Long, sets: List<SetLogEntity>) = db.withTransaction {
        require(finishedAt >= startedAt)
        require(sets.all { it.weightKg.isFinite() && it.weightKg >= 0 && it.reps > 0 })
        val session = requireNotNull(db.workoutDao().getSession(sessionId))
        db.workoutDao().updateSession(session.copy(startedAt = startedAt, finishedAt = finishedAt))
        db.workoutDao().deleteSets(sessionId)
        db.workoutDao().insertSets(sets.map { it.copy(id = 0, sessionId = sessionId) })
    }

    suspend fun addMeasurement(weight: Float?, waist: Float?, fat: Float?, chest: Float?, arm: Float?) {
        db.measurementDao().insert(
            BodyMeasurementEntity(
                epochDay = java.time.LocalDate.now().toEpochDay(), weightKg = weight, waistCm = waist,
                fatPercent = fat, chestCm = chest, armCm = arm
            )
        )
    }

    suspend fun finishWorkout(programId: Long?, day: Int?, startedAt: Long, sets: List<CompletedSet>,
        scheduleId: Long? = null, epochDay: Long = java.time.LocalDate.now().toEpochDay(),
        exercises: List<CompletedExercise> = emptyList()): Long =
        db.withTransaction {
            val now = System.currentTimeMillis()
            val id = db.workoutDao().insertSession(
                WorkoutSessionEntity(
                    programId = programId, dayPosition = day, epochDay = epochDay,
                    startedAt = startedAt, finishedAt = now, scheduleId = scheduleId
                )
            )
            db.workoutDao().insertSets(sets.map {
                SetLogEntity(
                    sessionId = id, exerciseId = it.exerciseId, setIndex = it.setIndex,
                    weightKg = it.weightKg, reps = it.reps, done = true, isTimed = it.isTimed
                )
            })
            db.workoutDao().insertExercises(exercises.map {
                ExerciseLogEntity(sessionId = id, exerciseId = it.exerciseId, position = it.position,
                    durationMillis = it.durationMillis.coerceAtLeast(0), completed = it.completed)
            })
            id
        }
}

/** Примеры программ для первого запуска. «Летний рельеф» специально составлен с ошибками под цель. */
object SamplePrograms {
    private fun i(ex: String, sets: Int, reps: RepRange, rest: Int) = ItemDraft(ex, sets, reps, rest)

    val all = listOf(
        ProgramDraft(
            name = "Фулбоди 3×", goal = Goal.MASS, colorArgb = 0xFFD4FF3A, icon = ProgramIcons.DUMBBELL,
            freqPerWeek = 3, isActive = true,
            days = listOf(
                DayDraft(listOf(i("squat", 4, RepRange.R6_8, 120), i("bench", 4, RepRange.R6_8, 90), i("bbrow", 3, RepRange.R8_12, 90), i("plank", 3, RepRange.TIME, 60))),
                DayDraft(listOf(i("rdl", 4, RepRange.R6_8, 120), i("dbpress", 3, RepRange.R8_12, 90), i("latpull", 3, RepRange.R8_12, 90), i("legpress", 3, RepRange.R12_15, 90), i("lateral", 3, RepRange.R12_15, 60))),
                DayDraft(listOf(i("incline", 4, RepRange.R8_12, 90), i("pullup", 4, RepRange.R6_8, 120), i("skull", 3, RepRange.R8_12, 60), i("bbcurl", 3, RepRange.R8_12, 60)))
            )
        ),
        ProgramDraft(
            name = "Сила 5×5", goal = Goal.STRENGTH, colorArgb = 0xFFFF3D6E, icon = ProgramIcons.BOLT, freqPerWeek = 3,
            days = listOf(
                DayDraft(listOf(i("squat", 5, RepRange.R3_5, 180), i("bench", 5, RepRange.R3_5, 180), i("bbrow", 5, RepRange.R3_5, 180))),
                DayDraft(listOf(i("squat", 5, RepRange.R3_5, 180), i("ohp", 5, RepRange.R3_5, 180), i("deadlift", 1, RepRange.R3_5, 240)))
            )
        ),
        ProgramDraft(
            name = "Летний рельеф", goal = Goal.ENDURANCE, colorArgb = 0xFFFFB23F, icon = ProgramIcons.FLAME, freqPerWeek = 2,
            days = listOf(
                DayDraft(listOf(i("bench", 5, RepRange.R3_5, 180), i("deadlift", 4, RepRange.R3_5, 180), i("ohp", 4, RepRange.R3_5, 150), i("bbcurl", 3, RepRange.R6_8, 120)))
            )
        )
    )
}


