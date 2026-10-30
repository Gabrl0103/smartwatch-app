package com.rumbo.watchapp.application.speech

import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.model.RouteStep
import com.rumbo.watchapp.domain.navigation.NavigationEvent
import com.rumbo.watchapp.domain.navigation.NavigationSnapshot
import com.rumbo.watchapp.domain.navigation.NavigationStatus
import com.rumbo.watchapp.domain.port.SpeechPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeechPhrasesTest {

    private val turn = RouteStep(2, Maneuver.LEFT, "Gire a la izquierda hacia the crosswalk", 120.0, 1.21, -77.28, "Calle 18")
    private val route = Route(
        destinationName = "Parque Bolívar",
        distanceM = 1649.0,
        durationS = 1556.0,
        steps = listOf(
            RouteStep(1, Maneuver.STRAIGHT, "Camine hacia el norte por la Carrera 24.", 300.0, 1.2, -77.2),
            turn,
        ),
        polyline = emptyList(),
    )

    private fun snapshot(
        nextStep: RouteStep? = turn,
        distance: Double? = 34.0,
        status: NavigationStatus = NavigationStatus.TURN_APPROACHING,
        currentStreet: String = "Carrera 25",
    ) = NavigationSnapshot(status, nextStep, distance, 120.0, 500.0, 100.0, 2.0, currentStreet)

    @Test
    fun numbersInWords() {
        assertEquals("uno", numberInWords(1))
        assertEquals("quince", numberInWords(15))
        assertEquals("veintidós", numberInWords(22))
        assertEquals("treinta", numberInWords(30))
        assertEquals("cuarenta y siete", numberInWords(47))
        assertEquals("cien", numberInWords(100))
        assertEquals("ciento cinco", numberInWords(105))
        assertEquals("quinientos cincuenta", numberInWords(550))
        assertEquals("novecientos noventa y nueve", numberInWords(999))
        assertEquals("1200", numberInWords(1200))
    }

    @Test
    fun numbersBeforeNounDropTheFinalO() {
        assertEquals("un minuto", countOf(1, "minuto", "minutos"))
        assertEquals("veintiún minutos", countOf(21, "minuto", "minutos"))
        assertEquals("treinta y un minutos", countOf(31, "minuto", "minutos"))
        assertEquals("ciento un metros", countOf(101, "metro", "metros"))
        assertEquals("doce minutos", countOf(12, "minuto", "minutos"))
    }

    @Test
    fun shortDistancesRoundToTensOfMeters() {
        assertEquals("treinta metros", spokenDistance(34.0))
        assertEquals("cincuenta metros", spokenDistance(48.0))
        assertEquals("diez metros", spokenDistance(3.0))
        assertEquals("cien metros", spokenDistance(97.0))
    }

    @Test
    fun mediumDistancesRoundToFiftyMeters() {
        assertEquals("ochocientos cincuenta metros", spokenDistance(838.0))
        assertEquals("doscientos metros", spokenDistance(210.0))
    }

    @Test
    fun longDistancesUseKilometers() {
        assertEquals("un kilómetro", spokenDistance(980.0))
        assertEquals("un kilómetro", spokenDistance(1020.0))
        assertEquals("un kilómetro y medio", spokenDistance(1500.0))
        assertEquals("uno coma seis kilómetros", spokenDistance(1649.0))
        assertEquals("dos kilómetros", spokenDistance(1960.0))
        assertEquals("dos kilómetros y medio", spokenDistance(2540.0))
        assertEquals("veintiún kilómetros", spokenDistance(21_000.0))
    }

    @Test
    fun minutesRoundUpAndNeverZero() {
        assertEquals("veintiséis minutos", spokenMinutes(1556.0))
        assertEquals("un minuto", spokenMinutes(20.0))
        assertEquals("un minuto", spokenMinutes(60.0))
        assertEquals("dos minutos", spokenMinutes(61.0))
    }

    @Test
    fun confirmPhraseHasDestinationDistanceAndMinutes() {
        assertEquals(
            "Parque Bolívar, a uno coma seis kilómetros, veintiséis minutos a pie. ¿Vamos?",
            confirmPhrase(route),
        )
    }

    @Test
    fun startPhraseReadsTheFirstInstruction() {
        val turnClose = snapshot(status = NavigationStatus.ON_ROUTE, distance = 60.0)

        assertEquals("Iniciando ruta. Camine hacia el norte por la Carrera 24.", startPhrase(route, turnClose, 80.0))
        assertEquals("Iniciando ruta.", startPhrase(route.copy(steps = emptyList()), turnClose, 80.0))
    }

    @Test
    fun startPhraseIsAlsoTheFarStageOfTheFirstTurn() {
        val turnFar = snapshot(status = NavigationStatus.ON_ROUTE, distance = 300.0)

        assertEquals(
            "Iniciando ruta. Camine hacia el norte por la Carrera 24. En trescientos metros, gira a la izquierda.",
            startPhrase(route, turnFar, 80.0),
        )
    }

    @Test
    fun startPhraseDoesNotRepeatAFirstStepThatIsAlreadyTheTurn() {
        val onlyTurn = route.copy(steps = listOf(turn))

        assertEquals(
            "Iniciando ruta. Gire a la izquierda hacia the crosswalk.",
            startPhrase(onlyTurn, snapshot(distance = 300.0), 80.0),
        )
    }

    @Test
    fun farStageSaysTheCurrentStreetAndTheTurnWithoutItsStreet() {
        assertEquals(
            "Sigue recto por Carrera 25. En ciento cincuenta metros, gira a la izquierda.",
            turnAheadPhrase(150.0, turn, "Carrera 25"),
        )
    }

    @Test
    fun farStageWithoutStreetKnown() {
        assertEquals("Sigue recto. En doscientos metros, gira a la izquierda.", turnAheadPhrase(203.0, turn, ""))
    }

    @Test
    fun farStageRoundsLikeEveryOtherDistance() {
        assertEquals("Sigue recto. En un kilómetro y medio, gira a la izquierda.", turnAheadPhrase(1508.0, turn, ""))
        assertEquals("Sigue recto. En uno coma tres kilómetros, gira a la izquierda.", turnAheadPhrase(1304.0, turn, ""))
    }

    @Test
    fun approachStageSaysTheStreetFromTheDataNotTheProviderText() {
        assertEquals("En cincuenta metros, gira a la izquierda en Calle 18.", turnApproachingPhrase(48.0, turn))
    }

    @Test
    fun approachStageWithoutStreetKnown() {
        assertEquals("En cincuenta metros, gira a la izquierda.", turnApproachingPhrase(50.0, turn.copy(street = "")))
    }

    @Test
    fun nowStageIsShort() {
        assertEquals("Gira ahora a la izquierda.", turnNowPhrase(Maneuver.LEFT))
        assertEquals("Gira ahora a la derecha.", turnNowPhrase(Maneuver.RIGHT))
    }

    @Test
    fun navigationEventsMapToPhrasesWithPriority() {
        assertEquals(
            Phrase("En treinta metros, gira a la izquierda en Calle 18.", SpeechPriority.HIGH),
            navigationPhrase(listOf(NavigationEvent.TurnApproaching(Maneuver.LEFT)), snapshot()),
        )
        assertEquals(
            Phrase("Gira ahora a la izquierda.", SpeechPriority.HIGH),
            navigationPhrase(listOf(NavigationEvent.TurnNow(Maneuver.LEFT)), snapshot()),
        )
        assertEquals(
            Phrase(OFF_ROUTE_PHRASE, SpeechPriority.HIGH),
            navigationPhrase(listOf(NavigationEvent.OffRoute), snapshot()),
        )
        assertEquals(
            Phrase(SIGNAL_LOST_PHRASE, SpeechPriority.NORMAL),
            navigationPhrase(listOf(NavigationEvent.SignalLost), snapshot()),
        )
        assertEquals(
            Phrase(SIGNAL_RECOVERED_PHRASE, SpeechPriority.NORMAL),
            navigationPhrase(listOf(NavigationEvent.SignalRecovered), snapshot()),
        )
    }

    @Test
    fun farStageHasNormalPriorityToNeverCutGiraAhora() {
        assertEquals(
            Phrase("Sigue recto por Carrera 25. En treinta metros, gira a la izquierda.", SpeechPriority.NORMAL),
            navigationPhrase(listOf(NavigationEvent.TurnAhead(Maneuver.LEFT)), snapshot()),
        )
    }

    @Test
    fun theClosestStageWinsWhenSeveralComeInTheSameReading() {
        val jump = listOf(NavigationEvent.TurnApproaching(Maneuver.LEFT), NavigationEvent.TurnNow(Maneuver.LEFT))
        assertEquals("Gira ahora a la izquierda.", navigationPhrase(jump, snapshot())?.text)

        val close = listOf(NavigationEvent.TurnAhead(Maneuver.LEFT), NavigationEvent.TurnApproaching(Maneuver.LEFT))
        assertEquals("En treinta metros, gira a la izquierda en Calle 18.", navigationPhrase(close, snapshot())?.text)
    }

    @Test
    fun arrivalWinsOverOtherEventsOfTheSameReading() {
        val events = listOf(NavigationEvent.SignalRecovered, NavigationEvent.NextStepChanged(turn), NavigationEvent.Arrived)

        assertEquals(Phrase(ARRIVED_PHRASE, SpeechPriority.HIGH), navigationPhrase(events, snapshot()))
    }

    @Test
    fun quietEventsSayNothing() {
        assertNull(navigationPhrase(listOf(NavigationEvent.BackOnRoute), snapshot()))
        assertNull(navigationPhrase(listOf(NavigationEvent.NextStepChanged(turn)), snapshot()))
        assertNull(navigationPhrase(emptyList(), snapshot()))
    }

    @Test
    fun repeatSaysTheWholeCurrentInstructionWithHighPriority() {
        assertEquals(
            Phrase("En treinta metros, gira a la izquierda en Calle 18.", SpeechPriority.HIGH),
            repeatPhrase(snapshot(status = NavigationStatus.ON_ROUTE)),
        )
        val arrive = RouteStep(3, Maneuver.ARRIVE, "Ha llegado.", 0.0, 1.2, -77.2)
        assertEquals("Tu destino está a cincuenta metros.", repeatPhrase(snapshot(arrive, 48.0)).text)
        assertEquals(KEEP_GOING_PHRASE, repeatPhrase(snapshot(nextStep = null, distance = null)).text)
    }

    @Test
    fun repeatDescribesOffRouteNoSignalAndArrival() {
        assertEquals(OFF_ROUTE_PHRASE, repeatPhrase(snapshot(status = NavigationStatus.OFF_ROUTE)).text)
        assertEquals(SIGNAL_LOST_PHRASE, repeatPhrase(snapshot(status = NavigationStatus.NO_SIGNAL)).text)
        assertEquals(ARRIVED_PHRASE, repeatPhrase(snapshot(status = NavigationStatus.ARRIVED)).text)
    }
}
