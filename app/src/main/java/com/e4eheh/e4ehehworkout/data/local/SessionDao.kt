package com.e4eheh.e4ehehworkout.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Data access object for locally persisted Session, SessionExercise, and SessionSet records. */
@Dao
interface SessionDao {
    @Transaction
    @Query("SELECT * FROM sessions WHERE deletedAt IS NULL ORDER BY startedAt DESC, createdAt DESC")
    fun observeActiveWithExercises(): Flow<List<SessionWithExercises>>

    @Transaction
    @Query("SELECT * FROM sessions WHERE deletedAt IS NULL ORDER BY startedAt DESC, createdAt DESC")
    suspend fun getActiveWithExercises(): List<SessionWithExercises>

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    fun observeWithExercisesById(id: String): Flow<SessionWithExercises?>

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    suspend fun getWithExercisesById(id: String): SessionWithExercises?

    @Upsert
    suspend fun upsertSession(session: SessionEntity)

    @Upsert
    suspend fun upsertSessionExercises(exercises: List<SessionExerciseEntity>)

    @Upsert
    suspend fun upsertSessionSets(sets: List<SessionSetEntity>)

    @Transaction
    suspend fun upsertSessionGraph(
        session: SessionEntity,
        exercises: List<SessionExerciseEntity>,
        sets: List<SessionSetEntity>,
    ) {
        upsertSession(session)
        if (exercises.isNotEmpty()) {
            upsertSessionExercises(exercises)
        }
        if (sets.isNotEmpty()) {
            upsertSessionSets(sets)
        }
    }
}
