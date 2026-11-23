package com.e4eheh.e4ehehworkout.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Offline-first local Room database for e4eHeH Workout. */
@Database(
    entities = [
        ExerciseEntity::class,
        UserPreferencesEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class,
        SessionEntity::class,
        SessionExerciseEntity::class,
        SessionSetEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao

    abstract fun userPreferencesDao(): UserPreferencesDao

    abstract fun routineDao(): RoutineDao

    abstract fun sessionDao(): SessionDao

    companion object {
        const val DATABASE_NAME = "e4eheh_workout.db"

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `user_preferences` (
                        `id` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `weightUnit` TEXT NOT NULL,
                        `distanceUnit` TEXT NOT NULL,
                        `defaultRestTimerSeconds` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `routines` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `discipline` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `deletedAt` INTEGER,
                        `syncStatus` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `routine_exercises` (
                        `id` TEXT NOT NULL,
                        `routineId` TEXT NOT NULL,
                        `exerciseId` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `defaultTargetSets` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `deletedAt` INTEGER,
                        `syncStatus` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_routine_exercises_routineId` ON `routine_exercises` (`routineId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_routine_exercises_exerciseId` ON `routine_exercises` (`exerciseId`)"
                )
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sessions` (
                        `id` TEXT NOT NULL,
                        `routineId` TEXT,
                        `title` TEXT NOT NULL,
                        `discipline` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `startedAt` INTEGER NOT NULL,
                        `completedAt` INTEGER,
                        `durationSeconds` INTEGER NOT NULL,
                        `notes` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `deletedAt` INTEGER,
                        `syncStatus` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sessions_status` ON `sessions` (`status`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sessions_startedAt` ON `sessions` (`startedAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sessions_routineId` ON `sessions` (`routineId`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `session_exercises` (
                        `id` TEXT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `exerciseId` TEXT NOT NULL,
                        `exerciseName` TEXT NOT NULL,
                        `exerciseDiscipline` TEXT NOT NULL,
                        `trackingMode` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `notes` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `deletedAt` INTEGER,
                        `syncStatus` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_session_exercises_sessionId` ON `session_exercises` (`sessionId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_session_exercises_exerciseId` ON `session_exercises` (`exerciseId`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `session_sets` (
                        `id` TEXT NOT NULL,
                        `sessionExerciseId` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `reps` INTEGER,
                        `loadKg` REAL,
                        `durationSeconds` INTEGER,
                        `distanceKm` REAL,
                        `rounds` INTEGER,
                        `isCompleted` INTEGER NOT NULL,
                        `isWarmup` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `deletedAt` INTEGER,
                        `syncStatus` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_session_sets_sessionExerciseId` ON `session_sets` (`sessionExerciseId`)"
                )
            }
        }
    }
}
