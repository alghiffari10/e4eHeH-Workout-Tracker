package com.e4eheh.e4ehehworkout.domain.repository

import com.e4eheh.e4ehehworkout.domain.model.DistanceUnit
import com.e4eheh.e4ehehworkout.domain.model.UserPreferences
import com.e4eheh.e4ehehworkout.domain.model.WeightUnit
import kotlinx.coroutines.flow.Flow

/** Persistence boundary for local athlete preferences. */
interface UserPreferencesRepository {
    fun observePreferences(): Flow<UserPreferences>

    suspend fun getPreferences(): UserPreferences

    suspend fun updateDisplayName(displayName: String): UserPreferences

    suspend fun updateWeightUnit(weightUnit: WeightUnit): UserPreferences

    suspend fun updateDistanceUnit(distanceUnit: DistanceUnit): UserPreferences

    suspend fun updateDefaultRestTimerSeconds(durationSeconds: Int): UserPreferences
}
