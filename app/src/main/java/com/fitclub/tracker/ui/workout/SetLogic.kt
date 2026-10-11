package com.fitclub.tracker.ui.workout

import com.fitclub.tracker.domain.Technique
import com.fitclub.tracker.domain.decimal
import kotlin.math.round

/** Default drop-set step: each stage 20% lighter than the previous one. */
const val DEFAULT_DROP_PERCENT = 20

/** Drop-set step choices offered on the set card. */
val DROP_PERCENTS = listOf(10, 20, 30)

/** A set with a superset partner logs a second set of that exercise. */
internal fun hasSupersetPartner(row: SetEntry): Boolean =
    !row.warmup && Technique.of(row.technique) == Technique.SUPERSET && row.partner.isNotBlank() && (row.partnerReps.toIntOrNull() ?: 0) > 0

private fun parse(text: String) = text.replace(',', '.').toFloatOrNull()

/**
 * Drop-set stages after the first one: each [percent] lighter than the previous, rounded to [step].
 * The first stage is the set's own weight and reps.
 */
internal fun dropStages(weight: String, reps: String, percent: Int, count: Int, step: Float): List<Pair<String, String>> {
    var current = parse(weight)
    return List(count) {
        current = current?.let { (round(it * (100 - percent) / 100f / step) * step).coerceAtLeast(0f) }
        (current?.let(::decimal) ?: "") to reps
    }
}

/** Rest-pause mini-sets: the same weight, about a third of the reps. */
internal fun restPauseStages(weight: String, reps: String, count: Int): List<Pair<String, String>> =
    List(count) { weight to ((reps.toIntOrNull() ?: 6) / 3).coerceAtLeast(1).toString() }

/**
 * A row after choosing how to perform it. Choosing a drop set or rest-pause fills in two stages right away,
 * so the card shows what to do instead of an empty "add stage" button.
 */
internal fun withTechnique(row: SetEntry, warmup: Boolean, technique: Technique, step: Float): SetEntry {
    if (warmup) return row.copy(warmup = true, technique = "", stages = emptyList(), partner = "", partnerWeight = "", partnerReps = "")
    val changed = Technique.of(row.technique) != technique || row.warmup
    val stages = when {
        !technique.hasStages -> emptyList()
        !changed && row.stages.isNotEmpty() -> row.stages
        technique == Technique.DROP -> dropStages(row.weight, row.reps, DEFAULT_DROP_PERCENT, 2, step)
        else -> restPauseStages(row.weight, row.reps, 2)
    }
    val superset = technique == Technique.SUPERSET
    return row.copy(warmup = false, technique = technique.id, stages = stages,
        dropPercent = if (changed) DEFAULT_DROP_PERCENT else row.dropPercent,
        partner = if (superset) row.partner else "", partnerWeight = if (superset) row.partnerWeight else "",
        partnerReps = if (superset) row.partnerReps else "")
}

/** New weight of the set's main stage; automatic drop stages follow it. */
internal fun withWeight(row: SetEntry, weight: String, step: Float): SetEntry {
    val auto = Technique.of(row.technique) == Technique.DROP && row.dropPercent > 0 && !row.warmup
    return row.copy(weight = weight, edited = true,
        stages = if (auto) dropStages(weight, row.reps, row.dropPercent, row.stages.size, step).mapIndexed { i, s -> s.first to (row.stages.getOrNull(i)?.second ?: s.second) } else row.stages)
}

/** A different drop between stages: recomputes every stage weight from the first. */
internal fun withDropPercent(row: SetEntry, percent: Int, step: Float): SetEntry =
    row.copy(dropPercent = percent, edited = true,
        stages = dropStages(row.weight, row.reps, percent, row.stages.size.coerceAtLeast(1), step).mapIndexed { i, s -> s.first to (row.stages.getOrNull(i)?.second ?: s.second) })

/**
 * After a set is done, the next set starts from the weight just lifted — unless the user already set it by hand.
 * Reps are copied only when the next set has none. A superset partner's weight carries over the same way.
 */
internal fun carryForward(rows: List<SetEntry>, doneIndex: Int, step: Float = 2.5f): List<SetEntry> {
    val done = rows.getOrNull(doneIndex) ?: return rows
    val next = (doneIndex + 1 until rows.size).firstOrNull { !rows[it].done } ?: return rows
    val target = rows[next]
    if (target.edited || done.warmup || done.weight.isBlank()) return rows
    var updated = target.copy(weight = done.weight, reps = target.reps.ifBlank { done.reps })
    if (Technique.of(updated.technique) == Technique.DROP && updated.dropPercent > 0 && updated.stages.isNotEmpty()) {
        updated = updated.copy(stages = dropStages(updated.weight, updated.reps, updated.dropPercent, updated.stages.size, step))
    }
    if (hasSupersetPartner(done) && Technique.of(updated.technique) == Technique.SUPERSET && updated.partner == done.partner) {
        updated = updated.copy(partnerWeight = done.partnerWeight, partnerReps = updated.partnerReps.ifBlank { done.partnerReps })
    }
    return rows.toMutableList().also { it[next] = updated }
}
