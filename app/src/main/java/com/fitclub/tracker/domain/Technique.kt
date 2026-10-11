package com.fitclub.tracker.domain

import com.fitclub.tracker.data.db.SetLogEntity
import com.fitclub.tracker.data.repo.WorkoutLog
import java.util.Locale

/** How a set is performed. Warm-up stays a separate flag because it is excluded from records. */
enum class Technique(val id: String, val title: String, val short: String, val hint: String, val hasStages: Boolean = false) {
    NORMAL("", "Обычный", "—", "Рабочий подход без особой техники"),
    DROP("drop", "Дропсет", "ДС", "До отказа, затем сразу снизить вес и продолжить без отдыха. Например: 60 кг → 48 кг → 38 кг", hasStages = true),
    REST_PAUSE("restpause", "Рест-пауза", "РП", "Паузы 10–20 с внутри подхода, повторы суммируются", hasStages = true),
    SUPERSET("superset", "Суперсет", "СС", "Сразу после этого подхода — подход другого упражнения, отдых после пары"),
    FAILURE("failure", "До отказа", "ДО", "Последний повтор — технический отказ"),
    NEGATIVE("negative", "Негативы", "НГ", "Медленное опускание 3–5 с"),
    PARTIAL("partial", "Частичные повторы", "ЧП", "Дожим в неполной амплитуде после отказа");

    companion object {
        fun of(id: String): Technique = entries.firstOrNull { it.id == id } ?: NORMAL
    }
}

/** Which planned sets get the technique. */
enum class TechniqueScope(val id: String, val title: String) {
    THIS("", "Этот подход"), LAST("last", "Последний"), ALL("all", "Все подходы");
    companion object { fun of(id: String) = entries.firstOrNull { it.id == id } ?: THIS }
}

data class Stage(val weightKg: Float, val reps: Int)

fun parseStages(text: String): List<Stage> = text.split(';').mapNotNull { part ->
    val fields = part.split('×', 'x')
    val weight = fields.getOrNull(0)?.trim()?.toFloatOrNull()
    val reps = fields.getOrNull(1)?.trim()?.toIntOrNull()
    if (weight != null && reps != null && weight.isFinite() && weight >= 0 && reps > 0) Stage(weight, reps) else null
}

fun formatStages(stages: List<Stage>): String =
    stages.joinToString(";") { "${String.format(Locale.US, "%.3f", it.weightKg).trimEnd('0').trimEnd('.')}×${it.reps}" }

/** Volume of a set including its drop-set or rest-pause stages. */
fun setVolumeKg(set: SetLogEntity): Float =
    set.weightKg * set.reps + parseStages(set.stages).sumOf { (it.weightKg * it.reps).toDouble() }.toFloat()

/** Working sets of the latest session with this exercise, in performed order. */
fun lastSessionSets(history: List<WorkoutLog>, exerciseId: String, timed: Boolean): Pair<WorkoutLog, List<SetLogEntity>>? {
    fun matches(row: SetLogEntity) = row.exerciseId == exerciseId && row.isTimed == timed && row.done && !row.warmup && !row.assisted
    val log = history.filter { log -> log.sets.any(::matches) }.maxByOrNull { it.session.startedAt } ?: return null
    return log to log.sets.filter(::matches)
}

/** Epley estimate of the one-repetition maximum. */
fun estimatedOneRepMax(weightKg: Float, reps: Int): Float = if (reps <= 1) weightKg else weightKg * (1 + reps / 30f)
