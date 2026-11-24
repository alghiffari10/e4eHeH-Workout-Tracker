package com.e4eheh.e4ehehworkout.data.repository

import com.e4eheh.e4ehehworkout.data.local.BodyweightEntryDao
import com.e4eheh.e4ehehworkout.data.local.toDomain
import com.e4eheh.e4ehehworkout.data.local.toEntity
import com.e4eheh.e4ehehworkout.domain.model.BodyweightEntry
import com.e4eheh.e4ehehworkout.domain.model.BodyweightEntryInput
import com.e4eheh.e4ehehworkout.domain.model.SyncStatus
import com.e4eheh.e4ehehworkout.domain.repository.BodyweightRepository
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room-backed BodyweightRepository enforcing canonical kg storage, sync metadata, and tombstones. */
class DefaultBodyweightRepository(
    private val bodyweightEntryDao: BodyweightEntryDao,
    private val clock: Clock,
) : BodyweightRepository {
    override fun observeActiveEntries(): Flow<List<BodyweightEntry>> =
        bodyweightEntryDao.observeActive().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getById(id: String, includeDeleted: Boolean): BodyweightEntry? {
        val entity = bodyweightEntryDao.getById(id) ?: return null
        if (!includeDeleted && entity.deletedAt != null) return null
        return entity.toDomain()
    }

    override suspend fun create(input: BodyweightEntryInput): BodyweightEntry {
        val normalized = normalizeInput(input)
        val now = nowMillis()
        val entry = BodyweightEntry(
            id = UUID.randomUUID().toString(),
            recordedAt = normalized.recordedAt,
            weightKg = normalized.weightKg,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
            syncStatus = SyncStatus.PENDING,
        )
        bodyweightEntryDao.upsert(entry.toEntity())
        return entry
    }

    override suspend fun update(id: String, input: BodyweightEntryInput): BodyweightEntry {
        val normalized = normalizeInput(input)
        val existing = bodyweightEntryDao.getById(id)?.takeIf { it.deletedAt == null }?.toDomain()
            ?: throw NoSuchElementException("Active BodyweightEntry not found for id=$id")

        val updated = existing.copy(
            recordedAt = normalized.recordedAt,
            weightKg = normalized.weightKg,
            updatedAt = nowMillis(),
            syncStatus = SyncStatus.PENDING,
        )
        bodyweightEntryDao.upsert(updated.toEntity())
        return updated
    }

    override suspend fun softDelete(id: String): BodyweightEntry {
        val existing = bodyweightEntryDao.getById(id)?.toDomain()
            ?: throw NoSuchElementException("BodyweightEntry not found for id=$id")
        if (existing.deletedAt != null) return existing

        val now = nowMillis()
        val deleted = existing.copy(
            updatedAt = now,
            deletedAt = now,
            syncStatus = SyncStatus.PENDING,
        )
        bodyweightEntryDao.upsert(deleted.toEntity())
        return deleted
    }

    private fun nowMillis(): Instant = clock.instant().truncatedTo(ChronoUnit.MILLIS)

    private fun normalizeInput(input: BodyweightEntryInput): BodyweightEntryInput {
        require(input.weightKg.isFinite() && input.weightKg > 0.0) {
            "Bodyweight must be a positive finite number."
        }
        return BodyweightEntryInput(
            recordedAt = input.recordedAt.truncatedTo(ChronoUnit.MILLIS),
            weightKg = input.weightKg,
        )
    }
}
