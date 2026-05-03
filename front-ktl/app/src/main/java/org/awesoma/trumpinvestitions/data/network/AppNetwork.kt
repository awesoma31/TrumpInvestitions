package org.awesoma.trumpinvestitions.data.network

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.okhttp.v3_0.OkHttpTelemetry
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

class AppNetwork(
    baseUrl: String,
    tokenManager: TokenManager? = null,
    openTelemetry: OpenTelemetry = OpenTelemetry.noop(),
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val otelInterceptor = OkHttpTelemetry.create(openTelemetry).newInterceptor()

    private val plainClient = OkHttpClient.Builder()
        .addInterceptor(logging)
        .addInterceptor(otelInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val authApiService: AuthApiService = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(plainClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(AuthApiService::class.java)

    val apiService: ApiService = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(
            if (tokenManager != null) {
                OkHttpClient.Builder()
                    .addInterceptor(logging)
                    .addInterceptor(otelInterceptor)
                    .addInterceptor(AuthInterceptor(tokenManager))
                    .authenticator(TokenRefreshAuthenticator(tokenManager, authApiService))
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
            } else plainClient
        )
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ApiService::class.java)
}
