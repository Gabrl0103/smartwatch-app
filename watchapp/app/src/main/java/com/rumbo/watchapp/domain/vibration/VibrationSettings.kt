package com.rumbo.watchapp.domain.vibration

/** Ajuste "Intensidad": escala la fuerza de cada pulso; solo se nota si el reloj la controla. */
enum class VibrationIntensity(val label: String, val factor: Double) {
    SOFT("suave", 0.6),
    NORMAL("normal", 1.0),
    STRONG("fuerte", 1.25),
}

/** Ajuste "Duración": "larga" alarga cada pulso un 30 % para relojes con motor débil. */
enum class PulseDuration(val label: String, val factor: Double) {
    NORMAL("normal", 1.0),
    LONG("larga", 1.3),
}

/** Ajustes de vibración del usuario; se aplican igual en la navegación y en "Vibraciones". */
data class VibrationSettings(
    val intensity: VibrationIntensity = VibrationIntensity.NORMAL,
    val duration: PulseDuration = PulseDuration.NORMAL,
)

/** El siguiente valor, volviendo al primero tras el último: así se cambia con un solo botón. */
fun VibrationIntensity.next(): VibrationIntensity =
    VibrationIntensity.entries[(ordinal + 1) % VibrationIntensity.entries.size]

fun PulseDuration.next(): PulseDuration = PulseDuration.entries[(ordinal + 1) % PulseDuration.entries.size]
