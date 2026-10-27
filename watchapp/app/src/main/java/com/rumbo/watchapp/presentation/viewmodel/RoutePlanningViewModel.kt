package com.rumbo.watchapp.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rumbo.watchapp.application.location.LocationTracker
import com.rumbo.watchapp.application.usecase.PlanRouteFromVoice
import com.rumbo.watchapp.application.usecase.TrackingState
import com.rumbo.watchapp.domain.error.AppError
import com.rumbo.watchapp.domain.port.AudioRecorder
import com.rumbo.watchapp.domain.port.NavigationRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val RECORDING_MS = 4_000L

class RoutePlanningViewModel(
    private val recorder: AudioRecorder,
    private val planRoute: PlanRouteFromVoice,
    private val locationTracker: LocationTracker,
    private val navigationRunner: NavigationRunner,
    val navigationState: StateFlow<TrackingState>,
) : ViewModel() {

    private val _state = MutableStateFlow<PlanState>(PlanState.Start)
    val state: StateFlow<PlanState> = _state.asStateFlow()

    /** Enciende el GPS (no hace nada si ya está encendido o no hay permiso). */
    fun startLocation() {
        locationTracker.start()
    }

    /** Graba 4 s, interpreta la orden y busca la ruta. El permiso de micrófono ya debe estar dado. */
    fun listen() {
        val current = _state.value
        if (current is PlanState.Listening || current is PlanState.Processing) return
        viewModelScope.launch {
            try {
                _state.value = PlanState.Listening
                recorder.start()
                delay(RECORDING_MS)
                val audio = recorder.stop()
                _state.value = PlanState.Processing
                _state.value = PlanState.Confirming(planRoute(audio))
            } catch (e: CancellationException) {
                recorder.release()
                throw e
            } catch (e: AppError) {
                recorder.release()
                showError(e.message ?: "Algo salió mal, intenta de nuevo")
            } catch (e: Exception) {
                recorder.release()
                showError("Algo salió mal, intenta de nuevo")
            }
        }
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
        _state.value = PlanState.Start
    }

    fun showError(message: String) {
        _state.value = PlanState.Error(message)
    }

    override fun onCleared() {
        recorder.release()
        // Si se está navegando, el servicio sigue dueño del GPS; si no, se apaga.
        if (_state.value !is PlanState.OnRoute) locationTracker.stop()
    }
}
