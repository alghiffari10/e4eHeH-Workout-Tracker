package com.e4eheh.e4ehehworkout.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.e4eheh.e4ehehworkout.data.local.WorkoutDatabase
import com.e4eheh.e4ehehworkout.di.AppContainer
import com.e4eheh.e4ehehworkout.domain.model.DistanceUnit
import com.e4eheh.e4ehehworkout.domain.model.UserPreferences
import com.e4eheh.e4ehehworkout.domain.model.WeightUnit
import com.e4eheh.e4ehehworkout.testing.FakeClock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfilePreferencesTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun profileTab_displaysDefaultPreferencesAndOfflineOnlySyncPlaceholder() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = AppContainer.createInMemory(context, FakeClock())
        try {
            composeTestRule.setContent {
                E4eHeHWorkoutApp(appContainer = container)
            }

            composeTestRule.onNodeWithTag("nav_tab_profile").performClick()

            composeTestRule.onNodeWithText("// OPERATOR PROFILE").assertIsDisplayed()
            composeTestRule.onNodeWithText("CALLSIGN: OPERATOR").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("weight_unit_kg").performScrollTo().assertIsSelected()
            composeTestRule.onNodeWithTag("distance_unit_km").performScrollTo().assertIsSelected()
            composeTestRule.onNodeWithTag("profile_unit_preview").performScrollTo()
            composeTestRule.onNodeWithText("PREVIEW: 100 kg  //  5 km").assertIsDisplayed()
            composeTestRule.onNodeWithTag("profile_rest_timer_value").performScrollTo()
            composeTestRule.onNodeWithText("90s // 01:30").assertIsDisplayed()

            composeTestRule.onNodeWithTag("profile_sync_placeholder").performScrollTo()
            composeTestRule.onNode(
                hasText("Server & API Key Sync") and
                    hasAnyAncestor(hasTestTag("profile_sync_placeholder")),
            ).assertIsDisplayed()
            composeTestRule.onNode(
                hasText("OFFLINE ONLY") and
                    hasAnyAncestor(hasTestTag("profile_sync_placeholder")),
            ).assertIsDisplayed()
            composeTestRule.onNode(
                hasText("NETWORK: DISABLED // NO NETWORK CALLS") and
                    hasAnyAncestor(hasTestTag("profile_sync_placeholder")),
            ).assertIsDisplayed()
        } finally {
            container.close()
        }
    }

    @Test
    fun profileTab_editsPreferences_validatesBlankName_andUpdatesUnitConversionPreview() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = AppContainer.createInMemory(context, FakeClock())
        try {
            composeTestRule.setContent {
                E4eHeHWorkoutApp(appContainer = container)
            }

            composeTestRule.onNodeWithTag("nav_tab_profile").performClick()

            composeTestRule.onNodeWithTag("profile_display_name_input").performScrollTo().performTextClearance()
            composeTestRule.onNodeWithTag("profile_save_name_button").performScrollTo().performClick()
            composeTestRule.onNodeWithText("Display name is required.").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText("CALLSIGN: OPERATOR").performScrollTo().assertIsDisplayed()

            composeTestRule.onNodeWithTag("profile_display_name_input").performScrollTo().performTextInput("Nightwing")
            composeTestRule.onNodeWithTag("profile_save_name_button").performScrollTo().performClick()
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText("CALLSIGN: Nightwing")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeTestRule.onNodeWithText("CALLSIGN: Nightwing").performScrollTo().assertIsDisplayed()

            composeTestRule.onNodeWithTag("weight_unit_lb").performScrollTo().performClick()
            composeTestRule.onNodeWithTag("distance_unit_mi").performScrollTo().performClick()
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText("PREVIEW: 220.46 lb  //  3.11 mi")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeTestRule.onNodeWithTag("weight_unit_lb").performScrollTo().assertIsSelected()
            composeTestRule.onNodeWithTag("distance_unit_mi").performScrollTo().assertIsSelected()
            composeTestRule.onNodeWithTag("profile_unit_preview").performScrollTo()
            composeTestRule.onNodeWithText("PREVIEW: 220.46 lb  //  3.11 mi").assertIsDisplayed()

            composeTestRule.onNodeWithTag("rest_timer_preset_120s").performScrollTo().performClick()
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText("120s // 02:00")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeTestRule.onNodeWithTag("rest_timer_decrement").performScrollTo().performClick()
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText("105s // 01:45")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }

            val persisted = runBlocking { container.userPreferencesRepository.getPreferences() }
            assertEquals(
                UserPreferences(
                    displayName = "Nightwing",
                    weightUnit = WeightUnit.LB,
                    distanceUnit = DistanceUnit.MI,
                    defaultRestTimerSeconds = 105,
                ),
                persisted,
            )
        } finally {
            container.close()
        }
    }

    @Test
    fun profileTab_persistsUpdatedPreferencesAcrossSimulatedAppRestart() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clock = FakeClock()
        context.deleteDatabase(WorkoutDatabase.DATABASE_NAME)
        var activeContainer = AppContainer.create(context, clock)
        var appSession by mutableIntStateOf(0)

        try {
            composeTestRule.setContent {
                key(appSession) {
                    val owner = remember(appSession) {
                        object : ViewModelStoreOwner {
                            override val viewModelStore: ViewModelStore = ViewModelStore()
                        }
                    }
                    DisposableEffect(owner) {
                        onDispose { owner.viewModelStore.clear() }
                    }
                    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                        E4eHeHWorkoutApp(appContainer = activeContainer)
                    }
                }
            }

            composeTestRule.onNodeWithTag("nav_tab_profile").performClick()

            composeTestRule.onNodeWithTag("profile_display_name_input").performScrollTo().performTextClearance()
            composeTestRule.onNodeWithTag("profile_display_name_input").performTextInput("Oracle")
            composeTestRule.onNodeWithTag("profile_save_name_button").performScrollTo().performClick()
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText("CALLSIGN: Oracle")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeTestRule.onNodeWithTag("weight_unit_lb").performScrollTo().performClick()
            composeTestRule.onNodeWithTag("distance_unit_mi").performScrollTo().performClick()
            composeTestRule.onNodeWithTag("rest_timer_preset_60s").performScrollTo().performClick()
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText("60s // 01:00")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeTestRule.onNodeWithTag("rest_timer_increment")
                .performScrollTo()
                .assertIsDisplayed()
                .assertIsEnabled()
                .performClick()
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText("75s // 01:15")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }

            val expected = UserPreferences(
                displayName = "Oracle",
                weightUnit = WeightUnit.LB,
                distanceUnit = DistanceUnit.MI,
                defaultRestTimerSeconds = 75,
            )
            assertEquals(expected, runBlocking { activeContainer.userPreferencesRepository.getPreferences() })

            // Simulate full app restart with a fresh container and ViewModelStore.
            activeContainer.close()
            activeContainer = AppContainer.create(context, clock)
            composeTestRule.runOnIdle {
                appSession += 1
            }

            composeTestRule.onNodeWithTag("nav_tab_profile").performClick()
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText("CALLSIGN: Oracle")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeTestRule.onNodeWithText("CALLSIGN: Oracle").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("weight_unit_lb").performScrollTo().assertIsSelected()
            composeTestRule.onNodeWithTag("distance_unit_mi").performScrollTo().assertIsSelected()
            composeTestRule.onNodeWithTag("profile_unit_preview").performScrollTo()
            composeTestRule.onNodeWithText("PREVIEW: 220.46 lb  //  3.11 mi").assertIsDisplayed()
            composeTestRule.onNodeWithTag("profile_rest_timer_value").performScrollTo()
            composeTestRule.onNodeWithText("75s // 01:15").assertIsDisplayed()
        } finally {
            activeContainer.close()
            context.deleteDatabase(WorkoutDatabase.DATABASE_NAME)
        }
    }
}
