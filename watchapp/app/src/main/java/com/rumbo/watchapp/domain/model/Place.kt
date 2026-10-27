package com.rumbo.watchapp.domain.model

/** Lugar encontrado a partir del nombre que dijo el usuario. */
data class Place(
    val name: String,
    val lat: Double,
    val lng: Double,
)
