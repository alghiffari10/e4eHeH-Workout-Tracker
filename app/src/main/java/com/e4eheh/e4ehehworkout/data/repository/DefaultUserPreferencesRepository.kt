package com.e4eheh.e4ehehworkout.data.repository

import com.e4eheh.e4ehehworkout.data.local.UserPreferencesDao
import com.e4eheh.e4ehehworkout.data.local.toDomain
import com.e4eheh.e4ehehworkout.data.local.toEntity
import com.e4eheh.e4ehehworkout.domain.model.DistanceUnit
import com.e4eheh.e4ehehworkout.domain.model.UserPreferences
import com.e4eheh.e4ehehworkout.domain.model.WeightUnit
import com.e4eheh.e4ehehworkout.domain.repository.UserPreferencesRepository
import java.time.Clock
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Room-backed UserPreferencesRepository enforcing defaults, validation, and atomic updates. */
class DefaultUserPreferencesRepository(
    private val userPreferencesDao: UserPreferencesDao,
    private val clock: Clock,
) : UserPreferencesRepository {
    private val writeMutex = Mutex()

    override fun observePreferences(): Flow<UserPreferences> =
        userPreferencesDao.observe().map { entity -> entity?.toDomain() ?: UserPreferences() }

    override suspend fun getPreferences(): UserPreferences =
        userPreferencesDao.get()?.toDomain() ?: UserPreferences()

    override suspend fun updateDisplayName(displayName: String): UserPreferences {
        val trimmed = displayName.trim()
        require(trimmed.isNotEmpty()) { "Display name must not be blank." }
        return mutate { it.copy(displayName = trimmed) }
    }

    override suspend fun updateWeightUnit(weightUnit: WeightUnit): UserPreferences =
        mutate { it.copy(weightUnit = weightUnit) }

    override suspend fun updateDistanceUnit(distanceUnit: DistanceUnit): UserPreferences =
        mutate { it.copy(distanceUnit = distanceUnit) }

    override suspend fun updateDefaultRestTimerSeconds(durationSeconds: Int): UserPreferences {
        require(
            durationSeconds in
                UserPreferences.MIN_REST_TIMER_SECONDS..UserPreferences.MAX_REST_TIMER_SECONDS
        ) {
            "Default rest timer must be between ${UserPreferences.MIN_REST_TIMER_SECONDS}s " +
                "and ${UserPreferences.MAX_REST_TIMER_SECONDS}s."
        }
        return mutate { it.copy(defaultRestTimerSeconds = durationSeconds) }
    }

    private suspend fun mutate(transform: (UserPreferences) -> UserPreferences): UserPreferences =
        writeMutex.withLock {
            val current = userPreferencesDao.get()?.toDomain() ?: UserPreferences()
            val updated = transform(current)
            val updatedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS)
            userPreferencesDao.upsert(updated.toEntity(updatedAt))
            updated
        }
}
