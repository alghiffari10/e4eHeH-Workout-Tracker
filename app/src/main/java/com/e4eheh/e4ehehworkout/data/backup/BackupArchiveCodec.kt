package com.e4eheh.e4ehehworkout.data.backup

import com.e4eheh.e4ehehworkout.data.local.BodyweightEntryEntity
import com.e4eheh.e4ehehworkout.data.local.ExerciseEntity
import com.e4eheh.e4ehehworkout.data.local.RoutineEntity
import com.e4eheh.e4ehehworkout.data.local.RoutineExerciseEntity
import com.e4eheh.e4ehehworkout.data.local.SessionEntity
import com.e4eheh.e4ehehworkout.data.local.SessionExerciseEntity
import com.e4eheh.e4ehehworkout.data.local.SessionSetEntity
import com.e4eheh.e4ehehworkout.domain.backup.BackupFileNames
import com.e4eheh.e4ehehworkout.domain.backup.BackupSummary
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.RoutineExercise
import com.e4eheh.e4ehehworkout.domain.model.SessionStatus
import com.e4eheh.e4ehehworkout.domain.model.SyncStatus
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import java.time.Instant
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Validated Routine graph ready for atomic Room persistence. */
data class ValidatedRoutineGraph(
    val routine: RoutineEntity,
    val exercises: List<RoutineExerciseEntity>,
)

/** Validated SessionExercise with its nested SessionSet rows. */
data class ValidatedSessionExerciseGraph(
    val sessionExercise: SessionExerciseEntity,
    val sets: List<SessionSetEntity>,
)

/** Validated Session graph ready for atomic Room persistence. */
data class ValidatedSessionGraph(
    val session: SessionEntity,
    val exercises: List<ValidatedSessionExerciseGraph>,
)

/** Validated backup payload ready to be written into Room with SyncStatus.PENDING. */
data class ValidatedBackup(
    val exercises: List<ExerciseEntity>,
    val routines: List<ValidatedRoutineGraph>,
    val sessions: List<ValidatedSessionGraph>,
    val bodyweightEntries: List<BodyweightEntryEntity>,
    val summary: BackupSummary,
)

/** Outcome of decoding and validating a JSON backup string. */
sealed interface BackupDecodeResult {
    data class Success(val backup: ValidatedBackup) : BackupDecodeResult

    data class Failure(val reason: String) : BackupDecodeResult
}

/** Encodes and validates versioned WorkoutBackupArchive JSON documents. */
object BackupArchiveCodec {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    fun encode(archive: WorkoutBackupArchive): String = json.encodeToString(archive)

    fun decodeAndValidate(rawJson: String): BackupDecodeResult {
        if (rawJson.isBlank()) {
            return BackupDecodeResult.Failure("Backup file is empty.")
        }

        val root = try {
            json.parseToJsonElement(rawJson)
        } catch (_: SerializationException) {
            return BackupDecodeResult.Failure("Backup file is not valid JSON.")
        }

        val rootObject = root as? JsonObject
            ?: return BackupDecodeResult.Failure("Backup file must be a JSON object.")

        val versionElement = rootObject["schemaVersion"] as? JsonPrimitive
            ?: return BackupDecodeResult.Failure("Backup schemaVersion is missing or invalid.")
        if (versionElement.isString) {
            return BackupDecodeResult.Failure("Backup schemaVersion must be an integer.")
        }
        val schemaVersion = versionElement.content.toIntOrNull()
            ?: return BackupDecodeResult.Failure("Backup schemaVersion is missing or invalid.")
        if (schemaVersion != BackupFileNames.CURRENT_SCHEMA_VERSION) {
            return BackupDecodeResult.Failure(
                "Unsupported backup schema version $schemaVersion " +
                    "(supported: v${BackupFileNames.CURRENT_SCHEMA_VERSION}).",
            )
        }

        val archive = try {
            json.decodeFromJsonElement(WorkoutBackupArchive.serializer(), rootObject)
        } catch (error: SerializationException) {
            return BackupDecodeResult.Failure(
                "Backup file does not match schema v${BackupFileNames.CURRENT_SCHEMA_VERSION}: " +
                    (error.message ?: "invalid fields"),
            )
        }

        return try {
            BackupDecodeResult.Success(validateAndMap(archive))
        } catch (error: BackupValidationException) {
            BackupDecodeResult.Failure(error.message ?: "Backup validation failed.")
        }
    }

    private fun validateAndMap(archive: WorkoutBackupArchive): ValidatedBackup {
        parseTimestamp(archive.exportedAt, "exportedAt")

        val pendingSync = SyncStatus.PENDING.name
        val exerciseIds = HashSet<String>()
        val routineIds = HashSet<String>()
        val routineExerciseIds = HashSet<String>()
        val sessionIds = HashSet<String>()
        val sessionExerciseIds = HashSet<String>()
        val sessionSetIds = HashSet<String>()
        val bodyweightIds = HashSet<String>()

        val exercises = archive.exercises.map { item ->
            val id = requireUniqueId(item.id, exerciseIds, "Exercise")
            val name = requireNonBlank(item.name, "Exercise '$id' name")
            val discipline = parseEnum<Discipline>(item.discipline, "Exercise '$id' discipline")
            val trackingMode = parseEnum<TrackingMode>(
                item.trackingMode,
                "Exercise '$id' trackingMode",
            )
            ExerciseEntity(
                id = id,
                name = name,
                discipline = discipline.name,
                trackingMode = trackingMode.name,
                defaultNotes = item.defaultNotes,
                createdAt = parseTimestamp(item.createdAt, "Exercise '$id' createdAt"),
                updatedAt = parseTimestamp(item.updatedAt, "Exercise '$id' updatedAt"),
                deletedAt = null,
                syncStatus = pendingSync,
            )
        }

        val routines = archive.routines.map { routine ->
            val routineId = requireUniqueId(routine.id, routineIds, "Routine")
            val name = requireNonBlank(routine.name, "Routine '$routineId' name")
            val discipline = parseEnum<Discipline>(routine.discipline, "Routine '$routineId' discipline")
            val routineEntity = RoutineEntity(
                id = routineId,
                name = name,
                discipline = discipline.name,
                createdAt = parseTimestamp(routine.createdAt, "Routine '$routineId' createdAt"),
                updatedAt = parseTimestamp(routine.updatedAt, "Routine '$routineId' updatedAt"),
                deletedAt = null,
                syncStatus = pendingSync,
            )
            val routineExercises = routine.exercises.map { entry ->
                val entryId = requireUniqueId(entry.id, routineExerciseIds, "RoutineExercise")
                val exerciseId = requireNonBlank(
                    entry.exerciseId,
                    "RoutineExercise '$entryId' exerciseId",
                )
                requireCondition(entry.sortOrder >= 0) {
                    "RoutineExercise '$entryId' sortOrder must be non-negative."
                }
                entry.defaultTargetSets?.let { targetSets ->
                    requireCondition(
                        targetSets in
                            RoutineExercise.MIN_DEFAULT_TARGET_SETS..RoutineExercise.MAX_DEFAULT_TARGET_SETS,
                    ) {
                        "RoutineExercise '$entryId' defaultTargetSets must be between " +
                            "${RoutineExercise.MIN_DEFAULT_TARGET_SETS} and " +
                            "${RoutineExercise.MAX_DEFAULT_TARGET_SETS}."
                    }
                }
                RoutineExerciseEntity(
                    id = entryId,
                    routineId = routineId,
                    exerciseId = exerciseId,
                    sortOrder = entry.sortOrder,
                    defaultTargetSets = entry.defaultTargetSets,
                    createdAt = parseTimestamp(entry.createdAt, "RoutineExercise '$entryId' createdAt"),
                    updatedAt = parseTimestamp(entry.updatedAt, "RoutineExercise '$entryId' updatedAt"),
                    deletedAt = null,
                    syncStatus = pendingSync,
                )
            }
            ValidatedRoutineGraph(routine = routineEntity, exercises = routineExercises)
        }

        val sessions = archive.sessions.map { session ->
            val sessionId = requireUniqueId(session.id, sessionIds, "Session")
            val discipline = parseEnum<Discipline>(session.discipline, "Session '$sessionId' discipline")
            val status = parseEnum<SessionStatus>(session.status, "Session '$sessionId' status")
            val startedAt = parseTimestamp(session.startedAt, "Session '$sessionId' startedAt")
            val completedAt = session.completedAt?.let {
                parseTimestamp(it, "Session '$sessionId' completedAt")
            }
            when (status) {
                SessionStatus.COMPLETED -> requireCondition(completedAt != null) {
                    "Completed Session '$sessionId' must include completedAt."
                }
                SessionStatus.IN_PROGRESS -> requireCondition(completedAt == null) {
                    "In-progress Session '$sessionId' must not have completedAt."
                }
            }
            requireCondition(session.durationSeconds >= 0L) {
                "Session '$sessionId' durationSeconds must be non-negative."
            }
            session.routineId?.let { requireNonBlank(it, "Session '$sessionId' routineId") }

            val sessionEntity = SessionEntity(
                id = sessionId,
                routineId = session.routineId,
                title = session.title,
                discipline = discipline.name,
                status = status.name,
                startedAt = startedAt,
                completedAt = completedAt,
                durationSeconds = session.durationSeconds,
                notes = session.notes,
                createdAt = parseTimestamp(session.createdAt, "Session '$sessionId' createdAt"),
                updatedAt = parseTimestamp(session.updatedAt, "Session '$sessionId' updatedAt"),
                deletedAt = null,
                syncStatus = pendingSync,
            )

            val sessionExercises = session.exercises.map { ex ->
                val exEntryId = requireUniqueId(ex.id, sessionExerciseIds, "SessionExercise")
                val exerciseId = requireNonBlank(ex.exerciseId, "SessionExercise '$exEntryId' exerciseId")
                val exerciseName = requireNonBlank(
                    ex.exerciseName,
                    "SessionExercise '$exEntryId' exerciseName",
                )
                val exerciseDiscipline = parseEnum<Discipline>(
                    ex.exerciseDiscipline,
                    "SessionExercise '$exEntryId' exerciseDiscipline",
                )
                val trackingMode = parseEnum<TrackingMode>(
                    ex.trackingMode,
                    "SessionExercise '$exEntryId' trackingMode",
                )
                requireCondition(ex.sortOrder >= 0) {
                    "SessionExercise '$exEntryId' sortOrder must be non-negative."
                }

                val sessionExerciseEntity = SessionExerciseEntity(
                    id = exEntryId,
                    sessionId = sessionId,
                    exerciseId = exerciseId,
                    exerciseName = exerciseName,
                    exerciseDiscipline = exerciseDiscipline.name,
                    trackingMode = trackingMode.name,
                    sortOrder = ex.sortOrder,
                    notes = ex.notes,
                    createdAt = parseTimestamp(ex.createdAt, "SessionExercise '$exEntryId' createdAt"),
                    updatedAt = parseTimestamp(ex.updatedAt, "SessionExercise '$exEntryId' updatedAt"),
                    deletedAt = null,
                    syncStatus = pendingSync,
                )

                val sets = ex.sets.map { set ->
                    val setId = requireUniqueId(set.id, sessionSetIds, "SessionSet")
                    requireCondition(set.sortOrder >= 0) {
                        "SessionSet '$setId' sortOrder must be non-negative."
                    }
                    set.reps?.let {
                        requireCondition(it >= 0) { "SessionSet '$setId' reps must be non-negative." }
                    }
                    set.durationSeconds?.let {
                        requireCondition(it >= 0) {
                            "SessionSet '$setId' durationSeconds must be non-negative."
                        }
                    }
                    set.rounds?.let {
                        requireCondition(it >= 0) { "SessionSet '$setId' rounds must be non-negative." }
                    }
                    val normalizedLoadKg = set.loadKg?.let { load ->
                        requireCondition(load.isFinite() && load >= 0.0) {
                            "SessionSet '$setId' loadKg must be finite and non-negative."
                        }
                        normalizeZeroDouble(load)
                    }
                    val normalizedDistanceKm = set.distanceKm?.let { distance ->
                        requireCondition(distance.isFinite() && distance >= 0.0) {
                            "SessionSet '$setId' distanceKm must be finite and non-negative."
                        }
                        normalizeZeroDouble(distance)
                    }

                    SessionSetEntity(
                        id = setId,
                        sessionExerciseId = exEntryId,
                        sortOrder = set.sortOrder,
                        reps = set.reps,
                        loadKg = normalizedLoadKg,
                        durationSeconds = set.durationSeconds,
                        distanceKm = normalizedDistanceKm,
                        rounds = set.rounds,
                        isCompleted = set.isCompleted,
                        isWarmup = set.isWarmup,
                        createdAt = parseTimestamp(set.createdAt, "SessionSet '$setId' createdAt"),
                        updatedAt = parseTimestamp(set.updatedAt, "SessionSet '$setId' updatedAt"),
                        deletedAt = null,
                        syncStatus = pendingSync,
                    )
                }
                ValidatedSessionExerciseGraph(
                    sessionExercise = sessionExerciseEntity,
                    sets = sets,
                )
            }
            ValidatedSessionGraph(session = sessionEntity, exercises = sessionExercises)
        }

        val bodyweightEntries = archive.bodyweightEntries.map { entry ->
            val id = requireUniqueId(entry.id, bodyweightIds, "BodyweightEntry")
            requireCondition(entry.weightKg.isFinite() && entry.weightKg > 0.0) {
                "BodyweightEntry '$id' weightKg must be a positive finite number."
            }
            BodyweightEntryEntity(
                id = id,
                recordedAt = parseTimestamp(entry.recordedAt, "BodyweightEntry '$id' recordedAt"),
                weightKg = entry.weightKg,
                createdAt = parseTimestamp(entry.createdAt, "BodyweightEntry '$id' createdAt"),
                updatedAt = parseTimestamp(entry.updatedAt, "BodyweightEntry '$id' updatedAt"),
                deletedAt = null,
                syncStatus = pendingSync,
            )
        }

        return ValidatedBackup(
            exercises = exercises,
            routines = routines,
            sessions = sessions,
            bodyweightEntries = bodyweightEntries,
            summary = BackupSummary(
                schemaVersion = archive.schemaVersion,
                exerciseCount = exercises.size,
                routineCount = routines.size,
                sessionCount = sessions.size,
                bodyweightEntryCount = bodyweightEntries.size,
            ),
        )
    }

    private fun requireUniqueId(
        rawId: String,
        seen: MutableSet<String>,
        entityType: String,
    ): String {
        val id = requireNonBlank(rawId, "$entityType id")
        if (!seen.add(id)) {
            throw BackupValidationException("Duplicate $entityType id '$id' in backup.")
        }
        return id
    }

    private fun requireNonBlank(value: String, fieldName: String): String {
        if (value.isBlank()) {
            throw BackupValidationException("$fieldName must not be blank.")
        }
        return value
    }

    private inline fun <reified T : Enum<T>> parseEnum(raw: String, fieldName: String): T =
        enumValues<T>().firstOrNull { it.name == raw }
            ?: throw BackupValidationException("Invalid $fieldName '$raw'.")

    private fun parseTimestamp(raw: String, fieldName: String): Long {
        if (raw.isBlank()) {
            throw BackupValidationException("$fieldName must not be blank.")
        }
        return try {
            Instant.parse(raw).truncatedTo(ChronoUnit.MILLIS).toEpochMilli()
        } catch (_: DateTimeParseException) {
            throw BackupValidationException("Invalid timestamp for $fieldName: '$raw'.")
        }
    }

    private inline fun requireCondition(condition: Boolean, message: () -> String) {
        if (!condition) {
            throw BackupValidationException(message())
        }
    }

    private fun normalizeZeroDouble(value: Double): Double = if (value == 0.0) 0.0 else value

    private class BackupValidationException(message: String) : IllegalArgumentException(message)
}
