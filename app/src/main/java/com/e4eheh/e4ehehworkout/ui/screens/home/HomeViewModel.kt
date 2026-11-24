package com.e4eheh.e4ehehworkout.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.e4eheh.e4ehehworkout.domain.analytics.HomeTelemetryCalculator
import com.e4eheh.e4ehehworkout.domain.analytics.ProgressAnalyticsCalculator
import com.e4eheh.e4ehehworkout.domain.analytics.RoutineDurationSource
import com.e4eheh.e4ehehworkout.domain.analytics.WeeklyCalendarWindow
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.Routine
import com.e4eheh.e4ehehworkout.domain.model.Session
import com.e4eheh.e4ehehworkout.domain.model.SessionStatus
import com.e4eheh.e4ehehworkout.domain.model.UserPreferences
import com.e4eheh.e4ehehworkout.domain.repository.ExerciseRepository
import com.e4eheh.e4ehehworkout.domain.repository.RoutineRepository
import com.e4eheh.e4ehehworkout.domain.repository.SessionRepository
import com.e4eheh.e4ehehworkout.domain.repository.UserPreferencesRepository
import com.e4eheh.e4ehehworkout.ui.components.formatCompactDuration
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** HUD banner summary for an auto-saved IN_PROGRESS Session on Home. */
data class ActiveSessionBannerUi(
    val sessionId: String,
    val displayTitle: String,
    val discipline: Discipline,
    val exerciseCount: Int,
    val completedSetCount: Int,
    val totalSetCount: Int,
    val elapsedSeconds: Long,
)

/** Home history summary for a completed Session that can be opened in Session Detail. */
data class CompletedSessionHistoryItem(
    val sessionId: String,
    val displayTitle: String,
    val discipline: Discipline,
    val dateTimeLabel: String,
    val durationSeconds: Long,
    val exerciseCount: Int,
)

/** UI summary for a single day inside the Monday–Sunday weekly calendar strip. */
data class WeeklyCalendarDayUi(
    val date: LocalDate,
    val dayLabel: String,
    val dayOfMonth: Int,
    val isToday: Boolean,
    val hasCompletedSessions: Boolean,
    val sessionCount: Int,
)

/** UI state for the Monday–Sunday weekly calendar strip on Home. */
data class WeeklyCalendarUi(
    val weekRangeLabel: String = "",
    val activeDayCount: Int = 0,
    val days: List<WeeklyCalendarDayUi> = emptyList(),
)

/** UI card model for a Routine in both the START picker and the Home Routine carousel. */
data class HomeRoutineCardUi(
    val routineId: String,
    val name: String,
    val discipline: Discipline,
    val exerciseCount: Int,
    val durationLabel: String,
    val isSuggestedNext: Boolean,
)

/** Combined UI state for the Batcomputer Home command center. */
data class HomeUiState(
    val displayName: String = UserPreferences.DEFAULT_DISPLAY_NAME,
    val exerciseCount: Int = 0,
    val activeSessionBanner: ActiveSessionBannerUi? = null,
    val pendingDiscardSession: ActiveSessionBannerUi? = null,
    val isDiscarding: Boolean = false,
    val discardError: String? = null,
    val isStartPickerVisible: Boolean = false,
    val weeklyCalendar: WeeklyCalendarUi = WeeklyCalendarUi(),
    val selectedCalendarDate: LocalDate? = null,
    val selectedCalendarDayLabel: String? = null,
    val selectedDaySessions: List<CompletedSessionHistoryItem> = emptyList(),
    val selectedDisciplineFilter: Discipline? = null,
    val allRoutineCards: List<HomeRoutineCardUi> = emptyList(),
    val filteredRoutineCards: List<HomeRoutineCardUi> = emptyList(),
    val suggestedRoutine: HomeRoutineCardUi? = null,
    val completedSessions: List<CompletedSessionHistoryItem> = emptyList(),
)

/** User and clock-tick actions dispatched from the Home screen. */
sealed interface HomeAction {
    data object RefreshElapsed : HomeAction
    data object RequestDiscardActiveSession : HomeAction
    data object DismissDiscardDialog : HomeAction
    data object ConfirmDiscardActiveSession : HomeAction
    data object OpenStartPicker : HomeAction
    data object DismissStartPicker : HomeAction
    data class SelectCalendarDate(val date: LocalDate) : HomeAction
    data class SelectDisciplineFilter(val discipline: Discipline?) : HomeAction
}

/** Coordinates Home command-center telemetry, START picker, calendar inspection, and IN_PROGRESS Session resume/discard. */
class HomeViewModel(
    exerciseRepository: ExerciseRepository,
    routineRepository: RoutineRepository,
    private val sessionRepository: SessionRepository,
    userPreferencesRepository: UserPreferencesRepository,
    private val clock: Clock,
) : ViewModel() {
    private data class InternalState(
        val pendingDiscardSessionId: String? = null,
        val isDiscarding: Boolean = false,
        val discardError: String? = null,
        val isStartPickerVisible: Boolean = false,
        val selectedCalendarDate: LocalDate? = null,
        val selectedDisciplineFilter: Discipline? = null,
        val clockTick: Long = 0L,
    )

    private val internalState = MutableStateFlow(InternalState())

    val uiState: StateFlow<HomeUiState> = combine(
        exerciseRepository.observeActiveExercises(),
        routineRepository.observeActiveRoutines(),
        sessionRepository.observeActiveSessions(),
        userPreferencesRepository.observePreferences(),
        internalState,
    ) { exercises, routines, sessions, preferences, state ->
        val now = clock.instant()
        val activeSessions = sessions.filter { it.status == SessionStatus.IN_PROGRESS }
        val activeBanner = activeSessions.firstOrNull()?.toBannerUi(now)
        val pendingDiscard = state.pendingDiscardSessionId?.let { targetId ->
            activeSessions.firstOrNull { it.id == targetId }?.toBannerUi(now)
        }

        val calendarWindow = HomeTelemetryCalculator.buildWeeklyCalendar(sessions, clock)
        val validSelectedDate = state.selectedCalendarDate?.takeIf {
            !it.isBefore(calendarWindow.weekStart) && !it.isAfter(calendarWindow.weekEnd)
        }
        val selectedDay = validSelectedDate?.let { date ->
            calendarWindow.days.firstOrNull { it.date == date }
        }

        val suggested = HomeTelemetryCalculator.suggestNextRoutine(routines, sessions)
        val orderedRoutines = HomeTelemetryCalculator.orderRoutinesForPicker(
            routines = routines,
            suggestedRoutineId = suggested?.id,
        )
        val allCards = orderedRoutines.map { routine ->
            routine.toHomeCardUi(
                sessions = sessions,
                isSuggestedNext = routine.id == suggested?.id,
            )
        }
        val filteredCards = state.selectedDisciplineFilter?.let { filter ->
            allCards.filter { it.discipline == filter }
        } ?: allCards

        val recentCompleted = ProgressAnalyticsCalculator.filterEligibleSessions(sessions)
            .sortedWith(compareByDescending<Session> { it.startedAt }.thenByDescending { it.createdAt })
            .take(RECENT_ACTIVITY_LIMIT)
            .map { it.toHistoryItem(clock) }

        val resolvedDisplayName = preferences.displayName.trim()
            .ifEmpty { UserPreferences.DEFAULT_DISPLAY_NAME }

        HomeUiState(
            displayName = resolvedDisplayName,
            exerciseCount = exercises.size,
            activeSessionBanner = activeBanner,
            pendingDiscardSession = pendingDiscard,
            isDiscarding = state.isDiscarding,
            discardError = if (activeBanner != null) state.discardError else null,
            isStartPickerVisible = state.isStartPickerVisible,
            weeklyCalendar = calendarWindow.toUi(),
            selectedCalendarDate = validSelectedDate,
            selectedCalendarDayLabel = validSelectedDate?.let(::formatSelectedDayLabel),
            selectedDaySessions = selectedDay?.completedSessions.orEmpty().map { it.toHistoryItem(clock) },
            selectedDisciplineFilter = state.selectedDisciplineFilter,
            allRoutineCards = allCards,
            filteredRoutineCards = filteredCards,
            suggestedRoutine = allCards.firstOrNull { it.isSuggestedNext },
            completedSessions = recentCompleted,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HomeUiState(
            weeklyCalendar = HomeTelemetryCalculator.buildWeeklyCalendar(emptyList(), clock).toUi(),
        ),
    )

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.RefreshElapsed -> {
                val calendarWindow = HomeTelemetryCalculator.buildWeeklyCalendar(emptyList(), clock)
                internalState.update { current ->
                    val retainedDate = current.selectedCalendarDate?.takeIf {
                        !it.isBefore(calendarWindow.weekStart) && !it.isAfter(calendarWindow.weekEnd)
                    }
                    current.copy(
                        clockTick = current.clockTick + 1L,
                        selectedCalendarDate = retainedDate,
                    )
                }
            }

            HomeAction.RequestDiscardActiveSession -> {
                val activeId = uiState.value.activeSessionBanner?.sessionId ?: return
                internalState.update {
                    it.copy(pendingDiscardSessionId = activeId, discardError = null)
                }
            }

            HomeAction.DismissDiscardDialog -> {
                if (internalState.value.isDiscarding) return
                internalState.update { it.copy(pendingDiscardSessionId = null) }
            }

            HomeAction.ConfirmDiscardActiveSession -> confirmDiscardActiveSession()

            HomeAction.OpenStartPicker -> {
                internalState.update { it.copy(isStartPickerVisible = true) }
            }

            HomeAction.DismissStartPicker -> {
                internalState.update { it.copy(isStartPickerVisible = false) }
            }

            is HomeAction.SelectCalendarDate -> {
                internalState.update { current ->
                    val nextDate = if (current.selectedCalendarDate == action.date) null else action.date
                    current.copy(selectedCalendarDate = nextDate)
                }
            }

            is HomeAction.SelectDisciplineFilter -> {
                internalState.update { it.copy(selectedDisciplineFilter = action.discipline) }
            }
        }
    }

    private fun confirmDiscardActiveSession() {
        val current = internalState.value
        if (current.isDiscarding) return
        val targetId = current.pendingDiscardSessionId
            ?: uiState.value.activeSessionBanner?.sessionId
            ?: return

        internalState.update { it.copy(isDiscarding = true, discardError = null) }

        viewModelScope.launch {
            var errorMessage: String? = null
            try {
                sessionRepository.softDelete(targetId)
            } catch (_: Exception) {
                errorMessage = "Unable to discard active Session. Please try again."
            } finally {
                internalState.update {
                    it.copy(
                        isDiscarding = false,
                        pendingDiscardSessionId = null,
                        discardError = errorMessage,
                    )
                }
            }
        }
    }

    private fun Routine.toHomeCardUi(
        sessions: List<Session>,
        isSuggestedNext: Boolean,
    ): HomeRoutineCardUi {
        val durationInfo = HomeTelemetryCalculator.resolveRoutineDuration(this, sessions)
        val durationLabel = when (durationInfo.source) {
            RoutineDurationSource.LAST_SESSION ->
                "LAST ${formatCompactDuration(durationInfo.durationSeconds)}"
            RoutineDurationSource.ESTIMATED -> if (durationInfo.durationSeconds > 0L) {
                "EST ~${formatCompactDuration(durationInfo.durationSeconds)}"
            } else {
                "EST --"
            }
        }
        return HomeRoutineCardUi(
            routineId = id,
            name = name,
            discipline = discipline,
            exerciseCount = exercises.count { it.deletedAt == null },
            durationLabel = durationLabel,
            isSuggestedNext = isSuggestedNext,
        )
    }

    private fun WeeklyCalendarWindow.toUi(): WeeklyCalendarUi = WeeklyCalendarUi(
        weekRangeLabel = "${formatMonthDay(weekStart)} - ${formatMonthDay(weekEnd)}",
        activeDayCount = activeDayCount,
        days = days.map { day ->
            WeeklyCalendarDayUi(
                date = day.date,
                dayLabel = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US).uppercase(Locale.US),
                dayOfMonth = day.date.dayOfMonth,
                isToday = day.isToday,
                hasCompletedSessions = day.hasCompletedSessions,
                sessionCount = day.completedSessions.size,
            )
        },
    )

    private fun formatMonthDay(date: LocalDate): String =
        MONTH_DAY_FORMATTER.format(date).uppercase(Locale.US)

    private fun formatSelectedDayLabel(date: LocalDate): String =
        SELECTED_DAY_FORMATTER.format(date).uppercase(Locale.US)

    private fun Session.toHistoryItem(sessionClock: Clock): CompletedSessionHistoryItem =
        CompletedSessionHistoryItem(
            sessionId = id,
            displayTitle = resolvedDisplayTitle(),
            discipline = discipline,
            dateTimeLabel = HISTORY_DATE_TIME_FORMATTER.format(startedAt.atZone(sessionClock.zone)),
            durationSeconds = durationSeconds,
            exerciseCount = exercises.size,
        )

    private fun Session.toBannerUi(now: Instant): ActiveSessionBannerUi {
        val allSets = exercises.flatMap { it.sets }
        val elapsed = java.time.Duration.between(startedAt, now).seconds.coerceAtLeast(0L)
        return ActiveSessionBannerUi(
            sessionId = id,
            displayTitle = resolvedDisplayTitle(),
            discipline = discipline,
            exerciseCount = exercises.size,
            completedSetCount = allSets.count { it.isCompleted },
            totalSetCount = allSets.size,
            elapsedSeconds = elapsed,
        )
    }

    private fun Session.resolvedDisplayTitle(): String =
        title.trim().ifEmpty { "${discipline.displayName} Session" }

    private companion object {
        const val RECENT_ACTIVITY_LIMIT = 10
        val HISTORY_DATE_TIME_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val MONTH_DAY_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMM dd", Locale.US)
        val SELECTED_DAY_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEE // MMM dd", Locale.US)
    }

    class Factory(
        private val exerciseRepository: ExerciseRepository,
        private val routineRepository: RoutineRepository,
        private val sessionRepository: SessionRepository,
        private val userPreferencesRepository: UserPreferencesRepository,
        private val clock: Clock,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HomeViewModel::class.java))
            return HomeViewModel(
                exerciseRepository = exerciseRepository,
                routineRepository = routineRepository,
                sessionRepository = sessionRepository,
                userPreferencesRepository = userPreferencesRepository,
                clock = clock,
            ) as T
        }
    }
}
