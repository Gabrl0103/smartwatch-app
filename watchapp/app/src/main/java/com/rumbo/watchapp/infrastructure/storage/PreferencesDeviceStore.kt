package com.rumbo.watchapp.infrastructure.storage

import android.content.Context
import com.rumbo.watchapp.domain.port.DeviceStore
import java.util.UUID

private const val PREFERENCES = "rumbo_device"
private const val KEY_DEVICE_ID = "device_id"
private const val KEY_REGISTERED = "registered"

/** Guarda el id del reloj y si ya se registró, en SharedPreferences. */
class PreferencesDeviceStore(context: Context) : DeviceStore {

    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override fun deviceId(): String =
        preferences.getString(KEY_DEVICE_ID, null)
            ?: UUID.randomUUID().toString().also {
                preferences.edit().putString(KEY_DEVICE_ID, it).apply()
            }

    override fun isRegistered(): Boolean = preferences.getBoolean(KEY_REGISTERED, false)

    override fun markRegistered() {
        preferences.edit().putBoolean(KEY_REGISTERED, true).apply()
    }
}
