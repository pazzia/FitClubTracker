package com.fitclub.tracker.data

import com.fitclub.tracker.data.seed.ExerciseSeed
import com.fitclub.tracker.ui.library.ExerciseSearchIndex
import com.fitclub.tracker.ui.library.boundedOsa
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ExerciseSearchIndexTest {
    private val index = ExerciseSearchIndex(ExerciseSeed.all)
    @Test fun normalizesAndRanksNameBeforeMetadata() {
        assertEquals("bench", index.search("  ЖИМ   ЛЕЖА ").first().id)
        assertEquals("bench", index.search("штанг лежа").single().id)
        assertTrue(index.search("жим гант").size > 1)
        assertTrue(index.search("накл жим").isNotEmpty())
        assertTrue(index.search("грудь жим").all { it.group.title == "Грудь" })
        assertEquals(ExerciseSeed.all.size, index.search(" ").size)
    }
    @Test fun correctsSubstitutionAndTranspositionWithoutShortQueryNoise() {
        assertTrue(index.search("жым").any { it.id == "bench" })
        assertTrue(index.search("тгяа").any { "тяга" in it.name.lowercase() })
        assertTrue(index.search("жи").any { it.id == "bench" })
        assertTrue(index.search("zzzzzz").isEmpty())
        assertTrue(index.search("жж").isEmpty())
        assertTrue(index.search("жим ноги zzzzzz").isEmpty())
        assertEquals(index.search("жым"), index.search("жым"))
    }
    @Test fun exactPartialMatchesExcludeFuzzyAlternativesAndAliasesAreSearchable() {
        assertTrue(index.search("планка").all { "планка" in it.name.lowercase() })
        assertTrue(index.search("bench press").any { it.id == "bench" })
    }
    @Test fun boundedDistanceMatchesReferenceIncludingBandEdges() {
        fun reference(a: String, b: String): Int {
            val d = Array(a.length + 1) { IntArray(b.length + 1) }
            for (i in 0..a.length) d[i][0] = i
            for (j in 0..b.length) d[0][j] = j
            for (i in 1..a.length) for (j in 1..b.length) {
                d[i][j] = minOf(d[i-1][j]+1, d[i][j-1]+1, d[i-1][j-1]+if (a[i-1] == b[j-1]) 0 else 1)
                if (i > 1 && j > 1 && a[i-1] == b[j-2] && a[i-2] == b[j-1]) d[i][j] = minOf(d[i][j], d[i-2][j-2]+1)
            }
            return d[a.length][b.length]
        }
        val random = Random(81)
        repeat(1000) {
            fun word() = (0 until random.nextInt(0, 12)).map { "абвгдеж"[random.nextInt(7)] }.joinToString("")
            val a = word(); val b = word()
            for (limit in 1..2) assertEquals("$a / $b / $limit", minOf(reference(a,b), limit+1), boundedOsa(a,b,limit))
        }
        assertEquals(1, boundedOsa("тяга", "тгяа", 1))
    }
}
