package com.rumbo.watchapp.presentation.viewmodel

import com.rumbo.watchapp.domain.model.Route

/** Pantalla actual del flujo "pedir destino por voz → confirmar". */
sealed interface PlanState {
    data object Start : PlanState
    data object Listening : PlanState
    data object Processing : PlanState
    data class Confirming(val route: Route) : PlanState
    data class OnRoute(val route: Route) : PlanState
    data class Error(val message: String) : PlanState
}
