package com.e4eheh.e4ehehworkout.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.ui.components.ArkhamDeleteConfirmationDialog
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudBadge
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudChip
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudPanel
import com.e4eheh.e4ehehworkout.ui.components.ArkhamPrimaryButton
import com.e4eheh.e4ehehworkout.ui.components.ArkhamSecondaryButton
import com.e4eheh.e4ehehworkout.ui.components.ArkhamSectionHeader
import com.e4eheh.e4ehehworkout.ui.components.formatElapsedDuration
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamColors
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamShapes
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryLargeStyle
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryStyle
import java.util.Locale
import kotlinx.coroutines.delay

/** Batcomputer Home command center with greeting, START picker, weekly calendar, Routine carousel, and Recent Activity. */
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    isStartingSession: Boolean,
    onAction: (HomeAction) -> Unit,
    onResumeSession: (String) -> Unit,
    onStartBlankSession: () -> Unit,
    onStartRoutineSession: (String) -> Unit,
    onNavigateToWorkout: () -> Unit,
    onOpenCompletedSession: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(uiState.activeSessionBanner?.sessionId) {
        if (uiState.activeSessionBanner != null) {
            while (true) {
                delay(1_000L)
                onAction(HomeAction.RefreshElapsed)
            }
        }
    }

    uiState.pendingDiscardSession?.let { pending ->
        ArkhamDeleteConfirmationDialog(
            title = "DISCARD ACTIVE SESSION?",
            itemName = pending.displayTitle,
            message = "This will remove the in-progress Session draft and all logged Sets.",
            onDismiss = { onAction(HomeAction.DismissDiscardDialog) },
            onConfirm = { onAction(HomeAction.ConfirmDiscardActiveSession) },
            confirmLabel = "Discard",
            confirmTestTag = "home_discard_confirm_button",
            dismissTestTag = "home_discard_cancel_button",
        )
    }

    if (uiState.isStartPickerVisible) {
        HomeStartPickerSheet(
            routineCards = uiState.allRoutineCards,
            isStartingSession = isStartingSession,
            onStartRoutine = { routineId ->
                onAction(HomeAction.DismissStartPicker)
                onStartRoutineSession(routineId)
            },
            onStartBlankSession = {
                onAction(HomeAction.DismissStartPicker)
                onStartBlankSession()
            },
            onNavigateToWorkout = {
                onAction(HomeAction.DismissStartPicker)
                onNavigateToWorkout()
            },
            onDismiss = { onAction(HomeAction.DismissStartPicker) },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ArkhamSectionHeader(
            title = "BATCOMPUTER // HOME",
            telemetryLabel = String.format(Locale.US, "%02d EXERCISES", uiState.exerciseCount),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "WELCOME BACK, ${uiState.displayName}",
                style = MaterialTheme.typography.headlineMedium,
                color = ArkhamColors.MintWhite,
                modifier = Modifier.testTag("home_greeting_text"),
            )
            Text(
                text = "SYSTEM ONLINE // OFFLINE MODE",
                style = ArkhamTelemetryStyle,
                color = ArkhamColors.PhosphorGreenDim,
            )
        }

        uiState.activeSessionBanner?.let { banner ->
            ActiveSessionResumeBanner(
                banner = banner,
                isDiscarding = uiState.isDiscarding,
                discardError = uiState.discardError,
                onResume = { onResumeSession(banner.sessionId) },
                onDiscard = { onAction(HomeAction.RequestDiscardActiveSession) },
            )
        }

        StartHeroCard(
            suggestedRoutine = uiState.suggestedRoutine,
            enabled = !isStartingSession,
            onOpenPicker = { onAction(HomeAction.OpenStartPicker) },
        )

        WeeklyCalendarSection(
            calendar = uiState.weeklyCalendar,
            selectedDay = uiState.selectedCalendarDay,
            onSelectDate = { date -> onAction(HomeAction.SelectCalendarDate(date)) },
            onOpenCompletedSession = onOpenCompletedSession,
        )

        RoutineCarouselSection(
            selectedDiscipline = uiState.selectedDisciplineFilter,
            allRoutineCount = uiState.allRoutineCards.size,
            filteredRoutines = uiState.filteredRoutineCards,
            isStartingSession = isStartingSession,
            onSelectDiscipline = { discipline ->
                onAction(HomeAction.SelectDisciplineFilter(discipline))
            },
            onStartRoutine = onStartRoutineSession,
            onNavigateToWorkout = onNavigateToWorkout,
        )

        RecentActivitySection(
            completedSessions = uiState.completedSessions,
            hasActiveBanner = uiState.activeSessionBanner != null,
            onNavigateToWorkout = onNavigateToWorkout,
            onOpenCompletedSession = onOpenCompletedSession,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StartHeroCard(
    suggestedRoutine: HomeRoutineCardUi?,
    enabled: Boolean,
    onOpenPicker: () -> Unit,
) {
    ArkhamHudPanel(
        highlighted = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_start_hero_card")
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onOpenPicker,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "MISSION LAUNCH",
                style = ArkhamTelemetryStyle,
                color = ArkhamColors.PhosphorGreen,
            )
            Text(
                text = if (suggestedRoutine != null) "SUGGESTED READY" else "2-TAP DEPLOY",
                style = ArkhamTelemetryStyle,
                color = ArkhamColors.MintMuted,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (suggestedRoutine != null) {
            Text(
                text = "NEXT: ${suggestedRoutine.name}",
                style = MaterialTheme.typography.titleLarge,
                color = ArkhamColors.MintWhite,
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ArkhamHudBadge(
                    label = "SUGGESTED NEXT",
                    accentColor = ArkhamColors.TacticalAmber,
                )
                ArkhamHudBadge(
                    label = suggestedRoutine.discipline.displayName,
                    accentColor = ArkhamColors.PhosphorGreen,
                )
                ArkhamHudBadge(
                    label = suggestedRoutine.durationLabel,
                    accentColor = ArkhamColors.PhosphorGreenDim,
                )
            }
        } else {
            Text(
                text = "READY FOR DEPLOYMENT",
                style = MaterialTheme.typography.titleLarge,
                color = ArkhamColors.MintWhite,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Launch a live blank Session or build Routines on the Workout tab.",
                style = MaterialTheme.typography.bodyMedium,
                color = ArkhamColors.MintMuted,
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        ArkhamPrimaryButton(
            text = "START",
            enabled = enabled,
            onClick = onOpenPicker,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("home_start_button"),
        )
    }
}

@Composable
private fun WeeklyCalendarSection(
    calendar: WeeklyCalendarUi,
    selectedDay: SelectedCalendarDayUi?,
    onSelectDate: (java.time.LocalDate) -> Unit,
    onOpenCompletedSession: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_weekly_calendar_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ArkhamSectionHeader(
            title = "WEEKLY CADENCE",
            telemetryLabel = "${calendar.weekRangeLabel} // ${calendar.activeDayCount}/7",
        )

        ArkhamHudPanel(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                calendar.days.forEach { day ->
                    val isSelected = day.date == selectedDay?.date
                    val borderColor = when {
                        isSelected -> ArkhamColors.PhosphorGreen
                        day.isToday -> ArkhamColors.PhosphorGreenDim
                        else -> ArkhamColors.TerminalEmerald
                    }
                    val containerColor = when {
                        isSelected -> ArkhamColors.TerminalEmerald.copy(alpha = 0.45f)
                        day.isToday -> ArkhamColors.ObsidianElevated
                        else -> ArkhamColors.ObsidianPanel
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_calendar_day_${day.date}")
                            .selectable(
                                selected = isSelected,
                                onClick = { onSelectDate(day.date) },
                                role = Role.Tab,
                            ),
                        shape = ArkhamShapes.chip,
                        color = containerColor,
                        border = BorderStroke(1.dp, borderColor),
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = day.dayLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected || day.isToday) {
                                    ArkhamColors.PhosphorGreen
                                } else {
                                    ArkhamColors.MintMuted
                                },
                            )
                            Text(
                                text = String.format(Locale.US, "%02d", day.dayOfMonth),
                                style = ArkhamTelemetryStyle,
                                color = ArkhamColors.MintWhite,
                            )
                            Canvas(
                                modifier = Modifier
                                    .size(12.dp)
                                    .let { mod ->
                                        if (day.hasCompletedSessions) {
                                            mod.testTag("home_calendar_indicator_${day.date}")
                                        } else {
                                            mod
                                        }
                                    },
                            ) {
                                if (day.hasCompletedSessions) {
                                    drawCircle(
                                        color = ArkhamColors.PhosphorGreen.copy(alpha = 0.30f),
                                        radius = size.minDimension / 2f,
                                    )
                                    drawCircle(
                                        color = ArkhamColors.PhosphorGreen,
                                        radius = size.minDimension / 3.4f,
                                    )
                                } else {
                                    drawCircle(
                                        color = ArkhamColors.TerminalEmerald.copy(alpha = 0.40f),
                                        radius = size.minDimension / 4.5f,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (selectedDay == null) {
                Text(
                    text = "TAP ANY DAY TO INSPECT LOGGED SESSIONS",
                    style = ArkhamTelemetryStyle,
                    color = ArkhamColors.MintMuted,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_calendar_selected_day_panel"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = selectedDay.dayLabel,
                            style = ArkhamTelemetryStyle,
                            color = ArkhamColors.PhosphorGreen,
                        )
                        ArkhamHudBadge(
                            label = if (selectedDay.sessions.size == 1) {
                                "1 SESSION"
                            } else {
                                "${selectedDay.sessions.size} SESSIONS"
                            },
                            accentColor = if (selectedDay.sessions.isEmpty()) {
                                ArkhamColors.MintMuted
                            } else {
                                ArkhamColors.PhosphorGreen
                            },
                        )
                    }

                    if (selectedDay.sessions.isEmpty()) {
                        Text(
                            text = "NO SESSIONS LOGGED ON THIS DAY",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ArkhamColors.MintMuted,
                            modifier = Modifier.testTag("home_calendar_day_empty"),
                        )
                    } else {
                        selectedDay.sessions.forEach { session ->
                            key(session.sessionId) {
                                ArkhamHudPanel(
                                    contentPadding = PaddingValues(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("home_calendar_session_${session.sessionId}")
                                        .clickable(
                                            role = Role.Button,
                                            onClick = { onOpenCompletedSession(session.sessionId) },
                                        ),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = session.displayTitle,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = ArkhamColors.MintWhite,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 8.dp),
                                        )
                                        Text(
                                            text = formatElapsedDuration(session.durationSeconds),
                                            style = ArkhamTelemetryStyle,
                                            color = ArkhamColors.PhosphorGreen,
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        ArkhamHudBadge(
                                            label = session.discipline.displayName,
                                            accentColor = ArkhamColors.PhosphorGreenDim,
                                        )
                                        Text(
                                            text = session.dateTimeLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ArkhamColors.MintMuted,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutineCarouselSection(
    selectedDiscipline: Discipline?,
    allRoutineCount: Int,
    filteredRoutines: List<HomeRoutineCardUi>,
    isStartingSession: Boolean,
    onSelectDiscipline: (Discipline?) -> Unit,
    onStartRoutine: (String) -> Unit,
    onNavigateToWorkout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_routines_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ArkhamSectionHeader(
            title = "ROUTINES",
            telemetryLabel = String.format(Locale.US, "%02d READY", filteredRoutines.size),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .testTag("home_discipline_filter_row"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ArkhamHudChip(
                label = "All",
                selected = selectedDiscipline == null,
                onClick = { onSelectDiscipline(null) },
                modifier = Modifier.testTag("home_discipline_filter_ALL"),
            )
            Discipline.entries.forEach { discipline ->
                ArkhamHudChip(
                    label = discipline.displayName,
                    selected = selectedDiscipline == discipline,
                    onClick = { onSelectDiscipline(discipline) },
                    modifier = Modifier.testTag("home_discipline_filter_${discipline.name}"),
                )
            }
        }

        when {
            allRoutineCount == 0 -> {
                ArkhamHudPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_routines_empty_state"),
                ) {
                    Text(
                        text = "NO ROUTINES BUILT YET",
                        style = MaterialTheme.typography.titleMedium,
                        color = ArkhamColors.MintWhite,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Build your Push, Pull, Legs, or Conditioning Routines on the Workout tab to launch them directly from Home.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ArkhamColors.MintMuted,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ArkhamSecondaryButton(
                        text = "BUILD ROUTINE",
                        onClick = onNavigateToWorkout,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_routines_empty_cta"),
                    )
                }
            }

            filteredRoutines.isEmpty() -> {
                val disciplineLabel = selectedDiscipline?.displayName?.uppercase(Locale.US).orEmpty()
                ArkhamHudPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_routines_filter_empty_state"),
                ) {
                    Text(
                        text = "NO $disciplineLabel ROUTINES",
                        style = MaterialTheme.typography.titleMedium,
                        color = ArkhamColors.MintWhite,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "No saved Routines match this Discipline filter.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ArkhamColors.MintMuted,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ArkhamSecondaryButton(
                        text = "SHOW ALL ROUTINES",
                        onClick = { onSelectDiscipline(null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_routines_clear_filter_button"),
                    )
                }
            }

            else -> {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_routine_carousel"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        items = filteredRoutines,
                        key = { it.routineId },
                    ) { card ->
                        HomeRoutineCarouselCard(
                            card = card,
                            enabled = !isStartingSession,
                            onClick = { onStartRoutine(card.routineId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeRoutineCarouselCard(
    card: HomeRoutineCardUi,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ArkhamHudPanel(
        highlighted = card.isSuggestedNext,
        modifier = Modifier
            .width(240.dp)
            .testTag("home_routine_card_${card.routineId}")
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArkhamHudBadge(
                label = card.discipline.displayName,
                accentColor = ArkhamColors.PhosphorGreen,
            )
            if (card.isSuggestedNext) {
                ArkhamHudBadge(
                    label = "NEXT",
                    accentColor = ArkhamColors.TacticalAmber,
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = card.name,
            style = MaterialTheme.typography.titleMedium,
            color = ArkhamColors.MintWhite,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(10.dp))

        HomeRoutineMetadataBadges(
            card = card,
            showDisciplineBadge = false,
            exerciseCountTestTag = "home_routine_card_exercises_${card.routineId}",
            durationTestTag = "home_routine_card_duration_${card.routineId}",
        )

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "TAP TO LAUNCH >>",
            style = ArkhamTelemetryStyle,
            color = ArkhamColors.PhosphorGreen,
        )
    }
}

@Composable
private fun RecentActivitySection(
    completedSessions: List<CompletedSessionHistoryItem>,
    hasActiveBanner: Boolean,
    onNavigateToWorkout: () -> Unit,
    onOpenCompletedSession: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_recent_activity_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArkhamSectionHeader(
            title = "RECENT ACTIVITY",
            telemetryLabel = String.format(Locale.US, "%02d LOGGED", completedSessions.size),
        )

        if (completedSessions.isEmpty()) {
            ArkhamHudPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_recent_activity_empty_state"),
                highlighted = !hasActiveBanner,
            ) {
                Text(
                    text = "NO SESSIONS LOGGED YET",
                    style = MaterialTheme.typography.titleMedium,
                    color = ArkhamColors.MintWhite,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Blank-slate telemetry initialized. Build your Exercise catalog on the Workout tab or tap START above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ArkhamColors.MintMuted,
                )
                Spacer(modifier = Modifier.height(14.dp))
                ArkhamPrimaryButton(
                    text = "OPEN EXERCISE LIBRARY",
                    onClick = onNavigateToWorkout,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            completedSessions.forEach { item ->
                key(item.sessionId) {
                    CompletedSessionHistoryCard(
                        item = item,
                        onClick = { onOpenCompletedSession(item.sessionId) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompletedSessionHistoryCard(
    item: CompletedSessionHistoryItem,
    onClick: () -> Unit,
) {
    ArkhamHudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_completed_session_${item.sessionId}")
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                color = ArkhamColors.MintWhite,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            )
            Text(
                text = formatElapsedDuration(item.durationSeconds),
                style = ArkhamTelemetryLargeStyle,
                color = ArkhamColors.PhosphorGreen,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.dateTimeLabel,
            style = ArkhamTelemetryStyle,
            color = ArkhamColors.MintMuted,
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ArkhamHudBadge(
                label = item.discipline.displayName,
                accentColor = ArkhamColors.PhosphorGreen,
            )
            ArkhamHudBadge(
                label = String.format(Locale.US, "%02d EX", item.exerciseCount),
                accentColor = ArkhamColors.PhosphorGreenDim,
            )
            ArkhamHudBadge(
                label = "TAP TO INSPECT",
                accentColor = ArkhamColors.PhosphorGreenDim,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveSessionResumeBanner(
    banner: ActiveSessionBannerUi,
    isDiscarding: Boolean,
    discardError: String?,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
) {
    ArkhamHudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_resume_session_banner"),
        highlighted = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (discardError != null) {
                Text(
                    text = discardError,
                    style = MaterialTheme.typography.bodySmall,
                    color = ArkhamColors.AlertRed,
                    modifier = Modifier.testTag("home_discard_error"),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "RESUME ACTIVE SESSION",
                    style = ArkhamTelemetryStyle,
                    color = ArkhamColors.PhosphorGreen,
                )
                Text(
                    text = formatElapsedDuration(banner.elapsedSeconds),
                    style = ArkhamTelemetryLargeStyle,
                    color = ArkhamColors.PhosphorGreen,
                    modifier = Modifier.testTag("home_resume_session_elapsed"),
                )
            }

            Text(
                text = banner.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                color = ArkhamColors.MintWhite,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ArkhamHudBadge(
                    label = banner.discipline.displayName,
                    accentColor = ArkhamColors.PhosphorGreen,
                )
                ArkhamHudBadge(
                    label = String.format(
                        Locale.US,
                        "%02d EX // %02d/%02d SETS",
                        banner.exerciseCount,
                        banner.completedSetCount,
                        banner.totalSetCount,
                    ),
                    accentColor = ArkhamColors.PhosphorGreenDim,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ArkhamPrimaryButton(
                    text = "RESUME",
                    onClick = onResume,
                    enabled = !isDiscarding,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_resume_session_button"),
                )
                ArkhamSecondaryButton(
                    text = "DISCARD",
                    onClick = onDiscard,
                    enabled = !isDiscarding,
                    contentColor = ArkhamColors.AlertRed,
                    borderColor = ArkhamColors.AlertRed,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_discard_session_button"),
                )
            }
        }
    }
}
