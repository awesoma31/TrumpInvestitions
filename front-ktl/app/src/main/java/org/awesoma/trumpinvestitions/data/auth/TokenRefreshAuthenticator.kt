package org.awesoma.trumpinvestitions.data.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import org.awesoma.trumpinvestitions.data.network.AuthApiService
import org.awesoma.trumpinvestitions.data.network.dto.RefreshRequestDto

class TokenRefreshAuthenticator(
    private val tokenManager: TokenManager,
    private val authApiService: AuthApiService
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.header("Authorization") == null) return null
        val newToken = runBlocking {
            try {
                val refresh = tokenManager.getRefreshToken() ?: return@runBlocking null
                val result = authApiService.refresh(RefreshRequestDto(refresh))
                tokenManager.save(result.accessToken, result.refreshToken)
                result.accessToken
            } catch (_: Exception) {
                tokenManager.clear()
                null
            }
        } ?: return null
        return response.request.newBuilder().header("Authorization", "Bearer $newToken").build()
    }
}
