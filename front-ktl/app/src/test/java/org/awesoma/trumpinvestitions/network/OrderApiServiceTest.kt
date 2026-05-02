package org.awesoma.trumpinvestitions.network

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.awesoma.trumpinvestitions.data.network.ApiService
import org.awesoma.trumpinvestitions.data.network.dto.CreateOrderRequestDto
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OrderApiServiceTest {

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

    private fun orderJson(
        id: String = "order-1",
        symbol: String = "AAPL",
        side: String = "BUY",
        type: String = "MARKET",
        status: String = "NEW",
        avgFillPrice: String? = null,
        filledQty: Int? = null,
        rejectionReason: String? = null
    ) = buildString {
        append("""{"id":"$id","userId":42,"symbol":"$symbol","side":"$side","type":"$type",""")
        append(""""quantity":10,""")
        if (filledQty != null) append(""""filledQuantity":$filledQty,""")
        if (avgFillPrice != null) append(""""avgFillPrice":"$avgFillPrice",""")
        append(""""status":"$status",""")
        if (rejectionReason != null) append(""""rejectionReason":"$rejectionReason",""")
        append(""""createdAt":"2024-01-15T10:00:00Z","updatedAt":"2024-01-15T10:01:00Z"}""")
    }

    // ── createOrder ──────────────────────────────────────────────────────────

    @Test
    fun `createOrder — POST to correct path with full body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(orderJson()))

        service.createOrder(CreateOrderRequestDto(symbol = "AAPL", side = "BUY", type = "MARKET", quantity = 10))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/orders", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("AAPL"))
        assertTrue(body.contains("BUY"))
        assertTrue(body.contains("MARKET"))
        assertTrue(body.contains("10"))
    }

    @Test
    fun `createOrder — parses NEW order response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(orderJson(id = "ord-42", status = "NEW")))

        val order = service.createOrder(CreateOrderRequestDto("AAPL", "BUY", "MARKET", 10))

        assertEquals("ord-42", order.id)
        assertEquals("AAPL", order.symbol)
        assertEquals("BUY", order.side)
        assertEquals("MARKET", order.type)
        assertEquals("NEW", order.status)
        assertEquals(42L, order.userId)
        assertNull(order.avgFillPrice)
    }

    @Test
    fun `createOrder — parses FILLED order with avgFillPrice`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            orderJson(status = "FILLED", avgFillPrice = "182.52", filledQty = 10)
        ))

        val order = service.createOrder(CreateOrderRequestDto("AAPL", "BUY", "MARKET", 10))

        assertEquals("FILLED", order.status)
        assertEquals("182.52", order.avgFillPrice)
        assertEquals(10, order.filledQuantity)
    }

    @Test
    fun `createOrder — parses REJECTED order with rejectionReason`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            orderJson(status = "REJECTED", rejectionReason = "Insufficient funds")
        ))

        val order = service.createOrder(CreateOrderRequestDto("AAPL", "BUY", "MARKET", 99999))

        assertEquals("REJECTED", order.status)
        assertEquals("Insufficient funds", order.rejectionReason)
    }

    // ── getOrders ────────────────────────────────────────────────────────────

    @Test
    fun `getOrders — GET to correct path, parses list with pagination`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {
                "items": [
                    ${orderJson(id = "ord-1", status = "FILLED", avgFillPrice = "182.00")},
                    ${orderJson(id = "ord-2", status = "NEW")}
                ],
                "total": 2, "limit": 50, "offset": 0
            }
        """.trimIndent()))

        val response = service.getOrders()

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/orders", request.path)
        assertEquals(2, response.items.size)
        assertEquals(2, response.total)
        assertEquals("ord-1", response.items[0].id)
        assertEquals("FILLED", response.items[0].status)
        assertEquals("ord-2", response.items[1].id)
        assertEquals("NEW", response.items[1].status)
    }

    @Test
    fun `getOrders — empty list handled`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"items": [], "total": 0, "limit": 50, "offset": 0}"""
        ))

        val response = service.getOrders()
        assertEquals(0, response.items.size)
    }

    // ── cancelOrder ──────────────────────────────────────────────────────────

    @Test
    fun `cancelOrder — POST to orders-id-cancel path`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            orderJson(id = "ord-5", status = "CANCELLED")
        ))

        val order = service.cancelOrder("ord-5")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/orders/ord-5/cancel", request.path)
        assertEquals("ord-5", order.id)
        assertEquals("CANCELLED", order.status)
    }

    // ── SELL order ───────────────────────────────────────────────────────────

    @Test
    fun `createOrder SELL — side field correctly serialized`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(orderJson(side = "SELL")))

        service.createOrder(CreateOrderRequestDto("TSLA", "SELL", "MARKET", 5))

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("SELL"))
    }

    @Test
    fun `createOrder LIMIT — type field correctly serialized`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(orderJson(type = "LIMIT")))

        service.createOrder(CreateOrderRequestDto("MSFT", "BUY", "LIMIT", 3))

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("LIMIT"))
    }
}
