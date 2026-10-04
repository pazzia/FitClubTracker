package com.fitclub.tracker.ui.library

import com.fitclub.tracker.data.db.ExerciseEntity
import com.fitclub.tracker.data.seed.FreeExerciseCatalog
import java.util.Locale

internal fun matchesExercise(exercise: ExerciseEntity, query: String): Boolean {
    fun normalize(text: String) = text.lowercase(Locale.ROOT).replace('ё', 'е')
    val words = normalize(query).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val text = normalize(listOf(exercise.name, exercise.group.title, exercise.equipment,
        exercise.primary.joinToString(" ") { it.title }, exercise.secondary.joinToString(" ") { it.title },
        FreeExerciseCatalog.media[exercise.id]?.sourceId?.replace('_', ' ') ?: "").joinToString(" "))
    return words.all { it in text }
}
