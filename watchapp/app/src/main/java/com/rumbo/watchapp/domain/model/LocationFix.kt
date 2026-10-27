package com.rumbo.watchapp.domain.model

/** Una lectura del GPS. Velocidad y rumbo son null si el proveedor no los da. */
data class LocationFix(
    val lat: Double,
    val lng: Double,
    val accuracyM: Double,
    val timeMs: Long,
    val speedMps: Double? = null,
    val bearingDeg: Double? = null,
)
