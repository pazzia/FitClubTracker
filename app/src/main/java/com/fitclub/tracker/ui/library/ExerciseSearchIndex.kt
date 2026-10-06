package com.fitclub.tracker.ui.library

import com.fitclub.tracker.data.db.ExerciseEntity
import com.fitclub.tracker.data.seed.FreeExerciseCatalog
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/** Immutable catalog index; one instance per picker. Call search on Dispatchers.Default. */
internal class ExerciseSearchIndex(catalog: List<ExerciseEntity>) {
    private data class Entry(val exercise: ExerciseEntity, val name: String, val words: Map<String, Int>, val order: Int)
    private val entries = catalog.distinctBy { it.id }.mapIndexed { order, e ->
        val words = linkedMapOf<String, Int>()
        fun add(text: String, penalty: Int) = tokens(text).forEach { word ->
            words[word] = min(words[word] ?: Int.MAX_VALUE, penalty)
        }
        add(e.name, 0)
        add(FreeExerciseCatalog.media[e.id]?.sourceId?.replace('_', ' ').orEmpty(), 20)
        add(listOf(e.group.title, e.equipment, e.primary.joinToString(" ") { it.title },
            e.secondary.joinToString(" ") { it.title }).joinToString(" "), 28)
        Entry(e, normalize(e.name), words, order)
    }
    private val vocabulary = entries.flatMap { it.words.keys }.distinct()
    private val cache = object : LinkedHashMap<String, List<ExerciseEntity>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<ExerciseEntity>>?) = size > 60
    }

    @Synchronized fun search(query: String): List<ExerciseEntity> {
        val terms = tokens(query.take(120)).distinct().take(8)
        val key = terms.joinToString(" ")
        cache[key]?.let { return it }
        if (terms.isEmpty()) return entries.map { it.exercise }.also { cache[key] = it }
        val scores = terms.associateWith { term ->
            val literal = vocabulary.mapNotNull { word ->
                val score = when { word == term -> 0; word.startsWith(term) -> 4; term in word -> 12; else -> null }
                score?.let { word to it }
            }.toMap()
            if (literal.isNotEmpty() || term.length < 3) literal else {
                val limit = if (term.length < 6) 1 else 2
                vocabulary.mapNotNull { word ->
                    val lengths = (term.length - limit..min(word.length, term.length + limit)).filter { it >= 1 }
                    val distance = lengths.minOfOrNull { boundedOsa(term, word.take(it), limit) } ?: (limit + 1)
                    if (distance <= limit) word to (40 + distance * 10) else null
                }.toMap()
            }
        }
        val matches = entries.mapNotNull { entry ->
            var total = 0
            for (term in terms) {
                val score = entry.words.minOfOrNull { (word, penalty) ->
                    scores.getValue(term)[word]?.plus(penalty) ?: 10000
                } ?: 10000
                if (score >= 10000) return@mapNotNull null
                total += score
            }
            if (entry.name.startsWith(key)) total -= 6
            Triple(entry.exercise, total, entry.order)
        }.sortedWith(compareBy({ it.second }, { it.third })).map { it.first }
        cache[key] = matches
        return matches
    }

    companion object {
        private val separators = Regex("[^\\p{L}\\p{N}]+")
        private fun normalize(text: String) = text.lowercase(Locale.ROOT).replace('ё', 'е').replace(separators, " ").trim()
        private fun tokens(text: String) = normalize(text).split(' ').filter { it.isNotEmpty() }
    }
}

/** Banded optimal string alignment distance, including adjacent transpositions. */
internal fun boundedOsa(a: String, b: String, limit: Int): Int {
    val infinity = limit + 1
    if (abs(a.length - b.length) > limit) return infinity
    var twoBack = IntArray(b.length + 1) { infinity }
    var previous = IntArray(b.length + 1) { if (it <= limit) it else infinity }
    for (i in 1..a.length) {
        val current = IntArray(b.length + 1) { infinity }
        if (i <= limit) current[0] = i
        for (j in maxOf(1, i - limit)..min(b.length, i + limit)) {
            var value = minOf(previous[j] + 1, current[j - 1] + 1,
                previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                value = min(value, twoBack[j - 2] + 1)
            }
            current[j] = min(value, infinity)
        }
        twoBack = previous
        previous = current
    }
    return previous[b.length]
}
