package com.e4eheh.e4ehehworkout.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Data access object for the singleton locally persisted athlete preferences row. */
@Dao
interface UserPreferencesDao {
    @Query("SELECT * FROM user_preferences WHERE id = '${UserPreferencesEntity.SINGLETON_ID}' LIMIT 1")
    fun observe(): Flow<UserPreferencesEntity?>

    @Query("SELECT * FROM user_preferences WHERE id = '${UserPreferencesEntity.SINGLETON_ID}' LIMIT 1")
    suspend fun get(): UserPreferencesEntity?

    @Upsert
    suspend fun upsert(entity: UserPreferencesEntity)
}
