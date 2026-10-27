package com.rumbo.watchapp.infrastructure.network

import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.Place
import com.rumbo.watchapp.domain.port.PlacesRepository
import java.net.HttpURLConnection
import java.net.URLEncoder

/** Busca lugares con GET /places/search. */
object PlacesApiClient : PlacesRepository {

    override suspend fun search(query: String, near: Coordinates): Place? {
        val q = URLEncoder.encode(query, "UTF-8")
        val response = backendRequest("GET", "/places/search?q=$q&lat=${near.lat}&lng=${near.lng}")
        if (response.code == HttpURLConnection.HTTP_NOT_FOUND) return null
        return parseJson(response.requireOk()) { json ->
            Place(
                name = json.getString("name"),
                lat = json.getDouble("lat"),
                lng = json.getDouble("lng"),
            )
        }
    }
}
