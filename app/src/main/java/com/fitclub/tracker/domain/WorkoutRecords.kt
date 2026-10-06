package com.fitclub.tracker.domain

import com.fitclub.tracker.data.db.SetLogEntity

/** Compare only one exercise and one measurement mode; weights remain in canonical kg. */
fun bestWorkingSet(sets: List<SetLogEntity>, exerciseId: String, isTimed: Boolean): SetLogEntity? =
    sets.filter { it.exerciseId == exerciseId && it.isTimed == isTimed && it.done && !it.warmup && !it.assisted }
        .maxWithOrNull(if (isTimed) compareBy<SetLogEntity> { it.reps }
            else compareBy<SetLogEntity> { it.weightKg }.thenBy { it.reps })
