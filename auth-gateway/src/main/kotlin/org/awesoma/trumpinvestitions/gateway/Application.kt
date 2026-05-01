package org.awesoma.trumpinvestitions.gateway

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.contentType
import io.ktor.server.request.httpMethod
import io.ktor.server.request.queryString
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.options
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.util.toMap
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Timestamp
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.Base64
import java.util.Date
import java.util.UUID

fun main() {
    val config = GatewayConfig.fromEnv()
    embeddedServer(Netty, host = config.host, port = config.port) {
        module(config)
    }.start(wait = true)
}

fun Application.module(config: GatewayConfig = GatewayConfig.fromEnv()) {
    val userRepository = UserRepository(config.database, config.jwt.refreshTokenTtlSeconds)
    val httpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 2_000
            connectTimeoutMillis = 1_000
            socketTimeoutMillis = 2_000
        }
    }
    val proxy = ProxyClient(httpClient, config.services)
    module(config, userRepository, proxy)
}

internal fun Application.module(
    config: GatewayConfig,
    userRepository: AuthRepository,
    proxy: UpstreamGateway,
) {
    val json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
        explicitNulls = false
    }
    val tokenService = TokenService(config.jwt)

    install(ContentNegotiation) { json(json) }
    install(CallLogging) { level = Level.INFO }
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Accept)
        allowHeader("X-Request-Id")
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
    }
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, call.error("VALIDATION_ERROR", cause.message ?: "Bad request"))
        }
        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, call.error("BAD_REQUEST", cause.message ?: "Bad request"))
        }
        exception<DuplicateUserException> { call, cause ->
            call.respond(HttpStatusCode.Conflict, call.error("USER_ALREADY_EXISTS", cause.message ?: "Username or email is already taken"))
        }
        exception<Throwable> { call, cause ->
            this@module.environment.log.error("Unhandled gateway error", cause)
            call.respond(HttpStatusCode.InternalServerError, call.error("INTERNAL_ERROR", "Internal server error"))
        }
    }
    install(Authentication) {
        jwt("jwt") {
            realm = config.jwt.realm
            verifier(tokenService.verifier)
            validate { credential ->
                val userId = credential.payload.getClaim("user_id").asLong()
                if (userId != null) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, call.error("UNAUTHORIZED", "Bearer token is missing or invalid"))
            }
        }
    }

    routing {
        route("/api/v1") {
            post("/auth/register") {
                val request = call.receive<RegisterRequest>()
                validateRegister(request)
                val user = userRepository.createUser(request.username, request.email, request.password)
                call.respond(HttpStatusCode.Created, tokenService.authResponse(user, userRepository.issueRefreshToken(user.id)))
            }
            post("/auth/login") {
                val request = call.receive<LoginRequest>()
                validateLogin(request)
                val user = userRepository.findByLogin(request.login)
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, call.error("INVALID_CREDENTIALS", "Invalid login or password"))
                if (!PasswordHasher.verify(request.password, user.passwordHash)) {
                    return@post call.respond(HttpStatusCode.Unauthorized, call.error("INVALID_CREDENTIALS", "Invalid login or password"))
                }
                call.respond(tokenService.authResponse(user.profile, userRepository.issueRefreshToken(user.id)))
            }
            post("/auth/refresh") {
                val request = call.receive<RefreshRequest>()
                val user = userRepository.rotateRefreshToken(request.refreshToken)
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, call.error("INVALID_REFRESH_TOKEN", "Refresh token is invalid or revoked"))
                call.respond(tokenService.authResponse(user.profile, user.refreshToken))
            }
            post("/auth/logout") {
                val request = call.receive<LogoutRequest>()
                userRepository.revokeRefreshToken(request.refreshToken)
                call.respond(HttpStatusCode.NoContent)
            }

            get("/system/health") {
                call.respond(HealthResponse(status = "UP", service = "auth-gateway", timestamp = Instant.now().toString()))
            }
            get("/system/ready") {
                val dependencies = mutableListOf<DependencyStatus>()
                dependencies += DependencyStatus("postgres", if (userRepository.isReady()) "UP" else "DOWN")
                dependencies += proxy.readiness()
                val ready = dependencies.all { it.status == "UP" }
                call.respond(
                    if (ready) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
                    ReadinessResponse(
                        status = if (ready) "READY" else "NOT_READY",
                        service = "auth-gateway",
                        dependencies = dependencies,
                        timestamp = Instant.now().toString(),
                    ),
                )
            }

            marketRoutes(proxy)

            authenticate("jwt") {
                orderRoutes(proxy)
                portfolioRoutes(proxy)
            }
        }
    }
}

private fun io.ktor.server.routing.Route.marketRoutes(proxy: UpstreamGateway) {
    get("/market/{...}") { proxy.forward(call, TargetService.Market, stripPrefix = "/api/v1/market") }
    post("/market/{...}") { proxy.forward(call, TargetService.Market, stripPrefix = "/api/v1/market") }
    put("/market/{...}") { proxy.forward(call, TargetService.Market, stripPrefix = "/api/v1/market") }
    delete("/market/{...}") { proxy.forward(call, TargetService.Market, stripPrefix = "/api/v1/market") }
    options("/market/{...}") { proxy.forward(call, TargetService.Market, stripPrefix = "/api/v1/market") }
}

private fun io.ktor.server.routing.Route.orderRoutes(proxy: UpstreamGateway) {
    get("/orders") { proxy.forwardAuthenticated(call, TargetService.Order, stripPrefix = "/api/v1") }
    post("/orders") { proxy.forwardAuthenticated(call, TargetService.Order, stripPrefix = "/api/v1") }
    get("/orders/{...}") { proxy.forwardAuthenticated(call, TargetService.Order, stripPrefix = "/api/v1") }
    post("/orders/{...}") { proxy.forwardAuthenticated(call, TargetService.Order, stripPrefix = "/api/v1") }
    get("/trades") { proxy.forwardAuthenticated(call, TargetService.Order, stripPrefix = "/api/v1") }
    get("/trades/{...}") { proxy.forwardAuthenticated(call, TargetService.Order, stripPrefix = "/api/v1") }
}

private fun io.ktor.server.routing.Route.portfolioRoutes(proxy: UpstreamGateway) {
    get("/portfolio") { proxy.forwardAuthenticated(call, TargetService.Portfolio, stripPrefix = "/api/v1") }
    get("/portfolio/{...}") { proxy.forwardAuthenticated(call, TargetService.Portfolio, stripPrefix = "/api/v1/portfolio") }
    post("/portfolio/{...}") { proxy.forwardAuthenticated(call, TargetService.Portfolio, stripPrefix = "/api/v1/portfolio") }
}

private suspend fun UpstreamGateway.forwardAuthenticated(call: ApplicationCall, service: TargetService, stripPrefix: String) {
    val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("user_id")?.asLong()
        ?: return call.respond(HttpStatusCode.Unauthorized, call.error("UNAUTHORIZED", "Bearer token is missing or invalid"))
    forward(call, service, stripPrefix, userId)
}

interface UpstreamGateway {
    suspend fun forward(call: ApplicationCall, service: TargetService, stripPrefix: String, userId: Long? = null)
    suspend fun readiness(): List<DependencyStatus>
}

class ProxyClient(
    private val httpClient: HttpClient,
    private val services: ServiceUrls,
) : UpstreamGateway {
    override suspend fun forward(call: ApplicationCall, service: TargetService, stripPrefix: String, userId: Long?) {
        val baseUrl = when (service) {
            TargetService.Market -> services.market
            TargetService.Order -> services.order
            TargetService.Portfolio -> services.portfolio
        }.trimEnd('/')
        val localPath = call.request.uri.substringBefore('?')
        val targetPath = localPath.removePrefix(stripPrefix).ifBlank { "/" }
        val query = call.request.queryString().takeIf { it.isNotBlank() }?.let { "?$it" } ?: ""
        val targetUrl = "$baseUrl$targetPath$query"
        val requestBody = if (call.request.httpMethod in listOf(HttpMethod.Post, HttpMethod.Put)) {
            call.receiveChannel().readRemaining().readBytes()
        } else {
            ByteArray(0)
        }

        val response = runCatching {
            httpClient.request(targetUrl) {
                method = call.request.httpMethod
                headers {
                    call.request.headers.toMap().forEach { (name, values) ->
                        if (!name.isHopByHopHeader() && !name.isGatewayManagedHeader() && !name.equals(HttpHeaders.Authorization, ignoreCase = true)) {
                            values.forEach { append(name, it) }
                        }
                    }
                    append("X-Request-Id", call.traceId())
                    if (userId != null) append("X-User-Id", userId.toString())
                }
                if (requestBody.isNotEmpty()) {
                    setBody(ByteArrayContent(requestBody, call.request.contentType()))
                }
            }
        }.getOrElse {
            val code = if (it is HttpRequestTimeoutException) "UPSTREAM_TIMEOUT" else "UPSTREAM_UNAVAILABLE"
            call.respond(HttpStatusCode.BadGateway, call.error(code, "Upstream service is unavailable"))
            return
        }

        val bytes = response.bodyAsChannel().readRemaining().readBytes()
        call.respondBytes(
            bytes = bytes,
            contentType = response.headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) } ?: ContentType.Application.Json,
            status = response.status,
        )
    }

    override suspend fun readiness(): List<DependencyStatus> {
        return coroutineScope {
            listOf(
                async { dependency("market-data-service", services.market) },
                async { dependency("trading-service", services.order) },
                async { dependency("portfolio-service", services.portfolio) },
            ).awaitAll()
        }
    }

    private suspend fun dependency(name: String, baseUrl: String): DependencyStatus {
        return withTimeoutOrNull(1_500) {
            runCatching {
                val response = httpClient.request("${baseUrl.trimEnd('/')}/system/health") { method = HttpMethod.Get }
                DependencyStatus(name, if (response.status.isSuccess()) "UP" else "DOWN")
            }.getOrElse {
                DependencyStatus(name, "DOWN")
            }
        } ?: DependencyStatus(name, "DOWN")
    }

    private companion object {
        val hopByHopHeaders = setOf(
            HttpHeaders.Connection,
            "Keep-Alive",
            HttpHeaders.ProxyAuthenticate,
            HttpHeaders.ProxyAuthorization,
            HttpHeaders.TE,
            HttpHeaders.Trailer,
            HttpHeaders.TransferEncoding,
            HttpHeaders.Upgrade,
            HttpHeaders.Host,
            HttpHeaders.ContentLength,
        )
        val gatewayManagedHeaders = setOf(
            "X-Request-Id",
            "X-Correlation-Id",
            "X-User-Id",
        )

        private fun String.isHopByHopHeader(): Boolean = hopByHopHeaders.any { equals(it, ignoreCase = true) }
        private fun String.isGatewayManagedHeader(): Boolean = gatewayManagedHeaders.any { equals(it, ignoreCase = true) }
    }
}

interface AuthRepository {
    fun createUser(username: String, email: String, password: String): UserProfile
    fun findByLogin(login: String): UserWithPassword?
    fun issueRefreshToken(userId: Long): IssuedRefreshToken
    fun rotateRefreshToken(refreshToken: String): RotatedRefreshToken?
    fun revokeRefreshToken(refreshToken: String)
    fun isReady(): Boolean
}

class UserRepository(database: DatabaseConfig, private val refreshTokenTtlSeconds: Long) : AuthRepository {
    private val dataSource = HikariDataSource(HikariConfig().apply {
        jdbcUrl = database.url
        username = database.user
        password = database.password
        maximumPoolSize = database.poolSize
        initializationFailTimeout = -1
        driverClassName = "org.postgresql.Driver"
    })

    init {
        retryDatabaseInitialization {
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeUpdate(
                        """
                        CREATE TABLE IF NOT EXISTS users (
                            id BIGSERIAL PRIMARY KEY,
                            username VARCHAR(64) NOT NULL UNIQUE,
                            email VARCHAR(255) NOT NULL UNIQUE,
                            password_hash VARCHAR(255) NOT NULL,
                            balance DECIMAL(18,2) NOT NULL DEFAULT 0,
                            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                            updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                        )
                        """.trimIndent(),
                    )
                    statement.executeUpdate(
                        """
                        CREATE TABLE IF NOT EXISTS auth_refresh_tokens (
                            id UUID PRIMARY KEY,
                            user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                            token_hash VARCHAR(64) NOT NULL UNIQUE,
                            expires_at TIMESTAMPTZ NOT NULL,
                            revoked_at TIMESTAMPTZ,
                            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                        )
                        """.trimIndent(),
                    )
                }
            }
        }
    }

    override fun createUser(username: String, email: String, password: String): UserProfile {
        return dataSource.connection.use { connection ->
            val sql = "INSERT INTO users(username, email, password_hash) VALUES (?, ?, ?)"
            connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { statement ->
                statement.setString(1, username)
                statement.setString(2, email)
                statement.setString(3, PasswordHasher.hash(password))
                runCatching { statement.executeUpdate() }.getOrElse {
                    throw DuplicateUserException("Username or email is already taken")
                }
                statement.generatedKeys.use { keys ->
                    keys.next()
                    val id = keys.getLong(1)
                    findProfileById(connection, id) ?: throw IllegalStateException("Created user was not found")
                }
            }
        }
    }

    override fun findByLogin(login: String): UserWithPassword? {
        return dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT id, username, email, password_hash, created_at, updated_at
                FROM users
                WHERE username = ? OR email = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, login)
                statement.setString(2, login)
                statement.executeQuery().use { rs ->
                    if (rs.next()) UserWithPassword(rs.userProfile(), rs.getString("password_hash")) else null
                }
            }
        }
    }

    override fun issueRefreshToken(userId: Long): IssuedRefreshToken {
        return dataSource.connection.use { connection ->
            issueRefreshToken(connection, userId)
        }
    }

    private fun issueRefreshToken(connection: Connection, userId: Long): IssuedRefreshToken {
        val raw = randomToken()
        val expiresAt = Instant.now().plusSeconds(refreshTokenTtlSeconds)
        connection.prepareStatement(
            """
            INSERT INTO auth_refresh_tokens(id, user_id, token_hash, expires_at)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.randomUUID())
            statement.setLong(2, userId)
            statement.setString(3, sha256(raw))
                statement.setTimestamp(4, Timestamp.from(expiresAt))
            statement.executeUpdate()
        }
        return IssuedRefreshToken(raw, expiresAt)
    }

    override fun rotateRefreshToken(refreshToken: String): RotatedRefreshToken? {
        return dataSource.connection.use { connection ->
            connection.autoCommit = false
            val profile = connection.prepareStatement(
                """
                SELECT u.id, u.username, u.email, u.created_at, u.updated_at
                FROM auth_refresh_tokens rt
                JOIN users u ON u.id = rt.user_id
                WHERE rt.token_hash = ? AND rt.revoked_at IS NULL AND rt.expires_at > NOW()
                FOR UPDATE
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, sha256(refreshToken))
                statement.executeQuery().use { rs -> if (rs.next()) rs.userProfile() else null }
            } ?: run {
                connection.rollback()
                return@use null
            }
            connection.prepareStatement("UPDATE auth_refresh_tokens SET revoked_at = NOW() WHERE token_hash = ?").use { statement ->
                statement.setString(1, sha256(refreshToken))
                statement.executeUpdate()
            }
            val newToken = issueRefreshToken(connection, profile.id)
            connection.commit()
            RotatedRefreshToken(profile, newToken)
        }
    }

    override fun revokeRefreshToken(refreshToken: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("UPDATE auth_refresh_tokens SET revoked_at = NOW() WHERE token_hash = ? AND revoked_at IS NULL").use { statement ->
                statement.setString(1, sha256(refreshToken))
                statement.executeUpdate()
            }
        }
    }

    override fun isReady(): Boolean = runCatching {
        dataSource.connection.use { connection ->
            connection.createStatement().use { it.executeQuery("SELECT 1").next() }
        }
    }.getOrDefault(false)

    private fun findProfileById(connection: Connection, id: Long): UserProfile? {
        return connection.prepareStatement(
            "SELECT id, username, email, created_at, updated_at FROM users WHERE id = ?",
        ).use { statement ->
            statement.setLong(1, id)
            statement.executeQuery().use { rs -> if (rs.next()) rs.userProfile() else null }
        }
    }

    private fun ResultSet.userProfile(): UserProfile = UserProfile(
        id = getLong("id"),
        username = getString("username"),
        email = getString("email"),
        createdAt = getTimestamp("created_at").toInstant().toString(),
        updatedAt = getTimestamp("updated_at").toInstant().toString(),
    )

    private fun retryDatabaseInitialization(block: () -> Unit) {
        var lastError: Throwable? = null
        repeat(30) { attempt ->
            runCatching {
                block()
                return
            }.onFailure {
                lastError = it
                TimeUnit.SECONDS.sleep(if (attempt < 5) 1 else 2)
            }
        }
        throw IllegalStateException("Database initialization failed", lastError)
    }
}

class TokenService(private val config: JwtConfig) {
    private val algorithm = Algorithm.HMAC256(config.secret)
    val verifier = JWT.require(algorithm)
        .withIssuer(config.issuer)
        .withAudience(config.audience)
        .build()

    fun authResponse(user: UserProfile, refreshToken: IssuedRefreshToken): AuthResponse {
        val now = Instant.now()
        val accessExpiresAt = now.plusSeconds(config.accessTokenTtlSeconds)
        val token = JWT.create()
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .withSubject(user.id.toString())
            .withClaim("user_id", user.id)
            .withClaim("username", user.username)
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(accessExpiresAt))
            .sign(algorithm)

        return AuthResponse(
            accessToken = token,
            refreshToken = refreshToken.value,
            tokenType = "Bearer",
            expiresIn = config.accessTokenTtlSeconds,
            refreshExpiresIn = config.refreshTokenTtlSeconds,
            user = user,
        )
    }
}

object PasswordHasher {
    fun hash(password: String): String = BCrypt.withDefaults().hashToString(12, password.toCharArray())
    fun verify(password: String, hash: String): Boolean =
        BCrypt.verifyer().verify(password.toCharArray(), hash).verified
}

class DuplicateUserException(message: String) : RuntimeException(message)

data class GatewayConfig(
    val host: String,
    val port: Int,
    val database: DatabaseConfig,
    val jwt: JwtConfig,
    val services: ServiceUrls,
) {
    companion object {
        fun fromEnv(): GatewayConfig = GatewayConfig(
            host = env("HOST", "0.0.0.0"),
            port = env("PORT", "8080").toInt(),
            database = DatabaseConfig(
                url = env("DATABASE_URL", "jdbc:postgresql://localhost:5434/auth_gateway"),
                user = env("DATABASE_USER", "auth"),
                password = env("DATABASE_PASSWORD", "auth"),
                poolSize = env("DATABASE_POOL_SIZE", "10").toInt(),
            ),
            jwt = JwtConfig(
                issuer = env("JWT_ISSUER", "trump-investitions-auth-gateway"),
                audience = env("JWT_AUDIENCE", "trump-investitions-clients"),
                realm = env("JWT_REALM", "trump-investitions"),
                secret = env("JWT_SECRET", "change-me-in-production"),
                accessTokenTtlSeconds = env("ACCESS_TOKEN_TTL_SECONDS", "900").toLong(),
                refreshTokenTtlSeconds = env("REFRESH_TOKEN_TTL_SECONDS", "2592000").toLong(),
            ),
            services = ServiceUrls(
                market = env("MARKET_SERVICE_URL", "http://localhost:8083/api/v1"),
                order = env("ORDER_SERVICE_URL", "http://localhost:8082/api/v1"),
                portfolio = env("PORTFOLIO_SERVICE_URL", "http://localhost:8081/api/v1"),
            ),
        )

        private fun env(name: String, default: String): String = System.getenv(name)?.takeIf { it.isNotBlank() } ?: default
    }
}

data class DatabaseConfig(val url: String, val user: String, val password: String, val poolSize: Int)
data class JwtConfig(
    val issuer: String,
    val audience: String,
    val realm: String,
    val secret: String,
    val accessTokenTtlSeconds: Long,
    val refreshTokenTtlSeconds: Long,
)
data class ServiceUrls(val market: String, val order: String, val portfolio: String)
data class IssuedRefreshToken(val value: String, val expiresAt: Instant)
data class RotatedRefreshToken(val profile: UserProfile, val refreshToken: IssuedRefreshToken)
data class UserWithPassword(val profile: UserProfile, val passwordHash: String) {
    val id: Long = profile.id
}
enum class TargetService { Market, Order, Portfolio }

@Serializable
data class RegisterRequest(val username: String, val email: String, val password: String)

@Serializable
data class LoginRequest(val login: String, val password: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class LogoutRequest(val refreshToken: String)

@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val refreshExpiresIn: Long,
    val user: UserProfile,
)

@Serializable
data class UserProfile(
    val id: Long,
    val username: String,
    val email: String,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class HealthResponse(val status: String, val service: String, val timestamp: String)

@Serializable
data class DependencyStatus(val name: String, val status: String)

@Serializable
data class ReadinessResponse(
    val status: String,
    val service: String,
    val dependencies: List<DependencyStatus>,
    val timestamp: String,
)

@Serializable
data class ErrorResponse(val code: String, val message: String, val details: List<ErrorDetail> = emptyList(), val traceId: String)

@Serializable
data class ErrorDetail(val field: String, val issue: String)

private fun validateRegister(request: RegisterRequest) {
    require(request.username.length in 3..64) { "username length must be between 3 and 64" }
    require(request.username.matches(Regex("^[a-zA-Z0-9._-]+$"))) { "username contains invalid characters" }
    require(request.email.length <= 255 && request.email.contains("@")) { "email is invalid" }
    require(request.password.length in 8..128) { "password length must be between 8 and 128" }
}

private fun validateLogin(request: LoginRequest) {
    require(request.login.length in 3..255) { "login length must be between 3 and 255" }
    require(request.password.length in 8..128) { "password length must be between 8 and 128" }
}

private fun ApplicationCall.error(code: String, message: String): ErrorResponse =
    ErrorResponse(code = code, message = message, traceId = traceId())

private fun ApplicationCall.traceId(): String =
    request.headers["X-Request-Id"] ?: request.headers["X-Correlation-Id"] ?: UUID.randomUUID().toString()

private fun randomToken(): String {
    val bytes = ByteArray(48)
    SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

private fun sha256(value: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}
