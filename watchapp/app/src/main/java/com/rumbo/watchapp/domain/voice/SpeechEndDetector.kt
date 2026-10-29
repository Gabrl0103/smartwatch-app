package com.rumbo.watchapp.domain.voice

/** Más alto que esto (≈ 1 000 de amplitud, -30 dB) es voz; por debajo, ruido de fondo. */
const val VOICE_LEVEL_THRESHOLD = 0.4f

/** Lo más que se graba aunque la persona siga hablando o no diga nada. */
const val MAX_RECORDING_MS = 8_000L

/**
 * Decide cuándo dejar de grabar la orden, con un nivel de voz cada [sampleMs]: se detiene sola
 * cuando hubo voz y después [silenceToStopMs] de silencio (pero nunca antes de [minRecordingMs]),
 * y siempre a los [maxRecordingMs]. Sin voz no corta antes del máximo: la persona puede tardar
 * en empezar a hablar. Solo es voz si dura al menos [minVoiceMs] seguidos: un pico suelto (un
 * golpe en el reloj o el clic del micrófono al arrancar) no cuenta, porque cortaría la grabación
 * al segundo, antes de que la persona hable. Con el nivel suavizado un pico de una lectura queda
 * dos por encima del umbral; por eso hacen falta tres (240 ms, menos que una sílaba).
 */
class SpeechEndDetector(
    private val sampleMs: Long = LEVEL_SAMPLE_MS,
    private val voiceThreshold: Float = VOICE_LEVEL_THRESHOLD,
    private val silenceToStopMs: Long = 1_200,
    private val minRecordingMs: Long = 1_000,
    private val maxRecordingMs: Long = MAX_RECORDING_MS,
    private val minVoiceMs: Long = 3 * LEVEL_SAMPLE_MS,
) {
    private var elapsedMs = 0L
    private var heardVoice = false
    private var voiceMs = 0L
    private var silenceMs = 0L

    /** Registra una lectura; true si ya hay que dejar de grabar. */
    fun onLevel(level: Float): Boolean {
        elapsedMs += sampleMs
        if (level >= voiceThreshold) {
            voiceMs += sampleMs
            if (voiceMs >= minVoiceMs) {
                heardVoice = true
                silenceMs = 0
            }
        } else {
            voiceMs = 0
            if (heardVoice) silenceMs += sampleMs
        }
        val finishedSpeaking = heardVoice && silenceMs >= silenceToStopMs && elapsedMs >= minRecordingMs
        return finishedSpeaking || elapsedMs >= maxRecordingMs
    }
}
