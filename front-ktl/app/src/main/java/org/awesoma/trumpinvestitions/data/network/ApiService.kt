package org.awesoma.trumpinvestitions.data.network

import org.awesoma.trumpinvestitions.data.network.dto.CandleListResponseDto
import org.awesoma.trumpinvestitions.data.network.dto.CreateOrderRequestDto
import org.awesoma.trumpinvestitions.data.network.dto.InstrumentListResponseDto
import org.awesoma.trumpinvestitions.data.network.dto.OrderDto
import org.awesoma.trumpinvestitions.data.network.dto.OrderListResponseDto
import org.awesoma.trumpinvestitions.data.network.dto.OrderBookResponseDto
import org.awesoma.trumpinvestitions.data.network.dto.PortfolioResponseDto
import org.awesoma.trumpinvestitions.data.network.dto.QuoteDto
import org.awesoma.trumpinvestitions.data.network.dto.QuoteListResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @GET("market/instruments")
    suspend fun getInstruments(
        @Query("q") q: String = "",
        @Query("limit") limit: Int = 50
    ): InstrumentListResponseDto

    @GET("market/quotes")
    suspend fun getQuotes(@Query("symbols") symbols: String): QuoteListResponseDto

    @GET("market/quotes/{symbol}")
    suspend fun getQuote(@Path("symbol") symbol: String): QuoteDto

    @GET("market/history/candles")
    suspend fun getCandles(
        @Query("symbol") symbol: String,
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("interval") interval: String = "1h",
        @Query("limit") limit: Int = 50
    ): CandleListResponseDto

    @GET("market/order-book/{symbol}")
    suspend fun getOrderBook(
        @Path("symbol") symbol: String,
        @Query("depth") depth: Int = 5
    ): OrderBookResponseDto

    @POST("orders")
    suspend fun createOrder(@Body order: CreateOrderRequestDto): OrderDto

    @POST("orders/{orderId}/cancel")
    suspend fun cancelOrder(@Path("orderId") orderId: String): OrderDto

    @GET("orders")
    suspend fun getOrders(): OrderListResponseDto

    @GET("portfolio")
    suspend fun getPortfolio(): PortfolioResponseDto
}
