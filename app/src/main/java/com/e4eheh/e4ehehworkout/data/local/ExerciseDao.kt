package com.e4eheh.e4ehehworkout.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Data access object for locally persisted Exercise records. */
@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE ASC, createdAt ASC")
    fun observeActive(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ExerciseEntity?

    @Upsert
    suspend fun upsert(entity: ExerciseEntity)
}
