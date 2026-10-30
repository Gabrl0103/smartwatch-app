package com.rumbo.watchapp.presentation

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.wear.compose.material3.AppScaffold
import com.rumbo.watchapp.application.usecase.TrackingState
import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.navigation.NavigationStatus
import com.rumbo.watchapp.presentation.screens.NavigationScreen
import com.rumbo.watchapp.presentation.theme.WatchappTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** El doble toque de "En ruta" con toques de verdad sobre la pantalla compuesta. */
class RouteDoubleTapTest {

    @get:Rule
    val compose = createComposeRule()

    private var repeats = 0
    private var finishes = 0

    private val state = TrackingState(
        status = NavigationStatus.ON_ROUTE,
        destinationName = "Parque Bolívar",
        nextInstruction = "Gire a la derecha",
        nextManeuver = Maneuver.RIGHT,
        distanceToNextStepM = 150.0,
        stepLengthM = 300.0,
        remainingM = 800.0,
    )

    @Before
    fun showTheRoute() {
        compose.setContent {
            WatchappTheme {
                AppScaffold {
                    NavigationScreen(
                        state = state,
                        confirmFinish = false,
                        onRepeat = { repeats++ },
                        onFinish = { finishes++ },
                        onKeepNavigating = {},
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun afterTheDoubleTapWindow() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    @Test
    fun doubleTapOverTheInstructionRepeats() {
        compose.onNodeWithText("Gire a la derecha").performTouchInput { doubleClick() }
        afterTheDoubleTapWindow()

        assertEquals(1, repeats)
    }

    @Test
    fun doubleTapOverRepetirRepeatsOnlyOnce() {
        compose.onNodeWithText("Repetir").performScrollTo().performTouchInput { doubleClick() }
        afterTheDoubleTapWindow()

        assertEquals(1, repeats)
    }

    @Test
    fun doubleTapOverTerminarRepeatsAndNeverEndsTheRoute() {
        compose.onNodeWithText("Terminar").performScrollTo().performTouchInput { doubleClick() }
        afterTheDoubleTapWindow()

        assertEquals(0, finishes)
        assertEquals(1, repeats)
    }

    @Test
    fun aSingleTapOnTerminarStillEndsTheRoute() {
        compose.onNodeWithText("Terminar").performScrollTo().performClick()
        afterTheDoubleTapWindow()

        assertEquals(1, finishes)
        assertEquals(0, repeats)
    }

    @Test
    fun scrollingIsNotADoubleTap() {
        compose.onRoot().performTouchInput { swipeUp() }
        compose.onRoot().performTouchInput { swipeUp() }
        afterTheDoubleTapWindow()

        assertEquals(0, repeats)
    }

    @Test
    fun withTalkBackActivatingTheInformationRepeats() {
        // TalkBack no manda toques: llama a la acción de clic del elemento enfocado.
        listOf("En ruta", "Gire a la derecha").forEach { text ->
            val node = compose.onAllNodesWithText(text, useUnmergedTree = true)[0]
            node.assertHasClickAction()
            node.fetchSemanticsNode().config[SemanticsActions.OnClick].action?.invoke()
        }
        compose.waitForIdle()

        assertEquals(2, repeats)
    }

    @Test
    fun talkBackAnnouncesWhatActivatingDoes() {
        val hasLabel = SemanticsMatcher("dice que repite la indicación") {
            it.config.getOrNull(SemanticsActions.OnClick)?.label == "repetir la indicación"
        }

        compose.onNodeWithText("Gire a la derecha").assert(hasLabel)
    }
}
