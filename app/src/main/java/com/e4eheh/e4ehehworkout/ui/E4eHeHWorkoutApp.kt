package com.e4eheh.e4ehehworkout.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.e4eheh.e4ehehworkout.di.AppContainer
import com.e4eheh.e4ehehworkout.ui.components.ArkhamBottomNavBar
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudBackground
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudPanel
import com.e4eheh.e4ehehworkout.ui.components.ArkhamSecondaryButton
import com.e4eheh.e4ehehworkout.ui.navigation.TopLevelTab
import com.e4eheh.e4ehehworkout.ui.screens.home.HomeScreen
import com.e4eheh.e4ehehworkout.ui.screens.profile.ProfileScreen
import com.e4eheh.e4ehehworkout.ui.screens.progress.ProgressScreen
import com.e4eheh.e4ehehworkout.ui.screens.workout.ExerciseLibraryViewModel
import com.e4eheh.e4ehehworkout.ui.screens.workout.WorkoutScreen
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamColors
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamMotion
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryLargeStyle
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryStyle
import com.e4eheh.e4ehehworkout.ui.theme.E4eHeHWorkoutTheme

/** Root Batcomputer HUD application shell hosting 4-tab navigation and the + quick-action sheet. */
@Composable
fun E4eHeHWorkoutApp(appContainer: AppContainer) {
    E4eHeHWorkoutTheme {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        val exercises by appContainer.exerciseRepository
            .observeActiveExercises()
            .collectAsStateWithLifecycle(initialValue = emptyList())
        var showQuickActionSheet by rememberSaveable { mutableStateOf(false) }

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
                    ArkhamBottomNavBar(
                        currentRoute = currentRoute,
                        onSelectTab = navigateToTab,
                        onQuickActionClick = { showQuickActionSheet = true },
                    )
                },
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = TopLevelTab.HOME.route,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    enterTransition = { fadeIn(tween(ArkhamMotion.TransitionMs)) },
                    exitTransition = { fadeOut(tween(ArkhamMotion.RevealMs)) },
                    popEnterTransition = { fadeIn(tween(ArkhamMotion.TransitionMs)) },
                    popExitTransition = { fadeOut(tween(ArkhamMotion.RevealMs)) },
                ) {
                    composable(TopLevelTab.HOME.route) {
                        HomeScreen(
                            exerciseCount = exercises.size,
                            onNavigateToWorkout = { navigateToTab(TopLevelTab.WORKOUT) },
                        )
                    }
                    composable(TopLevelTab.WORKOUT.route) {
                        val viewModel: ExerciseLibraryViewModel = viewModel(
                            factory = ExerciseLibraryViewModel.Factory(appContainer.exerciseRepository),
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
                        ProfileScreen()
                    }
                }
            }

            if (showQuickActionSheet) {
                QuickActionSheet(
                    onDismiss = { showQuickActionSheet = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickActionSheet(
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
            QuickActionStandbyCard(
                title = "Start Live Blank Session",
                subtitle = "SESSION LOGGER PENDING MODULE",
            )
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
