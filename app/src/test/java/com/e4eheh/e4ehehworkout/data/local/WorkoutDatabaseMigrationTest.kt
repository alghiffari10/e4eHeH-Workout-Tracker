package com.e4eheh.e4ehehworkout.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.e4eheh.e4ehehworkout.data.repository.DefaultExerciseRepository
import com.e4eheh.e4ehehworkout.data.repository.DefaultUserPreferencesRepository
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.DistanceUnit
import com.e4eheh.e4ehehworkout.domain.model.SyncStatus
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import com.e4eheh.e4ehehworkout.domain.model.UserPreferences
import com.e4eheh.e4ehehworkout.domain.model.WeightUnit
import com.e4eheh.e4ehehworkout.testing.FakeClock
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutDatabaseMigrationTest {
    @Test
    fun migrate1To2_preservesExercises_andPersistsUserPreferencesAcrossReopen() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_test_${UUID.randomUUID()}.db"
        val dbFile = context.getDatabasePath(dbName)
        val clock = FakeClock()
        val exerciseId = "00000000-0000-4000-8000-000000000001"

        try {
            context.deleteDatabase(dbName)
            dbFile.parentFile?.mkdirs()

            val v1Db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
            try {
                v1Db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `exercises` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `discipline` TEXT NOT NULL,
                        `trackingMode` TEXT NOT NULL,
                        `defaultNotes` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `deletedAt` INTEGER,
                        `syncStatus` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                v1Db.execSQL(
                    "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)"
                )
                v1Db.execSQL(
                    "INSERT OR REPLACE INTO room_master_table (id,identity_hash) " +
                        "VALUES(42, '192627b8ae17a7300b30d8cd767e0f41')"
                )
                v1Db.execSQL(
                    """
                    INSERT INTO `exercises` (
                        `id`, `name`, `discipline`, `trackingMode`, `defaultNotes`,
                        `createdAt`, `updatedAt`, `deletedAt`, `syncStatus`
                    ) VALUES (
                        '$exerciseId', 'Pull-up', 'CALISTHENICS', 'REPS_AND_LOAD',
                        'Chest to bar', 1700000000000, 1700000000000, NULL, 'PENDING'
                    )
                    """.trimIndent()
                )
                v1Db.version = 1
            } finally {
                v1Db.close()
            }

            val migratedDb = Room.databaseBuilder(context, WorkoutDatabase::class.java, dbName)
                .addMigrations(WorkoutDatabase.MIGRATION_1_2)
                .allowMainThreadQueries()
                .build()
            try {
                runBlocking {
                    val exercise = DefaultExerciseRepository(migratedDb.exerciseDao(), clock)
                        .getById(exerciseId)
                    assertNotNull(exercise)
                    assertEquals("Pull-up", exercise!!.name)
                    assertEquals(Discipline.CALISTHENICS, exercise.discipline)
                    assertEquals(TrackingMode.REPS_AND_LOAD, exercise.trackingMode)
                    assertEquals("Chest to bar", exercise.defaultNotes)
                    assertEquals(SyncStatus.PENDING, exercise.syncStatus)

                    val prefsRepo = DefaultUserPreferencesRepository(
                        migratedDb.userPreferencesDao(),
                        clock,
                    )
                    assertEquals(UserPreferences(), prefsRepo.getPreferences())
                    prefsRepo.updateDisplayName("Nightwing")
                    prefsRepo.updateWeightUnit(WeightUnit.LB)
                    prefsRepo.updateDistanceUnit(DistanceUnit.MI)
                    prefsRepo.updateDefaultRestTimerSeconds(120)
                }
            } finally {
                migratedDb.close()
            }

            val reopenedDb = Room.databaseBuilder(context, WorkoutDatabase::class.java, dbName)
                .addMigrations(WorkoutDatabase.MIGRATION_1_2)
                .allowMainThreadQueries()
                .build()
            try {
                runBlocking {
                    val reloaded = DefaultUserPreferencesRepository(
                        reopenedDb.userPreferencesDao(),
                        clock,
                    ).getPreferences()
                    assertEquals(
                        UserPreferences(
                            displayName = "Nightwing",
                            weightUnit = WeightUnit.LB,
                            distanceUnit = DistanceUnit.MI,
                            defaultRestTimerSeconds = 120,
                        ),
                        reloaded,
                    )
                }
            } finally {
                reopenedDb.close()
            }
        } finally {
            context.deleteDatabase(dbName)
        }
    }
}
