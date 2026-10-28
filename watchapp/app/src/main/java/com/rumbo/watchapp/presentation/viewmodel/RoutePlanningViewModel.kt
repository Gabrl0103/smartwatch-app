package com.rumbo.watchapp.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rumbo.watchapp.application.location.LocationTracker
import com.rumbo.watchapp.application.speech.confirmPhrase
import com.rumbo.watchapp.application.usecase.ManageSavedPlaces
import com.rumbo.watchapp.application.usecase.PlanRouteFromVoice
import com.rumbo.watchapp.application.usecase.TrackingState
import com.rumbo.watchapp.domain.error.AppError
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.model.SavedPlace
import com.rumbo.watchapp.domain.port.AudioRecorder
import com.rumbo.watchapp.domain.port.NavigationRunner
import com.rumbo.watchapp.domain.port.SpeechOutput
import com.rumbo.watchapp.domain.port.SpeechStatus
import com.rumbo.watchapp.domain.port.VoiceSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val RECORDING_MS = 4_000L
private const val GENERIC_ERROR = "Algo salió mal, intenta de nuevo"

class RoutePlanningViewModel(
    private val recorder: AudioRecorder,
    private val planRoute: PlanRouteFromVoice,
    private val savedPlaces: ManageSavedPlaces,
    private val locationTracker: LocationTracker,
    private val navigationRunner: NavigationRunner,
    val navigationState: StateFlow<TrackingState>,
    private val speech: SpeechOutput,
    private val voiceSettings: VoiceSettings,
) : ViewModel() {

    private val _state = MutableStateFlow<PlanState>(PlanState.Start)
    val state: StateFlow<PlanState> = _state.asStateFlow()

    private val _voiceEnabled = MutableStateFlow(voiceSettings.isEnabled())
    val voiceEnabled: StateFlow<Boolean> = _voiceEnabled.asStateFlow()

    /** Qué voz se está usando, para mostrarlo en Inicio. */
    val speechStatus: StateFlow<SpeechStatus> = speech.status

    private val busy get() = _state.value.let {
        it is PlanState.Listening || it is PlanState.Processing || it is PlanState.LoadingPlaces
    }

    /** Enciende el GPS (no hace nada si ya está encendido o no hay permiso). */
    fun startLocation() {
        locationTracker.start()
    }

    /** Graba 4 s, interpreta la orden y busca la ruta. El permiso de micrófono ya debe estar dado. */
    fun listen() {
        if (busy) return
        viewModelScope.launch {
            try {
                _state.value = PlanState.Listening
                recorder.start()
                delay(RECORDING_MS)
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
            }
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

    /** Interruptor "Voz: sí/no"; al apagarla se calla lo que esté sonando. Las vibraciones siguen. */
    fun setVoiceEnabled(enabled: Boolean) {
        voiceSettings.setEnabled(enabled)
        _voiceEnabled.value = enabled
        if (!enabled) speech.stop()
    }

    /** Arranca la navegación en el servicio en primer plano para que siga con la pantalla apagada. */
    fun go() {
        val current = _state.value
        if (current !is PlanState.Confirming) return
        navigationRunner.start(current.route)
        _state.value = PlanState.OnRoute(current.route)
    }

    /** Termina la navegación: el servicio apaga GPS y vibraciones; vuelve al inicio. */
    fun finish() {
        navigationRunner.stop()
        reset()
    }

    fun reset() {
        speech.stop()
        _state.value = PlanState.Start
    }

    /** Muestra el error y también lo lee. */
    fun showError(message: String, retry: RetryAction = RetryAction.LISTEN) {
        _state.value = PlanState.Error(message, retry)
        speech.speak(message)
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
        viewModelScope.launch {
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
