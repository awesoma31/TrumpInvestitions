package org.awesoma.trumpinvestitions.gateway

import com.sun.net.httpserver.HttpServer
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.testcontainers.containers.PostgreSQLContainer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthGatewayIntegrationTest {
    @Test
    fun `auth flow persists users and rotates refresh tokens in postgres`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module(integrationConfig()) }

        val suffix = uniqueSuffix()
        val username = "investor_$suffix"
        val email = "investor_$suffix@example.com"

        val register = client.post("/api/v1/auth/register") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"username":"$username","email":"$email","password":"StrongPass123!"}""")
        }
        assertEquals(HttpStatusCode.Created, register.status)
        val registerBody = register.bodyAsJson()
        val firstRefreshToken = registerBody.string("refreshToken")
        assertEquals(username, registerBody.jsonObject["user"]!!.jsonObject.string("username"))

        val duplicate = client.post("/api/v1/auth/register") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"username":"$username","email":"$email","password":"StrongPass123!"}""")
        }
        assertEquals(HttpStatusCode.Conflict, duplicate.status)

        val login = client.post("/api/v1/auth/login") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"login":"$email","password":"StrongPass123!"}""")
        }
        assertEquals(HttpStatusCode.OK, login.status)
        val loginRefreshToken = login.bodyAsJson().string("refreshToken")
        assertNotEquals(firstRefreshToken, loginRefreshToken)

        val refresh = client.post("/api/v1/auth/refresh") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"refreshToken":"$loginRefreshToken"}""")
        }
        assertEquals(HttpStatusCode.OK, refresh.status)
        val rotatedRefreshToken = refresh.bodyAsJson().string("refreshToken")
        assertNotEquals(loginRefreshToken, rotatedRefreshToken)

        val reusedRefresh = client.post("/api/v1/auth/refresh") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"refreshToken":"$loginRefreshToken"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, reusedRefresh.status)

        val logout = client.post("/api/v1/auth/logout") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"refreshToken":"$rotatedRefreshToken"}""")
        }
        assertEquals(HttpStatusCode.NoContent, logout.status)

        val refreshAfterLogout = client.post("/api/v1/auth/refresh") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"refreshToken":"$rotatedRefreshToken"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, refreshAfterLogout.status)
    }

    @Test
    fun `gateway checks postgres readiness and proxies requests with authenticated user id`() {
        StubUpstreamServer().use { upstream ->
            testApplication {
                environment { config = MapApplicationConfig() }
                application { module(integrationConfig(upstream.baseUrl)) }

                val ready = client.get("/api/v1/system/ready")
                assertEquals(HttpStatusCode.OK, ready.status)
                assertTrue(ready.bodyAsText().contains("\"postgres\",\"status\":\"UP\""))
                assertTrue(ready.bodyAsText().contains("\"market-data-service\",\"status\":\"UP\""))

                val suffix = uniqueSuffix()
                val register = client.post("/api/v1/auth/register") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(
                        """{"username":"proxy_$suffix","email":"proxy_$suffix@example.com","password":"StrongPass123!"}""",
                    )
                }
                assertEquals(HttpStatusCode.Created, register.status)
                val accessToken = register.bodyAsJson().string("accessToken")

                val market = client.get("/api/v1/market/quotes?symbols=AAPL") {
                    header("X-User-Id", "999")
                }
                assertEquals(HttpStatusCode.OK, market.status)
                assertEquals("/api/v1/quotes", upstream.lastRequest?.path)
                assertEquals("symbols=AAPL", upstream.lastRequest?.query)
                assertEquals(null, upstream.lastRequest?.userId)

                val ordersWithoutToken = client.get("/api/v1/orders")
                assertEquals(HttpStatusCode.Unauthorized, ordersWithoutToken.status)

                val orders = client.get("/api/v1/orders?limit=10") {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                    header("X-Request-Id", "integration-request")
                    header("X-User-Id", "999")
                }
                assertEquals(HttpStatusCode.OK, orders.status)
                val orderRequest = assertNotNull(upstream.lastRequest)
                assertEquals("/api/v1/orders", orderRequest.path)
                assertEquals("limit=10", orderRequest.query)
                assertNotNull(orderRequest.userId)
                assertNotEquals("999", orderRequest.userId)
                assertEquals("integration-request", orderRequest.requestId)
            }
        }
    }

    private companion object {
        private val postgres = KPostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("auth_gateway")
            withUsername("auth")
            withPassword("auth")
            start()
        }

        private fun integrationConfig(upstreamBaseUrl: String = "http://127.0.0.1:1/api/v1") = GatewayConfig(
            host = "127.0.0.1",
            port = 0,
            database = DatabaseConfig(postgres.jdbcUrl, postgres.username, postgres.password, 2),
            jwt = JwtConfig(
                issuer = "integration-test-issuer",
                audience = "integration-test-audience",
                realm = "integration-test-realm",
                secret = "integration-test-secret-with-enough-entropy",
                accessTokenTtlSeconds = 900,
                refreshTokenTtlSeconds = 2_592_000,
            ),
            services = ServiceUrls(upstreamBaseUrl, upstreamBaseUrl, upstreamBaseUrl),
        )

        private fun uniqueSuffix(): String = System.nanoTime().toString()
    }
}

private class KPostgreSQLContainer(imageName: String) : PostgreSQLContainer<KPostgreSQLContainer>(imageName)

private class StubUpstreamServer : AutoCloseable {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    @Volatile
    var lastRequest: CapturedRequest? = null
        private set

    val baseUrl: String
        get() = "http://127.0.0.1:${server.address.port}/api/v1"

    init {
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            if (path.endsWith("/system/health")) {
                exchange.respond(HttpStatusCode.OK.value, """{"status":"UP"}""")
                return@createContext
            }

            lastRequest = CapturedRequest(
                method = exchange.requestMethod,
                path = path,
                query = exchange.requestURI.rawQuery,
                userId = exchange.requestHeaders.getFirst("X-User-Id"),
                requestId = exchange.requestHeaders.getFirst("X-Request-Id"),
            )
            exchange.respond(
                HttpStatusCode.OK.value,
                """{"forwarded":true,"path":"$path","userId":${lastRequest?.userId?.let { "\"$it\"" } ?: "null"}}""",
            )
        }
        server.start()
    }

    override fun close() {
        server.stop(0)
    }
}

private data class CapturedRequest(
    val method: String,
    val path: String,
    val query: String?,
    val userId: String?,
    val requestId: String?,
)

private suspend fun io.ktor.client.statement.HttpResponse.bodyAsJson() =
    Json.parseToJsonElement(bodyAsText()).jsonObject

private fun kotlinx.serialization.json.JsonObject.string(name: String): String =
    get(name)!!.jsonPrimitive.content

private fun com.sun.net.httpserver.HttpExchange.respond(status: Int, body: String) {
    val bytes = body.toByteArray(StandardCharsets.UTF_8)
    responseHeaders.add(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    sendResponseHeaders(status, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}
