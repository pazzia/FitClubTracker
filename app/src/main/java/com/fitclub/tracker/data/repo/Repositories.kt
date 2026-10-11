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
import com.fitclub.tracker.domain.setVolumeKg
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
    val coverPhoto: String? = null,
    /** Keeps list order stable across edits and undo; 0 — set on first save. */
    val createdAt: Long = 0
) {
    val exerciseCount: Int get() = days.sumOf { it.items.size }
}

data class DayDraft(val items: List<ItemDraft> = emptyList())

data class ItemDraft(
    val exerciseId: String,
    val sets: Int,
    val reps: RepRange,
    val restSec: Int,
    val comment: String = "",
    /** Planned technique id ("" — обычные подходы). */
    val technique: String = "",
    /** "all" or "last": which planned sets use [technique]. */
    val techniqueScope: String = "",
    /** Superset group: exercises with the same non-zero number alternate without rest, in any positions. */
    val superset: Int = 0
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
    createdAt = program.createdAt,
    days = days.sortedBy { it.day.position }.map { d ->
        DayDraft(d.items.sortedBy { it.position }.map { ItemDraft(it.exerciseId, it.sets, it.reps, it.restSec, it.comment, it.technique, it.techniqueScope, it.superset) })
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
            isActive = draft.isActive, coverPhoto = draft.coverPhoto,
            createdAt = draft.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis()
        )
        val id = if (draft.id == 0L) dao.insertProgram(entity) else { dao.updateProgram(entity); draft.id }
        writeDays(id, draft)
        id
    }

    /** Puts a just-deleted program back with its original id, so plans and history still point to it. */
    suspend fun restore(draft: ProgramDraft): Long = db.withTransaction {
        require(draft.id != 0L)
        dao.insertProgram(ProgramEntity(
            id = draft.id, name = draft.name.ifBlank { "Без названия" }, goal = draft.goal,
            colorArgb = draft.colorArgb, icon = draft.icon, freqPerWeek = draft.freqPerWeek,
            isActive = draft.isActive, coverPhoto = draft.coverPhoto,
            createdAt = draft.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis()
        ))
        writeDays(draft.id, draft)
        draft.id
    }

    private suspend fun writeDays(id: Long, draft: ProgramDraft) {
        dao.deleteDays(id)
        draft.days.forEachIndexed { index, day ->
            val dayId = dao.insertDay(ProgramDayEntity(programId = id, position = index))
            dao.insertItems(day.items.mapIndexed { pos, it ->
                ProgramExerciseEntity(dayId = dayId, exerciseId = it.exerciseId, position = pos, sets = it.sets, reps = it.reps, restSec = it.restSec, comment = it.comment,
                    technique = it.technique, techniqueScope = it.techniqueScope, superset = it.superset)
            })
        }
        if (draft.isActive) dao.setActive(id)
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
    val volumeKg: Float get() = sets.filter { it.done && !it.isTimed && !it.assisted }.sumOf { setVolumeKg(it).toDouble() }.toFloat()
    val completedSets: Int get() = sets.count { it.done }
    val status: String get() = when {
        completedSets == 0 -> "Без выполненных подходов"
        exercises.isNotEmpty() && exercises.all { it.completed } -> "Выполнено"
        else -> "Частично выполнено"
    }
    val durationMin: Int get() = ((if (session.durationMillis >= 0) session.durationMillis else (session.finishedAt ?: session.startedAt) - session.startedAt) / 60_000L).toInt().coerceAtLeast(1)
}

data class CompletedExercise(val exerciseId: String, val position: Int, val durationMillis: Long, val completed: Boolean)

data class CompletedSet(val exerciseId: String, val setIndex: Int, val weightKg: Float, val reps: Int, val isTimed: Boolean = false, val exercisePosition: Int = -1, val warmup: Boolean = false, val rir: Int? = null, val assisted: Boolean = false,
    val technique: String = "", val stages: String = "")

class FitnessRepository(private val db: AppDatabase) {
    fun observeActive() = db.workoutDao().observeActive()
    suspend fun getActive(id: Long) = db.workoutDao().getActive(id)
    suspend fun saveActive(id: Long, title: String, snapshot: String) = db.workoutDao().saveActive(com.fitclub.tracker.data.db.ActiveWorkoutEntity(id, title, snapshot))

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
        require(sets.all { it.weightKg.isFinite() && it.weightKg >= 0 && it.reps > 0 && (it.rir == null || it.rir in 0..10) })
        val session = requireNotNull(db.workoutDao().getSession(sessionId))
        db.workoutDao().updateSession(session.copy(startedAt = startedAt, finishedAt = finishedAt, durationMillis = if (startedAt == session.startedAt && finishedAt == session.finishedAt) session.durationMillis else finishedAt - startedAt))
        db.workoutDao().deleteSets(sessionId)
        db.workoutDao().insertSets(sets.map { it.copy(id = 0, sessionId = sessionId) })
        val old = db.workoutDao().getExercises(sessionId)
        db.workoutDao().deleteExercises(sessionId)
        db.workoutDao().insertExercises(sets.groupBy { it.exerciseId to it.exercisePosition }.map { (key, rows) ->
            val previous = if (key.second == -1) {
                val legacy = old.filter { it.exerciseId == key.first }
                legacy.firstOrNull()?.copy(position = -1, durationMillis = legacy.sumOf { it.durationMillis }, completed = legacy.all { it.completed })
            } else old.firstOrNull { it.exerciseId == key.first && it.position == key.second }
            ExerciseLogEntity(sessionId = sessionId, exerciseId = key.first, position = key.second,
                durationMillis = previous?.durationMillis ?: 0, completed = (previous?.completed ?: true) && rows.isNotEmpty() && rows.all { it.done })
        })
    }

    /**
     * Saves a measurement for [epochDay]. A second entry on the same day fills in or corrects
     * that day's row instead of adding another point to the timeline.
     */
    suspend fun addMeasurement(weight: Float?, waist: Float?, fat: Float?, chest: Float?, arm: Float?,
        hips: Float? = null, thigh: Float? = null, epochDay: Long = java.time.LocalDate.now().toEpochDay()) = db.withTransaction {
        require(listOf(weight, waist, fat, chest, arm, hips, thigh).any { it != null })
        require(listOf(weight, waist, chest, arm, hips, thigh).all { it == null || (it.isFinite() && it > 0) })
        require(fat == null || (fat.isFinite() && fat in 0f..100f))
        val same = db.measurementDao().getForDay(epochDay)
        db.measurementDao().insert(
            BodyMeasurementEntity(
                id = same?.id ?: 0, epochDay = epochDay,
                weightKg = weight ?: same?.weightKg, waistCm = waist ?: same?.waistCm,
                fatPercent = fat ?: same?.fatPercent, chestCm = chest ?: same?.chestCm, armCm = arm ?: same?.armCm,
                hipsCm = hips ?: same?.hipsCm, thighCm = thigh ?: same?.thighCm
            )
        )
    }

    suspend fun finishWorkout(programId: Long?, day: Int?, startedAt: Long, sets: List<CompletedSet>,
        scheduleId: Long? = null, epochDay: Long = java.time.LocalDate.now().toEpochDay(),
        exercises: List<CompletedExercise> = emptyList(), title: String = "", plannedEpochDay: Long? = null, planSnapshot: String = "", elapsedMillis: Long = -1): Long =
        db.withTransaction {
            require(sets.all { it.weightKg.isFinite() && it.weightKg >= 0 && it.reps > 0 && (it.rir == null || it.rir in 0..10) })
            val now = System.currentTimeMillis()
            val id = db.workoutDao().insertSession(
                WorkoutSessionEntity(
                    programId = programId, dayPosition = day, epochDay = epochDay,
                    startedAt = startedAt, finishedAt = now, scheduleId = scheduleId, title = title, plannedEpochDay = plannedEpochDay, planSnapshot = planSnapshot, durationMillis = elapsedMillis
                )
            )
            db.workoutDao().insertSets(sets.map {
                SetLogEntity(
                    sessionId = id, exerciseId = it.exerciseId, setIndex = it.setIndex,
                    weightKg = it.weightKg, reps = it.reps, done = true, isTimed = it.isTimed, exercisePosition = it.exercisePosition, warmup = it.warmup, rir = it.rir, assisted = it.assisted,
                    technique = it.technique, stages = it.stages
                )
            })
            db.workoutDao().insertExercises(exercises.map {
                ExerciseLogEntity(sessionId = id, exerciseId = it.exerciseId, position = it.position,
                    durationMillis = it.durationMillis.coerceAtLeast(0), completed = it.completed)
            })
            if (scheduleId != null) db.workoutDao().deleteActive(scheduleId)
            id
        }
}

/** Примеры программ для первого запуска. «Летний рельеф» специально составлен с ошибками под цель. */
object SamplePrograms {
    private fun i(ex: String, sets: Int, reps: RepRange, rest: Int) = ItemDraft(ex, sets, reps, rest)

    val all = listOf(
        ProgramDraft(
            name = "Фулбоди 3×", goal = Goal.MASS, colorArgb = 0xFFD4FF3A, icon = ProgramIcons.DUMBBELL,
            // Sample programs are examples; the user makes one active deliberately.
            freqPerWeek = 3, isActive = false,
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
            name = "Учебный пример: ошибки программы", goal = Goal.ENDURANCE, colorArgb = 0xFFFFB23F, icon = ProgramIcons.FLAME, freqPerWeek = 2,
            days = listOf(
                DayDraft(listOf(i("bench", 5, RepRange.R3_5, 180), i("deadlift", 4, RepRange.R3_5, 180), i("ohp", 4, RepRange.R3_5, 150), i("bbcurl", 3, RepRange.R6_8, 120)))
            )
        )
    )
}

