package com.e4eheh.e4ehehworkout.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Data access object for locally persisted athlete preferences. */
@Dao
interface UserPreferencesDao {
    @Query("SELECT * FROM user_preferences WHERE id = :id LIMIT 1")
    fun observe(id: String = UserPreferencesEntity.SINGLETON_ID): Flow<UserPreferencesEntity?>

    @Query("SELECT * FROM user_preferences WHERE id = :id LIMIT 1")
    suspend fun get(id: String = UserPreferencesEntity.SINGLETON_ID): UserPreferencesEntity?

    @Upsert
    suspend fun upsert(entity: UserPreferencesEntity)
}
