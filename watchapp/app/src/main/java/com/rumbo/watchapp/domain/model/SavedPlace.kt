package com.rumbo.watchapp.domain.model

/** Lugar que el usuario guardó para elegirlo como destino sin hablar. */
data class SavedPlace(
    val id: Long,
    val name: String,
    val lat: Double,
    val lng: Double,
) {
    fun toPlace() = Place(name, lat, lng)
}
