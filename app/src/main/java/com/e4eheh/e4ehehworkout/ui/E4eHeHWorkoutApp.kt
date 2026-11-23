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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
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
import com.e4eheh.e4ehehworkout.ui.navigation.TopLevelTab
import com.e4eheh.e4ehehworkout.ui.screens.home.HomeScreen
import com.e4eheh.e4ehehworkout.ui.screens.profile.ProfileScreen
import com.e4eheh.e4ehehworkout.ui.screens.profile.ProfileViewModel
import com.e4eheh.e4ehehworkout.ui.screens.progress.ProgressScreen
import com.e4eheh.e4ehehworkout.ui.screens.session.ActiveSessionScreen
import com.e4eheh.e4ehehworkout.ui.screens.session.ActiveSessionViewModel
import com.e4eheh.e4ehehworkout.ui.screens.workout.WorkoutScreen
import com.e4eheh.e4ehehworkout.ui.screens.workout.WorkoutViewModel
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamColors
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamMotion
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryLargeStyle
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryStyle
import com.e4eheh.e4ehehworkout.ui.theme.E4eHeHWorkoutTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Root Batcomputer HUD application shell hosting 4-tab navigation, the + quick-action sheet, and live Session logging. */
@Composable
fun E4eHeHWorkoutApp(appContainer: AppContainer) {
    E4eHeHWorkoutTheme {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        val showBottomBar = currentRoute == null ||
            TopLevelTab.entries.any { it.route == currentRoute }
        val exercises by appContainer.exerciseRepository
            .observeActiveExercises()
            .collectAsStateWithLifecycle(initialValue = emptyList())
        var showQuickActionSheet by rememberSaveable { mutableStateOf(false) }
        var isStartingSession by rememberSaveable { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()

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
                        HomeScreen(
                            exerciseCount = exercises.size,
                            onNavigateToWorkout = { navigateToTab(TopLevelTab.WORKOUT) },
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
                        )
                    }
                    composable(TopLevelTab.PROGRESS.route) {
                        ProgressScreen()
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
                            ),
                        )
                        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                        ActiveSessionScreen(
                            uiState = uiState,
                            onAction = viewModel::onAction,
                            onExitSession = { navController.popBackStack() },
                        )
                    }
                }
            }

            if (showQuickActionSheet) {
                QuickActionSheet(
                    isStartingSession = isStartingSession,
                    onStartLiveBlankSession = {
                        if (!isStartingSession) {
                            isStartingSession = true
                            coroutineScope.launch(Dispatchers.Main.immediate) {
                                try {
                                    val created = appContainer.sessionRepository
                                        .createLiveBlankSession()
                                    showQuickActionSheet = false
                                    navController.navigate(ActiveSessionRoute.createRoute(created.id)) {
                                        launchSingleTop = true
                                    }
                                } finally {
                                    isStartingSession = false
                                }
                            }
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
            ArkhamHudPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quick_action_start_live_session")
                    .clickable(
                        enabled = !isStartingSession,
                        role = Role.Button,
                        onClick = onStartLiveBlankSession,
                    ),
                highlighted = true,
            ) {
                Text(
                    text = "Start Live Blank Session",
                    style = MaterialTheme.typography.titleMedium,
                    color = ArkhamColors.MintWhite,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "LIVE TIMER // AUTO-SAVED DRAFT",
                    style = ArkhamTelemetryStyle,
                    color = ArkhamColors.PhosphorGreen,
                )
            }
            QuickActionStandbyCard(
                title = "Log Backdated Session",
                subtitle = "HISTORY IMPORT PENDING MODULE",
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
private fun QuickActionStandbyCard(
    title: String,
    subtitle: String,
) {
    ArkhamHudPanel(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = ArkhamColors.MintWhite,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = ArkhamTelemetryStyle,
            color = ArkhamColors.MintMuted,
        )
    }
}
