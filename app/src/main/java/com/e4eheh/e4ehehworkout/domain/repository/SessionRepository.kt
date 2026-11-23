package com.e4eheh.e4ehehworkout.domain.repository

import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.Session
import com.e4eheh.e4ehehworkout.domain.model.SessionDraftInput
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** Persistence boundary for workout Sessions, auto-saved drafts, and completion snapshots. */
interface SessionRepository {
    fun observeActiveSessions(): Flow<List<Session>>

    fun observeById(id: String): Flow<Session?>

    suspend fun getById(id: String, includeDeleted: Boolean = false): Session?

    suspend fun createLiveBlankSession(
        discipline: Discipline = Discipline.CALISTHENICS,
        title: String = "",
    ): Session

    suspend fun createLiveSessionFromRoutine(routineId: String): Session

    suspend fun saveDraft(id: String, input: SessionDraftInput): Session

    suspend fun completeSession(id: String, input: SessionDraftInput): Session

    /**
     * Persists an already-performed Session for a chosen past date/time.
     *
     * Stores the supplied [startedAt] and [durationSeconds] with `completedAt` derived as
     * `startedAt + durationSeconds`, status `COMPLETED`, and `syncStatus = PENDING`.
     */
    suspend fun createCompletedSession(
        startedAt: Instant,
        durationSeconds: Long,
        input: SessionDraftInput,
        routineId: String? = null,
    ): Session

    /**
     * Edits an existing `COMPLETED` Session's timing and content, keeping sync metadata pending.
     */
    suspend fun updateCompletedSession(
        id: String,
        startedAt: Instant,
        durationSeconds: Long,
        input: SessionDraftInput,
    ): Session

    suspend fun softDelete(id: String): Session
}
