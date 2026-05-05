package org.awesoma.trumpinvestitions.network

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.awesoma.trumpinvestitions.data.network.AuthApiService
import org.awesoma.trumpinvestitions.data.network.dto.LoginRequestDto
import org.awesoma.trumpinvestitions.data.network.dto.LogoutRequestDto
import org.awesoma.trumpinvestitions.data.network.dto.RefreshRequestDto
import org.awesoma.trumpinvestitions.data.network.dto.RegisterRequestDto
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthApiServiceTest {

    private val server = MockWebServer()
    private lateinit var service: AuthApiService
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        server.start()
        service = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AuthApiService::class.java)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun authJson(
        accessToken: String = "access_123",
        refreshToken: String = "refresh_456",
        username: String = "testuser"
    ) = """
        {
            "accessToken": "$accessToken",
            "refreshToken": "$refreshToken",
            "tokenType": "Bearer",
            "expiresIn": 900,
            "refreshExpiresIn": 2592000,
            "user": {
                "id": 42,
                "username": "$username",
                "email": "test@example.com",
                "createdAt": "2024-01-15T10:00:00Z",
                "updatedAt": "2024-01-15T10:00:00Z"
            }
        }
    """.trimIndent()

    @Test
    fun `login — POST to correct path, parses tokens and user`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(authJson()))

        val response = service.login(LoginRequestDto(login = "testuser", password = "pass123"))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/auth/login", request.path)
        assertEquals("access_123", response.accessToken)
        assertEquals("refresh_456", response.refreshToken)
        assertEquals("Bearer", response.tokenType)
        assertEquals(900L, response.expiresIn)
        assertEquals(42L, response.user.id)
        assertEquals("testuser", response.user.username)
    }

    @Test
    fun `login — request body contains login and password fields`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(authJson()))

        service.login(LoginRequestDto(login = "mylogin", password = "mypassword"))

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("mylogin"), "Body must contain login")
        assertTrue(body.contains("mypassword"), "Body must contain password")
    }

    @Test
    fun `register — POST to correct path, parses response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(authJson("new_access", "new_refresh", "newuser")))

        val response = service.register(
            RegisterRequestDto(username = "newuser", email = "new@example.com", password = "secret")
        )

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/auth/register", request.path)
        assertEquals("new_access", response.accessToken)
        assertEquals("newuser", response.user.username)
    }

    @Test
    fun `register — request body contains username, email, password`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(authJson()))

        service.register(RegisterRequestDto(username = "u1", email = "u1@mail.com", password = "pwd"))

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("u1"))
        assertTrue(body.contains("u1@mail.com"))
        assertTrue(body.contains("pwd"))
    }

    @Test
    fun `refresh — sends old token, returns new tokens`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(authJson("new_access", "new_refresh")))

        val response = service.refresh(RefreshRequestDto(refreshToken = "old_refresh"))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/auth/refresh", request.path)
        assertTrue(request.body.readUtf8().contains("old_refresh"))
        assertEquals("new_access", response.accessToken)
        assertEquals("new_refresh", response.refreshToken)
    }

    @Test
    fun `logout — POST to correct path with refresh token in body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        service.logout(LogoutRequestDto(refreshToken = "token_to_revoke"))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/auth/logout", request.path)
        assertTrue(request.body.readUtf8().contains("token_to_revoke"))
    }
}
