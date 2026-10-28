package com.rumbo.watchapp.di

import android.content.Context
import android.util.Log
import com.rumbo.watchapp.application.location.LocationTracker
import com.rumbo.watchapp.application.usecase.ManageSavedPlaces
import com.rumbo.watchapp.application.usecase.PlanRouteFromVoice
import com.rumbo.watchapp.application.usecase.RegisterDevice
import com.rumbo.watchapp.application.usecase.TrackNavigation
import com.rumbo.watchapp.domain.port.AudioRecorder
import com.rumbo.watchapp.infrastructure.audio.MediaAudioRecorder
import com.rumbo.watchapp.infrastructure.location.AndroidLocationProvider
import com.rumbo.watchapp.infrastructure.navigation.ForegroundNavigationRunner
import com.rumbo.watchapp.infrastructure.network.DevicesApiClient
import com.rumbo.watchapp.infrastructure.network.PlacesApiClient
import com.rumbo.watchapp.infrastructure.network.RoutesApiClient
import com.rumbo.watchapp.infrastructure.network.SavedPlacesApiClient
import com.rumbo.watchapp.infrastructure.network.VibrationEventsApiClient
import com.rumbo.watchapp.infrastructure.network.VoiceApiClient
import com.rumbo.watchapp.infrastructure.storage.PreferencesDeviceStore
import com.rumbo.watchapp.infrastructure.vibration.AndroidVibrationController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Composition root: único lugar donde se eligen las implementaciones. Uno por proceso. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val clock: () -> Long = System::currentTimeMillis
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val registerDevice = RegisterDevice(DevicesApiClient, PreferencesDeviceStore(appContext))

    val audioRecorder: AudioRecorder = MediaAudioRecorder(appContext)
    val locationTracker = LocationTracker(AndroidLocationProvider(appContext), clock, appScope)
    val planRouteFromVoice = PlanRouteFromVoice(
        VoiceApiClient,
        PlacesApiClient,
        RoutesApiClient,
        origin = locationTracker::origin,
    )
    val manageSavedPlaces = ManageSavedPlaces(
        SavedPlacesApiClient,
        RoutesApiClient,
        origin = locationTracker::origin,
    )
    val trackNavigation = TrackNavigation(
        fixes = locationTracker.fixes,
        routes = RoutesApiClient,
        vibration = AndroidVibrationController(appContext),
        vibrationEvents = VibrationEventsApiClient,
        clock = clock,
    )
    val navigationRunner = ForegroundNavigationRunner(appContext)

    /** Registra el reloj en el backend sin bloquear el arranque; si falla, se reintenta al próximo. */
    fun registerDeviceInBackground() {
        appScope.launch {
            try {
                registerDevice()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Rumbo", "No se pudo registrar el reloj: ${e.message}")
            }
        }
    }
}
