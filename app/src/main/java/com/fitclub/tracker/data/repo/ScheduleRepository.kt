package com.fitclub.tracker.data.repo

import androidx.room.withTransaction
import com.fitclub.tracker.data.db.*
import kotlinx.coroutines.flow.map

data class ScheduledDraft(
    val id: Long = 0,
    val epochDay: Long,
    val title: String = "Моя тренировка",
    val startMinute: Int = 18 * 60 + 30,
    val programId: Long? = null,
    val dayPosition: Int? = null,
    val items: List<ItemDraft> = emptyList()
) {
    val canSave: Boolean get() = title.isNotBlank() && startMinute in 0..1439 && items.isNotEmpty() &&
        items.all { it.sets in 1..10 && it.restSec in 0..600 && it.exerciseId.isNotBlank() }

    fun fromProgram(program: ProgramDraft, day: Int): ScheduledDraft = copy(
        title = "${program.name} · день ${'A' + day}", programId = program.id,
        dayPosition = day, items = program.days.getOrNull(day)?.items.orEmpty()
    )
}

fun ScheduledWorkoutWithItems.toDraft() = ScheduledDraft(
    workout.id, workout.epochDay, workout.title, workout.startMinute, workout.programId, workout.dayPosition,
    items.sortedBy { it.position }.map { ItemDraft(it.exerciseId, it.sets, it.reps, it.restSec, it.comment) }
)

class ScheduleRepository(private val db: AppDatabase) {
    private val dao = db.scheduleDao()
    fun observeAll() = dao.observeAll().map { list -> list.map { it.toDraft() } }
    fun observe(id: Long) = dao.observe(id).map { it?.toDraft() }
    suspend fun get(id: Long) = dao.get(id)?.toDraft()
    suspend fun getAllForDate(epochDay: Long) = dao.getAllForDate(epochDay).map { it.toDraft() }
    suspend fun replaceItems(id: Long, items: List<ItemDraft>) = db.withTransaction {
        require(dao.get(id) != null)
        require(items.all { it.sets in 1..10 && it.restSec in 0..600 && it.exerciseId.isNotBlank() })
        dao.deleteItems(id)
        dao.insertItems(items.mapIndexed { position, item ->
            ScheduledExerciseEntity(scheduleId = id, exerciseId = item.exerciseId, position = position,
                sets = item.sets, reps = item.reps, restSec = item.restSec, comment = item.comment)
        })
    }
    suspend fun getForDate(epochDay: Long) = dao.getForDate(epochDay)?.toDraft()

    suspend fun save(draft: ScheduledDraft): Long {
        require(draft.canSave) { "Добавьте упражнения и проверьте время и подходы" }
        return db.withTransaction {
            val entity = ScheduledWorkoutEntity(draft.id, draft.epochDay, draft.title.trim(), draft.startMinute, draft.programId, draft.dayPosition)
            val id = if (draft.id == 0L) dao.insert(entity) else { dao.update(entity); draft.id }
            dao.deleteItems(id)
            dao.insertItems(draft.items.mapIndexed { position, item ->
                ScheduledExerciseEntity(scheduleId = id, exerciseId = item.exerciseId, position = position,
                    sets = item.sets, reps = item.reps, restSec = item.restSec, comment = item.comment)
            })
            id
        }
    }
    suspend fun delete(id: Long) = dao.delete(id)
}

