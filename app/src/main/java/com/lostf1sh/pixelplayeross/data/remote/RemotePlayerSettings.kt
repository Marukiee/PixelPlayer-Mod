package com.lostf1sh.pixelplayeross.data.remote

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** What the remote player feature keeps on the phone. */
@Singleton
class RemotePlayerSettings @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("remote_player", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))

    /** "Use as remote player": this phone waits for another phone to play its music here. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, on).apply()
        _enabled.value = on
    }

    /** The phone this one played on last, tried first when looking. */
    var lastDeviceKey: String?
        get() = prefs.getString(KEY_LAST_DEVICE, null)
        set(value) = prefs.edit().putString(KEY_LAST_DEVICE, value).apply()

    /** The session that was going when the app closed, so it can be picked up again. */
    fun saveSession(endpoint: RemoteEndpoint) {
        val json = JSONObject().put("at", System.currentTimeMillis()).put("name", endpoint.name)
        when (endpoint) {
            is RemoteEndpoint.Bluetooth -> json.put("kind", "bt").put("address", endpoint.address)
            is RemoteEndpoint.Tcp -> json.put("kind", "tcp").put("host", endpoint.host).put("port", endpoint.port)
        }
        prefs.edit().putString(KEY_SESSION, json.toString()).apply()
    }

    fun clearSession() = prefs.edit().remove(KEY_SESSION).apply()

    /** The saved session's phone, if the session is recent enough to still be going. */
    fun savedSession(maxAgeMs: Long): RemoteEndpoint? {
        val json = prefs.getString(KEY_SESSION, null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        if (System.currentTimeMillis() - json.optLong("at") > maxAgeMs) return null
        val name = json.optString("name")
        return when (json.optString("kind")) {
            "bt" -> RemoteEndpoint.Bluetooth(json.optString("address"), name)
            "tcp" -> RemoteEndpoint.Tcp(json.optString("host"), json.optInt("port"), name)
            else -> null
        }
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_LAST_DEVICE = "last_device"
        const val KEY_SESSION = "session"
    }
}
