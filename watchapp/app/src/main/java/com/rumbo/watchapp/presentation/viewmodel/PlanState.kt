package com.rumbo.watchapp.presentation.viewmodel

import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.model.SavedPlace

const val SAVE_FAILED_MESSAGE = "No pude guardar el lugar"

/** Estado del botón "Guardar" en la pantalla Confirmar. */
enum class SaveStatus { IDLE, SAVING, SAVED, FAILED }

/** Qué hace "Reintentar" en la pantalla de error. */
enum class RetryAction { LISTEN, SAVED_PLACES }

/** Permisos que se piden en el momento en que hacen falta, cada uno con su explicación si se niega. */
enum class Permission(val explanation: String) {
    MICROPHONE("Necesito el micrófono para escucharte"),
    LOCATION("Necesito tu ubicación para guiarte"),
    NOTIFICATIONS("Necesito mostrar una notificación para guiarte con la pantalla apagada"),
}

/**
 * Pantalla actual del flujo "elegir destino (voz o lugar guardado) → confirmar → navegar".
 * En [OnRoute], [OnRoute.confirmFinish] muestra "¿Terminar la ruta?" tras el gesto de volver.
 * En [PermissionNeeded], [PermissionNeeded.route] es la ruta que espera el permiso de notificaciones.
 */
sealed interface PlanState {
    data object Start : PlanState
    data object Listening : PlanState
    data object Processing : PlanState
    data object LoadingPlaces : PlanState
    data object Vibrations : PlanState
    data class SavedPlaces(val places: List<SavedPlace>) : PlanState
    data class ConfirmDelete(val place: SavedPlace, val places: List<SavedPlace>) : PlanState
    data class Confirming(val route: Route, val save: SaveStatus = SaveStatus.IDLE) : PlanState
    data class OnRoute(val route: Route, val confirmFinish: Boolean = false) : PlanState
    data class Error(val message: String, val retry: RetryAction = RetryAction.LISTEN) : PlanState
    data class PermissionNeeded(val permission: Permission, val route: Route? = null) : PlanState
}
