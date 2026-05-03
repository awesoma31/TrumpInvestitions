package org.awesoma.trumpinvestitions.repository

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.awesoma.trumpinvestitions.data.model.OrderKind
import org.awesoma.trumpinvestitions.data.model.OrderStatus
import org.awesoma.trumpinvestitions.data.model.OrderType
import org.awesoma.trumpinvestitions.data.network.ApiService
import org.awesoma.trumpinvestitions.data.repository.PortfolioRepository
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PortfolioRepositoryTest {

    private val server = MockWebServer()
    private lateinit var apiService: ApiService
    private lateinit var repository: PortfolioRepository

    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        server.start()
        apiService = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
        repository = PortfolioRepository(apiService)
    }

    @After
    fun tearDown() = server.shutdown()

    private val portfolioJson = """
        {
            "userId": 42,
            "cashBalance": "9500.00",
            "totalMarketValue": "1820.00",
            "totalEquity": "11320.00",
            "realizedPnl": "0.00",
            "unrealizedPnl": "20.00",
            "totalPnl": "20.00",
            "positions": [
                {
                    "symbol": "AAPL",
                    "quantity": 10,
                    "avgPrice": "180.00",
                    "currentPrice": "182.00",
                    "marketValue": "1820.00",
                    "realizedPnl": "0.00",
                    "unrealizedPnl": "20.00",
                    "totalPnl": "20.00",
                    "currency": "USD",
                    "updatedAt": "2024-01-15T10:00:00Z"
                }
            ],
            "updatedAt": "2024-01-15T10:00:00Z"
        }
    """.trimIndent()

    private fun ordersJson(vararg orders: String) = """
        {"items": [${orders.joinToString(",")}], "total": ${orders.size}, "limit": 50, "offset": 0}
    """.trimIndent()

    private fun orderJson(
        id: String = "ord-1",
        symbol: String = "AAPL",
        side: String = "BUY",
        type: String = "MARKET",
        status: String = "NEW",
        avgFillPrice: String? = null
    ) = buildString {
        append("""{"id":"$id","userId":42,"symbol":"$symbol","side":"$side","type":"$type","quantity":10,""")
        if (avgFillPrice != null) append(""""avgFillPrice":"$avgFillPrice",""")
        append(""""status":"$status","createdAt":"2024-01-15T10:00:00Z","updatedAt":"2024-01-15T10:01:00Z"}""")
    }

    // ── Portfolio state ───────────────────────────────────────────────────────

    @Test
    fun `getPortfolioFlow — parses cash balance and total pnl`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(portfolioJson))
        server.enqueue(MockResponse().setResponseCode(200).setBody(ordersJson()))

        val state = repository.getPortfolioFlow().first()

        assertEquals(9500.0, state.cashBalance)
        assertEquals(20.0, state.totalPnl)
    }

    @Test
    fun `getPortfolioFlow — maps positions correctly`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(portfolioJson))
        server.enqueue(MockResponse().setResponseCode(200).setBody(ordersJson()))

        val state = repository.getPortfolioFlow().first()

        assertEquals(1, state.positions.size)
        val pos = state.positions[0]
        assertEquals("AAPL", pos.symbol)
        assertEquals(10, pos.quantity)
        assertEquals(180.0, pos.avgBuyPrice)
        assertEquals(182.0, pos.currentPrice)
        // PnL from Position model: (182 - 180) * 10 = 20
        assertEquals(20.0, pos.pnl, 0.001)
    }

    // ── Order status mapping ──────────────────────────────────────────────────

    @Test
    fun `getPortfolioFlow — maps all order statuses correctly`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(portfolioJson))
        server.enqueue(MockResponse().setResponseCode(200).setBody(ordersJson(
            orderJson(id = "o1", status = "NEW"),
            orderJson(id = "o2", status = "FILLED", avgFillPrice = "182.00"),
            orderJson(id = "o3", status = "CANCELLED"),
            orderJson(id = "o4", status = "REJECTED")
        )))

        val orders = repository.getPortfolioFlow().first().orders

        assertEquals(4, orders.size)
        assertEquals(OrderStatus.NEW,       orders[0].status)
        assertEquals(OrderStatus.FILLED,    orders[1].status)
        assertEquals(OrderStatus.CANCELLED, orders[2].status)
        assertEquals(OrderStatus.REJECTED,  orders[3].status)
    }

    @Test
    fun `getPortfolioFlow — unknown status defaults to NEW`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(portfolioJson))
        server.enqueue(MockResponse().setResponseCode(200).setBody(ordersJson(
            orderJson(id = "o1", status = "PENDING")
        )))

        val orders = repository.getPortfolioFlow().first().orders
        assertEquals(OrderStatus.NEW, orders[0].status)
    }

    @Test
    fun `getPortfolioFlow — maps order side (BUY-SELL) to OrderType`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(portfolioJson))
        server.enqueue(MockResponse().setResponseCode(200).setBody(ordersJson(
            orderJson(id = "o1", side = "BUY"),
            orderJson(id = "o2", side = "SELL")
        )))

        val orders = repository.getPortfolioFlow().first().orders
        assertEquals(OrderType.BUY,  orders[0].type)
        assertEquals(OrderType.SELL, orders[1].type)
    }

    @Test
    fun `getPortfolioFlow — maps order type (MARKET-LIMIT) to OrderKind`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(portfolioJson))
        server.enqueue(MockResponse().setResponseCode(200).setBody(ordersJson(
            orderJson(id = "o1", type = "MARKET"),
            orderJson(id = "o2", type = "LIMIT")
        )))

        val orders = repository.getPortfolioFlow().first().orders
        assertEquals(OrderKind.MARKET, orders[0].orderKind)
        assertEquals(OrderKind.LIMIT,  orders[1].orderKind)
    }

    @Test
    fun `getPortfolioFlow — avgFillPrice used as order price`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(portfolioJson))
        server.enqueue(MockResponse().setResponseCode(200).setBody(ordersJson(
            orderJson(id = "o1", status = "FILLED", avgFillPrice = "182.52")
        )))

        val orders = repository.getPortfolioFlow().first().orders
        assertEquals(182.52, orders[0].price, 0.001)
    }

    @Test
    fun `getPortfolioFlow — missing avgFillPrice gives zero price`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(portfolioJson))
        server.enqueue(MockResponse().setResponseCode(200).setBody(ordersJson(
            orderJson(id = "o1", status = "NEW")
        )))

        val orders = repository.getPortfolioFlow().first().orders
        assertEquals(0.0, orders[0].price, 0.001)
    }

    @Test
    fun `getPortfolioFlow — empty positions and orders handled`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"userId":1,"cashBalance":"10000.00","totalMarketValue":"0.00","totalEquity":"10000.00",
             "realizedPnl":"0.00","unrealizedPnl":"0.00","totalPnl":"0.00","positions":[],"updatedAt":"2024-01-15T10:00:00Z"}
        """.trimIndent()))
        server.enqueue(MockResponse().setResponseCode(200).setBody(ordersJson()))

        val state = repository.getPortfolioFlow().first()
        assertEquals(0, state.positions.size)
        assertEquals(0, state.orders.size)
        assertEquals(10000.0, state.cashBalance)
    }
}
