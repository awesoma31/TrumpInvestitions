package org.awesoma.trumpinvestitions.network

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.awesoma.trumpinvestitions.data.network.ApiService
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarketApiServiceTest {

    private val server = MockWebServer()
    private lateinit var service: ApiService
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        server.start()
        service = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }

    @After
    fun tearDown() = server.shutdown()

    // ── Instruments ──────────────────────────────────────────────────────────

    @Test
    fun `getInstruments — GET to correct path, parses list`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {
                "items": [
                    {"symbol": "AAPL", "name": "Apple Inc.", "currency": "USD", "lotSize": 1, "active": true},
                    {"symbol": "GOOGL", "name": "Alphabet Inc.", "currency": "USD", "lotSize": 1, "active": true}
                ],
                "total": 2
            }
        """.trimIndent()))

        val response = service.getInstruments(q = "", limit = 50)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertTrue(request.path!!.startsWith("/market/instruments"))
        assertEquals(2, response.items.size)
        assertEquals("AAPL", response.items[0].symbol)
        assertEquals("Apple Inc.", response.items[0].name)
        assertEquals("GOOGL", response.items[1].symbol)
    }

    @Test
    fun `getInstruments — empty list handled`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"items": [], "total": 0}"""))

        val response = service.getInstruments()
        assertEquals(0, response.items.size)
    }

    // ── Quotes ───────────────────────────────────────────────────────────────

    @Test
    fun `getQuotes — sends symbols query param, parses all fields`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {
                "items": [
                    {
                        "symbol": "AAPL",
                        "bid": "182.50",
                        "ask": "182.55",
                        "last": "182.52",
                        "open": "180.00",
                        "high": "183.00",
                        "low": "181.00",
                        "volume": 12345678,
                        "timestamp": "2024-01-15T10:00:00Z"
                    }
                ]
            }
        """.trimIndent()))

        val response = service.getQuotes("AAPL,GOOGL")

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertTrue(request.path!!.contains("symbols="), "Path must contain symbols query param")
        assertEquals(1, response.items.size)
        val q = response.items[0]
        assertEquals("AAPL", q.symbol)
        assertEquals("182.50", q.bid)
        assertEquals("182.55", q.ask)
        assertEquals("182.52", q.last)
        assertEquals("180.00", q.open)
        assertEquals(12345678L, q.volume)
    }

    @Test
    fun `getQuotes — nullable optional fields absent`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"items": [{"symbol": "BTC", "bid": "50000", "ask": "50001", "last": "50000", "timestamp": "2024-01-15T10:00:00Z"}]}
        """.trimIndent()))

        val q = service.getQuotes("BTC").items[0]
        assertNull(q.open)
        assertNull(q.high)
        assertNull(q.volume)
    }

    @Test
    fun `getQuote by symbol — hits correct path`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"symbol": "TSLA", "bid": "240.00", "ask": "240.10", "last": "240.05", "timestamp": "2024-01-15T10:00:00Z"}
        """.trimIndent()))

        val quote = service.getQuote("TSLA")

        val request = server.takeRequest()
        assertEquals("/market/quotes/TSLA", request.path)
        assertEquals("TSLA", quote.symbol)
        assertEquals("240.05", quote.last)
    }

    // ── Candles ──────────────────────────────────────────────────────────────

    @Test
    fun `getCandles — correct path and query params, parses candle list`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {
                "symbol": "AAPL",
                "interval": "1h",
                "items": [
                    {"timestamp": "2024-01-15T09:00:00Z", "open": "180.00", "high": "182.00", "low": "179.50", "close": "181.50", "volume": 1000000},
                    {"timestamp": "2024-01-15T10:00:00Z", "open": "181.50", "high": "183.00", "low": "181.00", "close": "182.52", "volume": 1200000}
                ]
            }
        """.trimIndent()))

        val response = service.getCandles(
            symbol = "AAPL",
            from = "2024-01-15T09:00:00Z",
            to = "2024-01-15T11:00:00Z",
            interval = "1h",
            limit = 50
        )

        val request = server.takeRequest()
        assertTrue(request.path!!.contains("/market/history/candles"))
        assertTrue(request.path!!.contains("symbol=AAPL"))
        assertEquals("AAPL", response.symbol)
        assertEquals("1h", response.interval)
        assertEquals(2, response.items.size)
        assertEquals("181.50", response.items[0].close)
        assertEquals("182.52", response.items[1].close)
        assertEquals(1200000L, response.items[1].volume)
    }

    @Test
    fun `getCandles — empty items handled`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"symbol": "AAPL", "interval": "1h", "items": []}
        """.trimIndent()))

        val response = service.getCandles("AAPL", "2024-01-15T09:00:00Z", "2024-01-15T11:00:00Z")
        assertEquals(0, response.items.size)
    }

    // ── Order Book ───────────────────────────────────────────────────────────

    @Test
    fun `getOrderBook — correct path with depth param, parses bids and asks`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {
                "symbol": "AAPL",
                "bids": [
                    {"price": "182.50", "quantity": 100},
                    {"price": "182.49", "quantity": 200}
                ],
                "asks": [
                    {"price": "182.55", "quantity": 150},
                    {"price": "182.56", "quantity": 300}
                ],
                "bestBid": "182.50",
                "bestAsk": "182.55",
                "spread": "0.05",
                "timestamp": "2024-01-15T10:00:00Z"
            }
        """.trimIndent()))

        val response = service.getOrderBook("AAPL", depth = 5)

        val request = server.takeRequest()
        assertEquals("/market/order-book/AAPL", request.path!!.substringBefore("?"))
        assertTrue(request.path!!.contains("depth=5"))

        assertEquals("AAPL", response.symbol)
        assertEquals(2, response.bids.size)
        assertEquals(2, response.asks.size)
        assertEquals("182.50", response.bids[0].price)
        assertEquals(100L, response.bids[0].quantity)
        assertEquals("182.55", response.asks[0].price)
        assertEquals("182.50", response.bestBid)
        assertEquals("182.55", response.bestAsk)
        assertEquals("0.05", response.spread)
    }

    @Test
    fun `getOrderBook — optional fields absent when null`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"symbol": "NEW", "bids": [], "asks": [], "timestamp": "2024-01-15T10:00:00Z"}
        """.trimIndent()))

        val response = service.getOrderBook("NEW")
        assertNull(response.bestBid)
        assertNull(response.bestAsk)
        assertNull(response.spread)
    }
}
