package org.awesoma.trumpinvestitions.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequestDto(
    val login: String,
    val password: String
)

@Serializable
data class RegisterRequestDto(
    val username: String,
    val email: String,
    val password: String
)

@Serializable
data class RefreshRequestDto(
    val refreshToken: String
)

@Serializable
data class LogoutRequestDto(
    val refreshToken: String
)

@Serializable
data class UserProfileDto(
    val id: Long,
    val username: String,
    val email: String,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class AuthResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val refreshExpiresIn: Long,
    val user: UserProfileDto
)
