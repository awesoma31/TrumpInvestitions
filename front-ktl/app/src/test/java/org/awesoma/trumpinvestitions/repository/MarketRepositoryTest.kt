package org.awesoma.trumpinvestitions.repository

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.awesoma.trumpinvestitions.data.network.ApiService
import org.awesoma.trumpinvestitions.data.repository.MarketRepository
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarketRepositoryTest {

    private val server = MockWebServer()
    private lateinit var apiService: ApiService
    private lateinit var repository: MarketRepository

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
        repository = MarketRepository(apiService)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `getStocksFlow — maps quote fields to Stock correctly`() = runTest {
        // instruments response (with name)
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"items": [{"symbol": "AAPL", "name": "Apple Inc.", "currency": "USD", "lotSize": 1, "active": true}], "total": 1}
        """.trimIndent()))
        // quotes response
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"items": [{"symbol": "AAPL", "bid": "182.50", "ask": "182.55", "last": "185.00", "open": "180.00", "timestamp": "2024-01-15T10:00:00Z"}]}
        """.trimIndent()))

        val stocks = repository.getStocksFlow().first()

        assertEquals(1, stocks.size)
        val stock = stocks[0]
        assertEquals("AAPL", stock.symbol)
        assertEquals("Apple Inc.", stock.name)
        assertEquals(185.0, stock.price)
        assertEquals(182.50, stock.highestBid)
        assertEquals(182.55, stock.lowestAsk)
    }

    @Test
    fun `getStocksFlow — change percent computed from open and last`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"items": [], "total": 0}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"items": [{"symbol": "TSLA", "bid": "240.00", "ask": "241.00", "last": "210.00", "open": "200.00", "timestamp": "2024-01-15T10:00:00Z"}]}
        """.trimIndent()))

        val stocks = repository.getStocksFlow().first()

        assertEquals(1, stocks.size)
        // change = (210 - 200) / 200 * 100 = 5.0%
        assertEquals(5.0, stocks[0].changePercent, 0.001)
    }

    @Test
    fun `getStocksFlow — negative change percent when price drops`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"items": [], "total": 0}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"items": [{"symbol": "NVDA", "bid": "490.00", "ask": "491.00", "last": "475.00", "open": "500.00", "timestamp": "2024-01-15T10:00:00Z"}]}
        """.trimIndent()))

        val stocks = repository.getStocksFlow().first()

        assertTrue(stocks[0].changePercent < 0, "Change percent should be negative when price drops")
        assertEquals(-5.0, stocks[0].changePercent, 0.001)
    }

    @Test
    fun `getStocksFlow — uses symbol as name when instrument not found`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"items": [], "total": 0}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"items": [{"symbol": "UNKNOWN", "bid": "10.00", "ask": "10.05", "last": "10.00", "timestamp": "2024-01-15T10:00:00Z"}]}
        """.trimIndent()))

        val stocks = repository.getStocksFlow().first()

        assertEquals("UNKNOWN", stocks[0].name)
    }

    @Test
    fun `getStocksFlow — zero change percent when open is missing`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"items": [], "total": 0}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"items": [{"symbol": "BTC", "bid": "50000.00", "ask": "50001.00", "last": "50000.00", "timestamp": "2024-01-15T10:00:00Z"}]}
        """.trimIndent()))

        val stocks = repository.getStocksFlow().first()

        // When open == last, change% = 0
        assertEquals(0.0, stocks[0].changePercent, 0.001)
    }

    @Test
    fun `getCandles — parses timestamps to epoch seconds`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {
                "symbol": "AAPL", "interval": "1h",
                "items": [
                    {"timestamp": "2024-01-15T09:00:00Z", "open": "180.00", "high": "182.00", "low": "179.50", "close": "181.50", "volume": 1000000},
                    {"timestamp": "2024-01-15T10:00:00Z", "open": "181.50", "high": "183.00", "low": "181.00", "close": "182.52", "volume": 1200000}
                ]
            }
        """.trimIndent()))

        val candles = repository.getCandles("AAPL")

        assertEquals(2, candles.size)
        assertEquals(181.50, candles[0].close, 0.001)
        assertEquals(182.52, candles[1].close, 0.001)
        assertEquals(180.00, candles[0].open,  0.001)
        assertEquals(182.00, candles[0].high,  0.001)
        assertEquals(179.50, candles[0].low,   0.001)
        assertTrue(candles[0].timestamp > 0L, "Timestamp should be a positive epoch value")
        assertTrue(candles[1].timestamp > candles[0].timestamp, "Later candle should have larger timestamp")
    }

    @Test
    fun `getCandles — returns empty list when API fails`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val candles = repository.getCandles("AAPL")
        assertEquals(0, candles.size)
    }

    @Test
    fun `getOrderBook — returns null when API fails`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val result = repository.getOrderBook("UNKNOWN")
        assertEquals(null, result)
    }
}
