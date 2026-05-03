package org.awesoma.trumpinvestitions.data.repository

import org.awesoma.trumpinvestitions.data.auth.TokenManager
import org.awesoma.trumpinvestitions.data.network.AuthApiService
import org.awesoma.trumpinvestitions.data.network.dto.LoginRequestDto
import org.awesoma.trumpinvestitions.data.network.dto.LogoutRequestDto
import org.awesoma.trumpinvestitions.data.network.dto.RegisterRequestDto

class AuthRepository(
    private val authApiService: AuthApiService,
    private val tokenManager: TokenManager
) {

    suspend fun login(login: String, password: String) {
        val response = authApiService.login(LoginRequestDto(login, password))
        tokenManager.save(response.accessToken, response.refreshToken, response.user.username)
    }

    suspend fun register(username: String, email: String, password: String) {
        val response = authApiService.register(RegisterRequestDto(username, email, password))
        tokenManager.save(response.accessToken, response.refreshToken, response.user.username)
    }

    suspend fun logout() {
        try {
            val refreshToken = tokenManager.getRefreshToken()
            if (refreshToken != null) {
                authApiService.logout(LogoutRequestDto(refreshToken))
            }
        } catch (_: Exception) {
            // ignore errors on logout
        } finally {
            tokenManager.clear()
        }
    }
}
