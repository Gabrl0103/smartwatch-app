package com.rumbo.watchapp.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import com.rumbo.watchapp.application.usecase.TrackingState
import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.model.RouteStep
import com.rumbo.watchapp.domain.navigation.NavigationStatus
import com.rumbo.watchapp.domain.vibration.VibrationPatterns
import com.rumbo.watchapp.domain.vibration.VibrationSettings
import com.rumbo.watchapp.presentation.screens.ConfirmScreen
import com.rumbo.watchapp.presentation.screens.NavigationScreen
import com.rumbo.watchapp.presentation.screens.VibrationsScreen
import com.rumbo.watchapp.presentation.theme.WatchappTheme
import com.rumbo.watchapp.presentation.viewmodel.SaveStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** El micrófono en Confirmar, "Guardado ✓" en la ruta y las vibraciones de resultado. */
class VoiceCommandScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val route = Route(
        destinationName = "Parque Bolívar",
        distanceM = 1791.0,
        durationS = 1433.0,
        steps = listOf(RouteStep(1, Maneuver.ARRIVE, "Ha llegado.", 0.0, 1.206129, -77.27007)),
        polyline = listOf(Coordinates(1.214997, -77.278419), Coordinates(1.206129, -77.27007)),
    )

    private fun showRoute(saved: Boolean) {
        compose.setContent {
            WatchappTheme {
                AppScaffold {
                    NavigationScreen(
                        state = TrackingState(
                            status = NavigationStatus.ON_ROUTE,
                            destinationName = "Parque Bolívar",
                            nextInstruction = "Gire a la derecha",
                            nextManeuver = Maneuver.RIGHT,
                            distanceToNextStepM = 150.0,
                            stepLengthM = 300.0,
                            remainingM = 800.0,
                            destinationSaved = saved,
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

    private fun showConfirm(save: SaveStatus, onSpeak: () -> Unit = {}) {
        compose.setContent {
            WatchappTheme {
                AppScaffold {
                    ConfirmScreen(route, save, gpsAvailable = true, onGo = {}, onChange = {}, onSave = {}, onSpeak = onSpeak)
                }
            }
        }
    }

    @Test
    fun confirmHasAMicOfAtLeastFiftyTwoDp() {
        var spoken = 0
        showConfirm(SaveStatus.IDLE) { spoken++ }

        val mic = compose.onNodeWithText("Hablar", useUnmergedTree = true).performScrollTo()
        mic.assertIsDisplayed()
        mic.performClick()
        compose.waitForIdle()

        assertEquals(1, spoken)
    }

    @Test
    fun theMicKeepsTheMinimumTouchSize() {
        showConfirm(SaveStatus.IDLE)

        // El tamaño de layout: al pasar por el borde redondo el botón se ve más chico, pero solo un momento.
        val height = compose.onNodeWithText("Hablar").fetchSemanticsNode().size.height

        assertTrue("Alto $height px", height >= with(compose.density) { 52.dp.toPx() })
    }

    @Test
    fun confirmSaysSavedWithTheCheck() {
        showConfirm(SaveStatus.SAVED)

        compose.onNodeWithText("Guardado ✓").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aSavedDestinationSaysSoDuringTheRouteAndToTalkBack() {
        showRoute(saved = true)

        compose.onNodeWithContentDescription("Destino guardado").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun anUnsavedDestinationSaysNothing() {
        showRoute(saved = false)

        assertEquals(0, compose.onAllNodesWithText("Guardado ✓").fetchSemanticsNodes().size)
    }

    @Test
    fun theResultVibrationsCanBeTried() {
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

        compose.onNodeWithText("Hecho").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2 pulsos cortos").assertIsDisplayed()
        compose.onNodeWithText("Error").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1 pulso extra largo").assertIsDisplayed()
    }
}
