package org.awesoma.trumpinvestitions.data.network

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.awesoma.trumpinvestitions.data.auth.AuthInterceptor
import org.awesoma.trumpinvestitions.data.auth.TokenManager
import org.awesoma.trumpinvestitions.data.auth.TokenRefreshAuthenticator
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class AppNetwork(tokenManager: TokenManager) {

    companion object {
        const val BASE_URL = "http://10.0.2.2:8080/api/v1/"
    }

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val plainClient = OkHttpClient.Builder()
        .addInterceptor(logging)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val authApiService: AuthApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(plainClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(AuthApiService::class.java)

    val apiService: ApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(
            OkHttpClient.Builder()
                .addInterceptor(logging)
                .addInterceptor(AuthInterceptor(tokenManager))
                .authenticator(TokenRefreshAuthenticator(tokenManager, authApiService))
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        )
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ApiService::class.java)
}
