package com.trumpinvestitions.trading.domain.repository

import com.trumpinvestitions.trading.domain.model.Order
import com.trumpinvestitions.trading.domain.model.Quote
import kotlinx.coroutines.flow.Flow

interface TradingRepository {
    suspend fun getQuotes(): Flow<List<Quote>>
    suspend fun getQuote(ticker: String): Flow<Quote>
    suspend fun createOrder(order: Order): Result<Order>
    suspend fun cancelOrder(orderId: String): Result<Unit>
    suspend fun getOrders(): Flow<List<Order>>
    suspend fun getOrder(orderId: String): Flow<Order>
}