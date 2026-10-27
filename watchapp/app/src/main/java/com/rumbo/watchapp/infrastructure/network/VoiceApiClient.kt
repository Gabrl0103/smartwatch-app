package com.rumbo.watchapp.infrastructure.network

import com.rumbo.watchapp.domain.model.VoiceResult
import com.rumbo.watchapp.domain.port.VoiceRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Envía el audio grabado (AAC) al backend para que lo transcriba e interprete. */
object VoiceApiClient : VoiceRepository {

    override suspend fun interpret(audioFile: File): VoiceResult {
        val audio = withContext(Dispatchers.IO) { audioFile.readBytes() }
        val body = backendRequest("POST", "/voice/interpret", "audio/aac", audio).requireOk()
        return parseJson(body) { json ->
            VoiceResult(
                text = json.optString("text"),
                intent = json.optString("intent"),
                destination = json.optionalString("destination"),
                question = json.optionalString("question"),
            )
        }
    }
}
