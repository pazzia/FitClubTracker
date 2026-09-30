package com.fitclub.tracker.data

import com.fitclub.tracker.ui.dashboard.parseScheduleTime
import org.junit.Assert.*
import org.junit.Test

class ScheduleTimeTest {
    @Test fun validatesDayTimeBoundaries() {
        assertEquals(0, parseScheduleTime("00:00"))
        assertEquals(1439, parseScheduleTime("23:59"))
        assertEquals(1110, parseScheduleTime("18:30"))
        for (bad in listOf("24:00", "12:60", "-1:30", "18", "", "7:5")) assertNull(parseScheduleTime(bad))
    }
}
