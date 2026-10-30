package com.rumbo.watchapp.domain.vibration

/** Cada aviso que Rumbo da vibrando, en el orden en que se enseñan en "Vibraciones". */
enum class VibrationCue(val title: String) {
    ON_ROUTE("Recto"),
    TURN_APPROACHING("Giro próximo"),
    LEFT("Izquierda"),
    RIGHT("Derecha"),
    ATTENTION("Atención"),
    OFF_ROUTE("Desviado"),
    NO_SIGNAL("Sin señal"),
    ARRIVED("Llegada"),

    /** Una orden de voz se cumplió. */
    COMMAND_OK("Hecho"),

    /** Una orden de voz no se pudo cumplir. */
    COMMAND_ERROR("Error"),
}
