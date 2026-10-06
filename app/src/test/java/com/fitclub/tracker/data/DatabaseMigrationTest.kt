package com.fitclub.tracker.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.fitclub.tracker.data.db.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = android.app.Application::class)
class DatabaseMigrationTest {
    @Test fun migratesVersionOneWithoutLosingHistory() = checkMigration(1)
    @Test fun migratesVersionTwoWithoutLosingHistory() = checkMigration(2)
    @Test fun migratesVersionThreeWithoutLosingPlans() = checkMigration(3)
    @Test fun migratesVersionFourWithoutLosingHistory() = checkMigration(4)
    @Test fun migratesVersionFiveAndAllowsSecondPlan() = checkMigration(5)
    @Test fun migratesVersionSixWithoutLosingHistory() = checkMigration(6)
    private fun checkMigration(version: Int) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("fitclub.db")
        val old = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("fitclub.db").callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    legacySchema.forEach { db.execSQL(it) }
                    if (version >= 3) AppDatabase.MIGRATION_2_3.migrate(db)
                    db.execSQL("INSERT INTO programs VALUES (1, 'Моя программа', 'MASS', 4292149050, 'target', 3, 1, 1)")
                    db.execSQL("INSERT INTO body_measurements VALUES (1, 20000, 78.4, 82, NULL, NULL, NULL)")
                    db.execSQL("INSERT INTO workout_sessions (id, programId, dayPosition, epochDay, startedAt, finishedAt) VALUES (1, 1, 0, 20000, 1000, 61000)")
                    db.execSQL("INSERT INTO set_logs VALUES (1, 1, 'bench', 0, 60, 8, 1)")
                    db.execSQL("INSERT INTO program_days VALUES (1, 1, 0)")
                    db.execSQL("INSERT INTO program_exercises VALUES (1, 1, 'bench', 0, 3, 'R8_12', 90)")
                    if (version >= 3) {
                        db.execSQL("INSERT INTO scheduled_workouts VALUES (1, 20001, 'Мой план', 1080, 1, 0)")
                        db.execSQL("INSERT INTO scheduled_exercises VALUES (1, 1, 'bench', 0, 3, 'R8_12', 90)")
                    }
                    if (version >= 4) AppDatabase.MIGRATION_3_4.migrate(db)
                    if (version >= 5) AppDatabase.MIGRATION_4_5.migrate(db)
                    if (version >= 6) AppDatabase.MIGRATION_5_6.migrate(db)
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        old.writableDatabase
        old.close()
        val current = AppDatabase.build(context)
        try {
            current.openHelper.writableDatabase
            val program = current.programDao().get(1)!!
            assertEquals("Моя программа", program.program.name)
            assertNull(program.program.coverPhoto)
            assertEquals("", program.days.single().items.single().comment)
            assertEquals(3, program.days.single().items.single().sets)
            assertEquals(78.4f, current.measurementDao().observeAll().first().single().weightKg!!)
            val session = current.workoutDao().observeSessions().first().single()
            assertEquals(20000L, session.epochDay)
            assertNull(session.scheduleId)
            assertEquals(8, current.workoutDao().observeSets().first().single().reps)
            assertFalse(current.workoutDao().observeSets().first().single().isTimed)
            assertTrue(current.workoutDao().observeExercises().first().isEmpty())
            current.scheduleDao().insert(com.fitclub.tracker.data.db.ScheduledWorkoutEntity(epochDay = 20001, title = "Вторая", startMinute = 1200, programId = null, dayPosition = null))
            val plans = current.scheduleDao().observeAll().first()
            if (version >= 3) {
                assertEquals("Мой план", plans.first().workout.title)
                assertEquals("", plans.first().items.single().comment)
            } else assertEquals(1, plans.size)
        } finally { current.close(); context.deleteDatabase("fitclub.db") }
    }
    private val legacySchema = listOf(
        "CREATE TABLE IF NOT EXISTS `exercises` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `group` TEXT NOT NULL, `primary` TEXT NOT NULL, `secondary` TEXT NOT NULL, `type` TEXT NOT NULL, `level` INTEGER NOT NULL, `equipment` TEXT NOT NULL, `technique` TEXT NOT NULL, `impact` TEXT NOT NULL, `alternatives` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `programs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `goal` TEXT NOT NULL, `colorArgb` INTEGER NOT NULL, `icon` TEXT NOT NULL, `freqPerWeek` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS `program_days` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `programId` INTEGER NOT NULL, `position` INTEGER NOT NULL, FOREIGN KEY(`programId`) REFERENCES `programs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_program_days_programId` ON `program_days` (`programId`)",
        "CREATE TABLE IF NOT EXISTS `program_exercises` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dayId` INTEGER NOT NULL, `exerciseId` TEXT NOT NULL, `position` INTEGER NOT NULL, `sets` INTEGER NOT NULL, `reps` TEXT NOT NULL, `restSec` INTEGER NOT NULL, FOREIGN KEY(`dayId`) REFERENCES `program_days`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_program_exercises_dayId` ON `program_exercises` (`dayId`)",
        "CREATE TABLE IF NOT EXISTS `body_measurements` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `epochDay` INTEGER NOT NULL, `weightKg` REAL, `waistCm` REAL, `fatPercent` REAL, `chestCm` REAL, `armCm` REAL)",
        "CREATE TABLE IF NOT EXISTS `workout_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `programId` INTEGER, `dayPosition` INTEGER, `epochDay` INTEGER NOT NULL, `startedAt` INTEGER NOT NULL, `finishedAt` INTEGER)",
        "CREATE TABLE IF NOT EXISTS `set_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `exerciseId` TEXT NOT NULL, `setIndex` INTEGER NOT NULL, `weightKg` REAL NOT NULL, `reps` INTEGER NOT NULL, `done` INTEGER NOT NULL, FOREIGN KEY(`sessionId`) REFERENCES `workout_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_set_logs_sessionId` ON `set_logs` (`sessionId`)",
        "CREATE INDEX IF NOT EXISTS `index_set_logs_exerciseId` ON `set_logs` (`exerciseId`)"
    )
}

