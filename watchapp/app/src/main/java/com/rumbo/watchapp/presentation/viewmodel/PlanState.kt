package com.rumbo.watchapp.presentation.viewmodel

import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.model.SavedPlace

/** Estado del botón "Guardar" en la pantalla Confirmar. */
enum class SaveStatus { IDLE, SAVING, SAVED, FAILED }

/** Qué hace "Reintentar" en la pantalla de error. */
enum class RetryAction { LISTEN, SAVED_PLACES }

/** Pantalla actual del flujo "elegir destino (voz o lugar guardado) → confirmar → navegar". */
sealed interface PlanState {
    data object Start : PlanState
    data object Listening : PlanState
    data object Processing : PlanState
    data object LoadingPlaces : PlanState
    data class SavedPlaces(val places: List<SavedPlace>) : PlanState
    data class ConfirmDelete(val place: SavedPlace, val places: List<SavedPlace>) : PlanState
    data class Confirming(val route: Route, val save: SaveStatus = SaveStatus.IDLE) : PlanState
    data class OnRoute(val route: Route) : PlanState
    data class Error(val message: String, val retry: RetryAction = RetryAction.LISTEN) : PlanState
}
