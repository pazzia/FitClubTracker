package com.fitclub.tracker.data

import com.fitclub.tracker.data.db.SetLogEntity
import com.fitclub.tracker.data.db.WorkoutSessionEntity
import com.fitclub.tracker.data.repo.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutLogTest {
    @Test fun volumeCountsOnlyCompletedSets() {
        val session = WorkoutSessionEntity(1, 1, 0, 1, 1_000, 61_000)
        val sets = listOf(
            SetLogEntity(1, 1, "bench", 0, 50f, 10, true),
            SetLogEntity(2, 1, "bench", 1, 60f, 8, false),
            SetLogEntity(3, 1, "row", 0, 40f, 12, true)
        )
        val log = WorkoutLog(session, sets)
        assertEquals(980f, log.volumeKg)
        assertEquals(2, log.completedSets)
        assertEquals(1, log.durationMin)
    }
}
