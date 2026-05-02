package org.awesoma.trumpinvestitions

import android.app.Application
import org.awesoma.trumpinvestitions.data.auth.TokenManager
import org.awesoma.trumpinvestitions.data.network.AppNetwork

class TrumpApp : Application() {
    val tokenManager by lazy { TokenManager(this) }
    val network: AppNetwork by lazy { AppNetwork(tokenManager) }
}
