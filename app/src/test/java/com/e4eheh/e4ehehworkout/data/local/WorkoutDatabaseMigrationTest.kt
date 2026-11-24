package com.e4eheh.e4ehehworkout.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.e4eheh.e4ehehworkout.data.repository.DefaultBodyweightRepository
import com.e4eheh.e4ehehworkout.data.repository.DefaultExerciseRepository
import com.e4eheh.e4ehehworkout.data.repository.DefaultRoutineRepository
import com.e4eheh.e4ehehworkout.data.repository.DefaultSessionRepository
import com.e4eheh.e4ehehworkout.data.repository.DefaultUserPreferencesRepository
import com.e4eheh.e4ehehworkout.domain.model.BodyweightEntryInput
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.DistanceUnit
import com.e4eheh.e4ehehworkout.domain.model.RoutineExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.RoutineInput
import com.e4eheh.e4ehehworkout.domain.model.SessionDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionExerciseDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionSetDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionStatus
import com.e4eheh.e4ehehworkout.domain.model.SyncStatus
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import com.e4eheh.e4ehehworkout.domain.model.UserPreferences
import com.e4eheh.e4ehehworkout.domain.model.WeightUnit
import com.e4eheh.e4ehehworkout.testing.FakeClock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutDatabaseMigrationTest {
    @Test
    fun migrate1To4_preservesExercises_andPersistsUserPreferencesAcrossReopen() {
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
                .addMigrations(*WorkoutDatabase.ALL_MIGRATIONS)
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
                .addMigrations(*WorkoutDatabase.ALL_MIGRATIONS)
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

    @Test
    fun migrate2To4_preservesExercisesAndPreferences_andPersistsRoutinesAcrossReopen() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_2_3_test_${UUID.randomUUID()}.db"
        val dbFile = context.getDatabasePath(dbName)
        val clock = FakeClock()
        val exercise1Id = "00000000-0000-4000-8000-000000000011"
        val exercise2Id = "00000000-0000-4000-8000-000000000012"

        try {
            context.deleteDatabase(dbName)
            dbFile.parentFile?.mkdirs()

            val v2Db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
            try {
                v2Db.execSQL(
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
                v2Db.execSQL(
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
                v2Db.execSQL(
                    "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)"
                )
                v2Db.execSQL(
                    "INSERT OR REPLACE INTO room_master_table (id,identity_hash) " +
                        "VALUES(42, 'ace9b6b77d0a6d8cc18f02aa386fb85d')"
                )
                v2Db.execSQL(
                    """
                    INSERT INTO `exercises` (
                        `id`, `name`, `discipline`, `trackingMode`, `defaultNotes`,
                        `createdAt`, `updatedAt`, `deletedAt`, `syncStatus`
                    ) VALUES
                    ('$exercise1Id', 'Archer Push-up', 'CALISTHENICS', 'REPS_AND_LOAD', NULL, 1700000000000, 1700000000000, NULL, 'SYNCED'),
                    ('$exercise2Id', 'EMOM Circuit', 'CONDITIONING', 'CONDITIONING_ROUNDS', NULL, 1700000001000, 1700000001000, NULL, 'PENDING')
                    """.trimIndent()
                )
                v2Db.execSQL(
                    """
                    INSERT INTO `user_preferences` (
                        `id`, `displayName`, `weightUnit`, `distanceUnit`, `defaultRestTimerSeconds`, `updatedAt`
                    ) VALUES (
                        '${UserPreferencesEntity.SINGLETON_ID}', 'Oracle', 'LB', 'MI', 120, 1700000002000
                    )
                    """.trimIndent()
                )
                v2Db.version = 2
            } finally {
                v2Db.close()
            }

            val createdRoutineId: String
            val createdRoutineExerciseIds: List<String>

            val migratedDb = Room.databaseBuilder(context, WorkoutDatabase::class.java, dbName)
                .addMigrations(*WorkoutDatabase.ALL_MIGRATIONS)
                .allowMainThreadQueries()
                .build()
            try {
                runBlocking {
                    val exerciseRepo = DefaultExerciseRepository(migratedDb.exerciseDao(), clock)
                    assertEquals("Archer Push-up", exerciseRepo.getById(exercise1Id)?.name)
                    assertEquals("EMOM Circuit", exerciseRepo.getById(exercise2Id)?.name)

                    val prefs = DefaultUserPreferencesRepository(
                        migratedDb.userPreferencesDao(),
                        clock,
                    ).getPreferences()
                    assertEquals(
                        UserPreferences(
                            displayName = "Oracle",
                            weightUnit = WeightUnit.LB,
                            distanceUnit = DistanceUnit.MI,
                            defaultRestTimerSeconds = 120,
                        ),
                        prefs,
                    )

                    val routineRepo = DefaultRoutineRepository(migratedDb.routineDao(), clock)
                    val created = routineRepo.create(
                        RoutineInput(
                            name = "Calisthenics Push",
                            discipline = Discipline.CALISTHENICS,
                            exercises = listOf(
                                RoutineExerciseInput(exerciseId = exercise1Id, defaultTargetSets = 4),
                                RoutineExerciseInput(exerciseId = exercise2Id, defaultTargetSets = null),
                            ),
                        )
                    )
                    createdRoutineId = created.id
                    createdRoutineExerciseIds = created.exercises.map { it.id }
                }
            } finally {
                migratedDb.close()
            }

            val reopenedDb = Room.databaseBuilder(context, WorkoutDatabase::class.java, dbName)
                .addMigrations(*WorkoutDatabase.ALL_MIGRATIONS)
                .allowMainThreadQueries()
                .build()
            try {
                runBlocking {
                    val reloaded = DefaultRoutineRepository(reopenedDb.routineDao(), clock)
                        .getById(createdRoutineId)
                    assertNotNull(reloaded)
                    assertEquals(createdRoutineId, reloaded!!.id)
                    assertEquals("Calisthenics Push", reloaded.name)
                    assertEquals(Discipline.CALISTHENICS, reloaded.discipline)
                    assertEquals(SyncStatus.PENDING, reloaded.syncStatus)
                    assertEquals(createdRoutineExerciseIds, reloaded.exercises.map { it.id })
                    assertEquals(listOf(exercise1Id, exercise2Id), reloaded.exercises.map { it.exerciseId })
                    assertEquals(listOf(0, 1), reloaded.exercises.map { it.sortOrder })
                    assertEquals(listOf(4, null), reloaded.exercises.map { it.defaultTargetSets })
                    assertEquals(listOf(SyncStatus.PENDING, SyncStatus.PENDING), reloaded.exercises.map { it.syncStatus })
                }
            } finally {
                reopenedDb.close()
            }
        } finally {
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun migrate3To4_preservesRoutinesExercisesAndPreferences_andPersistsSessionsAcrossReopen() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_3_4_test_${UUID.randomUUID()}.db"
        val dbFile = context.getDatabasePath(dbName)
        val clock = FakeClock()
        val exerciseId = "00000000-0000-4000-8000-000000000021"
        val routineId = "00000000-0000-4000-8000-000000000022"
        val routineEntryId = "00000000-0000-4000-8000-000000000023"

        try {
            context.deleteDatabase(dbName)
            dbFile.parentFile?.mkdirs()

            val v3Db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
            try {
                v3Db.execSQL(
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
                v3Db.execSQL(
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
                v3Db.execSQL(
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
                v3Db.execSQL(
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
                v3Db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_routine_exercises_routineId` ON `routine_exercises` (`routineId`)"
                )
                v3Db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_routine_exercises_exerciseId` ON `routine_exercises` (`exerciseId`)"
                )
                v3Db.execSQL(
                    "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)"
                )
                v3Db.execSQL(
                    "INSERT OR REPLACE INTO room_master_table (id,identity_hash) " +
                        "VALUES(42, '67a4e61bfafce790eef38d5e158ac885')"
                )
                v3Db.execSQL(
                    """
                    INSERT INTO `exercises` (
                        `id`, `name`, `discipline`, `trackingMode`, `defaultNotes`,
                        `createdAt`, `updatedAt`, `deletedAt`, `syncStatus`
                    ) VALUES (
                        '$exerciseId', 'Weighted Pull-up', 'CALISTHENICS', 'REPS_AND_LOAD', NULL,
                        1700000000000, 1700000000000, NULL, 'SYNCED'
                    )
                    """.trimIndent()
                )
                v3Db.execSQL(
                    """
                    INSERT INTO `user_preferences` (
                        `id`, `displayName`, `weightUnit`, `distanceUnit`, `defaultRestTimerSeconds`, `updatedAt`
                    ) VALUES (
                        '${UserPreferencesEntity.SINGLETON_ID}', 'Oracle', 'LB', 'MI', 90, 1700000001000
                    )
                    """.trimIndent()
                )
                v3Db.execSQL(
                    """
                    INSERT INTO `routines` (
                        `id`, `name`, `discipline`, `createdAt`, `updatedAt`, `deletedAt`, `syncStatus`
                    ) VALUES (
                        '$routineId', 'Pull Block', 'CALISTHENICS', 1700000002000, 1700000002000, NULL, 'SYNCED'
                    )
                    """.trimIndent()
                )
                v3Db.execSQL(
                    """
                    INSERT INTO `routine_exercises` (
                        `id`, `routineId`, `exerciseId`, `sortOrder`, `defaultTargetSets`,
                        `createdAt`, `updatedAt`, `deletedAt`, `syncStatus`
                    ) VALUES (
                        '$routineEntryId', '$routineId', '$exerciseId', 0, 3,
                        1700000002000, 1700000002000, NULL, 'SYNCED'
                    )
                    """.trimIndent()
                )
                v3Db.version = 3
            } finally {
                v3Db.close()
            }

            val createdSessionId: String
            val migratedDb = Room.databaseBuilder(context, WorkoutDatabase::class.java, dbName)
                .addMigrations(*WorkoutDatabase.ALL_MIGRATIONS)
                .allowMainThreadQueries()
                .build()
            try {
                runBlocking {
                    val routineRepo = DefaultRoutineRepository(migratedDb.routineDao(), clock)
                    val preservedRoutine = routineRepo.getById(routineId)
                    assertNotNull(preservedRoutine)
                    assertEquals("Pull Block", preservedRoutine!!.name)
                    assertEquals(exerciseId, preservedRoutine.exercises.single().exerciseId)

                    val sessionRepo = DefaultSessionRepository(migratedDb.sessionDao(), clock)
                    val started = sessionRepo.createLiveBlankSession(
                        discipline = Discipline.CALISTHENICS,
                        title = "Migrated Session",
                    )
                    createdSessionId = started.id
                    clock.advanceBy(Duration.ofMinutes(20))
                    sessionRepo.completeSession(
                        id = started.id,
                        input = SessionDraftInput(
                            title = "Migrated Session",
                            discipline = Discipline.CALISTHENICS,
                            notes = "Solid sets",
                            exercises = listOf(
                                SessionExerciseDraftInput(
                                    exerciseId = exerciseId,
                                    exerciseName = "Weighted Pull-up",
                                    exerciseDiscipline = Discipline.CALISTHENICS,
                                    trackingMode = TrackingMode.REPS_AND_LOAD,
                                    notes = "Clean pause",
                                    sets = listOf(
                                        SessionSetDraftInput(
                                            reps = 8,
                                            loadKg = 10.0,
                                            isCompleted = true,
                                            isWarmup = false,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    )
                }
            } finally {
                migratedDb.close()
            }

            val reopenedDb = Room.databaseBuilder(context, WorkoutDatabase::class.java, dbName)
                .addMigrations(*WorkoutDatabase.ALL_MIGRATIONS)
                .allowMainThreadQueries()
                .build()
            try {
                runBlocking {
                    val reloaded = DefaultSessionRepository(reopenedDb.sessionDao(), clock)
                        .getById(createdSessionId)
                    assertNotNull(reloaded)
                    assertEquals(SessionStatus.COMPLETED, reloaded!!.status)
                    assertEquals("Migrated Session", reloaded.title)
                    assertEquals(1200L, reloaded.durationSeconds)
                    assertEquals("Solid sets", reloaded.notes)
                    assertEquals(SyncStatus.PENDING, reloaded.syncStatus)
                    val ex = reloaded.exercises.single()
                    assertEquals("Weighted Pull-up", ex.exerciseName)
                    assertEquals("Clean pause", ex.notes)
                    val set = ex.sets.single()
                    assertEquals(8, set.reps)
                    assertEquals(10.0, set.loadKg!!, 1e-6)
                }
            } finally {
                reopenedDb.close()
            }
        } finally {
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun migrate4To5_preservesExistingData_andPersistsBodyweightEntriesAcrossReopen() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_4_5_test_${UUID.randomUUID()}.db"
        val dbFile = context.getDatabasePath(dbName)
        val clock = FakeClock(Instant.parse("2026-11-23T08:00:00Z"))
        val sessionId = "00000000-0000-4000-8000-000000000041"

        try {
            context.deleteDatabase(dbName)
            dbFile.parentFile?.mkdirs()

            val v4Db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
            try {
                v4Db.execSQL(
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
                v4Db.execSQL(
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
                v4Db.execSQL(
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
                v4Db.execSQL(
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
                v4Db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_routine_exercises_routineId` ON `routine_exercises` (`routineId`)"
                )
                v4Db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_routine_exercises_exerciseId` ON `routine_exercises` (`exerciseId`)"
                )
                v4Db.execSQL(
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
                v4Db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sessions_status` ON `sessions` (`status`)"
                )
                v4Db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sessions_startedAt` ON `sessions` (`startedAt`)"
                )
                v4Db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sessions_routineId` ON `sessions` (`routineId`)"
                )
                v4Db.execSQL(
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
                v4Db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_session_exercises_sessionId` ON `session_exercises` (`sessionId`)"
                )
                v4Db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_session_exercises_exerciseId` ON `session_exercises` (`exerciseId`)"
                )
                v4Db.execSQL(
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
                v4Db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_session_sets_sessionExerciseId` ON `session_sets` (`sessionExerciseId`)"
                )
                v4Db.execSQL(
                    "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)"
                )
                v4Db.execSQL(
                    "INSERT OR REPLACE INTO room_master_table (id,identity_hash) " +
                        "VALUES(42, '06158b8353ea91f8e8292ef5ebcf9bde')"
                )
                v4Db.execSQL(
                    """
                    INSERT INTO `sessions` (
                        `id`, `routineId`, `title`, `discipline`, `status`,
                        `startedAt`, `completedAt`, `durationSeconds`, `notes`,
                        `createdAt`, `updatedAt`, `deletedAt`, `syncStatus`
                    ) VALUES (
                        '$sessionId', NULL, 'Morning Push', 'CALISTHENICS', 'COMPLETED',
                        1700000000000, 1700001800000, 1800, NULL,
                        1700000000000, 1700001800000, NULL, 'SYNCED'
                    )
                    """.trimIndent()
                )
                v4Db.version = 4
            } finally {
                v4Db.close()
            }

            val createdEntryId: String
            val migratedDb = Room.databaseBuilder(context, WorkoutDatabase::class.java, dbName)
                .addMigrations(*WorkoutDatabase.ALL_MIGRATIONS)
                .allowMainThreadQueries()
                .build()
            try {
                runBlocking {
                    val preservedSession = DefaultSessionRepository(migratedDb.sessionDao(), clock)
                        .getById(sessionId)
                    assertNotNull(preservedSession)
                    assertEquals("Morning Push", preservedSession!!.title)

                    val bodyweightRepo = DefaultBodyweightRepository(
                        migratedDb.bodyweightEntryDao(),
                        clock,
                    )
                    val created = bodyweightRepo.create(
                        BodyweightEntryInput(
                            recordedAt = Instant.parse("2026-11-22T07:00:00Z"),
                            weightKg = 74.5,
                        ),
                    )
                    createdEntryId = created.id
                }
            } finally {
                migratedDb.close()
            }

            val reopenedDb = Room.databaseBuilder(context, WorkoutDatabase::class.java, dbName)
                .addMigrations(*WorkoutDatabase.ALL_MIGRATIONS)
                .allowMainThreadQueries()
                .build()
            try {
                runBlocking {
                    val reloaded = DefaultBodyweightRepository(
                        reopenedDb.bodyweightEntryDao(),
                        clock,
                    ).getById(createdEntryId)
                    assertNotNull(reloaded)
                    assertEquals(createdEntryId, reloaded!!.id)
                    assertEquals(Instant.parse("2026-11-22T07:00:00Z"), reloaded.recordedAt)
                    assertEquals(74.5, reloaded.weightKg, 1e-9)
                    assertEquals(SyncStatus.PENDING, reloaded.syncStatus)
                }
            } finally {
                reopenedDb.close()
            }
        } finally {
            context.deleteDatabase(dbName)
        }
    }
}
