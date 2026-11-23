package com.e4eheh.e4ehehworkout.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.e4eheh.e4ehehworkout.domain.model.DistanceUnit
import com.e4eheh.e4ehehworkout.domain.model.UserPreferences
import com.e4eheh.e4ehehworkout.domain.model.WeightUnit
import java.time.Instant

/** Room singleton row persisting the athlete's local Batcomputer preferences. */
@Entity(tableName = "user_preferences")
data class UserPreferencesEntity(
    @PrimaryKey val id: String = SINGLETON_ID,
    val displayName: String,
    val weightUnit: String,
    val distanceUnit: String,
    val defaultRestTimerSeconds: Int,
    val updatedAt: Long,
) {
    companion object {
        const val SINGLETON_ID = "default"
    }
}

fun UserPreferencesEntity.toDomain(): UserPreferences = UserPreferences(
    displayName = displayName,
    weightUnit = WeightUnit.valueOf(weightUnit),
    distanceUnit = DistanceUnit.valueOf(distanceUnit),
    defaultRestTimerSeconds = defaultRestTimerSeconds,
)

fun UserPreferences.toEntity(updatedAt: Instant): UserPreferencesEntity = UserPreferencesEntity(
    id = UserPreferencesEntity.SINGLETON_ID,
    displayName = displayName,
    weightUnit = weightUnit.name,
    distanceUnit = distanceUnit.name,
    defaultRestTimerSeconds = defaultRestTimerSeconds,
    updatedAt = updatedAt.toEpochMilli(),
)
