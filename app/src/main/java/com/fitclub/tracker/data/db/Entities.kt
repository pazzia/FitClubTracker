package com.fitclub.tracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.fitclub.tracker.data.model.ExerciseType
import com.fitclub.tracker.data.model.Goal
import com.fitclub.tracker.data.model.Muscle
import com.fitclub.tracker.data.model.MuscleGroup
import com.fitclub.tracker.data.model.RepRange

data class Alternative(val name: String, val why: String)

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val group: MuscleGroup,
    val primary: List<Muscle>,
    val secondary: List<Muscle>,
    val type: ExerciseType,
    val level: Int,
    val equipment: String,
    val technique: String,
    val impact: String,
    val alternatives: List<Alternative>
) {
    /** Оценка сообщества. В MVP — псевдослучайная, но стабильная для каждого упражнения. */
    val communityRating: Float
        get() = 3.8f + (stableHash(name) % 12) / 10f

    val communityVotes: Int
        get() = 40 + stableHash(name) % 460

    private fun stableHash(s: String): Int {
        var h = 7L
        for (c in s) h = (h * 31 + c.code) and 0xFFFFFFFFL
        return (h % Int.MAX_VALUE).toInt()
    }
}

@Entity(tableName = "programs")
data class ProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val goal: Goal,
    val colorArgb: Long,
    val icon: String,
    val freqPerWeek: Int,
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "NULL") val coverPhoto: String? = null
)

@Entity(
    tableName = "program_days",
    foreignKeys = [ForeignKey(
        entity = ProgramEntity::class, parentColumns = ["id"], childColumns = ["programId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("programId")]
)
data class ProgramDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programId: Long,
    val position: Int
)

@Entity(
    tableName = "program_exercises",
    foreignKeys = [ForeignKey(
        entity = ProgramDayEntity::class, parentColumns = ["id"], childColumns = ["dayId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("dayId")]
)
data class ProgramExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,
    val exerciseId: String,
    val position: Int,
    val sets: Int,
    val reps: RepRange,
    val restSec: Int,
    @ColumnInfo(defaultValue = "''") val comment: String = ""
)

data class DayWithItems(
    @Embedded val day: ProgramDayEntity,
    @Relation(parentColumn = "id", entityColumn = "dayId")
    val items: List<ProgramExerciseEntity>
)

data class ProgramWithDays(
    @Embedded val program: ProgramEntity,
    @Relation(entity = ProgramDayEntity::class, parentColumn = "id", entityColumn = "programId")
    val days: List<DayWithItems>
)

/** Замеры тела — используются на следующих этапах (личный кабинет). */
@Entity(tableName = "body_measurements")
data class BodyMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val weightKg: Float?,
    val waistCm: Float?,
    val fatPercent: Float?,
    val chestCm: Float?,
    val armCm: Float?
)

/** Факт тренировки и подходы — используются на этапе 2 (активная тренировка). */
@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programId: Long?,
    val dayPosition: Int?,
    val epochDay: Long,
    val startedAt: Long,
    val finishedAt: Long?,
    val scheduleId: Long? = null,
    @ColumnInfo(defaultValue = "''") val title: String = "",
    @ColumnInfo(defaultValue = "NULL") val plannedEpochDay: Long? = null,
    @ColumnInfo(defaultValue = "''") val planSnapshot: String = "",
    @ColumnInfo(defaultValue = "-1") val durationMillis: Long = -1
)

@Entity(
    tableName = "set_logs",
    foreignKeys = [ForeignKey(
        entity = WorkoutSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId"), Index("exerciseId")]
)
data class SetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: String,
    val setIndex: Int,
    val weightKg: Float,
    val reps: Int,
    val done: Boolean,
    @ColumnInfo(defaultValue = "0") val isTimed: Boolean = false,
    @ColumnInfo(defaultValue = "-1") val exercisePosition: Int = -1,
    @ColumnInfo(defaultValue = "0") val warmup: Boolean = false,
    @ColumnInfo(defaultValue = "NULL") val rir: Int? = null,
    @ColumnInfo(defaultValue = "0") val assisted: Boolean = false
)

/** Снимок тренировки на конкретную дату: изменения программы не меняют этот план. */
@Entity(tableName = "scheduled_workouts", indices = [Index(value = ["epochDay"])])
data class ScheduledWorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val title: String,
    val startMinute: Int,
    val programId: Long?,
    val dayPosition: Int?
)

@Entity(
    tableName = "scheduled_exercises",
    foreignKeys = [ForeignKey(entity = ScheduledWorkoutEntity::class,
        parentColumns = ["id"], childColumns = ["scheduleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("scheduleId")]
)
data class ScheduledExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleId: Long,
    val exerciseId: String,
    val position: Int,
    val sets: Int,
    val reps: RepRange,
    val restSec: Int,
    @ColumnInfo(defaultValue = "''") val comment: String = ""
)

data class ScheduledWorkoutWithItems(
    @Embedded val workout: ScheduledWorkoutEntity,
    @Relation(parentColumn = "id", entityColumn = "scheduleId")
    val items: List<ScheduledExerciseEntity>
)


/** Время и статус каждого упражнения, в том числе при досрочном завершении. */
@Entity(tableName = "exercise_logs",
    foreignKeys = [ForeignKey(entity = WorkoutSessionEntity::class, parentColumns = ["id"],
        childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId"), Index("exerciseId")])
data class ExerciseLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: String,
    val position: Int,
    val durationMillis: Long,
    val completed: Boolean
)

/** Durable draft independent of the plan: recover after process death. */
@Entity(tableName = "active_workouts")
data class ActiveWorkoutEntity(@PrimaryKey val scheduleId: Long, val title: String, val snapshot: String)
