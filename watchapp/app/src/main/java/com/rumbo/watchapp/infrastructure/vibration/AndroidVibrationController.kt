package com.rumbo.watchapp.infrastructure.vibration

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.rumbo.watchapp.domain.port.VibrationController
import com.rumbo.watchapp.domain.vibration.VibrationPattern

private const val TAG = "Vibracion"
private const val NO_REPEAT = -1

/** Vibra con el motor del reloj. Siempre registra el patrón en Logcat: el emulador no vibra. */
class AndroidVibrationController(context: Context) : VibrationController {

    @Suppress("DEPRECATION")
    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    override fun play(pattern: VibrationPattern) {
        Log.i(TAG, "${pattern.label} tiempos=${pattern.timingsMs} amplitudes=${pattern.amplitudes}")
        if (!vibrator.hasVibrator()) return
        val timings = pattern.timingsMs.toLongArray()
        val effect = if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings, pattern.amplitudes.toIntArray(), NO_REPEAT)
        } else {
            VibrationEffect.createWaveform(timings, NO_REPEAT)
        }
        vibrator.vibrate(effect)
    }

    override fun stop() {
        Log.i(TAG, "Detenida")
        vibrator.cancel()
    }
}
