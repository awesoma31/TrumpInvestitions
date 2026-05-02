package org.awesoma.trumpinvestitions

import android.app.Application
import org.awesoma.trumpinvestitions.data.auth.TokenManager
import org.awesoma.trumpinvestitions.data.network.AppNetwork
import org.awesoma.trumpinvestitions.data.settings.SettingsManager

class TrumpApp : Application() {
    val tokenManager by lazy { TokenManager(this) }
    val settingsManager by lazy { SettingsManager(this) }
    var network: AppNetwork = AppNetwork("http://placeholder/")

    override fun onCreate() {
        super.onCreate()
        network = AppNetwork(settingsManager.baseUrl, tokenManager)
    }

    fun rebuildNetwork() {
        network = AppNetwork(settingsManager.baseUrl, tokenManager)
    }
}
