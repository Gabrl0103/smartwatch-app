package com.rumbo.watchapp.di

import android.content.Context
import android.util.Log
import com.rumbo.watchapp.application.location.LocationTracker
import com.rumbo.watchapp.application.usecase.ManageSavedPlaces
import com.rumbo.watchapp.application.usecase.PlanRouteFromVoice
import com.rumbo.watchapp.application.usecase.RegisterDevice
import com.rumbo.watchapp.application.usecase.TrackNavigation
import com.rumbo.watchapp.domain.port.ActiveRouteStore
import com.rumbo.watchapp.domain.port.AudioRecorder
import com.rumbo.watchapp.domain.port.ServerStatus
import com.rumbo.watchapp.domain.port.SpeechOutput
import com.rumbo.watchapp.domain.port.VibrationController
import com.rumbo.watchapp.domain.port.VibrationSettingsStore
import com.rumbo.watchapp.domain.port.VoiceSettings
import com.rumbo.watchapp.infrastructure.audio.MediaAudioRecorder
import com.rumbo.watchapp.infrastructure.location.AndroidLocationProvider
import com.rumbo.watchapp.infrastructure.logging.AndroidLogger
import com.rumbo.watchapp.infrastructure.navigation.ForegroundNavigationRunner
import com.rumbo.watchapp.infrastructure.network.BackendServerStatus
import com.rumbo.watchapp.infrastructure.network.DevicesApiClient
import com.rumbo.watchapp.infrastructure.network.HealthApiClient
import com.rumbo.watchapp.infrastructure.network.PlacesApiClient
import com.rumbo.watchapp.infrastructure.network.RoutesApiClient
import com.rumbo.watchapp.infrastructure.network.SavedPlacesApiClient
import com.rumbo.watchapp.infrastructure.network.VibrationEventsApiClient
import com.rumbo.watchapp.infrastructure.network.VoiceApiClient
import com.rumbo.watchapp.infrastructure.storage.PreferencesActiveRouteStore
import com.rumbo.watchapp.infrastructure.storage.PreferencesDeviceStore
import com.rumbo.watchapp.infrastructure.storage.PreferencesVibrationSettingsStore
import com.rumbo.watchapp.infrastructure.storage.PreferencesVoiceSettings
import com.rumbo.watchapp.infrastructure.tts.AndroidTextToSpeech
import com.rumbo.watchapp.infrastructure.tts.GeminiSpeechPlayer
import com.rumbo.watchapp.infrastructure.tts.SpeechOutputImpl
import com.rumbo.watchapp.infrastructure.vibration.AndroidVibrationController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Composition root: único lugar donde se eligen las implementaciones. Uno por proceso. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val clock: () -> Long = System::currentTimeMillis
    // Un error inesperado en segundo plano queda en el log; nunca cierra la app.
    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            CoroutineExceptionHandler { _, e -> Log.e("Rumbo", "Error inesperado en segundo plano", e) },
    )
    private val registerDevice = RegisterDevice(DevicesApiClient, PreferencesDeviceStore(appContext))

    val audioRecorder: AudioRecorder = MediaAudioRecorder(appContext)
    val voiceSettings: VoiceSettings = PreferencesVoiceSettings(appContext)
    // La voz del reloj arranca aquí, al iniciar la app, para estar lista en la primera frase.
    val speech: SpeechOutput = SpeechOutputImpl(
        local = AndroidTextToSpeech(appContext),
        remote = GeminiSpeechPlayer(appContext),
        isEnabled = voiceSettings::isEnabled,
        scope = appScope,
        clock = clock,
        onError = { Log.w("Voz", "No se pudo hablar: ${it.message}") },
    )
    // Uno solo para la navegación y para probar los patrones en "Vibraciones", con los mismos ajustes.
    val vibration: VibrationController = AndroidVibrationController(appContext)
    val vibrationSettings: VibrationSettingsStore = PreferencesVibrationSettingsStore(appContext)
    val locationTracker = LocationTracker(AndroidLocationProvider(appContext), clock, appScope)
    val serverStatus: ServerStatus = BackendServerStatus
    val planRouteFromVoice = PlanRouteFromVoice(
        VoiceApiClient,
        PlacesApiClient,
        RoutesApiClient,
        origin = locationTracker::awaitOrigin,
    )
    val manageSavedPlaces = ManageSavedPlaces(
        SavedPlacesApiClient,
        RoutesApiClient,
        origin = locationTracker::awaitOrigin,
    )
    val activeRoutes: ActiveRouteStore = PreferencesActiveRouteStore(appContext, clock)
    val trackNavigation = TrackNavigation(
        fixes = locationTracker.fixes,
        routes = RoutesApiClient,
        vibration = vibration,
        vibrationEvents = VibrationEventsApiClient,
        speech = speech,
        clock = clock,
        activeRoutes = activeRoutes,
        logger = AndroidLogger,
        vibrationSettings = vibrationSettings::load,
    )
    val navigationRunner = ForegroundNavigationRunner(appContext, trackNavigation, locationTracker, appScope)

    /**
     * Despierta el servidor de Render apenas se abre la app, para que al pedir la ruta ya esté
     * listo. Si falla no pasa nada: la petición real avisa y reintenta.
     */
    fun wakeUpServerInBackground() {
        appScope.launch {
            try {
                HealthApiClient.ping()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Red", "No se pudo despertar el servidor: ${e.message}")
            }
        }
    }

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
