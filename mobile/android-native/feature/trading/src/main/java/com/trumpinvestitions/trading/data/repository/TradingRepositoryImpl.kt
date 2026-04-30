package com.trumpinvestitions.trading.data.repository

import com.trumpinvestitions.core.network.ApiService
import com.trumpinvestitions.core.network.WebSocketService
import com.trumpinvestitions.core.network.dto.OrderDto
import com.trumpinvestitions.core.network.dto.QuoteDto
import com.trumpinvestitions.trading.data.mapper.toDomain
import com.trumpinvestitions.trading.data.mapper.toDto
import com.trumpinvestitions.trading.domain.model.Order
import com.trumpinvestitions.trading.domain.model.Quote
import com.trumpinvestitions.trading.domain.repository.TradingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TradingRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val webSocketService: WebSocketService
) : TradingRepository {

    override suspend fun getQuotes(): Flow<List<Quote>> {
        // В реальном приложении здесь будет комбинация из REST API и WebSocket
        // Для примера используем WebSocket для реального времени
        return webSocketService.quotesFlow.map { quoteDto ->
            listOf(quoteDto.toDomain())
        }
    }

    override suspend fun getQuote(ticker: String): Flow<Quote> {
        return webSocketService.quotesFlow.map { quoteDto ->
            if (quoteDto.ticker == ticker) {
                quoteDto.toDomain()
            } else {
                throw IllegalArgumentException("Quote not found for ticker: $ticker")
            }
        }
    }

    override suspend fun createOrder(order: Order): Result<Order> {
        return try {
            val orderDto = order.toDto()
            val createdOrderDto = apiService.createOrder(orderDto)
            Result.success(createdOrderDto.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun cancelOrder(orderId: String): Result<Unit> {
        return try {
            apiService.cancelOrder(orderId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getOrders(): Flow<List<Order>> {
        // В реальном приложении здесь будет реализация с кэшированием
        // и обновлением через WebSocket
        return kotlinx.coroutines.flow.flow {
            try {
                val ordersDto = apiService.getOrders()
                emit(ordersDto.map { it.toDomain() })
            } catch (e: Exception) {
                // Обработка ошибки
                emit(emptyList())
            }
        }
    }

    override suspend fun getOrder(orderId: String): Flow<Order> {
        return kotlinx.coroutines.flow.flow {
            try {
                val orderDto = apiService.getOrder(orderId)
                emit(orderDto.toDomain())
            } catch (e: Exception) {
                throw e
            }
        }
    }
}