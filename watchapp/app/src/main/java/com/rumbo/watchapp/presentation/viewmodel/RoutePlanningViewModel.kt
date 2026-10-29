package com.rumbo.watchapp.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rumbo.watchapp.application.location.LocationTracker
import com.rumbo.watchapp.application.speech.confirmPhrase
import com.rumbo.watchapp.application.usecase.ManageSavedPlaces
import com.rumbo.watchapp.application.usecase.PlanRouteFromVoice
import com.rumbo.watchapp.application.usecase.TrackingState
import com.rumbo.watchapp.domain.error.AppError
import com.rumbo.watchapp.domain.error.SERVER_WAKING_MESSAGE
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.model.SavedPlace
import com.rumbo.watchapp.domain.navigation.NavigationStatus
import com.rumbo.watchapp.domain.port.ActiveRouteStore
import com.rumbo.watchapp.domain.port.AudioRecorder
import com.rumbo.watchapp.domain.port.NavigationRunner
import com.rumbo.watchapp.domain.port.SpeechOutput
import com.rumbo.watchapp.domain.port.SpeechStatus
import com.rumbo.watchapp.domain.port.VibrationController
import com.rumbo.watchapp.domain.port.VibrationSettingsStore
import com.rumbo.watchapp.domain.port.VoiceSettings
import com.rumbo.watchapp.domain.vibration.VibrationCue
import com.rumbo.watchapp.domain.vibration.VibrationPattern
import com.rumbo.watchapp.domain.vibration.VibrationPatterns
import com.rumbo.watchapp.domain.vibration.VibrationSettings
import com.rumbo.watchapp.domain.vibration.next
import com.rumbo.watchapp.domain.voice.MAX_RECORDING_MS
import com.rumbo.watchapp.domain.voice.SpeechEndDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// Por si el micrófono deja de dar niveles: se corta igual un poco después del máximo.
private const val LISTEN_TIMEOUT_MS = MAX_RECORDING_MS + 1_000
// Pausa entre un patrón y el siguiente en la secuencia de demostración.
const val DEMO_PAUSE_MS = 2_000L

// Lo que vibra al cambiar un ajuste, para sentir la diferencia enseguida.
val SETTINGS_SAMPLE = VibrationCue.LEFT
private const val GENERIC_ERROR = "Algo salió mal, intenta de nuevo"
const val GPS_SEARCHING_MESSAGE = "Buscando señal GPS"

class RoutePlanningViewModel(
    private val recorder: AudioRecorder,
    private val planRoute: PlanRouteFromVoice,
    private val savedPlaces: ManageSavedPlaces,
    private val locationTracker: LocationTracker,
    private val navigationRunner: NavigationRunner,
    val navigationState: StateFlow<TrackingState>,
    private val repeatNavigation: () -> Unit,
    private val vibration: VibrationController,
    private val vibrationSettingsStore: VibrationSettingsStore,
    private val speech: SpeechOutput,
    private val voiceSettings: VoiceSettings,
    serverWaking: StateFlow<Boolean>,
    private val activeRoutes: ActiveRouteStore,
) : ViewModel() {

    private val _state = MutableStateFlow<PlanState>(PlanState.Start)
    val state: StateFlow<PlanState> = _state.asStateFlow()

    private val _voiceEnabled = MutableStateFlow(voiceSettings.isEnabled())
    val voiceEnabled: StateFlow<Boolean> = _voiceEnabled.asStateFlow()

    /** Qué voz se está usando, para mostrarlo en Inicio. */
    val speechStatus: StateFlow<SpeechStatus> = speech.status

    /** Si la app está hablando ahora, para animar las barras en Confirmar. */
    val speaking: StateFlow<Boolean> = speech.speaking

    /** Si el motor controla la intensidad; elige la versión de los patrones que se enseña y vibra. */
    val supportsAmplitude: Boolean = vibration.supportsAmplitude

    private val _vibrationSettings = MutableStateFlow(vibrationSettingsStore.load())

    /** Ajustes "Intensidad" y "Duración"; los mismos que usa la navegación. */
    val vibrationSettings: StateFlow<VibrationSettings> = _vibrationSettings.asStateFlow()

    /** Los patrones tal como los siente este reloj con los ajustes, para la pantalla "Vibraciones". */
    val vibrationPatterns: StateFlow<Map<VibrationCue, VibrationPattern>> = _vibrationSettings
        .map { VibrationPatterns.forDevice(supportsAmplitude, it) }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            VibrationPatterns.forDevice(supportsAmplitude, _vibrationSettings.value),
        )

    private val _voiceLevel = MutableStateFlow(0f)

    /** Nivel de la voz de 0 a 1 mientras se graba la orden; 0 el resto del tiempo. */
    val voiceLevel: StateFlow<Float> = _voiceLevel.asStateFlow()

    private val _previousRoute = MutableStateFlow<Route?>(null)

    /** Ruta que quedó a medias (el proceso murió navegando): Inicio ofrece "Continuar ruta anterior". */
    val previousRoute: StateFlow<Route?> = _previousRoute.asStateFlow()

    // Grabación o carga en curso; volver al inicio la cancela.
    private var job: Job? = null

    private val busy get() = _state.value.let {
        it is PlanState.Listening || it is PlanState.Processing || it is PlanState.LoadingPlaces
    }

    /**
     * Por qué se está esperando, para la pantalla Procesando: "Buscando señal GPS" mientras no hay
     * un origen bueno o "El servidor está despertando..." mientras Render arranca; null si nada.
     */
    val waitMessage: StateFlow<String?> = combine(locationTracker.searching, serverWaking) { gps, waking ->
        when {
            gps -> GPS_SEARCHING_MESSAGE
            waking -> SERVER_WAKING_MESSAGE
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // Si el servicio sigue navegando (la pantalla se cerró y se volvió a abrir), se retoma ahí.
        val running = navigationRunner.activeRoute
        if (running != null) {
            _state.value = PlanState.OnRoute(running)
        } else {
            activeRoutes.load()?.let { route ->
                _previousRoute.value = route
                speech.speak("Tienes una ruta sin terminar a ${route.destinationName}. Toca Continuar ruta anterior.")
            }
        }
        // Quien no ve la pantalla también tiene que saber por qué tarda.
        viewModelScope.launch {
            waitMessage.collect { message ->
                val waiting = _state.value == PlanState.Processing || _state.value == PlanState.LoadingPlaces
                if (message != null && waiting) speech.speak(message)
            }
        }
    }

    /** Enciende el GPS (no hace nada si ya está encendido o no hay permiso). */
    fun startLocation() {
        locationTracker.start()
    }

    /**
     * Graba la orden hasta que la persona termina de hablar (ver [SpeechEndDetector]: 1,2 s de
     * silencio tras la voz, como mucho 8 s), la interpreta y busca la ruta. Mientras graba,
     * [voiceLevel] mueve las barras de "Escuchando". El permiso de micrófono ya debe estar dado.
     */
    fun listen() {
        if (busy) return
        job = viewModelScope.launch {
            try {
                _state.value = PlanState.Listening
                recorder.start()
                awaitEndOfSpeech()
                val audio = recorder.stop()
                _state.value = PlanState.Processing
                confirm(planRoute(audio))
            } catch (e: CancellationException) {
                recorder.release()
                throw e
            } catch (e: AppError) {
                recorder.release()
                showError(e.message ?: GENERIC_ERROR)
            } catch (e: Exception) {
                recorder.release()
                showError(GENERIC_ERROR)
            } finally {
                _voiceLevel.value = 0f
            }
        }
    }

    /** "Cancelar" en Escuchando: deja de grabar sin enviar nada y vuelve a Inicio. */
    fun cancelListening() {
        if (_state.value == PlanState.Listening) reset()
    }

    private suspend fun awaitEndOfSpeech() {
        val detector = SpeechEndDetector()
        withTimeoutOrNull(LISTEN_TIMEOUT_MS) {
            recorder.levels
                .takeWhile { level ->
                    _voiceLevel.value = level
                    !detector.onLevel(level)
                }
                .collect()
        }
    }

    fun openSavedPlaces() {
        if (busy) return
        launchSavedPlaces {
            _state.value = PlanState.LoadingPlaces
            _state.value = PlanState.SavedPlaces(savedPlaces.list())
        }
    }

    /** Pide la ruta hasta el lugar y pasa a Confirmar, igual que por voz (ya figura como guardado). */
    fun chooseSavedPlace(place: SavedPlace) {
        if (_state.value !is PlanState.SavedPlaces) return
        launchSavedPlaces {
            _state.value = PlanState.Processing
            confirm(savedPlaces.planRoute(place), SaveStatus.SAVED)
        }
    }

    fun askDelete(place: SavedPlace) {
        val current = _state.value
        if (current is PlanState.SavedPlaces) _state.value = PlanState.ConfirmDelete(place, current.places)
    }

    fun cancelDelete() {
        val current = _state.value
        if (current is PlanState.ConfirmDelete) _state.value = PlanState.SavedPlaces(current.places)
    }

    fun confirmDelete() {
        val current = _state.value
        if (current !is PlanState.ConfirmDelete) return
        launchSavedPlaces {
            _state.value = PlanState.LoadingPlaces
            savedPlaces.delete(current.place)
            _state.value = PlanState.SavedPlaces(savedPlaces.list())
        }
    }

    /** Guarda el destino que se está confirmando como lugar guardado. */
    fun saveDestination() {
        val current = _state.value
        if (current !is PlanState.Confirming) return
        if (current.save == SaveStatus.SAVING || current.save == SaveStatus.SAVED) return
        val route = current.route
        setSaveStatus(route, SaveStatus.SAVING)
        viewModelScope.launch {
            val status = try {
                savedPlaces.saveDestination(route)
                SaveStatus.SAVED
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SaveStatus.FAILED
            }
            setSaveStatus(route, status)
            if (status == SaveStatus.FAILED) speech.speak(SAVE_FAILED_MESSAGE)
        }
    }

    fun openVibrations() {
        if (!busy) _state.value = PlanState.Vibrations()
    }

    /** Hace sentir un patrón en la pantalla "Vibraciones". */
    fun playVibration(cue: VibrationCue) {
        if (_state.value == PlanState.Vibrations()) vibration.play(patternFor(cue))
    }

    /** Ajuste "Intensidad": pasa al siguiente (suave → normal → fuerte), lo guarda y lo hace sentir. */
    fun changeVibrationIntensity() = changeVibrationSettings { it.copy(intensity = it.intensity.next()) }

    /** Ajuste "Duración": cambia entre normal y larga, lo guarda y lo hace sentir. */
    fun changeVibrationDuration() = changeVibrationSettings { it.copy(duration = it.duration.next()) }

    /**
     * "Secuencia de demostración": vibra todos los patrones en orden y muestra el nombre de cada
     * uno mientras suena, con [DEMO_PAUSE_MS] de pausa después de cada patrón. Al terminar
     * vuelve a la lista.
     */
    fun startVibrationDemo() {
        if (_state.value != PlanState.Vibrations()) return
        job = viewModelScope.launch {
            val cues = VibrationCue.entries
            cues.forEachIndexed { i, cue ->
                val pattern = patternFor(cue)
                _state.value = PlanState.Vibrations(DemoStep(cue, i + 1, cues.size))
                vibration.play(pattern)
                delay(pattern.durationMs + DEMO_PAUSE_MS)
            }
            _state.value = PlanState.Vibrations()
        }
    }

    /** "Detener" (o volver) durante la demostración: corta la vibración y vuelve a la lista. */
    fun stopVibrationDemo() {
        val current = _state.value as? PlanState.Vibrations ?: return
        if (current.demo == null) return
        job?.cancel()
        job = null
        vibration.stop()
        _state.value = PlanState.Vibrations()
    }

    /** Interruptor "Voz: sí/no"; al apagarla se calla lo que esté sonando. Las vibraciones siguen. */
    fun setVoiceEnabled(enabled: Boolean) {
        voiceSettings.setEnabled(enabled)
        _voiceEnabled.value = enabled
        if (!enabled) speech.stop()
    }

    /** Arranca la navegación en el servicio en primer plano para que siga con la pantalla apagada. */
    fun go() {
        val current = _state.value
        if (current is PlanState.Confirming) startNavigation(current.route)
    }

    /** "Continuar ruta anterior" en Inicio: retoma la ruta guardada desde donde esté el usuario. */
    fun resumePreviousRoute() {
        val route = _previousRoute.value ?: return
        if (_state.value == PlanState.Start) startNavigation(route)
    }

    /**
     * El usuario negó [permission]: muestra y dice para qué hace falta. Las notificaciones solo se
     * piden para arrancar una ruta (la que se confirma o la anterior): queda guardada para
     * arrancar cuando lo dé o elija "Ir sin notificación".
     */
    fun permissionDenied(permission: Permission) {
        val current = _state.value
        val route = when {
            permission != Permission.NOTIFICATIONS -> null
            current is PlanState.Confirming -> current.route
            else -> _previousRoute.value
        }
        _state.value = PlanState.PermissionNeeded(permission, route)
        speech.speak(permission.explanation)
    }

    /** Volvió de Ajustes con el permiso dado: si una ruta lo esperaba, arranca; si no, a Inicio. */
    fun permissionGranted() {
        val current = _state.value as? PlanState.PermissionNeeded ?: return
        val route = current.route
        if (route != null) startNavigation(route) else reset()
    }

    /** Sin permiso de notificaciones se navega igual: solo falta el aviso en la lista. */
    fun goWithoutNotification() {
        val current = _state.value as? PlanState.PermissionNeeded ?: return
        current.route?.let(::startNavigation)
    }

    /** "Repetir" en ruta: vuelve a vibrar y a decir la indicación actual. */
    fun repeatInstruction() {
        if (_state.value is PlanState.OnRoute) repeatNavigation()
    }

    /** "Terminar": apaga GPS, vibraciones, voz, servicio y notificación, olvida la ruta y vuelve a Inicio. */
    fun finish() {
        navigationRunner.stop()
        activeRoutes.clear()
        reset()
    }

    /** Responde "Seguir" a "¿Terminar la ruta?". */
    fun keepNavigating() {
        val current = _state.value
        if (current is PlanState.OnRoute) _state.value = current.copy(confirmFinish = false)
    }

    /**
     * Gesto de volver. En ruta primero pregunta si terminar (salvo que ya se haya llegado); en
     * Borrar vuelve a la lista; en la demostración de vibraciones la detiene; en el resto, a
     * Inicio. En Inicio no hace nada: ahí sale de la app.
     */
    fun back() {
        when (val current = _state.value) {
            PlanState.Start -> Unit
            is PlanState.ConfirmDelete -> cancelDelete()
            is PlanState.Vibrations -> if (current.demo != null) stopVibrationDemo() else reset()
            is PlanState.OnRoute -> when {
                current.confirmFinish -> keepNavigating()
                navigationState.value.status == NavigationStatus.ARRIVED -> finish()
                else -> _state.value = current.copy(confirmFinish = true)
            }
            else -> reset()
        }
    }

    fun reset() {
        job?.cancel()
        job = null
        if (_state.value is PlanState.Vibrations) vibration.stop()
        speech.stop()
        _previousRoute.value = if (navigationRunner.activeRoute == null) activeRoutes.load() else null
        _state.value = PlanState.Start
    }

    /** Muestra el error y también lo lee. */
    fun showError(message: String, retry: RetryAction = RetryAction.LISTEN) {
        _state.value = PlanState.Error(message, retry)
        speech.speak(message)
    }

    private fun patternFor(cue: VibrationCue): VibrationPattern =
        VibrationPatterns.of(cue, supportsAmplitude, _vibrationSettings.value)

    private fun changeVibrationSettings(change: (VibrationSettings) -> VibrationSettings) {
        if (_state.value != PlanState.Vibrations()) return
        val settings = change(_vibrationSettings.value)
        vibrationSettingsStore.save(settings)
        _vibrationSettings.value = settings
        vibration.play(patternFor(SETTINGS_SAMPLE))
    }

    private fun startNavigation(route: Route) {
        navigationRunner.start(route)
        _previousRoute.value = null
        _state.value = PlanState.OnRoute(route)
    }

    private fun confirm(route: Route, save: SaveStatus = SaveStatus.IDLE) {
        _state.value = PlanState.Confirming(route, save)
        speech.speak(confirmPhrase(route))
    }

    // Solo cambia el botón si el usuario sigue confirmando esa misma ruta.
    private fun setSaveStatus(route: Route, status: SaveStatus) {
        _state.update {
            if (it is PlanState.Confirming && it.route == route) it.copy(save = status) else it
        }
    }

    // Errores del flujo de lugares guardados: "Reintentar" vuelve a abrir la lista.
    private fun launchSavedPlaces(block: suspend () -> Unit) {
        job = viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AppError) {
                showError(e.message ?: GENERIC_ERROR, RetryAction.SAVED_PLACES)
            } catch (e: Exception) {
                showError(GENERIC_ERROR, RetryAction.SAVED_PLACES)
            }
        }
    }

    override fun onCleared() {
        recorder.release()
        // Si se está navegando, el servicio sigue dueño del GPS; si no, se apaga.
        if (_state.value !is PlanState.OnRoute) locationTracker.stop()
    }
}
