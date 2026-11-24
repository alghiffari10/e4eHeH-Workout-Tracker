package com.e4eheh.e4ehehworkout.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory storage seam mirroring SessionDao semantics for fast JVM repository tests. */
class InMemorySessionDao : SessionDao {
    private data class StorageState(
        val sessions: Map<String, SessionEntity> = emptyMap(),
        val sessionExercises: Map<String, SessionExerciseEntity> = emptyMap(),
        val sessionSets: Map<String, SessionSetEntity> = emptyMap(),
    )

    private val state = MutableStateFlow(StorageState())

    override fun observeActiveWithExercises(): Flow<List<SessionWithExercises>> =
        state.map(::activeSorted)

    override suspend fun getActiveWithExercises(): List<SessionWithExercises> = activeSorted(state.value)

    private fun activeSorted(current: StorageState): List<SessionWithExercises> =
        current.sessions.values
            .filter { it.deletedAt == null }
            .sortedWith(
                compareByDescending<SessionEntity> { it.startedAt }
                    .thenByDescending { it.createdAt },
            )
            .map { session -> buildGraph(current, session) }

    override fun observeWithExercisesById(id: String): Flow<SessionWithExercises?> =
        state.map { current ->
            val session = current.sessions[id]?.takeIf { it.deletedAt == null } ?: return@map null
            buildGraph(current, session)
        }

    override suspend fun getWithExercisesById(id: String): SessionWithExercises? {
        val current = state.value
        val session = current.sessions[id] ?: return null
        return buildGraph(current, session)
    }

    override suspend fun upsertSession(session: SessionEntity) {
        state.update { current ->
            current.copy(sessions = current.sessions + (session.id to session))
        }
    }

    override suspend fun upsertSessionExercises(exercises: List<SessionExerciseEntity>) {
        state.update { current ->
            val next = current.sessionExercises.toMutableMap()
            exercises.forEach { next[it.id] = it }
            current.copy(sessionExercises = next)
        }
    }

    override suspend fun upsertSessionSets(sets: List<SessionSetEntity>) {
        state.update { current ->
            val next = current.sessionSets.toMutableMap()
            sets.forEach { next[it.id] = it }
            current.copy(sessionSets = next)
        }
    }

    override suspend fun upsertSessionGraph(
        session: SessionEntity,
        exercises: List<SessionExerciseEntity>,
        sets: List<SessionSetEntity>,
    ) {
        state.update { current ->
            val nextExercises = current.sessionExercises.toMutableMap()
            exercises.forEach { nextExercises[it.id] = it }
            val nextSets = current.sessionSets.toMutableMap()
            sets.forEach { nextSets[it.id] = it }
            current.copy(
                sessions = current.sessions + (session.id to session),
                sessionExercises = nextExercises,
                sessionSets = nextSets,
            )
        }
    }

    private fun buildGraph(
        current: StorageState,
        session: SessionEntity,
    ): SessionWithExercises {
        val setsByExercise = current.sessionSets.values.groupBy { it.sessionExerciseId }
        val exercises = current.sessionExercises.values
            .filter { it.sessionId == session.id }
            .map { exercise ->
                SessionExerciseWithSets(
                    sessionExercise = exercise,
                    sets = setsByExercise[exercise.id].orEmpty(),
                )
            }
        return SessionWithExercises(session = session, exercises = exercises)
    }
}
