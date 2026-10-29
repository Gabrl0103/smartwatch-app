package com.rumbo.watchapp.infrastructure.storage

import android.content.Context
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.port.ActiveRouteStore
import com.rumbo.watchapp.infrastructure.network.routeFromJson
import com.rumbo.watchapp.infrastructure.network.routeToJson
import org.json.JSONException
import org.json.JSONObject

private const val PREFERENCES = "rumbo_active_route"
private const val KEY_ROUTE = "route"
private const val KEY_SAVED_AT = "saved_at"

// Una ruta de hace más de 6 horas ya no es "la de hace un momento": no se ofrece continuarla.
private const val MAX_AGE_MS = 6 * 60 * 60 * 1000L

/** Guarda la ruta en curso como JSON (el mismo de POST /routes) en SharedPreferences. */
class PreferencesActiveRouteStore(context: Context, private val clock: () -> Long) : ActiveRouteStore {

    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override fun save(route: Route) {
        preferences.edit()
            .putString(KEY_ROUTE, routeToJson(route).toString())
            .putLong(KEY_SAVED_AT, clock())
            .apply()
    }

    override fun load(): Route? {
        val json = preferences.getString(KEY_ROUTE, null) ?: return null
        if (clock() - preferences.getLong(KEY_SAVED_AT, 0) > MAX_AGE_MS) {
            clear()
            return null
        }
        return try {
            routeFromJson(JSONObject(json))
        } catch (e: JSONException) {
            clear()
            null
        }
    }

    override fun clear() {
        preferences.edit().clear().apply()
    }
}
