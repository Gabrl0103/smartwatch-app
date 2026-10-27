package com.rumbo.watchapp.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rumbo.watchapp.application.location.LocationTracker
import com.rumbo.watchapp.application.usecase.PlanRouteFromVoice
import com.rumbo.watchapp.application.usecase.TrackNavigation
import com.rumbo.watchapp.application.usecase.TrackingState
import com.rumbo.watchapp.domain.error.AppError
import com.rumbo.watchapp.domain.port.AudioRecorder
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
    private val navigation: TrackNavigation,
) : ViewModel() {

    private val _state = MutableStateFlow<PlanState>(PlanState.Start)
    val state: StateFlow<PlanState> = _state.asStateFlow()

    val navigationState: StateFlow<TrackingState> = navigation.state

    /** Enciende el GPS (no hace nada si ya está encendido o no hay permiso). */
    fun startLocation() {
        locationTracker.start(viewModelScope)
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

    fun go() {
        val current = _state.value
        if (current !is PlanState.Confirming) return
        startLocation()
        navigation.start(current.route, viewModelScope)
        _state.value = PlanState.OnRoute(current.route)
    }

    /** Termina la navegación: apaga GPS y vibraciones y vuelve al inicio. */
    fun finish() {
        navigation.stop()
        locationTracker.stop()
        reset()
    }

    fun reset() {
        _state.value = PlanState.Start
    }

    fun showError(message: String) {
        _state.value = PlanState.Error(message)
    }

    override fun onCleared() {
        navigation.stop()
        locationTracker.stop()
        recorder.release()
    }
}
