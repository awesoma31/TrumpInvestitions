package org.awesoma.trumpinvestitions.load

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil
import kotlin.math.max
import kotlin.random.Random
import kotlin.system.measureTimeMillis

private val log = LoggerFactory.getLogger("load-service")

fun main() {
    val config = LoadConfig.fromEnv()
    val runner = LoadRunner(config)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    if (config.runOnStart) {
        scope.launch {
            delay(config.startDelaySeconds * 1000L)
            runner.start(config.toRequest())
        }
    }

    embeddedServer(Netty, host = config.host, port = config.port) {
        module(config, runner)
    }.start(wait = true)
}

fun Application.module(config: LoadConfig, runner: LoadRunner) {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    install(CORS) { anyHost() }
    install(ContentNegotiation) { json(json) }
    install(CallLogging) { level = Level.INFO }

    routing {
        route("/api/v1") {
            get("/system/health") {
                call.respond(HealthResponse("UP", "load-service", Instant.now().toString()))
            }
            get("/system/ready") {
                call.respond(HealthResponse("READY", "load-service", Instant.now().toString()))
            }
            get("/load/status") {
                call.respond(runner.status())
            }
            post("/load/run") {
                val request = call.receiveNullable<LoadRunRequest>() ?: config.toRequest()
                val started = runner.start(request)
                call.respond(
                    if (started) HttpStatusCode.Accepted else HttpStatusCode.Conflict,
                    runner.status(),
                )
            }
        }
    }
}

class LoadRunner(private val defaultConfig: LoadConfig) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val current = AtomicRunState()
    @Volatile private var job: Job? = null
    @Volatile private var lastSummary: LoadSummary? = null

    fun start(request: LoadRunRequest): Boolean {
        if (job?.isActive == true) return false
        val effective = request.normalized(defaultConfig)
        current.reset(effective)
        job = scope.launch {
            lastSummary = runLoad(effective, current)
        }
        return true
    }

    fun status(): LoadStatus {
        val summary = lastSummary
        return LoadStatus(
            running = job?.isActive == true,
            startedAt = current.startedAt?.toString(),
            finishedAt = summary?.finishedAt,
            activeUsers = current.activeUsers.get(),
            configuredUsers = current.configuredUsers,
            totalRequests = current.totalRequests.get(),
            successfulRequests = current.successfulRequests.get(),
            failedRequests = current.failedRequests.get(),
            registeredUsers = current.registeredUsers.get(),
            loggedInUsers = current.loggedInUsers.get(),
            ordersSubmitted = current.ordersSubmitted.get(),
            summary = summary,
        )
    }

    private suspend fun runLoad(config: EffectiveLoadConfig, state: AtomicRunState): LoadSummary {
        val client = HttpClient(CIO) {
            engine {
                maxConnectionsCount = max(1000, config.virtualUsers * 2)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = config.requestTimeoutMs
                connectTimeoutMillis = config.requestTimeoutMs
                socketTimeoutMillis = config.requestTimeoutMs
            }
            install(ClientContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        log.info(
            "Starting load run: users={}, duration={}s, ramp={}s, gateway={}",
            config.virtualUsers,
            config.durationSeconds,
            config.rampUpSeconds,
            config.gatewayUrl,
        )

        val started = Instant.now()
        val deadline = started.plusSeconds(config.durationSeconds.toLong())
        val progressJob = scope.launch {
            while (isActive && Instant.now().isBefore(deadline)) {
                delay(config.progressLogIntervalSeconds * 1000L)
                val snapshot = state.statusSnapshot()
                log.info(
                    "Progress: active={}, registered={}, loggedIn={}, orders={}, requests={}, success={}, failed={}, topFailures={}",
                    snapshot.activeUsers,
                    snapshot.registeredUsers,
                    snapshot.loggedInUsers,
                    snapshot.ordersSubmitted,
                    snapshot.totalRequests,
                    snapshot.successfulRequests,
                    snapshot.failedRequests,
                    state.topFailures(),
                )
            }
        }
        val launchDelayMs = if (config.rampUpSeconds <= 0) {
            0L
        } else {
            ceil(config.rampUpSeconds * 1000.0 / config.virtualUsers).toLong()
        }

        try {
            coroutineScope {
                (1..config.virtualUsers).map { index ->
                    async {
                        if (launchDelayMs > 0) delay(launchDelayMs * (index - 1))
                        virtualUser(index, config, state, client, deadline)
                    }
                }.awaitAll()
            }
        } finally {
            progressJob.cancel()
            client.close()
        }

        val finished = Instant.now()
        val summary = state.summary(started, finished)
        log.info(
            "Load run finished: requests={}, success={}, failed={}, p95={}ms, rps={}",
            summary.totalRequests,
            summary.successfulRequests,
            summary.failedRequests,
            summary.latency.p95Ms,
            summary.requestsPerSecond,
        )
        return summary
    }

    private suspend fun virtualUser(
        index: Int,
        config: EffectiveLoadConfig,
        state: AtomicRunState,
        client: HttpClient,
        deadline: Instant,
    ) {
        state.activeUsers.incrementAndGet()
        try {
            val runId = state.runId
            val username = "load_${runId}_$index"
            val email = "$username@example.com"
            val password = "StrongPass123!"
            val token = registerAndLogin(client, config.gatewayUrl, username, email, password, state) ?: return

            state.loggedInUsers.incrementAndGet()
            request(state) {
                client.post("${config.gatewayUrl}/portfolio/balance/deposit") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(BalanceRequest(config.initialDeposit))
                }
            }

            var iteration = 0
            while (Instant.now().isBefore(deadline) && scope.isActive) {
                iteration += 1
                val symbol = config.symbols.random()
                when (Random.nextInt(100)) {
                    in 0..44 -> hitMarketApi(config, state, client, symbol)
                    in 45..69 -> hitPortfolioApi(config, state, client, token, symbol)
                    in 70..89 -> hitTradingLists(config, state, client, token, symbol)
                    else -> hitPublicGateway(config, state, client)
                }

                if (iteration % config.orderEveryNIterations == 0) {
                    placeOrderAndReadDetails(config, state, client, token, symbol)
                }

                if (config.thinkTimeMs > 0) delay(config.thinkTimeMs)
            }
        } catch (error: Throwable) {
            state.failedRequests.incrementAndGet()
            log.debug("Virtual user {} failed", index, error)
        } finally {
            state.activeUsers.decrementAndGet()
        }
    }

    private suspend fun hitPublicGateway(
        config: EffectiveLoadConfig,
        state: AtomicRunState,
        client: HttpClient,
    ) {
        if (Random.nextInt(100) >= config.systemRequestPercent) return
        when (Random.nextInt(3)) {
            0 -> request(state, "GET /system/health") { client.get("${config.gatewayUrl}/system/health") }
            1 -> request(state, "GET /system/ready") { client.get("${config.gatewayUrl}/system/ready") }
            else -> request(state, "GET /market/system/health") { client.get("${config.gatewayUrl}/market/system/health") }
        }
    }

    private suspend fun hitMarketApi(
        config: EffectiveLoadConfig,
        state: AtomicRunState,
        client: HttpClient,
        symbol: String,
    ) {
        val now = Instant.now()
        val from = now.minusSeconds(config.historyWindowSeconds.toLong())
        val querySymbol = symbol.take(Random.nextInt(1, symbol.length + 1))

        when (Random.nextInt(7)) {
            0 -> request(state, "GET /market/instruments") {
                client.get("${config.gatewayUrl}/market/instruments") {
                    parameter("limit", 50)
                    parameter("offset", 0)
                }
            }
            1 -> request(state, "GET /market/instruments?q") {
                client.get("${config.gatewayUrl}/market/instruments") {
                    parameter("q", querySymbol)
                    parameter("limit", 10)
                }
            }
            2 -> request(state, "GET /market/instruments/{symbol}") { client.get("${config.gatewayUrl}/market/instruments/$symbol") }
            3 -> request(state, "GET /market/quotes") {
                client.get("${config.gatewayUrl}/market/quotes") {
                    parameter("symbols", config.symbols.joinToString(","))
                }
            }
            4 -> request(state, "GET /market/quotes/{symbol}") { client.get("${config.gatewayUrl}/market/quotes/$symbol") }
            5 -> request(state, "GET /market/history/candles") {
                client.get("${config.gatewayUrl}/market/history/candles") {
                    parameter("symbol", symbol)
                    parameter("from", from.toString())
                    parameter("to", now.toString())
                    parameter("interval", config.candleIntervals.random())
                    parameter("limit", config.historyLimit)
                }
            }
            else -> request(state, "GET /market/order-book/{symbol}") {
                client.get("${config.gatewayUrl}/market/order-book/$symbol") {
                    parameter("depth", listOf(5, 10, 20, 50).random())
                }
            }
        }
    }

    private suspend fun hitPortfolioApi(
        config: EffectiveLoadConfig,
        state: AtomicRunState,
        client: HttpClient,
        token: String,
        symbol: String,
    ) {
        when (Random.nextInt(8)) {
            0 -> request(state, "GET /portfolio") { client.get("${config.gatewayUrl}/portfolio") { bearerAuth(token) } }
            1 -> request(state, "GET /portfolio/positions") { client.get("${config.gatewayUrl}/portfolio/positions") { bearerAuth(token) } }
            2 -> request(state, "GET /portfolio/positions/{symbol}", expectedStatuses = setOf(200, 404)) {
                client.get("${config.gatewayUrl}/portfolio/positions/$symbol") { bearerAuth(token) }
            }
            3 -> request(state, "GET /portfolio/pnl") { client.get("${config.gatewayUrl}/portfolio/pnl") { bearerAuth(token) } }
            4 -> request(state, "GET /portfolio/balance/cash") { client.get("${config.gatewayUrl}/portfolio/balance/cash") { bearerAuth(token) } }
            5 -> request(state, "GET /portfolio/assets/{symbol}/quantity") { client.get("${config.gatewayUrl}/portfolio/assets/$symbol/quantity") { bearerAuth(token) } }
            6 -> request(state, "GET /portfolio/orders") {
                client.get("${config.gatewayUrl}/portfolio/orders") {
                    bearerAuth(token)
                    parameter("symbol", symbol)
                    parameter("limit", 20)
                }
            }
            else -> request(state, "GET /portfolio/trades") {
                client.get("${config.gatewayUrl}/portfolio/trades") {
                    bearerAuth(token)
                    parameter("symbol", symbol)
                    parameter("limit", 20)
                }
            }
        }

        if (Random.nextInt(100) < config.withdrawRequestPercent) {
            request(state, "POST /portfolio/balance/withdraw") {
                client.post("${config.gatewayUrl}/portfolio/balance/withdraw") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(BalanceRequest(config.withdrawAmount))
                }
            }
            request(state, "POST /portfolio/balance/deposit") {
                client.post("${config.gatewayUrl}/portfolio/balance/deposit") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(BalanceRequest(config.withdrawAmount))
                }
            }
        }
    }

    private suspend fun hitTradingLists(
        config: EffectiveLoadConfig,
        state: AtomicRunState,
        client: HttpClient,
        token: String,
        symbol: String,
    ) {
        when (Random.nextInt(4)) {
            0 -> request(state, "GET /orders") {
                client.get("${config.gatewayUrl}/orders") {
                    bearerAuth(token)
                    parameter("limit", 20)
                    parameter("offset", 0)
                }
            }
            1 -> request(state, "GET /orders?filters") {
                client.get("${config.gatewayUrl}/orders") {
                    bearerAuth(token)
                    parameter("status", "FILLED")
                    parameter("symbol", symbol)
                    parameter("side", listOf("BUY", "SELL").random())
                    parameter("limit", 10)
                }
            }
            2 -> request(state, "GET /trades") {
                client.get("${config.gatewayUrl}/trades") {
                    bearerAuth(token)
                    parameter("limit", 20)
                }
            }
            else -> request(state, "GET /trades?filters") {
                client.get("${config.gatewayUrl}/trades") {
                    bearerAuth(token)
                    parameter("symbol", symbol)
                    parameter("side", listOf("BUY", "SELL").random())
                    parameter("limit", 10)
                }
            }
        }
    }

    private suspend fun placeOrderAndReadDetails(
        config: EffectiveLoadConfig,
        state: AtomicRunState,
        client: HttpClient,
        token: String,
        symbol: String,
    ) {
        val side = if (Random.nextInt(100) < config.sellOrderPercent) "SELL" else "BUY"
        var orderId: String? = null

        request(state, "POST /orders") {
            val response = client.post("${config.gatewayUrl}/orders") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(OrderRequest(symbol, side, "MARKET", Random.nextInt(1, config.maxOrderQuantity + 1)))
            }
            if (response.status.isSuccess()) {
                orderId = runCatching { response.body<OrderResponse>().id }.getOrNull()
            }
            response
        }
        state.ordersSubmitted.incrementAndGet()

        orderId?.let { id ->
            request(state, "GET /orders/{orderId}") { client.get("${config.gatewayUrl}/orders/$id") { bearerAuth(token) } }
        }

        val tradeId = runCatching {
            client.get("${config.gatewayUrl}/trades") {
                bearerAuth(token)
                parameter("symbol", symbol)
                parameter("limit", 1)
            }.body<TradeListResponse>().items.firstOrNull()?.id
        }.getOrNull()

        tradeId?.let { id ->
            request(state, "GET /trades/{tradeId}") { client.get("${config.gatewayUrl}/trades/$id") { bearerAuth(token) } }
        }
    }

    private suspend fun registerAndLogin(
        client: HttpClient,
        gatewayUrl: String,
        username: String,
        email: String,
        password: String,
        state: AtomicRunState,
    ): String? {
        val registered = request(state, "POST /auth/register") {
            client.post("$gatewayUrl/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest(username, email, password))
            }
        }
        if (!registered) return null
        state.registeredUsers.incrementAndGet()

        return runCatching {
            var token: String? = null
            request(state, "POST /auth/login") {
                val response = client.post("$gatewayUrl/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody(LoginRequest(username, password))
                }
                if (response.status.isSuccess()) token = response.body<AuthResponse>().accessToken
                response
            }
            token
        }.getOrNull()
    }

    private suspend fun request(
        state: AtomicRunState,
        name: String = "unknown",
        expectedStatuses: Set<Int>? = null,
        block: suspend () -> io.ktor.client.statement.HttpResponse,
    ): Boolean {
        var success = false
        var statusCode = "exception"
        val elapsed = measureTimeMillis {
            runCatching {
                val response = block()
                statusCode = response.status.value.toString()
                success = expectedStatuses?.contains(response.status.value) ?: (response.status.value in 200..399)
            }.onFailure {
                success = false
                statusCode = it::class.simpleName ?: "exception"
            }
        }
        state.totalRequests.incrementAndGet()
        if (success) state.successfulRequests.incrementAndGet() else state.failedRequests.incrementAndGet()
        state.recordEndpoint(name, success, statusCode)
        state.latenciesMs.add(elapsed)
        return success
    }
}

private class AtomicRunState {
    val activeUsers = AtomicLong()
    val totalRequests = AtomicLong()
    val successfulRequests = AtomicLong()
    val failedRequests = AtomicLong()
    val registeredUsers = AtomicLong()
    val loggedInUsers = AtomicLong()
    val ordersSubmitted = AtomicLong()
    val latenciesMs = ConcurrentLinkedQueue<Long>()
    private val endpoints = ConcurrentHashMap<String, EndpointCounters>()

    @Volatile var startedAt: Instant? = null
    @Volatile var configuredUsers: Int = 0
    @Volatile var runId: String = "none"

    fun reset(config: EffectiveLoadConfig) {
        activeUsers.set(0)
        totalRequests.set(0)
        successfulRequests.set(0)
        failedRequests.set(0)
        registeredUsers.set(0)
        loggedInUsers.set(0)
        ordersSubmitted.set(0)
        latenciesMs.clear()
        endpoints.clear()
        configuredUsers = config.virtualUsers
        runId = UUID.randomUUID().toString().replace("-", "").take(12)
        startedAt = Instant.now()
    }

    fun recordEndpoint(name: String, success: Boolean, statusCode: String) {
        val counters = endpoints.computeIfAbsent(name) { EndpointCounters() }
        counters.total.incrementAndGet()
        if (success) counters.success.incrementAndGet() else counters.failed.incrementAndGet()
        if (!success) {
            val key = "$name -> $statusCode"
            counters.failures.computeIfAbsent(key) { AtomicLong() }.incrementAndGet()
        }
    }

    fun statusSnapshot(): LoadStatus = LoadStatus(
        running = true,
        startedAt = startedAt?.toString(),
        finishedAt = null,
        activeUsers = activeUsers.get(),
        configuredUsers = configuredUsers,
        totalRequests = totalRequests.get(),
        successfulRequests = successfulRequests.get(),
        failedRequests = failedRequests.get(),
        registeredUsers = registeredUsers.get(),
        loggedInUsers = loggedInUsers.get(),
        ordersSubmitted = ordersSubmitted.get(),
        summary = null,
    )

    fun topFailures(limit: Int = 5): String {
        val failures = endpoints.values
            .flatMap { it.failures.entries }
            .sortedByDescending { it.value.get() }
            .take(limit)
            .joinToString("; ") { "${it.key}: ${it.value.get()}" }
        return failures.ifBlank { "none" }
    }

    fun summary(started: Instant, finished: Instant): LoadSummary {
        val duration = max(1.0, Duration.between(started, finished).toMillis() / 1000.0)
        val latencies = latenciesMs.toList().sorted()
        return LoadSummary(
            runId = runId,
            startedAt = started.toString(),
            finishedAt = finished.toString(),
            durationSeconds = duration,
            configuredUsers = configuredUsers,
            totalRequests = totalRequests.get(),
            successfulRequests = successfulRequests.get(),
            failedRequests = failedRequests.get(),
            registeredUsers = registeredUsers.get(),
            loggedInUsers = loggedInUsers.get(),
            ordersSubmitted = ordersSubmitted.get(),
            requestsPerSecond = "%.2f".format(totalRequests.get() / duration),
            latency = LatencySummary.from(latencies),
            endpoints = endpoints.map { (name, counters) ->
                EndpointSummary(
                    name = name,
                    total = counters.total.get(),
                    successful = counters.success.get(),
                    failed = counters.failed.get(),
                )
            }.sortedByDescending { it.total },
        )
    }
}

private class EndpointCounters {
    val total = AtomicLong()
    val success = AtomicLong()
    val failed = AtomicLong()
    val failures = ConcurrentHashMap<String, AtomicLong>()
}

private fun HttpStatusCode.isSuccess(): Boolean = value in 200..399

data class LoadConfig(
    val host: String,
    val port: Int,
    val gatewayUrl: String,
    val virtualUsers: Int,
    val maxVirtualUsers: Int,
    val durationSeconds: Int,
    val rampUpSeconds: Int,
    val requestTimeoutMs: Long,
    val orderEveryNIterations: Int,
    val sellOrderPercent: Int,
    val maxOrderQuantity: Int,
    val initialDeposit: String,
    val withdrawAmount: String,
    val withdrawRequestPercent: Int,
    val systemRequestPercent: Int,
    val progressLogIntervalSeconds: Long,
    val historyWindowSeconds: Int,
    val historyLimit: Int,
    val candleIntervals: List<String>,
    val thinkTimeMs: Long,
    val symbols: List<String>,
    val runOnStart: Boolean,
    val startDelaySeconds: Long,
) {
    fun toRequest(): LoadRunRequest = LoadRunRequest(
        virtualUsers = virtualUsers,
        durationSeconds = durationSeconds,
        rampUpSeconds = rampUpSeconds,
        orderEveryNIterations = orderEveryNIterations,
    )

    companion object {
        fun fromEnv(): LoadConfig = LoadConfig(
            host = env("HOST", "0.0.0.0"),
            port = env("PORT", "8095").toInt(),
            gatewayUrl = env("GATEWAY_URL", "http://auth-gateway:8080/api/v1").trimEnd('/'),
            virtualUsers = env("VIRTUAL_USERS", "1000").toInt(),
            maxVirtualUsers = env("MAX_VIRTUAL_USERS", "10000").toInt(),
            durationSeconds = env("DURATION_SECONDS", "120").toInt(),
            rampUpSeconds = env("RAMP_UP_SECONDS", "30").toInt(),
            requestTimeoutMs = env("REQUEST_TIMEOUT_MS", "30000").toLong(),
            orderEveryNIterations = env("ORDER_EVERY_N_ITERATIONS", "6").toInt(),
            sellOrderPercent = env("SELL_ORDER_PERCENT", "0").toInt(),
            maxOrderQuantity = env("MAX_ORDER_QUANTITY", "5").toInt(),
            initialDeposit = env("INITIAL_DEPOSIT", "1000000.00"),
            withdrawAmount = env("WITHDRAW_AMOUNT", "10.00"),
            withdrawRequestPercent = env("WITHDRAW_REQUEST_PERCENT", "5").toInt(),
            systemRequestPercent = env("SYSTEM_REQUEST_PERCENT", "10").toInt(),
            progressLogIntervalSeconds = env("PROGRESS_LOG_INTERVAL_SECONDS", "10").toLong(),
            historyWindowSeconds = env("HISTORY_WINDOW_SECONDS", "86400").toInt(),
            historyLimit = env("HISTORY_LIMIT", "100").toInt(),
            candleIntervals = env("CANDLE_INTERVALS", "1m,5m,15m,1h")
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() },
            thinkTimeMs = env("THINK_TIME_MS", "10000").toLong(),
            symbols = env("SYMBOLS", "AAPL,MSFT,TSLA,BTCUSDT,ETHUSDT")
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() },
            runOnStart = env("RUN_ON_START", "true").toBooleanStrictOrNull() ?: true,
            startDelaySeconds = env("START_DELAY_SECONDS", "10").toLong(),
        )

        private fun env(name: String, default: String): String =
            System.getenv(name)?.takeIf { it.isNotBlank() } ?: default
    }
}

@Serializable
data class LoadRunRequest(
    val virtualUsers: Int? = null,
    val durationSeconds: Int? = null,
    val rampUpSeconds: Int? = null,
    val orderEveryNIterations: Int? = null,
)

private fun LoadRunRequest.normalized(defaults: LoadConfig): EffectiveLoadConfig {
    val users = (virtualUsers ?: defaults.virtualUsers).coerceIn(1, defaults.maxVirtualUsers)
    return EffectiveLoadConfig(
        gatewayUrl = defaults.gatewayUrl,
        virtualUsers = users,
        durationSeconds = (durationSeconds ?: defaults.durationSeconds).coerceAtLeast(1),
        rampUpSeconds = (rampUpSeconds ?: defaults.rampUpSeconds).coerceAtLeast(0),
        requestTimeoutMs = defaults.requestTimeoutMs,
        orderEveryNIterations = (orderEveryNIterations ?: defaults.orderEveryNIterations).coerceAtLeast(1),
        sellOrderPercent = defaults.sellOrderPercent.coerceIn(0, 100),
        maxOrderQuantity = defaults.maxOrderQuantity.coerceAtLeast(1),
        initialDeposit = defaults.initialDeposit,
        withdrawAmount = defaults.withdrawAmount,
        withdrawRequestPercent = defaults.withdrawRequestPercent.coerceIn(0, 100),
        systemRequestPercent = defaults.systemRequestPercent.coerceIn(0, 100),
        progressLogIntervalSeconds = defaults.progressLogIntervalSeconds.coerceAtLeast(1),
        historyWindowSeconds = defaults.historyWindowSeconds.coerceAtLeast(60),
        historyLimit = defaults.historyLimit.coerceIn(1, 10000),
        candleIntervals = defaults.candleIntervals.ifEmpty { listOf("1m") },
        thinkTimeMs = defaults.thinkTimeMs.coerceAtLeast(0),
        symbols = defaults.symbols.ifEmpty { listOf("AAPL") },
    )
}

data class EffectiveLoadConfig(
    val gatewayUrl: String,
    val virtualUsers: Int,
    val durationSeconds: Int,
    val rampUpSeconds: Int,
    val requestTimeoutMs: Long,
    val orderEveryNIterations: Int,
    val sellOrderPercent: Int,
    val maxOrderQuantity: Int,
    val initialDeposit: String,
    val withdrawAmount: String,
    val withdrawRequestPercent: Int,
    val systemRequestPercent: Int,
    val progressLogIntervalSeconds: Long,
    val historyWindowSeconds: Int,
    val historyLimit: Int,
    val candleIntervals: List<String>,
    val thinkTimeMs: Long,
    val symbols: List<String>,
)

@Serializable
data class RegisterRequest(val username: String, val email: String, val password: String)

@Serializable
data class LoginRequest(val login: String, val password: String)

@Serializable
data class BalanceRequest(val amount: String)

@Serializable
data class OrderRequest(val symbol: String, val side: String, val type: String, val quantity: Int)

@Serializable
data class AuthResponse(val accessToken: String)

@Serializable
data class OrderResponse(val id: String)

@Serializable
data class TradeListResponse(val items: List<TradeItem> = emptyList())

@Serializable
data class TradeItem(val id: String)

@Serializable
data class HealthResponse(val status: String, val service: String, val timestamp: String)

@Serializable
data class LoadStatus(
    val running: Boolean,
    val startedAt: String?,
    val finishedAt: String?,
    val activeUsers: Long,
    val configuredUsers: Int,
    val totalRequests: Long,
    val successfulRequests: Long,
    val failedRequests: Long,
    val registeredUsers: Long,
    val loggedInUsers: Long,
    val ordersSubmitted: Long,
    val summary: LoadSummary?,
)

@Serializable
data class LoadSummary(
    val runId: String,
    val startedAt: String,
    val finishedAt: String,
    val durationSeconds: Double,
    val configuredUsers: Int,
    val totalRequests: Long,
    val successfulRequests: Long,
    val failedRequests: Long,
    val registeredUsers: Long,
    val loggedInUsers: Long,
    val ordersSubmitted: Long,
    val requestsPerSecond: String,
    val latency: LatencySummary,
    val endpoints: List<EndpointSummary>,
)

@Serializable
data class EndpointSummary(
    val name: String,
    val total: Long,
    val successful: Long,
    val failed: Long,
)

@Serializable
data class LatencySummary(
    val minMs: Long,
    val avgMs: Long,
    val p50Ms: Long,
    val p95Ms: Long,
    val p99Ms: Long,
    val maxMs: Long,
) {
    companion object {
        fun from(sorted: List<Long>): LatencySummary {
            if (sorted.isEmpty()) return LatencySummary(0, 0, 0, 0, 0, 0)
            fun percentile(p: Double): Long {
                val index = ((sorted.size - 1) * p).toInt().coerceIn(0, sorted.lastIndex)
                return sorted[index]
            }
            return LatencySummary(
                minMs = sorted.first(),
                avgMs = sorted.average().toLong(),
                p50Ms = percentile(0.50),
                p95Ms = percentile(0.95),
                p99Ms = percentile(0.99),
                maxMs = sorted.last(),
            )
        }
    }
}
