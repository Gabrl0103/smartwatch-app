package com.rumbo.watchapp.infrastructure.logging

import android.util.Log
import com.rumbo.watchapp.domain.port.Logger

/** Escribe en Logcat; filtra con `adb logcat -s Nav Vibracion Red`. */
object AndroidLogger : Logger {
    override fun info(tag: String, message: String) {
        Log.i(tag, message)
    }

    override fun warn(tag: String, message: String) {
        Log.w(tag, message)
    }
}
