package com.e4eheh.e4ehehworkout.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.e4eheh.e4ehehworkout.di.AppContainer
import com.e4eheh.e4ehehworkout.ui.components.ArkhamBottomNavBar
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudBackground
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudPanel
import com.e4eheh.e4ehehworkout.ui.components.ArkhamSecondaryButton
import com.e4eheh.e4ehehworkout.ui.navigation.ActiveSessionRoute
import com.e4eheh.e4ehehworkout.ui.navigation.SessionEditorRoute
import com.e4eheh.e4ehehworkout.ui.navigation.TopLevelTab
import com.e4eheh.e4ehehworkout.ui.screens.home.HomeScreen
import com.e4eheh.e4ehehworkout.ui.screens.home.HomeViewModel
import com.e4eheh.e4ehehworkout.ui.screens.profile.ProfileScreen
import com.e4eheh.e4ehehworkout.ui.screens.profile.ProfileViewModel
import com.e4eheh.e4ehehworkout.ui.screens.progress.ProgressScreen
import com.e4eheh.e4ehehworkout.ui.screens.progress.ProgressViewModel
import com.e4eheh.e4ehehworkout.ui.screens.session.ActiveSessionScreen
import com.e4eheh.e4ehehworkout.ui.screens.session.ActiveSessionViewModel
import com.e4eheh.e4ehehworkout.ui.screens.session.SessionEditorScreen
import com.e4eheh.e4ehehworkout.ui.screens.session.SessionEditorViewModel
import com.e4eheh.e4ehehworkout.ui.screens.workout.WorkoutScreen
import com.e4eheh.e4ehehworkout.ui.screens.workout.WorkoutViewModel
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamColors
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamMotion
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryLargeStyle
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryStyle
import com.e4eheh.e4ehehworkout.ui.theme.E4eHeHWorkoutTheme

/** Root Batcomputer HUD application shell hosting 4-tab navigation, the + quick-action sheet, and live Session logging. */
@Composable
fun E4eHeHWorkoutApp(appContainer: AppContainer) {
    E4eHeHWorkoutTheme {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        val showBottomBar = currentRoute == null ||
            TopLevelTab.entries.any { it.route == currentRoute }
        var showQuickActionSheet by rememberSaveable { mutableStateOf(false) }
        val appShellViewModel: AppShellViewModel = viewModel(
            factory = AppShellViewModel.Factory(appContainer.sessionRepository),
        )
        val shellUiState by appShellViewModel.uiState.collectAsStateWithLifecycle()

        LaunchedEffect(shellUiState.pendingSessionId) {
            val sessionId = shellUiState.pendingSessionId ?: return@LaunchedEffect
            showQuickActionSheet = false
            navController.navigate(ActiveSessionRoute.createRoute(sessionId)) {
                launchSingleTop = true
            }
            appShellViewModel.consumePendingSessionNavigation(sessionId)
        }

        val navigateToTab: (TopLevelTab) -> Unit = { tab ->
            navController.navigate(tab.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        ArkhamHudBackground {
            Scaffold(
                containerColor = Color.Transparent,
                contentColor = ArkhamColors.MintWhite,
                bottomBar = {
                    if (showBottomBar) {
                        ArkhamBottomNavBar(
                            currentRoute = currentRoute,
                            onSelectTab = navigateToTab,
                            onQuickActionClick = { showQuickActionSheet = true },
                        )
                    }
                },
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = TopLevelTab.HOME.route,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding),
                    enterTransition = { fadeIn(tween(ArkhamMotion.TRANSITION_MS)) },
                    exitTransition = { fadeOut(tween(ArkhamMotion.STATE_MS)) },
                    popEnterTransition = { fadeIn(tween(ArkhamMotion.TRANSITION_MS)) },
                    popExitTransition = { fadeOut(tween(ArkhamMotion.STATE_MS)) },
                ) {
                    composable(TopLevelTab.HOME.route) {
                        val viewModel: HomeViewModel = viewModel(
                            factory = HomeViewModel.Factory(
                                exerciseRepository = appContainer.exerciseRepository,
                                sessionRepository = appContainer.sessionRepository,
                                clock = appContainer.clock,
                            ),
                        )
                        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                        HomeScreen(
                            uiState = uiState,
                            onAction = viewModel::onAction,
                            onResumeSession = { sessionId ->
                                navController.navigate(ActiveSessionRoute.createRoute(sessionId)) {
                                    launchSingleTop = true
                                }
                            },
                            onNavigateToWorkout = { navigateToTab(TopLevelTab.WORKOUT) },
                            onOpenCompletedSession = { sessionId ->
                                navController.navigate(
                                    SessionEditorRoute.createDetailRoute(sessionId),
                                ) {
                                    launchSingleTop = true
                                }
                            },
                        )
                    }
                    composable(TopLevelTab.WORKOUT.route) {
                        val viewModel: WorkoutViewModel = viewModel(
                            factory = WorkoutViewModel.Factory(
                                exerciseRepository = appContainer.exerciseRepository,
                                routineRepository = appContainer.routineRepository,
                            ),
                        )
                        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                        WorkoutScreen(
                            uiState = uiState,
                            onAction = viewModel::onAction,
                            onStartRoutineSession = appShellViewModel::startSessionFromRoutine,
                        )
                    }
                    composable(TopLevelTab.PROGRESS.route) {
                        val viewModel: ProgressViewModel = viewModel(
                            factory = ProgressViewModel.Factory(
                                sessionRepository = appContainer.sessionRepository,
                                clock = appContainer.clock,
                            ),
                        )
                        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                        ProgressScreen(
                            uiState = uiState,
                            onAction = viewModel::onAction,
                        )
                    }
                    composable(TopLevelTab.PROFILE.route) {
                        val viewModel: ProfileViewModel = viewModel(
                            factory = ProfileViewModel.Factory(appContainer.userPreferencesRepository),
                        )
                        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                        ProfileScreen(
                            uiState = uiState,
                            onAction = viewModel::onAction,
                        )
                    }
                    composable(
                        route = ActiveSessionRoute.PATTERN,
                        arguments = listOf(
                            navArgument(ActiveSessionRoute.ARG_SESSION_ID) {
                                type = NavType.StringType
                            },
                        ),
                    ) { backStackEntry ->
                        val sessionId = backStackEntry.arguments
                            ?.getString(ActiveSessionRoute.ARG_SESSION_ID)
                            ?: return@composable
                        val viewModel: ActiveSessionViewModel = viewModel(
                            factory = ActiveSessionViewModel.Factory(
                                sessionId = sessionId,
                                sessionRepository = appContainer.sessionRepository,
                                exerciseRepository = appContainer.exerciseRepository,
                                userPreferencesRepository = appContainer.userPreferencesRepository,
                                clock = appContainer.clock,
                                routineRepository = appContainer.routineRepository,
                            ),
                        )
                        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                        ActiveSessionScreen(
                            uiState = uiState,
                            onAction = viewModel::onAction,
                            onExitSession = { navController.popBackStack() },
                        )
                    }
                    composable(
                        route = SessionEditorRoute.PATTERN,
                        arguments = listOf(
                            navArgument(SessionEditorRoute.ARG_SESSION_ID) {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                    ) { backStackEntry ->
                        val sessionId = backStackEntry.arguments
                            ?.getString(SessionEditorRoute.ARG_SESSION_ID)
                        val viewModel: SessionEditorViewModel = viewModel(
                            factory = SessionEditorViewModel.Factory(
                                sessionId = sessionId,
                                sessionRepository = appContainer.sessionRepository,
                                exerciseRepository = appContainer.exerciseRepository,
                                userPreferencesRepository = appContainer.userPreferencesRepository,
                                clock = appContainer.clock,
                                routineRepository = appContainer.routineRepository,
                            ),
                        )
                        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                        SessionEditorScreen(
                            uiState = uiState,
                            onAction = viewModel::onAction,
                            onExit = { navController.popBackStack() },
                        )
                    }
                }
            }

            if (showQuickActionSheet) {
                QuickActionSheet(
                    isStartingSession = shellUiState.isStartingSession,
                    onStartLiveBlankSession = appShellViewModel::startLiveBlankSession,
                    onLogBackdatedSession = {
                        showQuickActionSheet = false
                        navController.navigate(SessionEditorRoute.createBackdatedRoute()) {
                            launchSingleTop = true
                        }
                    },
                    onDismiss = { showQuickActionSheet = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickActionSheet(
    isStartingSession: Boolean,
    onStartLiveBlankSession: () -> Unit,
    onLogBackdatedSession: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ArkhamColors.ObsidianPanel,
        contentColor = ArkhamColors.MintWhite,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "QUICK ACTION",
                style = ArkhamTelemetryLargeStyle,
                color = ArkhamColors.PhosphorGreen,
            )
            QuickActionCard(
                title = "Start Live Blank Session",
                subtitle = "LIVE TIMER // AUTO-SAVED DRAFT",
                highlighted = true,
                enabled = !isStartingSession,
                subtitleColor = ArkhamColors.PhosphorGreen,
                testTag = "quick_action_start_live_session",
                onClick = onStartLiveBlankSession,
            )
            QuickActionCard(
                title = "Log Backdated Session",
                subtitle = "PAST DATE & TIME // COMPLETED SNAPSHOT",
                highlighted = false,
                enabled = !isStartingSession,
                subtitleColor = ArkhamColors.MintMuted,
                testTag = "quick_action_log_backdated_session",
                onClick = onLogBackdatedSession,
            )
            Spacer(modifier = Modifier.height(4.dp))
            ArkhamSecondaryButton(
                text = "Dismiss",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    highlighted: Boolean,
    enabled: Boolean,
    subtitleColor: Color,
    testTag: String,
    onClick: () -> Unit,
) {
    ArkhamHudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        highlighted = highlighted,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = ArkhamColors.MintWhite,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = ArkhamTelemetryStyle,
            color = subtitleColor,
        )
    }
}
