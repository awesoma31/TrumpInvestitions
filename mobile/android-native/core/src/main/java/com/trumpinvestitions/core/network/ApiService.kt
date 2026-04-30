package com.trumpinvestitions.core.network

import com.trumpinvestitions.core.network.dto.OrderDto
import com.trumpinvestitions.core.network.dto.QuoteDto
import retrofit2.http.*

interface ApiService {
    
    @GET("quotes")
    suspend fun getQuotes(): List<QuoteDto>
    
    @GET("quotes/{ticker}")
    suspend fun getQuote(@Path("ticker") ticker: String): QuoteDto
    
    @POST("orders")
    suspend fun createOrder(@Body order: OrderDto): OrderDto
    
    @DELETE("orders/{orderId}")
    suspend fun cancelOrder(@Path("orderId") orderId: String)
    
    @GET("orders")
    suspend fun getOrders(): List<OrderDto>
    
    @GET("orders/{orderId}")
    suspend fun getOrder(@Path("orderId") orderId: String): OrderDto
}