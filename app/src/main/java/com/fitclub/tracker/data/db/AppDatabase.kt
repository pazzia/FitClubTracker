package com.fitclub.tracker.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fitclub.tracker.data.model.Muscle
import kotlinx.coroutines.flow.Flow

class Converters {
    @TypeConverter
    fun musclesToString(value: List<Muscle>): String = value.joinToString(",") { it.name }

    @TypeConverter
    fun stringToMuscles(value: String): List<Muscle> =
        if (value.isBlank()) emptyList() else value.split(",").map { Muscle.valueOf(it) }

    @TypeConverter
    fun alternativesToString(value: List<Alternative>): String =
        value.joinToString(RECORD) { it.name + UNIT + it.why }

    @TypeConverter
    fun stringToAlternatives(value: String): List<Alternative> =
        if (value.isBlank()) emptyList() else value.split(RECORD).map {
            val parts = it.split(UNIT)
            Alternative(parts[0], parts.getOrElse(1) { "" })
        }

    private companion object {
        const val RECORD = "\u001E"
        const val UNIT = "\u001F"
    }
}

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observe(id: String): Flow<ExerciseEntity?>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(items: List<ExerciseEntity>)
}

@Dao
interface ProgramDao {
    @Transaction
    @Query("SELECT * FROM programs ORDER BY createdAt")
    fun observeAll(): Flow<List<ProgramWithDays>>

    @Transaction
    @Query("SELECT * FROM programs WHERE id = :id")
    suspend fun get(id: Long): ProgramWithDays?

    @Query("SELECT COUNT(*) FROM programs")
    suspend fun count(): Int

    @Insert
    suspend fun insertProgram(program: ProgramEntity): Long

    @Update
    suspend fun updateProgram(program: ProgramEntity)

    @Query("DELETE FROM programs WHERE id = :id")
    suspend fun deleteProgram(id: Long)

    @Query("DELETE FROM program_days WHERE programId = :programId")
    suspend fun deleteDays(programId: Long)

    @Insert
    suspend fun insertDay(day: ProgramDayEntity): Long

    @Insert
    suspend fun insertItems(items: List<ProgramExerciseEntity>)

    @Query("UPDATE programs SET isActive = (id = :id)")
    suspend fun setActive(id: Long)

    @Transaction
    @Query("SELECT * FROM programs WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<ProgramWithDays?>
}

@Dao
interface MeasurementDao {
    @Query("SELECT * FROM body_measurements ORDER BY epochDay")
    fun observeAll(): Flow<List<BodyMeasurementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: BodyMeasurementEntity)
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM active_workouts") fun observeActive(): Flow<List<ActiveWorkoutEntity>>
    @Query("SELECT * FROM active_workouts WHERE scheduleId = :id") suspend fun getActive(id: Long): ActiveWorkoutEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveActive(item: ActiveWorkoutEntity)
    @Query("DELETE FROM active_workouts WHERE scheduleId = :id") suspend fun deleteActive(id: Long)
    @Query("DELETE FROM exercise_logs WHERE sessionId = :id") suspend fun deleteExercises(id: Long)
    @Query("SELECT * FROM exercise_logs WHERE sessionId = :id ORDER BY position") suspend fun getExercises(id: Long): List<ExerciseLogEntity>

    @Query("SELECT * FROM workout_sessions ORDER BY startedAt DESC")
    fun observeSessions(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM set_logs ORDER BY sessionId DESC, setIndex")
    fun observeSets(): Flow<List<SetLogEntity>>

    @Query("SELECT * FROM exercise_logs ORDER BY sessionId DESC, position")
    fun observeExercises(): Flow<List<ExerciseLogEntity>>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getSession(id: Long): WorkoutSessionEntity?

    @Update
    suspend fun updateSession(item: WorkoutSessionEntity)

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("DELETE FROM set_logs WHERE sessionId = :id")
    suspend fun deleteSets(id: Long)

    @Insert
    suspend fun insertExercises(items: List<ExerciseLogEntity>)

    @Insert
    suspend fun insertSession(item: WorkoutSessionEntity): Long

    @Insert
    suspend fun insertSets(items: List<SetLogEntity>)
}

@Dao
interface ScheduleDao {
    @Transaction
    @Query("SELECT * FROM scheduled_workouts ORDER BY epochDay")
    fun observeAll(): Flow<List<ScheduledWorkoutWithItems>>

    @Transaction
    @Query("SELECT * FROM scheduled_workouts WHERE id = :id")
    fun observe(id: Long): Flow<ScheduledWorkoutWithItems?>

    @Transaction
    @Query("SELECT * FROM scheduled_workouts WHERE epochDay = :epochDay ORDER BY startMinute, id LIMIT 1")
    suspend fun getForDate(epochDay: Long): ScheduledWorkoutWithItems?

    @Transaction
    @Query("SELECT * FROM scheduled_workouts WHERE id = :id")
    suspend fun get(id: Long): ScheduledWorkoutWithItems?

    @Transaction
    @Query("SELECT * FROM scheduled_workouts WHERE epochDay = :epochDay ORDER BY startMinute, id")
    suspend fun getAllForDate(epochDay: Long): List<ScheduledWorkoutWithItems>

    @Insert
    suspend fun insert(workout: ScheduledWorkoutEntity): Long

    @Update
    suspend fun update(workout: ScheduledWorkoutEntity)

    @Query("DELETE FROM scheduled_workouts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM scheduled_exercises WHERE scheduleId = :id")
    suspend fun deleteItems(id: Long)

    @Insert
    suspend fun insertItems(items: List<ScheduledExerciseEntity>)
}

@Database(
    entities = [
        ExerciseEntity::class, ProgramEntity::class, ProgramDayEntity::class, ProgramExerciseEntity::class,
        BodyMeasurementEntity::class, WorkoutSessionEntity::class, SetLogEntity::class,
        ScheduledWorkoutEntity::class, ScheduledExerciseEntity::class, ExerciseLogEntity::class, ActiveWorkoutEntity::class
    ],
    version = 9,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun programDao(): ProgramDao
    abstract fun measurementDao(): MeasurementDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) = Unit
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN scheduleId INTEGER")
                db.execSQL("CREATE TABLE IF NOT EXISTS scheduled_workouts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, epochDay INTEGER NOT NULL, title TEXT NOT NULL, startMinute INTEGER NOT NULL, programId INTEGER, dayPosition INTEGER)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_scheduled_workouts_epochDay ON scheduled_workouts (epochDay)")
                db.execSQL("CREATE TABLE IF NOT EXISTS scheduled_exercises (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, scheduleId INTEGER NOT NULL, exerciseId TEXT NOT NULL, position INTEGER NOT NULL, sets INTEGER NOT NULL, reps TEXT NOT NULL, restSec INTEGER NOT NULL, FOREIGN KEY(scheduleId) REFERENCES scheduled_workouts(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_scheduled_exercises_scheduleId ON scheduled_exercises (scheduleId)")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE programs ADD COLUMN coverPhoto TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE program_exercises ADD COLUMN comment TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE scheduled_exercises ADD COLUMN comment TEXT NOT NULL DEFAULT ''")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE set_logs ADD COLUMN isTimed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS exercise_logs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sessionId INTEGER NOT NULL, exerciseId TEXT NOT NULL, position INTEGER NOT NULL, durationMillis INTEGER NOT NULL, completed INTEGER NOT NULL, FOREIGN KEY(sessionId) REFERENCES workout_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_logs_sessionId ON exercise_logs (sessionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_logs_exerciseId ON exercise_logs (exerciseId)")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_scheduled_workouts_epochDay")
                db.execSQL("CREATE INDEX index_scheduled_workouts_epochDay ON scheduled_workouts (epochDay)")
            }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN title TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN plannedEpochDay INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN planSnapshot TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN durationMillis INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE set_logs ADD COLUMN exercisePosition INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE set_logs ADD COLUMN warmup INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE set_logs ADD COLUMN rir INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE set_logs ADD COLUMN assisted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE active_workouts (scheduleId INTEGER NOT NULL PRIMARY KEY, title TEXT NOT NULL, snapshot TEXT NOT NULL)")
                db.execSQL("UPDATE workout_sessions SET title = COALESCE((SELECT title FROM scheduled_workouts WHERE id = scheduleId), '')")
                // Older sets cannot be unambiguously split between duplicate blocks; preserve as legacy (-1).
            }
        }
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (table in listOf("program_exercises", "scheduled_exercises")) {
                    db.execSQL("ALTER TABLE $table ADD COLUMN technique TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE $table ADD COLUMN techniqueScope TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE $table ADD COLUMN linkNext INTEGER NOT NULL DEFAULT 0")
                }
                db.execSQL("ALTER TABLE set_logs ADD COLUMN technique TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE set_logs ADD COLUMN stages TEXT NOT NULL DEFAULT ''")
            }
        }
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (table in listOf("program_exercises", "scheduled_exercises")) {
                    db.execSQL("ALTER TABLE $table ADD COLUMN superset INTEGER NOT NULL DEFAULT 0")
                }
            }
        }
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "fitclub.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .build()
    }
}

