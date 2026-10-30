package com.rumbo.watchapp.domain.navigation

/** Todos los umbrales de la navegación en un solo lugar para poder ajustarlos en pruebas de campo. */
data class NavigationThresholds(
    // Filtro de lecturas
    val maxAccuracyM: Double = 25.0,
    val maxFixAgeMs: Long = 5_000,
    val medianWindow: Int = 3,
    // Más rápido que esto no camina nadie: una velocidad así es un salto del GPS y se descarta.
    val maxSpeedMps: Double = 4.0,
    // Giros, en tres etapas: al empezar un tramo más largo que turnAheadMinM, giro próximo y maniobra
    val turnAheadMinM: Double = 80.0,
    val turnApproachMinM: Double = 50.0,
    val turnApproachLookaheadS: Double = 10.0,
    val maneuverM: Double = 12.0,
    // Desvío (entra con offRouteM, sale con backOnRouteM: la diferencia evita el parpadeo)
    val offRouteM: Double = 25.0,
    val offRouteReadings: Int = 3,
    val backOnRouteM: Double = 15.0,
    val recalculateAfterMs: Long = 10_000,
    // Recálculos seguidos sin volver a la ruta; después se sigue con la anterior.
    val maxRecalculations: Int = 5,
    // Señal y llegada
    val signalLostAfterMs: Long = 10_000,
    val arrivalM: Double = 15.0,
    // Ventana para pegar la posición a la ruta alrededor del progreso actual
    val snapBehindM: Double = 30.0,
    val snapAheadM: Double = 150.0,
    // Origen de la ruta a partir de la última lectura
    val originMaxAgeMs: Long = 30_000,
    val originMaxAccuracyM: Double = 50.0,
)
