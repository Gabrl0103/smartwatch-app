package com.rumbo.watchapp.application.usecase

import com.rumbo.watchapp.domain.error.AppError
import com.rumbo.watchapp.domain.port.DeviceRepository
import com.rumbo.watchapp.domain.port.DeviceStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RegisterDeviceTest {

    private class FakeDevices : DeviceRepository {
        val registered = mutableListOf<String>()
        var error: AppError? = null

        override suspend fun register(deviceId: String) {
            error?.let { throw it }
            registered += deviceId
        }
    }

    private class FakeStore(var registered: Boolean = false) : DeviceStore {
        override fun deviceId() = "reloj-1"

        override fun isRegistered() = registered

        override fun markRegistered() {
            registered = true
        }
    }

    @Test
    fun firstStartRegistersAndRemembersIt() = runBlocking {
        val devices = FakeDevices()
        val store = FakeStore()

        RegisterDevice(devices, store)()

        assertEquals(listOf("reloj-1"), devices.registered)
        assertTrue(store.registered)
    }

    @Test
    fun laterStartsDoNotCallTheBackend() = runBlocking {
        val devices = FakeDevices()
        val registerDevice = RegisterDevice(devices, FakeStore())

        registerDevice()
        registerDevice()

        assertEquals(listOf("reloj-1"), devices.registered)
    }

    @Test
    fun failureIsNotRememberedSoItRetriesNextTime() {
        val devices = FakeDevices().apply { error = AppError("No pude conectar con el servidor") }
        val store = FakeStore()

        assertThrows(AppError::class.java) { runBlocking { RegisterDevice(devices, store)() } }

        assertFalse(store.registered)
    }
}
