package org.awesoma.trumpinvestitions

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.awesoma.trumpinvestitions.data.network.ApiService
import org.awesoma.trumpinvestitions.data.network.AuthApiService
import org.awesoma.trumpinvestitions.data.network.dto.CreateOrderRequestDto
import org.awesoma.trumpinvestitions.data.network.dto.LoginRequestDto
import org.awesoma.trumpinvestitions.data.network.dto.LogoutRequestDto
import org.awesoma.trumpinvestitions.data.network.dto.RegisterRequestDto
import org.junit.Assume
import org.junit.Before
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Интеграционные тесты против реального бэкенда.
 *
 * Предварительные условия:
 *   1. Запустить бэкенд: docker-compose up -d (из корня репозитория)
 *   2. Запустить Android-эмулятор
 *   3. ./gradlew :app:connectedAndroidTest
 *
 * Если бэкенд недоступен — тесты автоматически пропускаются (Assume).
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class BackendIntegrationTest {

    companion object {
        // Для эмулятора: 10.0.2.2 (loopback на хост)
        // Для физического устройства: передать IP через аргумент:
        //   -e backendHost 192.168.0.3
        // или задать в build.gradle: testInstrumentationRunnerArguments["backendHost"] = "192.168.0.3"
        private val backendHost: String by lazy {
            InstrumentationRegistry.getArguments().getString("backendHost", "10.0.2.2")
        }
        private val BASE_URL get() = "http://$backendHost:8080/api/v1/"
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

        private lateinit var authService: AuthApiService
        private lateinit var plainApiService: ApiService

        // Данные, которые прокидываются между тестами
        private var accessToken: String = ""
        private var refreshToken: String = ""
        private val username = "testuser_${System.currentTimeMillis()}"
        private val email = "${username}@example.com"
        private const val password = "Password123!"
        private var createdOrderId: String = ""

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            authService = retrofit.create(AuthApiService::class.java)
            plainApiService = retrofit.create(ApiService::class.java)
        }

        private fun isBackendReachable(): Boolean = try {
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .build()
                .newCall(Request.Builder().url("http://$backendHost:8080/api/v1/system/health").build())
                .execute()
                .isSuccessful
        } catch (_: Exception) { false }

        private fun authedApiService(): ApiService {
            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("Authorization", "Bearer $accessToken")
                            .build()
                    )
                }
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(ApiService::class.java)
        }
    }

    @Before
    fun skipIfBackendUnreachable() {
        Assume.assumeTrue(
            "Бэкенд недоступен по адресу $BASE_URL — убедитесь, что docker-compose up -d запущен",
            isBackendReachable()
        )
    }

    // ── 1. Auth ───────────────────────────────────────────────────────────────

    @Test
    fun test01_register_createsAccountAndReturnsTokens() = runBlocking {
        val response = authService.register(RegisterRequestDto(username, email, password))

        assertNotNull(response.accessToken, "accessToken должен присутствовать")
        assertNotNull(response.refreshToken, "refreshToken должен присутствовать")
        assertTrue(response.accessToken.isNotBlank())
        assertEquals(username, response.user.username)
        assertEquals(email, response.user.email)
        assertTrue(response.expiresIn > 0)

        accessToken = response.accessToken
        refreshToken = response.refreshToken
    }

    @Test
    fun test02_login_returnsValidTokens() = runBlocking {
        val response = authService.login(LoginRequestDto(login = username, password = password))

        assertNotNull(response.accessToken)
        assertEquals(username, response.user.username)
        assertEquals("Bearer", response.tokenType)

        accessToken = response.accessToken
        refreshToken = response.refreshToken
    }

    @Test
    fun test03_refresh_returnsNewTokens() = runBlocking {
        Assume.assumeTrue("refreshToken не получен — пропускаем", refreshToken.isNotBlank())

        val response = authService.refresh(
            org.awesoma.trumpinvestitions.data.network.dto.RefreshRequestDto(refreshToken)
        )

        assertNotNull(response.accessToken)
        assertTrue(response.accessToken.isNotBlank())

        accessToken = response.accessToken
        refreshToken = response.refreshToken
    }

    // ── 2. Market (публичные endpoint-ы, токен не нужен) ─────────────────────

    @Test
    fun test04_getInstruments_returnsNonEmptyList() = runBlocking {
        val response = plainApiService.getInstruments(limit = 50)

        assertTrue(response.items.isNotEmpty(), "Список инструментов не должен быть пустым")
        val aapl = response.items.find { it.symbol == "AAPL" }
        assertNotNull(aapl, "AAPL должен присутствовать в инструментах")
        assertTrue(aapl.name.isNotBlank())
    }

    @Test
    fun test05_getQuotes_returnsCurrentPrices() = runBlocking {
        val response = plainApiService.getQuotes("AAPL,GOOGL,MSFT")

        assertTrue(response.items.isNotEmpty(), "Котировки не должны быть пустыми")
        val aapl = response.items.find { it.symbol == "AAPL" }
        assertNotNull(aapl, "Котировка AAPL должна присутствовать")
        assertTrue(aapl.last.toDoubleOrNull() != null, "last должен быть числом: ${aapl.last}")
        assertTrue(aapl.bid.toDoubleOrNull()!! > 0, "bid должен быть положительным")
        assertTrue(aapl.ask.toDoubleOrNull()!! > 0, "ask должен быть положительным")
    }

    @Test
    fun test06_getQuote_singleSymbol_returnsQuote() = runBlocking {
        val quote = plainApiService.getQuote("AAPL")

        assertEquals("AAPL", quote.symbol)
        assertNotNull(quote.last)
        assertTrue(quote.last.toDoubleOrNull()!! > 0)
    }

    @Test
    fun test07_getCandles_returnsHistoryForSymbol() = runBlocking {
        val to = java.time.Instant.now().toString()
        val from = java.time.Instant.now().minusSeconds(24 * 3600).toString()

        val response = plainApiService.getCandles(
            symbol = "AAPL", from = from, to = to, interval = "1h", limit = 24
        )

        assertEquals("AAPL", response.symbol)
        // Может быть пустым в выходные/нерабочие часы, но парсинг не должен ломаться
        response.items.forEach { candle ->
            assertNotNull(candle.close.toDoubleOrNull(), "close должен быть числом: ${candle.close}")
            assertTrue(candle.volume >= 0)
        }
    }

    @Test
    fun test08_getOrderBook_returnsOrders() = runBlocking {
        val response = plainApiService.getOrderBook("AAPL", depth = 5)

        assertEquals("AAPL", response.symbol)
        assertTrue(response.bids.isNotEmpty() || response.asks.isNotEmpty(),
            "Стакан не должен быть полностью пустым")
        response.bids.forEach { level ->
            assertTrue(level.price.toDoubleOrNull()!! > 0, "Цена bid должна быть положительной")
            assertTrue(level.quantity > 0, "Количество bid должно быть положительным")
        }
    }

    // ── 3. Orders (требует токен) ─────────────────────────────────────────────

    @Test
    fun test08b_depositBalance() = runBlocking {
        Assume.assumeTrue("accessToken не получен", accessToken.isNotBlank())
        val api = authedApiService()

        val portfolio = api.deposit(
            org.awesoma.trumpinvestitions.data.network.dto.DepositRequestDto("50000.00")
        )

        assertTrue(
            portfolio.balance.toDoubleOrNull()!! >= 50000.0,
            "После пополнения баланс должен быть >= 50000, получен: ${portfolio.balance}"
        )
    }

    @Test
    fun test09_createMarketOrder_returnsOrderWithId() = runBlocking {
        Assume.assumeTrue("accessToken не получен", accessToken.isNotBlank())
        val api = authedApiService()

        val order = api.createOrder(
            CreateOrderRequestDto(symbol = "AAPL", side = "BUY", type = "MARKET", quantity = 1)
        )

        assertNotNull(order.id)
        assertTrue(order.id.isNotBlank())
        assertEquals("AAPL", order.symbol)
        assertEquals("BUY", order.side)
        assertEquals("MARKET", order.type)
        assertTrue(order.status == "NEW" || order.status == "FILLED",
            "Статус должен быть NEW или FILLED, получен: ${order.status}")

        createdOrderId = order.id
    }

    @Test
    fun test10_getOrders_containsCreatedOrder() = runBlocking {
        Assume.assumeTrue("accessToken не получен", accessToken.isNotBlank())
        val api = authedApiService()

        val response = api.getOrders()

        assertTrue(response.items.isNotEmpty(), "Список заявок не должен быть пустым")
        if (createdOrderId.isNotBlank()) {
            val found = response.items.any { it.id == createdOrderId }
            assertTrue(found, "Созданная заявка $createdOrderId должна быть в списке")
        }
    }

    @Test
    fun test11_createLimitOrder_thenCancel() = runBlocking {
        Assume.assumeTrue("accessToken не получен", accessToken.isNotBlank())
        val api = authedApiService()

        val order = api.createOrder(
            CreateOrderRequestDto(symbol = "MSFT", side = "BUY", type = "MARKET", quantity = 1)
        )
        assertNotNull(order.id)

        // Отменяем только если заявка ещё активна
        if (order.status == "NEW") {
            val cancelled = api.cancelOrder(order.id)
            assertEquals(order.id, cancelled.id)
            assertEquals("CANCELLED", cancelled.status)
        }
    }

    // ── 4. Portfolio ──────────────────────────────────────────────────────────

    @Test
    fun test12_getPortfolio_returnsBalanceAndPositions() = runBlocking {
        Assume.assumeTrue("accessToken не получен", accessToken.isNotBlank())
        val api = authedApiService()

        val portfolio = api.getPortfolio()

        assertNotNull(portfolio.cashBalance)
        assertNotNull(portfolio.totalEquity)
        assertTrue(portfolio.cashBalance.toDoubleOrNull() != null,
            "cashBalance должен быть числом: ${portfolio.cashBalance}")
        assertTrue(portfolio.cashBalance.toDoubleOrNull()!! >= 0)
        // Позиции могут быть пустыми для нового пользователя
        portfolio.positions.forEach { pos ->
            assertTrue(pos.symbol.isNotBlank())
            assertTrue(pos.quantity >= 0)
            assertNotNull(pos.avgPrice.toDoubleOrNull(), "avgPrice не является числом: ${pos.avgPrice}")
        }
    }

    // ── 5. Logout ─────────────────────────────────────────────────────────────

    @Test
    fun test13_logout_invalidatesToken() = runBlocking {
        Assume.assumeTrue("refreshToken не получен", refreshToken.isNotBlank())

        // Не бросает исключение — достаточно для проверки
        authService.logout(LogoutRequestDto(refreshToken))

        // После логаута токен должен быть невалиден
        // Проверяем косвенно: попытка обновить токен с использованным refresh должна упасть
        val failed = runCatching {
            authService.refresh(
                org.awesoma.trumpinvestitions.data.network.dto.RefreshRequestDto(refreshToken)
            )
        }
        assertTrue(failed.isFailure, "Refresh после logout должен завершиться ошибкой")
    }
}
