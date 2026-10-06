package com.fitclub.tracker.domain

import com.fitclub.tracker.data.db.SetLogEntity
import com.fitclub.tracker.data.repo.WorkoutLog

/** A previous assistance weight must never become an unmarked external working weight. */
fun previousWorkingSet(history: List<WorkoutLog>, exerciseId: String, timed: Boolean, index: Int): SetLogEntity? {
    fun matches(row: SetLogEntity) = row.exerciseId == exerciseId && row.isTimed == timed && row.done &&
        !row.warmup && !row.assisted && row.weightKg.isFinite() && row.weightKg >= 0
    return history.filter { log -> log.sets.any(::matches) }.maxByOrNull { it.session.startedAt }
        ?.sets?.filter(::matches)?.getOrNull(index)
}
