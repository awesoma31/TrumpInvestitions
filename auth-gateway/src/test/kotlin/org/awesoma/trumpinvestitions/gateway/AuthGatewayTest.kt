package org.awesoma.trumpinvestitions.gateway

import com.auth0.jwt.JWT
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.response.respond
import io.ktor.server.testing.testApplication
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.time.Instant

class AuthGatewayTest {
    @Test
    fun `password hasher verifies only matching password`() {
        val hash = PasswordHasher.hash("StrongPass123!")

        assertNotEquals("StrongPass123!", hash)
        assertTrue(PasswordHasher.verify("StrongPass123!", hash))
        assertFalse(PasswordHasher.verify("WrongPass123!", hash))
    }

    @Test
    fun `token service issues bearer jwt with user id claim`() {
        val tokenService = TokenService(testConfig.jwt)
        val response = tokenService.authResponse(testUser, IssuedRefreshToken("refresh-token", Instant.now()))
        val decoded = JWT.require(tokenAlgorithm()).withIssuer(testConfig.jwt.issuer).withAudience(testConfig.jwt.audience).build()
            .verify(response.accessToken)

        assertEquals("Bearer", response.tokenType)
        assertEquals(1L, decoded.getClaim("user_id").asLong())
        assertEquals("investor_01", decoded.getClaim("username").asString())
        assertEquals(testConfig.jwt.accessTokenTtlSeconds, response.expiresIn)
    }

    @Test
    fun `health returns UP`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module(testConfig, FakeAuthRepository(), FakeUpstreamGateway()) }

        val response = client.get("/api/v1/system/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"service\":\"auth-gateway\""))
    }

    @Test
    fun `ready returns dependency statuses from auth repository and upstreams`() = testApplication {
        environment { config = MapApplicationConfig() }
        application {
            module(
                testConfig,
                FakeAuthRepository(ready = true),
                FakeUpstreamGateway(readiness = listOf(DependencyStatus("market-data-service", "DOWN"))),
            )
        }

        val response = client.get("/api/v1/system/ready")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertTrue(response.bodyAsText().contains("\"postgres\",\"status\":\"UP\""))
        assertTrue(response.bodyAsText().contains("\"market-data-service\",\"status\":\"DOWN\""))
    }

    @Test
    fun `register validates request before creating user`() = testApplication {
        val repository = FakeAuthRepository()
        environment { config = MapApplicationConfig() }
        application { module(testConfig, repository, FakeUpstreamGateway()) }

        val response = client.post("/api/v1/auth/register") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"username":"bad username","email":"investor@example.com","password":"StrongPass123!"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(0, repository.createdUsers)
    }

    @Test
    fun `register creates user and returns auth response`() = testApplication {
        val repository = FakeAuthRepository()
        environment { config = MapApplicationConfig() }
        application { module(testConfig, repository, FakeUpstreamGateway()) }

        val response = client.post("/api/v1/auth/register") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"username":"investor_02","email":"investor02@example.com","password":"StrongPass123!"}""")
        }

        val body = response.bodyAsText()
        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(1, repository.createdUsers)
        assertTrue(body.contains("\"tokenType\":\"Bearer\""))
        assertTrue(body.contains("\"username\":\"investor_02\""))
    }

    @Test
    fun `bad auth json returns 400`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module(testConfig, FakeAuthRepository(), FakeUpstreamGateway()) }

        val response = client.post("/api/v1/auth/login") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{\"login\":\"broken\"""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `login rejects invalid credentials`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module(testConfig, FakeAuthRepository(), FakeUpstreamGateway()) }

        val response = client.post("/api/v1/auth/login") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"login":"unknown","password":"StrongPass123!"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `protected route without token returns 401`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module(testConfig, FakeAuthRepository(), FakeUpstreamGateway()) }

        val response = client.get("/api/v1/orders")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `protected route forwards user id from jwt`() = testApplication {
        val upstream = FakeUpstreamGateway()
        environment { config = MapApplicationConfig() }
        application { module(testConfig, FakeAuthRepository(), upstream) }
        val accessToken = TokenService(testConfig.jwt)
            .authResponse(testUser, IssuedRefreshToken("refresh-token", Instant.now()))
            .accessToken

        val response = client.get("/api/v1/orders?limit=10") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(TargetService.Order, upstream.lastService)
        assertEquals("/api/v1", upstream.lastStripPrefix)
        assertEquals(1L, upstream.lastUserId)
    }

    private fun tokenAlgorithm() = com.auth0.jwt.algorithms.Algorithm.HMAC256(testConfig.jwt.secret)
}

private val testConfig = GatewayConfig(
    host = "127.0.0.1",
    port = 0,
    database = DatabaseConfig("jdbc:postgresql://localhost/test", "postgres", "postgres", 1),
    jwt = JwtConfig(
        issuer = "test-issuer",
        audience = "test-audience",
        realm = "test-realm",
        secret = "test-secret-with-enough-entropy",
        accessTokenTtlSeconds = 900,
        refreshTokenTtlSeconds = 2592000,
    ),
    services = ServiceUrls("http://market.test", "http://order.test", "http://portfolio.test"),
)

private val testUser = UserProfile(
    id = 1,
    username = "investor_01",
    email = "investor01@example.com",
    createdAt = "2026-04-27T00:00:00Z",
    updatedAt = "2026-04-27T00:00:00Z",
)

private class FakeAuthRepository(
    private val ready: Boolean = true,
) : AuthRepository {
    var createdUsers = 0
    private val users = mutableMapOf("investor_01" to UserWithPassword(testUser, PasswordHasher.hash("StrongPass123!")))
    private val refreshTokens = mutableMapOf("refresh-token" to testUser)

    override fun createUser(username: String, email: String, password: String): UserProfile {
        createdUsers += 1
        val user = testUser.copy(id = createdUsers.toLong(), username = username, email = email)
        users[username] = UserWithPassword(user, PasswordHasher.hash(password))
        return user
    }

    override fun findByLogin(login: String): UserWithPassword? = users[login] ?: users.values.firstOrNull { it.profile.email == login }

    override fun issueRefreshToken(userId: Long): IssuedRefreshToken {
        val user = users.values.first { it.profile.id == userId }.profile
        refreshTokens["refresh-$userId"] = user
        return IssuedRefreshToken("refresh-$userId", Instant.now().plusSeconds(testConfig.jwt.refreshTokenTtlSeconds))
    }

    override fun rotateRefreshToken(refreshToken: String): RotatedRefreshToken? {
        val user = refreshTokens.remove(refreshToken) ?: return null
        val newToken = IssuedRefreshToken("rotated-${user.id}", Instant.now().plusSeconds(testConfig.jwt.refreshTokenTtlSeconds))
        refreshTokens[newToken.value] = user
        return RotatedRefreshToken(user, newToken)
    }

    override fun revokeRefreshToken(refreshToken: String) {
        refreshTokens.remove(refreshToken)
    }

    override fun isReady(): Boolean = ready
}

private class FakeUpstreamGateway(
    private val readiness: List<DependencyStatus> = emptyList(),
) : UpstreamGateway {
    var lastService: TargetService? = null
    var lastStripPrefix: String? = null
    var lastUserId: Long? = null

    override suspend fun forward(call: ApplicationCall, service: TargetService, stripPrefix: String, userId: Long?) {
        lastService = service
        lastStripPrefix = stripPrefix
        lastUserId = userId
        call.respond(HttpStatusCode.OK, mapOf("forwarded" to true))
    }

    override suspend fun readiness(): List<DependencyStatus> = readiness
}
