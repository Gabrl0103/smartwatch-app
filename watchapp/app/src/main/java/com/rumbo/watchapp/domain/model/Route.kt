package com.rumbo.watchapp.domain.model

enum class Maneuver {
    LEFT, RIGHT, STRAIGHT, ARRIVE;

    /** true si hay que avisarla por etapas: todo lo que no sea seguir recto o llegar. */
    val isTurn: Boolean get() = this != STRAIGHT && this != ARRIVE
}

/**
 * Una maniobra de la ruta: dónde ocurre y cuánto se camina hasta la siguiente. [street] es la
 * calle por la que se sigue después del paso; vacía si el proveedor no la da.
 */
data class RouteStep(
    val order: Int,
    val maneuver: Maneuver,
    val instruction: String,
    val distanceM: Double,
    val lat: Double,
    val lng: Double,
    val street: String = "",
)

/** Ruta a pie calculada por el backend. [id] es null si el backend no pudo guardarla. */
data class Route(
    val destinationName: String,
    val distanceM: Double,
    val durationS: Double,
    val steps: List<RouteStep>,
    val polyline: List<Coordinates>,
    val id: Long? = null,
) {
    /** Punto final de la ruta: último de la polilínea o, si no hay, el del último paso. */
    val end: Coordinates
        get() = polyline.lastOrNull() ?: steps.last().let { Coordinates(it.lat, it.lng) }
}
