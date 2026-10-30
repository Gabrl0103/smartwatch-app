package com.rumbo.watchapp.application.usecase

import com.rumbo.watchapp.application.speech.DESTINATION_ALREADY_SAVED_PHRASE
import com.rumbo.watchapp.application.speech.DESTINATION_JUST_SAVED_PHRASE
import com.rumbo.watchapp.application.speech.DESTINATION_NOT_SAVED_PHRASE
import com.rumbo.watchapp.application.speech.DESTINATION_SAVED_PHRASE
import com.rumbo.watchapp.application.speech.confirmPhrase
import com.rumbo.watchapp.domain.error.AppError
import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.Place
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.model.SavedPlace
import com.rumbo.watchapp.domain.model.VoiceCommand
import com.rumbo.watchapp.domain.model.VoiceResult
import com.rumbo.watchapp.domain.navigation.NavigationStatus
import com.rumbo.watchapp.domain.port.AssistantRepository
import com.rumbo.watchapp.domain.port.NavigationRunner
import com.rumbo.watchapp.domain.port.PlacesRepository
import com.rumbo.watchapp.domain.port.RoutesRepository
import com.rumbo.watchapp.domain.port.SavedPlacesRepository
import com.rumbo.watchapp.domain.port.VoiceRepository
import com.rumbo.watchapp.testing.BASE
import com.rumbo.watchapp.testing.lRoute
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecuteVoiceCommandTest {

    private val route = lRoute()
    private val savedRoute = route.copy(destinationSaved = true)

    private object NoVoice : VoiceRepository {
        override suspend fun interpret(audioFile: File): VoiceResult = throw IllegalStateException()
    }

    private class FakePlaces(private val found: Boolean = true) : PlacesRepository {
        override suspend fun search(query: String, near: Coordinates) =
            if (found) Place(query, near.lat, near.lng) else null
    }

    private class FakeRoutes(private val route: Route) : RoutesRepository {
        override suspend fun create(origin: Coordinates, destination: Place) = route
    }

    private class FakeSavedPlaces(var places: List<SavedPlace> = emptyList()) : SavedPlacesRepository {
        val added = mutableListOf<String>()
        var error: AppError? = null

        override suspend fun list(): List<SavedPlace> {
            error?.let { throw it }
            return places
        }

        override suspend fun add(name: String, lat: Double, lng: Double): SavedPlace {
            error?.let { throw it }
            added += name
            return SavedPlace(1, name, lat, lng)
        }

        override suspend fun delete(id: Long) = Unit
    }

    private class FakeAssistant : AssistantRepository {
        val asked = mutableListOf<String>()

        override suspend fun ask(question: String, context: String): String {
            asked += question
            return "Sí."
        }
    }

    private class FakeRunner(override var activeRoute: Route? = null) : NavigationRunner {
        var marked = 0

        override fun start(route: Route) = Unit

        override fun stop() = Unit

        override fun markDestinationSaved() {
            marked++
            activeRoute = activeRoute?.copy(destinationSaved = true)
        }
    }

    private val saved = FakeSavedPlaces()
    private val assistant = FakeAssistant()
    private var tracking = TrackingState(remainingM = 200.0)
    private var repeats = 0

    private fun execute(
        command: VoiceCommand,
        active: Route? = null,
        confirming: Route? = null,
        places: PlacesRepository = FakePlaces(),
        runner: FakeRunner = FakeRunner(active),
    ): VoiceOutcome = runBlocking {
        val routes = FakeRoutes(route)
        ExecuteVoiceCommand(
            PlanRouteFromVoice(NoVoice, places, routes) { BASE },
            ManageSavedPlaces(saved, routes) { BASE },
            AskAssistant(assistant) { tracking },
            runner,
            tracking = { tracking },
            repeatInstruction = { repeats++ },
        )(command, confirming)
    }

    @Test
    fun aQuestionGoesToTheAssistantWhereverItIsAsked() {
        val answer = VoiceOutcome.Answer("¿Qué calle es?", "Sí.")

        assertEquals(answer, execute(VoiceCommand.Ask("¿Qué calle es?")))
        assertEquals(answer, execute(VoiceCommand.Ask("¿Qué calle es?"), active = route))
        assertEquals(answer, execute(VoiceCommand.Ask("¿Qué calle es?"), confirming = route))
    }

    @Test
    fun aDestinationGivesARouteToConfirm() {
        assertEquals(VoiceOutcome.Confirm(route), execute(VoiceCommand.Navigate("parque")))
    }

    @Test
    fun aDestinationThatIsAlreadySavedComesMarked() {
        saved.places = listOf(SavedPlace(1, route.destinationName.uppercase(), 0.0, 0.0))

        assertEquals(VoiceOutcome.Confirm(savedRoute), execute(VoiceCommand.Navigate("parque")))
    }

    @Test
    fun withoutNetworkTheSavedListDoesNotStopTheRoute() {
        saved.error = AppError("Sin conexión")

        assertEquals(VoiceOutcome.Confirm(route), execute(VoiceCommand.Navigate("parque")))
    }

    @Test
    fun aPlaceThatDoesNotExistFailsAsAnAppError() {
        assertThrows(AppError::class.java) { execute(VoiceCommand.Navigate("Xyzzy"), places = FakePlaces(found = false)) }
    }

    @Test
    fun aRouteInProgressIsNotReplacedByVoice() {
        val outcome = execute(VoiceCommand.Navigate("plaza"), active = route)

        assertEquals(
            VoiceOutcome.Refused("Ya vas hacia Parque de prueba. Para ir a otro lugar, termina esta ruta."),
            outcome,
        )
    }

    @Test
    fun repeatDependsOnWhereItIsSaid() {
        assertEquals(VoiceOutcome.Repeated, execute(VoiceCommand.Repeat, active = route))
        assertEquals(1, repeats)
        assertEquals(VoiceOutcome.Done(confirmPhrase(route)), execute(VoiceCommand.Repeat, confirming = route))
        assertEquals(VoiceOutcome.Refused(NOTHING_TO_REPEAT_MESSAGE), execute(VoiceCommand.Repeat))
        assertEquals(1, repeats)
    }

    @Test
    fun cancelDependsOnWhereItIsSaid() {
        assertEquals(VoiceOutcome.AskToFinish, execute(VoiceCommand.Cancel, active = route))
        assertEquals(VoiceOutcome.DestinationDropped, execute(VoiceCommand.Cancel, confirming = route))
        assertEquals(VoiceOutcome.Refused(NOTHING_TO_CANCEL_MESSAGE), execute(VoiceCommand.Cancel))
    }

    @Test
    fun savingInConfirmDoesNotTouchTheNavigation() {
        val runner = FakeRunner()

        val outcome = execute(VoiceCommand.SaveDestination, confirming = route, runner = runner)

        assertEquals(VoiceOutcome.Saved(DESTINATION_JUST_SAVED_PHRASE), outcome)
        assertEquals(listOf(route.destinationName), saved.added)
        assertEquals(0, runner.marked)
    }

    @Test
    fun savingOnRouteMarksTheRouteInProgress() {
        val runner = FakeRunner(route)

        val outcome = execute(VoiceCommand.SaveDestination, active = route, runner = runner)

        assertEquals(VoiceOutcome.Saved(DESTINATION_JUST_SAVED_PHRASE), outcome)
        assertEquals(1, runner.marked)
        assertEquals(savedRoute, runner.activeRoute)
    }

    @Test
    fun whatIsAlreadySavedIsNotSavedAgain() {
        assertEquals(
            VoiceOutcome.Done(DESTINATION_ALREADY_SAVED_PHRASE),
            execute(VoiceCommand.SaveDestination, active = savedRoute),
        )
        assertTrue(saved.added.isEmpty())
    }

    @Test
    fun aFailedSaveIsRefusedAndDoesNotMarkTheRoute() {
        saved.error = AppError("Sin conexión")
        val runner = FakeRunner(route)

        val outcome = execute(VoiceCommand.SaveDestination, active = route, runner = runner)

        assertEquals(VoiceOutcome.Refused(SAVE_FAILED_MESSAGE), outcome)
        assertEquals(0, runner.marked)
    }

    @Test
    fun withoutDestinationThereIsNothingToSave() {
        assertEquals(VoiceOutcome.Refused(NOTHING_TO_SAVE_MESSAGE), execute(VoiceCommand.SaveDestination))
    }

    @Test
    fun howFarOnRouteUsesWhatRemains() {
        assertEquals(
            VoiceOutcome.Done("Faltan doscientos metros, tres minutos a pie."),
            execute(VoiceCommand.HowFar, active = route),
        )
        assertTrue(assistant.asked.isEmpty())
    }

    @Test
    fun howFarAfterArrivingSaysSo() {
        tracking = TrackingState(status = NavigationStatus.ARRIVED)

        assertEquals(VoiceOutcome.Done("Ya llegaste."), execute(VoiceCommand.HowFar, active = route))
    }

    @Test
    fun howFarInConfirmIsTheWholeRoute() {
        assertEquals(
            VoiceOutcome.Done("Son cuatrocientos metros, cinco minutos a pie."),
            execute(VoiceCommand.HowFar, confirming = route),
        )
    }

    @Test
    fun howFarWithoutARouteIsRefused() {
        assertEquals(VoiceOutcome.Refused(NO_DESTINATION_MESSAGE), execute(VoiceCommand.HowFar))
    }

    @Test
    fun savedStatusAnswersBothWays() {
        assertEquals(VoiceOutcome.Done(DESTINATION_SAVED_PHRASE), execute(VoiceCommand.SavedStatus, active = savedRoute))
        assertEquals(VoiceOutcome.Done(DESTINATION_SAVED_PHRASE), execute(VoiceCommand.SavedStatus, confirming = savedRoute))
        assertEquals(VoiceOutcome.Done(DESTINATION_NOT_SAVED_PHRASE), execute(VoiceCommand.SavedStatus, active = route))
        assertEquals(VoiceOutcome.Done(DESTINATION_NOT_SAVED_PHRASE), execute(VoiceCommand.SavedStatus, confirming = route))
    }

    @Test
    fun savedStatusWithoutDestinationIsRefused() {
        assertEquals(VoiceOutcome.Refused(NO_DESTINATION_MESSAGE), execute(VoiceCommand.SavedStatus))
    }

    @Test
    fun notUnderstoodStaysNotUnderstood() {
        assertEquals(VoiceOutcome.NotUnderstood, execute(VoiceCommand.NotUnderstood))
    }
}
