package com.rumbo.watchapp.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.wear.compose.material3.AppScaffold
import com.rumbo.watchapp.application.usecase.TrackingState
import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.navigation.NavigationStatus
import com.rumbo.watchapp.domain.vibration.VibrationPatterns
import com.rumbo.watchapp.domain.vibration.VibrationSettings
import com.rumbo.watchapp.presentation.screens.NavigationScreen
import com.rumbo.watchapp.presentation.screens.VibrationsScreen
import com.rumbo.watchapp.presentation.theme.WatchappTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Lo que ve (y lee TalkBack) quien llega a un paso desconocido, y su vibración en la lista. */
class UnknownStepScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun showRoute(status: NavigationStatus, instruction: String) {
        compose.setContent {
            WatchappTheme {
                AppScaffold {
                    NavigationScreen(
                        state = TrackingState(
                            status = status,
                            destinationName = "Parque Bolívar",
                            nextInstruction = instruction,
                            nextManeuver = Maneuver.UNKNOWN,
                            distanceToNextStepM = 30.0,
                            stepLengthM = 300.0,
                            remainingM = 800.0,
                        ),
                        confirmFinish = false,
                        onRepeat = {},
                        onFinish = {},
                        onKeepNavigating = {},
                    )
                }
            }
        }
    }

    @Test
    fun theApproachShowsAttentionWithTheTextAndTheDistanceNotATurn() {
        showRoute(NavigationStatus.TURN_APPROACHING, "Atención: tome las escaleras.")

        compose.onNodeWithText("Atención").assertIsDisplayed()
        compose.onNodeWithText("Atención: tome las escaleras.").assertIsDisplayed()
        compose.onNodeWithContentDescription("En 30 metros", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Advertencia").performScrollTo().assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Giro próximo").fetchSemanticsNodes().size)
    }

    @Test
    fun theManeuverNeverSaysToTurn() {
        showRoute(NavigationStatus.MANEUVER, "Tome el ascensor a Level -1.")

        compose.onNodeWithText("Atención").assertIsDisplayed()
        compose.onNodeWithText("Tome el ascensor a Level -1.").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Gira ahora").fetchSemanticsNodes().size)
    }

    @Test
    fun attentionIsInTheVibrationsScreen() {
        compose.setContent {
            WatchappTheme {
                AppScaffold {
                    VibrationsScreen(
                        patterns = VibrationPatterns.forDevice(true),
                        supportsAmplitude = true,
                        settings = VibrationSettings(),
                        demo = null,
                        onPlay = {},
                        onChangeIntensity = {},
                        onChangeDuration = {},
                        onStartDemo = {},
                        onStopDemo = {},
                        onBack = {},
                    )
                }
            }
        }

        compose.onNodeWithText("Atención").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("4 pulsos cortos seguidos").assertIsDisplayed()
    }
}
