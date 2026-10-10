package com.fitclub.tracker.domain

import com.fitclub.tracker.data.repo.ItemDraft

/** Exercises sharing a non-zero [ItemDraft.superset] form a superset, in plan order, wherever they stand. */
fun supersetGroups(items: List<ItemDraft>): List<List<Int>> =
    items.indices.filter { items[it].superset != 0 }
        .groupBy { items[it].superset }.values
        .filter { it.size > 1 }
        .sortedBy { it.first() }

/** "A1", "A2", "B1"… for exercises in supersets. */
fun supersetLabels(items: List<ItemDraft>): Map<Int, String> =
    supersetGroups(items).flatMapIndexed { g, members -> members.mapIndexed { k, i -> i to "${'A' + g}${k + 1}" } }.toMap()

/**
 * Puts [index] into a superset with [partner] (joining the partner's group or starting a new one),
 * or takes it out of its superset when [partner] is null. Groups left with one exercise are dissolved.
 */
fun assignSuperset(items: List<ItemDraft>, index: Int, partner: Int?): List<ItemDraft> {
    if (index !in items.indices || partner == index) return items
    val updated = items.toMutableList()
    if (partner == null || partner !in items.indices) {
        updated[index] = updated[index].copy(superset = 0)
    } else {
        val group = updated[partner].superset.takeIf { it != 0 } ?: ((updated.maxOfOrNull { it.superset } ?: 0) + 1)
        updated[partner] = updated[partner].copy(superset = group)
        updated[index] = updated[index].copy(superset = group)
    }
    val sizes = updated.filter { it.superset != 0 }.groupingBy { it.superset }.eachCount()
    return updated.map { if (it.superset != 0 && (sizes[it.superset] ?: 0) < 2) it.copy(superset = 0) else it }
}
