package com.rumbo.watchapp.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import com.rumbo.watchapp.application.usecase.TrackingState
import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.model.RouteStep
import com.rumbo.watchapp.domain.navigation.NavigationStatus
import com.rumbo.watchapp.presentation.screens.NavigationScreen
import com.rumbo.watchapp.presentation.screens.RouteMapScreen
import com.rumbo.watchapp.presentation.theme.WatchappTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** La pantalla Mapa y el botón que lleva a ella, con toques de verdad. */
class RouteMapScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val start = Coordinates(1.2150, -77.2784)
    private val end = Coordinates(1.2160, -77.2784)
    private val route = Route(
        destinationName = "Parque Bolívar",
        distanceM = 111.0,
        durationS = 89.0,
        steps = listOf(RouteStep(1, Maneuver.ARRIVE, "Ha llegado.", 0.0, end.lat, end.lng)),
        polyline = listOf(start, end),
    )
    private val walking = TrackingState(
        status = NavigationStatus.ON_ROUTE,
        destinationName = "Parque Bolívar",
        nextInstruction = "Gire a la derecha.",
        nextManeuver = Maneuver.RIGHT,
        distanceToNextStepM = 30.0,
        stepLengthM = 100.0,
        remainingM = 80.0,
        route = route,
        position = start,
        headingDeg = 0.0,
        progressM = 30.0,
    )
    private var backs = 0

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            WatchappTheme {
                AppScaffold { content() }
            }
        }
        compose.waitForIdle()
    }

    private fun showMap(state: TrackingState = walking) = show { RouteMapScreen(state, onBack = { backs++ }) }

    @Test
    fun theMapShowsTheStateInWordsAndDescribesItselfForTalkBack() {
        showMap()

        compose.onNodeWithText("En ruta").assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "Mapa de cerca. En ruta. Gire a la derecha. Faltan 80 metros",
        ).assertExists()
    }

    @Test
    fun offRouteIsSaidInWords() {
        showMap(walking.copy(status = NavigationStatus.OFF_ROUTE))

        compose.onNodeWithText("Fuera de ruta").assertIsDisplayed()
    }

    @Test
    fun noSignalIsSaidInWords() {
        showMap(walking.copy(status = NavigationStatus.NO_SIGNAL))

        compose.onNodeWithText("Sin señal").assertIsDisplayed()
    }

    @Test
    fun arrivalIsSaidInWordsAndDoesNotKeepGuiding() {
        showMap(walking.copy(status = NavigationStatus.ARRIVED))

        compose.onNodeWithText("Llegaste").assertIsDisplayed()
        compose.onNodeWithContentDescription("Mapa de cerca. Llegaste").assertExists()
    }

    @Test
    fun aMapWithoutRouteStillShowsItsControls() {
        showMap(TrackingState())

        compose.onNodeWithText("Atrás").assertIsDisplayed()
    }

    @Test
    fun backIsTheButtonOnTheMapAndFitsAFinger() {
        showMap()

        compose.onNodeWithText("Atrás").assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(1, backs)
    }

    @Test
    fun theZoomButtonSwitchesBetweenBothViews() {
        showMap()

        compose.onNodeWithContentDescription("Ver toda la ruta").assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithContentDescription("Mapa de toda la ruta", substring = true).assertExists()

        compose.onNodeWithContentDescription("Ver de cerca").performClick()
        compose.onNodeWithContentDescription("Mapa de cerca", substring = true).assertExists()
    }

    @Test
    fun theRouteHasAMapButton() {
        var maps = 0
        show {
            NavigationScreen(
                state = walking,
                confirmFinish = false,
                onRepeat = {},
                onFinish = {},
                onKeepNavigating = {},
                onMap = { maps++ },
            )
        }
        repeat(3) {
            compose.onRoot().performTouchInput { swipeUp() }
            compose.waitForIdle()
        }

        compose.onNodeWithText("Ver mapa").assertHeightIsAtLeast(48.dp).performClick()
        // Espera a que pase la ventana del doble toque de En ruta.
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        assertEquals(1, maps)
    }
}
