package com.rumbo.watchapp.infrastructure.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.rumbo.watchapp.domain.error.AppError
import com.rumbo.watchapp.domain.port.AudioRecorder
import java.io.File

/** Graba el micrófono en AAC, el formato que el backend envía a Gemini. */
class MediaAudioRecorder(private val context: Context) : AudioRecorder {

    private val file = File(context.cacheDir, "orden.aac")
    private var recorder: MediaRecorder? = null

    @Suppress("DEPRECATION")
    override fun start() {
        release()
        val newRecorder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }
        try {
            newRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            newRecorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            newRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            newRecorder.setAudioSamplingRate(16000)
            newRecorder.setAudioEncodingBitRate(64000)
            newRecorder.setOutputFile(file.absolutePath)
            newRecorder.prepare()
            newRecorder.start()
            recorder = newRecorder
        } catch (e: Exception) {
            newRecorder.release()
            throw AppError("No pude usar el micrófono")
        }
    }

    override fun stop(): File {
        val current = recorder ?: throw AppError("No se estaba grabando")
        recorder = null
        val stopped = runCatching { current.stop() }.isSuccess
        current.release()
        if (!stopped || file.length() == 0L) throw AppError("No se grabó audio, intenta de nuevo")
        return file
    }

    override fun release() {
        recorder?.let { runCatching { it.release() } }
        recorder = null
    }
}
