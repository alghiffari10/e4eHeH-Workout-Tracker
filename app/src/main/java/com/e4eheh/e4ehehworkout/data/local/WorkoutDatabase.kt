package com.e4eheh.e4ehehworkout.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/** Offline-first local Room database for e4eHeH Workout. */
@Database(
    entities = [ExerciseEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao

    companion object {
        const val DATABASE_NAME = "e4eheh_workout.db"
    }
}
