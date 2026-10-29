package com.rumbo.watchapp.infrastructure.tts

import com.rumbo.watchapp.domain.error.QuotaExhaustedError
import com.rumbo.watchapp.domain.port.SpeechEngine
import com.rumbo.watchapp.domain.port.SpeechOutput
import com.rumbo.watchapp.domain.port.SpeechPriority
import com.rumbo.watchapp.domain.port.SpeechStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Dice una frase y vuelve cuando terminó; si se cancela la corrutina, debe callarse. Llama a
 * [onStart] cuando el audio empieza a sonar de verdad (no al pedirlo ni al descargarlo).
 */
interface SpeechPlayer {
    suspend fun play(text: String, onStart: () -> Unit)
}

enum class LocalVoiceState { STARTING, READY, MISSING_DATA, UNAVAILABLE }

/** Voz del propio reloj (TextToSpeech): arranca de forma asíncrona y puede no existir. */
interface LocalSpeechPlayer : SpeechPlayer {
    val state: StateFlow<LocalVoiceState>
}

/** Tras un QUOTA_EXHAUSTED no se vuelve a pedir voz a Gemini durante este tiempo. */
internal const val QUOTA_PAUSE_MS = 30 * 60 * 1000L
private const val LOCAL_START_WAIT_MS = 3_000L

/**
 * Decide qué frase suena y con qué voz. Una nueva cancela la actual, salvo que la nueva sea
 * NORMAL y la actual HIGH (esa se descarta). Voz: 1) la del reloj si está lista; 2) Gemini
 * solo si la del reloj no está disponible; 3) nada. Si la voz está apagada no dice nada.
 * Los errores van a [onError] y nunca salen de aquí.
 */
class SpeechOutputImpl(
    private val local: LocalSpeechPlayer,
    private val remote: SpeechPlayer,
    private val isEnabled: () -> Boolean,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onError: (Exception) -> Unit = {},
    private val localStartWaitMs: Long = LOCAL_START_WAIT_MS,
) : SpeechOutput {

    private val _status = MutableStateFlow(SpeechStatus())
    override val status: StateFlow<SpeechStatus> = _status.asStateFlow()

    private val _speaking = MutableStateFlow(false)
    override val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private var current: Job? = null

    // La frase cuyo audio está sonando: solo ella apaga [speaking] al terminar o cancelarse.
    private var speakingJob: Job? = null
    private var currentPriority = SpeechPriority.NORMAL

    @Volatile
    private var remotePausedUntilMs = 0L

    init {
        scope.launch { local.state.collect { refreshStatus() } }
    }

    @Synchronized
    override fun speak(text: String, priority: SpeechPriority) {
        if (text.isBlank() || !isEnabled()) return
        val playing = current?.isActive == true
        if (playing && currentPriority == SpeechPriority.HIGH && priority == SpeechPriority.NORMAL) return
        current?.cancel()
        currentPriority = priority
        current = scope.launch {
            val job = coroutineContext.job
            try {
                say(text) { markSpeaking(job) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e)
            } finally {
                stopSpeaking(job)
            }
        }
    }

    @Synchronized
    override fun stop() {
        current?.cancel()
        current = null
    }

    @Synchronized
    private fun markSpeaking(job: Job) {
        speakingJob = job
        _speaking.value = true
    }

    @Synchronized
    private fun stopSpeaking(job: Job) {
        if (speakingJob !== job) return
        speakingJob = null
        _speaking.value = false
    }

    private suspend fun say(text: String, onStart: () -> Unit) {
        refreshStatus()
        if (localReady()) {
            try {
                local.play(text, onStart)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Falló la voz del reloj en esta frase: se intenta con Gemini.
                onError(e)
            }
        }
        if (remotePaused()) return
        try {
            remote.play(text, onStart)
        } catch (e: QuotaExhaustedError) {
            remotePausedUntilMs = clock() + QUOTA_PAUSE_MS
            refreshStatus()
            scope.launch {
                delay(QUOTA_PAUSE_MS)
                refreshStatus()
            }
            throw e
        }
    }

    // Al abrir la app el motor del reloj puede estar arrancando: se le da un momento.
    private suspend fun localReady(): Boolean {
        val state = withTimeoutOrNull(localStartWaitMs) {
            local.state.first { it != LocalVoiceState.STARTING }
        }
        return state == LocalVoiceState.READY
    }

    private fun remotePaused() = clock() < remotePausedUntilMs

    private fun refreshStatus() {
        val localState = local.state.value
        _status.value = SpeechStatus(
            engine = when {
                localState == LocalVoiceState.READY -> SpeechEngine.DEVICE
                remotePaused() -> SpeechEngine.NONE
                else -> SpeechEngine.GEMINI
            },
            deviceVoiceMissing = localState == LocalVoiceState.MISSING_DATA,
        )
    }
}
