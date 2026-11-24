package com.e4eheh.e4ehehworkout.ui.components

import com.e4eheh.e4ehehworkout.domain.analytics.formatChartDateLabel
import com.e4eheh.e4ehehworkout.domain.model.BodyweightEntry
import com.e4eheh.e4ehehworkout.domain.model.BodyweightEntryInput
import com.e4eheh.e4ehehworkout.domain.model.WeightUnit
import com.e4eheh.e4ehehworkout.domain.repository.BodyweightRepository
import com.e4eheh.e4ehehworkout.domain.unit.UnitConverter
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Display-ready row model for a persisted Bodyweight Entry formatted in the active WeightUnit. */
data class BodyweightEntryRowUi(
    val entry: BodyweightEntry,
    val dateLabel: String,
    val formattedWeight: String,
)

/** Form state for creating or editing a dated Bodyweight Entry. */
data class BodyweightEntryEditorState(
    val editingEntryId: String? = null,
    val originalRecordedAt: Instant? = null,
    val dateText: String = "",
    val weightText: String = "",
    val dateError: String? = null,
    val weightError: String? = null,
    val isSaving: Boolean = false,
)

/** Combined modal dialog state exposed by BodyweightEntryCoordinator. */
data class BodyweightModalState(
    val editor: BodyweightEntryEditorState? = null,
    val deletingEntry: BodyweightEntry? = null,
)

/** Shared CRUD actions for managing Bodyweight Entries from Profile or Progress. */
sealed interface BodyweightCrudAction {
    data object OpenCreate : BodyweightCrudAction
    data class OpenEdit(val entry: BodyweightEntry) : BodyweightCrudAction
    data object DismissEditor : BodyweightCrudAction
    data class UpdateDate(val dateText: String) : BodyweightCrudAction
    data class UpdateWeight(val weightText: String) : BodyweightCrudAction
    data object Save : BodyweightCrudAction
    data class RequestDelete(val entry: BodyweightEntry) : BodyweightCrudAction
    data object DismissDelete : BodyweightCrudAction
    data object ConfirmDelete : BodyweightCrudAction
}

/** Coordinates Bodyweight Entry create/edit/soft-delete dialogs across Profile and Progress ViewModels. */
class BodyweightEntryCoordinator(
    private val repository: BodyweightRepository,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val currentWeightUnit: () -> WeightUnit,
) {
    private val editorState = MutableStateFlow<BodyweightEntryEditorState?>(null)
    private val deletingEntry = MutableStateFlow<BodyweightEntry?>(null)
    private val isDeleting = MutableStateFlow(false)

    val modalState: Flow<BodyweightModalState> = combine(
        editorState,
        deletingEntry,
    ) { editor, deleting ->
        BodyweightModalState(editor = editor, deletingEntry = deleting)
    }

    fun onAction(action: BodyweightCrudAction) {
        when (action) {
            BodyweightCrudAction.OpenCreate -> {
                val today = clock.instant().atZone(clock.zone).toLocalDate()
                editorState.value = BodyweightEntryEditorState(
                    dateText = today.toString(),
                )
            }

            is BodyweightCrudAction.OpenEdit -> {
                val entryDate = action.entry.recordedAt.atZone(clock.zone).toLocalDate()
                editorState.value = BodyweightEntryEditorState(
                    editingEntryId = action.entry.id,
                    originalRecordedAt = action.entry.recordedAt,
                    dateText = entryDate.toString(),
                    weightText = UnitConverter.formatWeightValue(
                        canonicalKg = action.entry.weightKg,
                        unit = currentWeightUnit(),
                    ),
                )
            }

            BodyweightCrudAction.DismissEditor -> {
                if (editorState.value?.isSaving != true) {
                    editorState.value = null
                }
            }

            is BodyweightCrudAction.UpdateDate -> {
                editorState.update { current ->
                    current?.copy(
                        dateText = sanitizeDateText(action.dateText),
                        dateError = null,
                    )
                }
            }

            is BodyweightCrudAction.UpdateWeight -> {
                editorState.update { current ->
                    current?.copy(
                        weightText = sanitizeNonNegativeDecimal(action.weightText),
                        weightError = null,
                    )
                }
            }

            BodyweightCrudAction.Save -> save()

            is BodyweightCrudAction.RequestDelete -> {
                if (!isDeleting.value) {
                    deletingEntry.value = action.entry
                }
            }

            BodyweightCrudAction.DismissDelete -> {
                if (!isDeleting.value) {
                    deletingEntry.value = null
                }
            }

            BodyweightCrudAction.ConfirmDelete -> confirmDelete()
        }
    }

    private fun save() {
        val state = editorState.value ?: return
        if (state.isSaving) return

        val today = clock.instant().atZone(clock.zone).toLocalDate()
        val parsedDate = try {
            LocalDate.parse(state.dateText.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: DateTimeParseException) {
            null
        }
        val dateError = when {
            parsedDate == null -> "Enter a valid date (YYYY-MM-DD)."
            parsedDate.isAfter(today) -> "Date cannot be in the future."
            else -> null
        }

        val parsedWeight = state.weightText.trim().toDoubleOrNull()
        val weightError = if (parsedWeight == null || !parsedWeight.isFinite() || parsedWeight <= 0.0) {
            "Enter a valid bodyweight greater than 0."
        } else {
            null
        }

        if (dateError != null || weightError != null) {
            editorState.value = state.copy(
                dateError = dateError,
                weightError = weightError,
            )
            return
        }

        val validDate = parsedDate!!
        val validWeight = parsedWeight!!
        val recordedAt = resolveRecordedAt(
            date = validDate,
            today = today,
            originalRecordedAt = state.originalRecordedAt,
        )
        val canonicalKg = UnitConverter.weightToCanonicalKg(validWeight, currentWeightUnit())

        editorState.value = state.copy(isSaving = true, dateError = null, weightError = null)
        scope.launch {
            try {
                val input = BodyweightEntryInput(
                    recordedAt = recordedAt,
                    weightKg = canonicalKg,
                )
                if (state.editingEntryId == null) {
                    repository.create(input)
                } else {
                    repository.update(state.editingEntryId, input)
                }
                editorState.value = null
            } catch (_: Exception) {
                editorState.update { current ->
                    current?.copy(
                        isSaving = false,
                        weightError = "Unable to save Bodyweight Entry. Please try again.",
                    )
                }
            }
        }
    }

    private fun confirmDelete() {
        val target = deletingEntry.value ?: return
        if (isDeleting.value) return
        isDeleting.value = true
        scope.launch {
            try {
                repository.softDelete(target.id)
                deletingEntry.value = null
            } finally {
                isDeleting.value = false
            }
        }
    }

    private fun resolveRecordedAt(
        date: LocalDate,
        today: LocalDate,
        originalRecordedAt: Instant?,
    ): Instant {
        if (originalRecordedAt != null &&
            originalRecordedAt.atZone(clock.zone).toLocalDate() == date
        ) {
            return originalRecordedAt.truncatedTo(ChronoUnit.MILLIS)
        }
        if (date == today) {
            return clock.instant().truncatedTo(ChronoUnit.MILLIS)
        }
        val currentLocalTime = clock.instant().atZone(clock.zone).toLocalTime()
        return LocalDateTime.of(date, currentLocalTime)
            .atZone(clock.zone)
            .toInstant()
            .truncatedTo(ChronoUnit.MILLIS)
    }

    companion object {
        fun toRowUi(
            entry: BodyweightEntry,
            weightUnit: WeightUnit,
            clock: Clock,
        ): BodyweightEntryRowUi = BodyweightEntryRowUi(
            entry = entry,
            dateLabel = formatChartDateLabel(entry.recordedAt, clock),
            formattedWeight = UnitConverter.formatWeight(entry.weightKg, weightUnit),
        )
    }
}
