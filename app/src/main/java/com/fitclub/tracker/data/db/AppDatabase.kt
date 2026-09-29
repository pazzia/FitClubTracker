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
}

@Database(
    entities = [
        ExerciseEntity::class, ProgramEntity::class, ProgramDayEntity::class, ProgramExerciseEntity::class,
        BodyMeasurementEntity::class, WorkoutSessionEntity::class, SetLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun programDao(): ProgramDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "fitclub.db").build()
    }
}
