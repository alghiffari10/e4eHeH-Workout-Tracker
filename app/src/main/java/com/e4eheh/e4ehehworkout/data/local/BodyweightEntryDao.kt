package com.e4eheh.e4ehehworkout.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Data access object for locally persisted Bodyweight Entry records. */
@Dao
interface BodyweightEntryDao {
    @Query("SELECT * FROM bodyweight_entries WHERE deletedAt IS NULL ORDER BY recordedAt DESC, createdAt DESC")
    fun observeActive(): Flow<List<BodyweightEntryEntity>>

    @Query("SELECT * FROM bodyweight_entries WHERE deletedAt IS NULL ORDER BY recordedAt DESC, createdAt DESC")
    suspend fun getActive(): List<BodyweightEntryEntity>

    @Query("SELECT * FROM bodyweight_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): BodyweightEntryEntity?

    @Upsert
    suspend fun upsert(entity: BodyweightEntryEntity)
}
