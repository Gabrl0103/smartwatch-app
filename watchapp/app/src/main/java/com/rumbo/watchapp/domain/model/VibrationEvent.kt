package com.rumbo.watchapp.domain.model

/** Vibración emitida, para el log del backend. Ruta y paso son null si no se conocen. */
data class VibrationEvent(
    val pattern: String,
    val routeId: Long?,
    val stepOrder: Int?,
)
