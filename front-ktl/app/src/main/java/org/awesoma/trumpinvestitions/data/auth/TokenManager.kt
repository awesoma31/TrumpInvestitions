package org.awesoma.trumpinvestitions.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.authDataStore by preferencesDataStore(name = "auth_prefs")

class TokenManager(private val context: Context) {
    companion object {
        private val KEY_ACCESS = stringPreferencesKey("access_token")
        private val KEY_REFRESH = stringPreferencesKey("refresh_token")
        private val KEY_USERNAME = stringPreferencesKey("username")
    }

    suspend fun save(accessToken: String, refreshToken: String, username: String = "") {
        context.authDataStore.edit { prefs ->
            prefs[KEY_ACCESS] = accessToken
            prefs[KEY_REFRESH] = refreshToken
            if (username.isNotEmpty()) prefs[KEY_USERNAME] = username
        }
    }

    fun getAccessTokenBlocking(): String? = runBlocking {
        context.authDataStore.data.first()[KEY_ACCESS]
    }

    suspend fun getRefreshToken(): String? = context.authDataStore.data.first()[KEY_REFRESH]

    suspend fun getUsername(): String = context.authDataStore.data.first()[KEY_USERNAME] ?: ""

    suspend fun clear() = context.authDataStore.edit { it.clear() }

    fun isLoggedInFlow(): Flow<Boolean> = context.authDataStore.data.map { it[KEY_ACCESS] != null }
}
