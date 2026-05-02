package org.awesoma.trumpinvestitions.data.settings

import android.content.Context

class SettingsManager(context: Context) {

    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    var serverHost: String
        get() = prefs.getString("server_host", "10.0.2.2:8080") ?: "10.0.2.2:8080"
        set(value) = prefs.edit().putString("server_host", value).apply()

    val baseUrl: String get() = "http://$serverHost/api/v1/"
}
