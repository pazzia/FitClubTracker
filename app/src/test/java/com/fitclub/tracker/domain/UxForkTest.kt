package com.fitclub.tracker.domain

import com.fitclub.tracker.ui.startup.ReleaseNotes
import com.fitclub.tracker.ui.startup.starterProgram
import org.junit.Assert.*
import org.junit.Test

class UxForkTest {
    @Test fun platesAreCountedPerSideHeaviestFirst() {
        assertEquals(listOf(25f, 10f, 2.5f), platesPerSide(95f))
        assertEquals(emptyList<Float>(), platesPerSide(20f))
        assertNull(platesPerSide(15f))
        assertNull(platesPerSide(21f))
        assertEquals(listOf(15f, 1.25f), platesPerSide(52.5f))
        assertEquals("Гриф 20 + на сторону 25 + 10 + 2,5", plateHint(95f))
        assertEquals("Пустой гриф 20 кг", plateHint(20f))
        assertEquals("Гриф 10 + на сторону 5", plateHint(20f, barKg = 10f))
        assertEquals(20f, barFor("Штанга"))
        assertNull(barFor("Гантели"))
    }

    @Test fun releaseNotesShowOnlyWhatIsNewSinceTheLastSeenVersion() {
        assertTrue(ReleaseNotes.newer("0.10.0", "0.9.6"))
        assertFalse(ReleaseNotes.newer("0.9.6-test", "0.9.6"))
        assertEquals(listOf("0.10.0"), ReleaseNotes.since("0.9.6-test").map { it.first })
        assertEquals(listOf("0.10.0", "0.9.6", "0.9.5"), ReleaseNotes.since("0.9.4").map { it.first })
        assertEquals(1, ReleaseNotes.since(null).size)
    }

    @Test fun starterProgramMatchesTheWeeklyFrequency() {
        assertEquals(2, starterProgram(2).freqPerWeek)
        assertEquals(2, starterProgram(2).days.size)
        assertEquals("Фулбоди 3×", starterProgram(3).name)
        assertEquals(4, starterProgram(4).freqPerWeek)
        assertTrue(listOf(2, 3, 4).all { starterProgram(it).isActive })
    }
}
