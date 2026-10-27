package com.rumbo.watchapp.domain.port

import com.rumbo.watchapp.domain.model.VoiceResult
import java.io.File

interface VoiceRepository {
    /** Transcribe e interpreta una orden de voz grabada. */
    suspend fun interpret(audioFile: File): VoiceResult
}
