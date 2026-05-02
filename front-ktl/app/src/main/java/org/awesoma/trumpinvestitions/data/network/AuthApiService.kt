package org.awesoma.trumpinvestitions.data.network

import org.awesoma.trumpinvestitions.data.network.dto.AuthResponseDto
import org.awesoma.trumpinvestitions.data.network.dto.LoginRequestDto
import org.awesoma.trumpinvestitions.data.network.dto.LogoutRequestDto
import org.awesoma.trumpinvestitions.data.network.dto.RefreshRequestDto
import org.awesoma.trumpinvestitions.data.network.dto.RegisterRequestDto
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApiService {

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequestDto): AuthResponseDto

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequestDto): AuthResponseDto

    @POST("auth/refresh")
    suspend fun refresh(@Body request: RefreshRequestDto): AuthResponseDto

    @POST("auth/logout")
    suspend fun logout(@Body request: LogoutRequestDto)
}
