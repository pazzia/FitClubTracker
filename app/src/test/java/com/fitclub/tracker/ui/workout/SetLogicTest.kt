package com.fitclub.tracker.ui.workout

import com.fitclub.tracker.domain.Technique
import org.junit.Assert.*
import org.junit.Test

class SetLogicTest {
    @Test fun nextSetStartsFromTheWeightJustLifted() {
        val rows = listOf(SetEntry("60", "10", done = true), SetEntry("", "10"), SetEntry("", ""))
        val after = carryForward(rows, 0)
        assertEquals("60", after[1].weight)
        assertEquals("10", after[1].reps)
        // Only the next set changes; the one after waits for its turn.
        assertEquals("", after[2].weight)
    }

    @Test fun historyPrefillIsReplacedButHandEditsAreKept() {
        val prefilled = listOf(SetEntry("62,5", "10", done = true), SetEntry("57,5", "8"))
        assertEquals("62,5", carryForward(prefilled, 0)[1].weight)
        assertEquals("8", carryForward(prefilled, 0)[1].reps)
        val edited = listOf(SetEntry("62,5", "10", done = true), SetEntry("40", "8", edited = true))
        assertEquals("40", carryForward(edited, 0)[1].weight)
        val warmup = listOf(SetEntry("20", "15", done = true, warmup = true), SetEntry("", "8"))
        assertEquals("", carryForward(warmup, 0)[1].weight)
    }

    @Test fun choosingDropSetFillsTwoLighterStagesAtOnce() {
        val row = withTechnique(SetEntry("60", "10"), warmup = false, technique = Technique.DROP, step = 2.5f)
        assertEquals("drop", row.technique)
        assertEquals(listOf("47,5" to "10", "37,5" to "10"), row.stages)
        // The stages follow the first stage while they are automatic…
        val heavier = withWeight(row, "80", 2.5f)
        assertEquals(listOf("65", "52,5"), heavier.stages.map { it.first })
        // …a different drop recomputes them…
        assertEquals(listOf("72,5", "65"), withDropPercent(heavier, 10, 2.5f).stages.map { it.first })
        // …and a stage set by hand stays as typed.
        val manual = heavier.copy(dropPercent = 0, stages = listOf("50" to "10", "30" to "10"))
        assertEquals(listOf("50", "30"), withWeight(manual, "90", 2.5f).stages.map { it.first })
    }

    @Test fun switchingTechniqueKeepsOrClearsWhatBelongsToIt() {
        val drop = withTechnique(SetEntry("60", "9"), false, Technique.DROP, 2.5f)
        assertEquals(drop.stages, withTechnique(drop, false, Technique.DROP, 2.5f).stages)
        assertTrue(withTechnique(drop, false, Technique.NORMAL, 2.5f).stages.isEmpty())
        val rp = withTechnique(SetEntry("60", "9"), false, Technique.REST_PAUSE, 2.5f)
        assertEquals(listOf("60" to "3", "60" to "3"), rp.stages)
        val warm = withTechnique(drop, true, Technique.DROP, 2.5f)
        assertTrue(warm.warmup && warm.technique.isEmpty() && warm.stages.isEmpty())
    }

    @Test fun supersetPartnerIsLoggedOnlyWhenComplete() {
        val base = withTechnique(SetEntry("60", "10"), false, Technique.SUPERSET, 2.5f)
        assertFalse(hasSupersetPartner(base))
        val paired = base.copy(partner = "bbrow", partnerWeight = "50", partnerReps = "12")
        assertTrue(hasSupersetPartner(paired))
        assertTrue(validSet(paired))
        assertFalse(validSet(paired.copy(partnerReps = "")))
        // Leaving the superset drops the partner.
        assertEquals("", withTechnique(paired, false, Technique.NORMAL, 2.5f).partner)
        // The partner's weight carries to the next superset set as well.
        val next = withTechnique(SetEntry("", "10"), false, Technique.SUPERSET, 2.5f).copy(partner = "bbrow")
        val after = carryForward(listOf(paired.copy(done = true), next), 0)
        assertEquals("50", after[1].partnerWeight)
        assertEquals("12", after[1].partnerReps)
    }
}
