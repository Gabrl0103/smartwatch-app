package com.rumbo.watchapp.infrastructure.tts

import com.rumbo.watchapp.domain.port.SpeechOutput
import com.rumbo.watchapp.domain.port.SpeechPriority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Dice una frase y vuelve cuando terminó; si se cancela la corrutina, debe callarse. */
interface SpeechPlayer {
    suspend fun play(text: String)
}

/**
 * Decide qué frase suena: una nueva cancela la actual, salvo que la nueva sea NORMAL y la
 * actual HIGH (esa se descarta). Si la voz está apagada no dice nada. Los errores del
 * reproductor van a [onError] y nunca salen de aquí.
 */
class SpeechOutputImpl(
    private val player: SpeechPlayer,
    private val isEnabled: () -> Boolean,
    private val scope: CoroutineScope,
    private val onError: (Exception) -> Unit = {},
) : SpeechOutput {

    private var current: Job? = null
    private var currentPriority = SpeechPriority.NORMAL

    @Synchronized
    override fun speak(text: String, priority: SpeechPriority) {
        if (text.isBlank() || !isEnabled()) return
        val playing = current?.isActive == true
        if (playing && currentPriority == SpeechPriority.HIGH && priority == SpeechPriority.NORMAL) return
        current?.cancel()
        currentPriority = priority
        current = scope.launch {
            try {
                player.play(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    @Synchronized
    override fun stop() {
        current?.cancel()
        current = null
    }
}
