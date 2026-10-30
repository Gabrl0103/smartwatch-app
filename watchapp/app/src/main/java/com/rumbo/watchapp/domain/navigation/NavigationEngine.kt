package com.rumbo.watchapp.domain.navigation

import com.rumbo.watchapp.domain.geo.cumulativeDistances
import com.rumbo.watchapp.domain.geo.haversineM
import com.rumbo.watchapp.domain.geo.snapToPolyline
import com.rumbo.watchapp.domain.geo.stepAlongDistances
import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.LocationFix
import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.model.Route
import kotlin.math.max

/**
 * Máquina de estados de la navegación. No lee el reloj del sistema: el tiempo llega en cada
 * llamada, así se prueba con rutas y tiempos sintéticos. Espera lecturas ya filtradas
 * ([LocationFilter]) y [onTick] cada segundo aunque no lleguen lecturas.
 *
 * Con [announceFirstLeg] el primer tramo también da su aviso lejano: en una ruta recalculada hace
 * falta y al iniciar no, porque ya lo dice la frase de inicio.
 */
class NavigationEngine(
    route: Route,
    startMs: Long,
    private val thresholds: NavigationThresholds = NavigationThresholds(),
    announceFirstLeg: Boolean = false,
) {
    private val steps = route.steps
    private val polyline = route.polyline.ifEmpty { steps.map { Coordinates(it.lat, it.lng) } }

    init {
        require(polyline.isNotEmpty()) { "La ruta no tiene puntos" }
    }

    private val cumulative = cumulativeDistances(polyline)
    private val stepAlong = stepAlongDistances(steps, polyline, cumulative)
    private val totalM = cumulative.last()
    private val destination = polyline.last()

    private var status = NavigationStatus.ON_ROUTE
    private var progressM = 0.0
    private var nextIndex = 0
    private var distanceToRouteM: Double? = null
    private var speedMps = 0.0
    private var lastFixMs = startMs
    private var located = false

    private var farReadings = 0
    private var offRoute = false
    private var offRouteSinceMs = 0L
    private var lastRecalculateMs: Long? = null

    // Avisos ya dados para el paso actual: evitan repetirlos y que el estado parpadee.
    private var aheadAnnounced = false
    private var approachAnnounced = false
    private var maneuverAnnounced = false

    init {
        advanceNextStep(mutableListOf())
        aheadAnnounced = !announceFirstLeg
    }

    val snapshot: NavigationSnapshot
        get() {
            val next = steps.getOrNull(nextIndex)
            return NavigationSnapshot(
                status = status,
                nextStep = next,
                distanceToNextStepM = next?.let { distanceToNextStep() },
                stepLengthM = next?.let { stepLength() },
                remainingM = (totalM - progressM).coerceAtLeast(0.0),
                progressM = progressM,
                distanceToRouteM = distanceToRouteM,
                currentStreet = steps.getOrNull(nextIndex - 1)?.street.orEmpty(),
            )
        }

    fun onLocation(fix: LocationFix, nowMs: Long): List<NavigationEvent> {
        if (status == NavigationStatus.ARRIVED) return emptyList()
        val events = mutableListOf<NavigationEvent>()
        lastFixMs = nowMs
        if (status == NavigationStatus.NO_SIGNAL) events += NavigationEvent.SignalRecovered
        speedMps = fix.speedMps ?: 0.0

        val here = Coordinates(fix.lat, fix.lng)
        val near = snapToPolyline(
            here,
            polyline,
            cumulative,
            fromAlongM = progressM - thresholds.snapBehindM,
            toAlongM = progressM + thresholds.snapAheadM,
        )
        // Si la primera lectura no está cerca del inicio (p. ej. al continuar una ruta anterior
        // a mitad de camino), se busca en toda la ruta antes de darla por desviada.
        val snap = if (!located && near.distanceToRouteM > thresholds.offRouteM) {
            snapToPolyline(here, polyline, cumulative)
        } else {
            near
        }
        located = true
        distanceToRouteM = snap.distanceToRouteM
        updateOffRoute(snap.distanceToRouteM, nowMs, events)
        // El progreso solo avanza, y solo con lecturas sobre la ruta.
        if (!offRoute && snap.distanceToRouteM <= thresholds.offRouteM) {
            progressM = max(progressM, snap.alongM)
        }
        advanceNextStep(events)

        if (haversineM(here, destination) <= thresholds.arrivalM) {
            status = NavigationStatus.ARRIVED
            progressM = totalM
            events += NavigationEvent.Arrived
            return events
        }

        status = if (offRoute) NavigationStatus.OFF_ROUTE else turnStatus(events)
        requestRecalculationIfDue(nowMs, events)
        return events
    }

    fun onTick(nowMs: Long): List<NavigationEvent> {
        if (status == NavigationStatus.ARRIVED || status == NavigationStatus.NO_SIGNAL) return emptyList()
        val events = mutableListOf<NavigationEvent>()
        if (nowMs - lastFixMs >= thresholds.signalLostAfterMs) {
            status = NavigationStatus.NO_SIGNAL
            events += NavigationEvent.SignalLost
            return events
        }
        requestRecalculationIfDue(nowMs, events)
        return events
    }

    private fun distanceToNextStep() = (stepAlong[nextIndex] - progressM).coerceAtLeast(0.0)

    private fun stepLength() = stepAlong[nextIndex] - (stepAlong.getOrNull(nextIndex - 1) ?: 0.0)

    private fun advanceNextStep(events: MutableList<NavigationEvent>) {
        var index = nextIndex
        while (index < steps.size && stepAlong[index] <= progressM) index++
        if (index == nextIndex) return
        // Un giro nunca se salta en silencio: si una lectura lo deja atrás sin haberlo avisado (p. ej.
        // el primero, a pocos metros del inicio, y la primera lectura cae un poco más allá) y se acaba
        // de pasar, la persona está en la esquina: se avisa igual, en sus dos etapas.
        val passed = index - 1
        val turn = steps[passed].maneuver
        val announced = passed == nextIndex && maneuverAnnounced
        if (turn.isTurn && !announced && progressM - stepAlong[passed] <= thresholds.maneuverM) {
            if (!(passed == nextIndex && approachAnnounced)) events += NavigationEvent.TurnApproaching(turn)
            events += NavigationEvent.TurnNow(turn)
        }
        nextIndex = index
        aheadAnnounced = false
        approachAnnounced = false
        maneuverAnnounced = false
        steps.getOrNull(index)?.let { events += NavigationEvent.NextStepChanged(it) }
    }

    private fun updateOffRoute(distanceM: Double, nowMs: Long, events: MutableList<NavigationEvent>) {
        if (offRoute) {
            if (distanceM < thresholds.backOnRouteM) {
                offRoute = false
                farReadings = 0
                events += NavigationEvent.BackOnRoute
            }
            return
        }
        farReadings = if (distanceM > thresholds.offRouteM) farReadings + 1 else 0
        if (farReadings >= thresholds.offRouteReadings) {
            offRoute = true
            offRouteSinceMs = nowMs
            lastRecalculateMs = null
            events += NavigationEvent.OffRoute
        }
    }

    private fun turnStatus(events: MutableList<NavigationEvent>): NavigationStatus {
        val step = steps.getOrNull(nextIndex) ?: return NavigationStatus.ON_ROUTE
        if (!step.maneuver.isTurn) return NavigationStatus.ON_ROUTE
        val distance = distanceToNextStep()
        if (maneuverAnnounced || distance <= thresholds.maneuverM) {
            if (!maneuverAnnounced) {
                // Cada giro avisa en dos etapas: si se llega a la maniobra sin haber avisado del
                // giro próximo (un salto del GPS o un tramo corto), se avisan las dos juntas.
                if (!approachAnnounced) events += NavigationEvent.TurnApproaching(step.maneuver)
                maneuverAnnounced = true
                approachAnnounced = true
                events += NavigationEvent.TurnNow(step.maneuver)
            }
            return NavigationStatus.MANEUVER
        }
        val approachM = max(thresholds.turnApproachMinM, speedMps * thresholds.turnApproachLookaheadS)
        if (approachAnnounced || distance <= approachM) {
            if (!approachAnnounced) {
                approachAnnounced = true
                events += NavigationEvent.TurnApproaching(step.maneuver)
            }
            return NavigationStatus.TURN_APPROACHING
        }
        // Si en esta lectura se avisó un giro pasado por sorpresa, el lejano espera a la siguiente:
        // solo suena una frase por lectura y la de la maniobra es la que importa.
        if (!aheadAnnounced && distance > thresholds.turnAheadMinM && events.none { it is NavigationEvent.TurnNow }) {
            aheadAnnounced = true
            events += NavigationEvent.TurnAhead(step.maneuver)
        }
        return NavigationStatus.ON_ROUTE
    }

    private fun requestRecalculationIfDue(nowMs: Long, events: MutableList<NavigationEvent>) {
        if (!offRoute || nowMs - offRouteSinceMs < thresholds.recalculateAfterMs) return
        val last = lastRecalculateMs
        if (last != null && nowMs - last < thresholds.recalculateAfterMs) return
        lastRecalculateMs = nowMs
        events += NavigationEvent.RecalculateRequested
    }
}
