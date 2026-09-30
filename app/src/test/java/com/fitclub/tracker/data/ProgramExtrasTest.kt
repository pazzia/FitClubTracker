package com.fitclub.tracker.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fitclub.tracker.data.db.AppDatabase
import com.fitclub.tracker.data.model.RepRange
import com.fitclub.tracker.data.repo.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = android.app.Application::class)
class ProgramExtrasTest {
    @Test fun coverAndCommentsPersistAndScheduleKeepsItsOwnSnapshot() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val programs = ProgramRepository(db)
            val schedules = ScheduleRepository(db)
            val cover = "11111111-1111-1111-1111-111111111111.jpg"
            val comment = "Без рывков\nУзкий хват"
            val id = programs.save(ProgramDraft(name = "Личный план", coverPhoto = cover,
                days = listOf(DayDraft(listOf(ItemDraft("bench", 3, RepRange.R8_12, 90, comment))))))
            val restored = programs.get(id)!!
            assertEquals(cover, restored.coverPhoto)
            assertEquals(comment, restored.days.single().items.single().comment)
            val draft = ScheduledDraft(epochDay = 20001).fromProgram(restored, 0)
            schedules.save(draft)
            programs.save(restored.copy(days = listOf(DayDraft(listOf(restored.days.single().items.single().copy(comment = "Другой"))))))
            val plan = schedules.getForDate(20001)!!
            assertEquals(comment, plan.items.single().comment)
            schedules.save(plan.copy(items = plan.items.map { it.copy(comment = "Только для этой даты") }))
            assertEquals("Только для этой даты", schedules.getForDate(20001)!!.items.single().comment)
            assertEquals("Другой", programs.get(id)!!.days.single().items.single().comment)
        } finally { db.close() }
    }
}
