package com.e4eheh.e4ehehworkout.di

import android.content.Context
import androidx.room.Room
import com.e4eheh.e4ehehworkout.data.local.WorkoutDatabase
import com.e4eheh.e4ehehworkout.data.repository.DefaultExerciseRepository
import com.e4eheh.e4ehehworkout.domain.repository.ExerciseRepository
import java.time.Clock

/** Manual dependency graph for production and in-memory test environments. */
class AppContainer private constructor(
    val clock: Clock,
    private val database: WorkoutDatabase,
    val exerciseRepository: ExerciseRepository,
) {
    fun close() {
        database.close()
    }

    companion object {
        fun create(
            context: Context,
            clock: Clock = Clock.systemDefaultZone(),
        ): AppContainer {
            val database = Room.databaseBuilder(
                context.applicationContext,
                WorkoutDatabase::class.java,
                WorkoutDatabase.DATABASE_NAME,
            ).build()
            return buildContainer(database, clock)
        }

        fun createInMemory(
            context: Context,
            clock: Clock = Clock.systemUTC(),
        ): AppContainer {
            val database = Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                WorkoutDatabase::class.java,
            )
                .allowMainThreadQueries()
                .build()
            return buildContainer(database, clock)
        }

        private fun buildContainer(
            database: WorkoutDatabase,
            clock: Clock,
        ): AppContainer = AppContainer(
            clock = clock,
            database = database,
            exerciseRepository = DefaultExerciseRepository(
                exerciseDao = database.exerciseDao(),
                clock = clock,
            ),
        )
    }
}
