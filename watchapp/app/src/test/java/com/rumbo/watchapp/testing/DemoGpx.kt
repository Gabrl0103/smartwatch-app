package com.rumbo.watchapp.testing

import com.rumbo.watchapp.domain.model.LocationFix
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.navigation.LocationFilter
import com.rumbo.watchapp.domain.navigation.NavigationEngine
import com.rumbo.watchapp.domain.navigation.NavigationEvent
import com.rumbo.watchapp.domain.navigation.NavigationStatus
import com.rumbo.watchapp.infrastructure.network.routeFromJson
import java.io.File
import java.time.Instant
import org.json.JSONObject

/** Un punto del GPX con su hora en milisegundos y la precisión que informó el GPS. */
data class GpxPoint(val lat: Double, val lng: Double, val timeMs: Long, val accuracyM: Double = 5.0)

private val TRKPT = Regex("""<trkpt lat="([-\d.]+)" lon="([-\d.]+)"><time>([^<]+)</time>(?:<hdop>([\d.]+)</hdop>)?</trkpt>""")

// tools/route_to_gpx.py escribe la precisión como HDOP: precisión ≈ 5 m × HDOP.
private const val HDOP_UERE_M = 5.0

/** Nombre de la ruta del backend detrás de los GPX de docs/demo (JSON de POST /routes). */
const val DEMO_ROUTE = "ruta_parque_narino_a_parque_bolivar.json"

/**
 * Carpeta docs/demo. Gradle la pasa en la propiedad "rumbo.demoDir"; si se corre el test desde
 * el IDE sin ella, se busca relativa al módulo app.
 */
fun demoDir(): File = File(System.getProperty("rumbo.demoDir") ?: "../../docs/demo")

/** Lee los puntos del recorrido (trkpt) de un GPX de docs/demo, con su precisión si la trae. */
fun readGpx(name: String): List<GpxPoint> {
    val text = File(demoDir(), name).readText()
    return TRKPT.findAll(text).map { match ->
        val (lat, lng, time, hdop) = match.destructured
        val accuracy = hdop.toDoubleOrNull()?.let { it * HDOP_UERE_M } ?: 5.0
        GpxPoint(lat.toDouble(), lng.toDouble(), Instant.parse(time).toEpochMilli(), accuracy)
    }.toList().also { require(it.isNotEmpty()) { "$name no tiene puntos" } }
}

/** La ruta real que devolvió el backend para los GPX de docs/demo, leída como la lee el reloj. */
fun readDemoRoute(name: String = DEMO_ROUTE): Route = routeFromJson(JSONObject(File(demoDir(), name).readText()))

/** Un estado que duró de [fromMs] a [toMs] (inclusive, en segundos enteros). */
data class StatusRun(val status: NavigationStatus, val fromMs: Long, val toMs: Long) {
    val durationMs: Long get() = toMs - fromMs + 1_000
}

/**
 * Lo que pasó al recorrer un GPX: estados segundo a segundo, todos los eventos y a cuántos metros
 * del giro se avisó cada "Giro próximo" (sin contar los que llegan junto con "Gira ahora").
 */
class Replay(
    val runs: List<StatusRun>,
    val events: List<NavigationEvent>,
    val approachDistancesM: List<Double> = emptyList(),
) {
    val statuses: List<NavigationStatus> get() = runs.map { it.status }

    fun count(event: NavigationEvent) = events.count { it == event }

    inline fun <reified T : NavigationEvent> countOf() = events.count { it is T }

    fun describe() = runs.joinToString("\n") { "${it.status} ${it.durationMs / 1000} s" }
}

/**
 * Recorre [track] como lo vería el reloj: cada segundo un tic del motor y, si en ese segundo hay
 * un punto, una lectura con su precisión (sin velocidad ni rumbo: los calcula el filtro). Sigue
 * unos segundos después del último punto para que se vea la llegada.
 */
fun replay(route: Route, track: List<GpxPoint>): Replay {
    val startMs = track.first().timeMs
    val endMs = track.last().timeMs + 5_000
    val byTime = track.associateBy { it.timeMs }
    val engine = NavigationEngine(route, startMs)
    val filter = LocationFilter()
    val events = mutableListOf<NavigationEvent>()
    val approaches = mutableListOf<Double>()
    val runs = mutableListOf<StatusRun>()
    for (now in startMs..endMs step 1_000) {
        byTime[now]?.let { point ->
            val raw = LocationFix(point.lat, point.lng, point.accuracyM, now)
            filter.accept(raw, now)?.let { fix ->
                val new = engine.onLocation(fix, now)
                if (new.any { it is NavigationEvent.TurnApproaching } && new.none { it is NavigationEvent.TurnNow }) {
                    engine.snapshot.distanceToNextStepM?.let { approaches += it }
                }
                events += new
            }
        }
        events += engine.onTick(now)
        val status = engine.snapshot.status
        val last = runs.lastOrNull()
        if (last != null && last.status == status) {
            runs[runs.lastIndex] = last.copy(toMs = now)
        } else {
            runs += StatusRun(status, now, now)
        }
    }
    return Replay(runs, events, approaches)
}
