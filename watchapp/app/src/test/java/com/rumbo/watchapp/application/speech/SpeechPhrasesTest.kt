package com.rumbo.watchapp.application.speech

import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.model.RouteStep
import com.rumbo.watchapp.domain.model.Side
import com.rumbo.watchapp.domain.navigation.NavigationEvent
import com.rumbo.watchapp.domain.navigation.NavigationSnapshot
import com.rumbo.watchapp.domain.navigation.NavigationStatus
import com.rumbo.watchapp.domain.navigation.NavigationThresholds
import com.rumbo.watchapp.domain.port.SpeechPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeechPhrasesTest {

    private val thresholds = NavigationThresholds()
    private val turn = RouteStep(2, Maneuver.LEFT, "Gire a la izquierda hacia the crosswalk", 120.0, 1.21, -77.28, "Calle 18")
    private val arrive = RouteStep(3, Maneuver.ARRIVE, "Ha llegado.", 0.0, 1.2, -77.2)
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
        then: Maneuver? = null,
        following: RouteStep? = null,
        followingLegM: Double? = null,
    ) = NavigationSnapshot(status, nextStep, distance, 120.0, 500.0, 100.0, 2.0, currentStreet, then, following, followingLegM)

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

        assertEquals("Iniciando ruta. Camine hacia el norte por la Carrera 24.", startPhrase(route, turnClose, thresholds))
        assertEquals("Iniciando ruta.", startPhrase(route.copy(steps = emptyList()), turnClose, thresholds))
    }

    @Test
    fun startPhraseIsAlsoTheFarStageOfTheFirstTurn() {
        val turnFar = snapshot(status = NavigationStatus.ON_ROUTE, distance = 300.0)

        assertEquals(
            "Iniciando ruta. Camine hacia el norte por la Carrera 24. En trescientos metros, gira a la izquierda.",
            startPhrase(route, turnFar, thresholds),
        )
    }

    @Test
    fun startPhraseSaysTheDestinationIfTheWholeRouteIsShort() {
        val close = snapshot(arrive.copy(destinationSide = Side.LEFT), 60.0, NavigationStatus.ON_ROUTE)

        assertEquals(
            "Iniciando ruta. Camine hacia el norte por la Carrera 24. Tu destino está en sesenta metros, a la izquierda.",
            startPhrase(route, close, thresholds),
        )
    }

    @Test
    fun startPhraseLeavesAFarDestinationForLater() {
        assertEquals(
            "Iniciando ruta. Camine hacia el norte por la Carrera 24.",
            startPhrase(route, snapshot(arrive, 300.0, NavigationStatus.ON_ROUTE), thresholds),
        )
    }

    @Test
    fun destinationNearSaysTheDistanceAndTheSide() {
        assertEquals("Tu destino está en cien metros, a la derecha.", destinationNearPhrase(98.0, Side.RIGHT))
        assertEquals("Tu destino está en ochenta metros, a la izquierda.", destinationNearPhrase(83.0, Side.LEFT))
    }

    @Test
    fun destinationNearWithoutKnownSideOnlySaysTheDistance() {
        assertEquals("Tu destino está en cien metros.", destinationNearPhrase(100.0, null))
    }

    @Test
    fun destinationNearIsHighPriority() {
        val step = arrive.copy(destinationSide = Side.RIGHT)

        assertEquals(
            Phrase("Tu destino está en cien metros, a la derecha.", SpeechPriority.HIGH),
            navigationPhrase(listOf(NavigationEvent.DestinationNear), snapshot(step, 99.0)),
        )
    }

    @Test
    fun arrivingBeatsTheDestinationNotice() {
        val events = listOf(NavigationEvent.DestinationNear, NavigationEvent.Arrived)

        assertEquals(ARRIVED_PHRASE, navigationPhrase(events, snapshot(arrive, 10.0))?.text)
    }

    @Test
    fun startPhraseDoesNotRepeatAFirstStepThatIsAlreadyTheTurn() {
        val onlyTurn = route.copy(steps = listOf(turn))

        assertEquals(
            "Iniciando ruta. Gire a la izquierda hacia the crosswalk.",
            startPhrase(onlyTurn, snapshot(distance = 300.0), thresholds),
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
    fun slightAndSharpTurnsSaySoInEveryStage() {
        val slight = turn.copy(maneuver = Maneuver.SLIGHT_RIGHT)
        val sharp = turn.copy(maneuver = Maneuver.SHARP_LEFT)

        assertEquals("Sigue recto. En cien metros, gira ligeramente a la derecha.", turnAheadPhrase(100.0, slight, ""))
        assertEquals("En cincuenta metros, gira ligeramente a la derecha en Calle 18.", turnApproachingPhrase(50.0, slight))
        assertEquals("Gira ahora ligeramente a la derecha.", turnNowPhrase(Maneuver.SLIGHT_RIGHT))
        assertEquals("Gira ahora ligeramente a la izquierda.", turnNowPhrase(Maneuver.SLIGHT_LEFT))

        assertEquals("Sigue recto. En cien metros, gira cerrado a la izquierda.", turnAheadPhrase(100.0, sharp, ""))
        assertEquals("En cincuenta metros, gira cerrado a la izquierda en Calle 18.", turnApproachingPhrase(50.0, sharp))
        assertEquals("Gira ahora cerrado a la izquierda.", turnNowPhrase(Maneuver.SHARP_LEFT))
        assertEquals("Gira ahora cerrado a la derecha.", turnNowPhrase(Maneuver.SHARP_RIGHT))
    }

    @Test
    fun uTurnSaysHalfTurnWithoutTheSide() {
        val uTurn = turn.copy(maneuver = Maneuver.U_TURN_LEFT)

        assertEquals("Sigue recto. En cien metros, da media vuelta.", turnAheadPhrase(100.0, uTurn, ""))
        assertEquals("En cincuenta metros, da media vuelta en Calle 18.", turnApproachingPhrase(50.0, uTurn))
        assertEquals("Da media vuelta ahora.", turnNowPhrase(Maneuver.U_TURN_LEFT))
        assertEquals("Da media vuelta ahora.", turnNowPhrase(Maneuver.U_TURN_RIGHT))
    }

    @Test
    fun roundaboutSaysTheExitAndNotItsOwnName() {
        val roundabout = turn.copy(maneuver = Maneuver.ROUNDABOUT, street = "Glorieta de Chapal", roundaboutExit = 2)

        assertEquals(
            "Sigue recto. En cien metros, en la rotonda toma la segunda salida.",
            turnAheadPhrase(100.0, roundabout, ""),
        )
        assertEquals("En cincuenta metros, en la rotonda toma la segunda salida.", turnApproachingPhrase(50.0, roundabout))
        assertEquals("Ahora, en la rotonda toma la segunda salida.", turnNowPhrase(Maneuver.ROUNDABOUT, 2))
    }

    @Test
    fun roundaboutWithoutExitNumberOnlySaysToEnter() {
        assertEquals("Ahora, entra a la rotonda.", turnNowPhrase(Maneuver.ROUNDABOUT))
        assertEquals("Ahora, entra a la rotonda.", turnNowPhrase(Maneuver.ROUNDABOUT, 0))
    }

    @Test
    fun exitsAreOrdinalUpToTheTenth() {
        assertEquals("la primera salida", exitInWords(1))
        assertEquals("la séptima salida", exitInWords(7))
        assertEquals("la décima salida", exitInWords(10))
        assertEquals("la salida número once", exitInWords(11))
    }

    @Test
    fun nowStageWarnsOfACloseNextTurn() {
        assertEquals(
            "Gira ahora a la derecha. Luego, gira a la izquierda.",
            turnNowPhrase(Maneuver.RIGHT, then = Maneuver.LEFT),
        )
        assertEquals(
            "Da media vuelta ahora. Luego, gira ligeramente a la derecha.",
            turnNowPhrase(Maneuver.U_TURN_LEFT, then = Maneuver.SLIGHT_RIGHT),
        )
        assertEquals(
            "Ahora, en la rotonda toma la primera salida. Luego, entra a la rotonda.",
            turnNowPhrase(Maneuver.ROUNDABOUT, 1, then = Maneuver.ROUNDABOUT),
        )
        assertEquals(
            Phrase("Gira ahora a la derecha. Luego, gira a la izquierda.", SpeechPriority.HIGH),
            navigationPhrase(listOf(NavigationEvent.TurnNow(Maneuver.RIGHT, then = Maneuver.LEFT)), snapshot()),
        )
    }

    @Test
    fun approachStageOfTwoJoinedTurnsSaysBothAndTheStreetOfTheSecond() {
        val joined = turn.copy(maneuver = Maneuver.RIGHT, street = "Carrera 17B", thenManeuver = Maneuver.LEFT)

        assertEquals(
            "En cincuenta metros, gira a la derecha. Luego, gira a la izquierda en Carrera 17B.",
            turnApproachingPhrase(50.0, joined),
        )
        assertEquals(
            "En cincuenta metros, gira a la derecha. Luego, gira a la izquierda.",
            turnApproachingPhrase(50.0, joined.copy(street = "")),
        )
    }

    @Test
    fun theExitNumberTravelsWithTheEventForTurnsPassedBySurprise() {
        val event = NavigationEvent.TurnNow(Maneuver.ROUNDABOUT, 3)

        assertEquals(
            Phrase("Ahora, en la rotonda toma la tercera salida.", SpeechPriority.HIGH),
            navigationPhrase(listOf(event), snapshot(nextStep = null, distance = null)),
        )
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
    fun beforeTheApproachStageItsPhraseIsPreparedWithTheDistanceItWillSay() {
        val snapshot = snapshot(distance = 300.0, status = NavigationStatus.ON_ROUTE)

        assertEquals("En cincuenta metros, gira a la izquierda en Calle 18.", upcomingPhrase(snapshot, thresholds))
    }

    @Test
    fun aShortLegPreparesTheDistanceItReallyHas() {
        val snapshot = snapshot(distance = 40.0, status = NavigationStatus.ON_ROUTE)

        assertEquals("En cuarenta metros, gira a la izquierda en Calle 18.", upcomingPhrase(snapshot, thresholds))
    }

    @Test
    fun aTurnRightAtTheStartPreparesTheNowStageBecauseTheApproachWillNotSound() {
        val snapshot = snapshot(distance = 4.0, status = NavigationStatus.ON_ROUTE)

        assertEquals("Gira ahora a la izquierda.", upcomingPhrase(snapshot, thresholds))
    }

    @Test
    fun duringTheApproachTheNowStageIsPrepared() {
        assertEquals("Gira ahora a la izquierda.", upcomingPhrase(snapshot(), thresholds))
        assertEquals(
            "Gira ahora a la izquierda. Luego, gira a la derecha.",
            upcomingPhrase(snapshot(then = Maneuver.RIGHT), thresholds),
        )
    }

    @Test
    fun atTheTurnTheFarStageOfTheNextLegIsPrepared() {
        val next = turn.copy(maneuver = Maneuver.RIGHT, street = "Calle 20")
        val snapshot = snapshot(status = NavigationStatus.MANEUVER, following = next, followingLegM = 300.0)

        assertEquals(
            "Sigue recto por Calle 18. En trescientos metros, gira a la derecha.",
            upcomingPhrase(snapshot, thresholds),
        )
    }

    @Test
    fun atTheTurnAShortNextLegPreparesItsApproach() {
        val next = turn.copy(maneuver = Maneuver.RIGHT, street = "Calle 20")
        val snapshot = snapshot(status = NavigationStatus.MANEUVER, following = next, followingLegM = 70.0)

        assertEquals("En cincuenta metros, gira a la derecha en Calle 20.", upcomingPhrase(snapshot, thresholds))
    }

    @Test
    fun theLastStagesAreTheDestinationAndTheArrival() {
        val side = arrive.copy(destinationSide = Side.LEFT)

        assertEquals(
            "Tu destino está en cien metros, a la izquierda.",
            upcomingPhrase(snapshot(side, 300.0, NavigationStatus.ON_ROUTE), thresholds),
        )
        assertEquals(
            "Tu destino está en sesenta metros, a la izquierda.",
            upcomingPhrase(snapshot(status = NavigationStatus.MANEUVER, following = side, followingLegM = 60.0), thresholds),
        )
        assertEquals(ARRIVED_PHRASE, upcomingPhrase(snapshot(side, 80.0, NavigationStatus.ON_ROUTE), thresholds))
    }

    @Test
    fun nothingIsPreparedWhenTheNextPhraseCannotBeKnown() {
        assertNull(upcomingPhrase(snapshot(status = NavigationStatus.OFF_ROUTE), thresholds))
        assertNull(upcomingPhrase(snapshot(status = NavigationStatus.NO_SIGNAL), thresholds))
        assertNull(upcomingPhrase(snapshot(status = NavigationStatus.ARRIVED), thresholds))
        assertNull(upcomingPhrase(snapshot(nextStep = null, distance = null), thresholds))
        assertNull(upcomingPhrase(snapshot(status = NavigationStatus.MANEUVER), thresholds))
        assertNull(
            upcomingPhrase(snapshot(turn.copy(maneuver = Maneuver.STRAIGHT), 200.0, NavigationStatus.ON_ROUTE), thresholds),
        )
    }

    @Test
    fun repeatSaysTheStageOfNowWithTodaysDistanceAndHighPriority() {
        assertEquals(
            Phrase("Sigue recto por Carrera 25. En cuatrocientos metros, gira a la izquierda.", SpeechPriority.HIGH),
            repeatPhrase(snapshot(distance = 400.0, status = NavigationStatus.ON_ROUTE)),
        )
        assertEquals(
            Phrase("En treinta metros, gira a la izquierda en Calle 18.", SpeechPriority.HIGH),
            repeatPhrase(snapshot(status = NavigationStatus.TURN_APPROACHING)),
        )
        assertEquals(
            Phrase("Gira ahora a la izquierda.", SpeechPriority.HIGH),
            repeatPhrase(snapshot(distance = 9.0, status = NavigationStatus.MANEUVER)),
        )
    }

    @Test
    fun repeatKeepsTheNuancesOfTheManeuver() {
        val roundabout = turn.copy(maneuver = Maneuver.ROUNDABOUT, roundaboutExit = 2)

        assertEquals(
            "Ahora, en la rotonda toma la segunda salida.",
            repeatPhrase(snapshot(roundabout, 9.0, NavigationStatus.MANEUVER)).text,
        )
        assertEquals(
            "Gira ahora a la izquierda. Luego, gira a la derecha.",
            repeatPhrase(snapshot(distance = 9.0, status = NavigationStatus.MANEUVER, then = Maneuver.RIGHT)).text,
        )
    }

    @Test
    fun repeatAfterTurningNeverSaysTurnAgain() {
        // Ya dobló: el tramo es corto y no tiene aviso lejano, pero Repetir dice lo que viene.
        assertEquals(
            "Sigue recto por Carrera 25. En sesenta metros, gira a la izquierda.",
            repeatPhrase(snapshot(distance = 60.0, status = NavigationStatus.ON_ROUTE)).text,
        )
    }

    @Test
    fun repeatOnAStretchWithoutTurnsSaysToKeepGoing() {
        val straight = turn.copy(maneuver = Maneuver.STRAIGHT)

        assertEquals("Sigue recto por Carrera 25.", repeatPhrase(snapshot(straight, 200.0, NavigationStatus.ON_ROUTE)).text)
    }

    @Test
    fun repeatDescribesTheDestinationAndTheEnd() {
        val side = arrive.copy(destinationSide = Side.RIGHT)

        assertEquals("Tu destino está en cincuenta metros, a la derecha.", repeatPhrase(snapshot(side, 48.0)).text)
        assertEquals(KEEP_GOING_PHRASE, repeatPhrase(snapshot(nextStep = null, distance = null)).text)
    }

    @Test
    fun repeatDescribesOffRouteNoSignalAndArrival() {
        assertEquals(OFF_ROUTE_PHRASE, repeatPhrase(snapshot(status = NavigationStatus.OFF_ROUTE)).text)
        assertEquals(SIGNAL_LOST_PHRASE, repeatPhrase(snapshot(status = NavigationStatus.NO_SIGNAL)).text)
        assertEquals(ARRIVED_PHRASE, repeatPhrase(snapshot(status = NavigationStatus.ARRIVED)).text)
    }
}
