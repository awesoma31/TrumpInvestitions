package com.trumpinvestitions.trading.domain.model

import java.math.BigDecimal
import java.time.LocalDateTime

data class Order(
    val id: String,
    val ticker: String,
    val type: OrderType,
    val side: OrderSide,
    val quantity: Int,
    val price: BigDecimal,
    val status: OrderStatus,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

enum class OrderType {
    MARKET, LIMIT
}

enum class OrderSide {
    BUY, SELL
}

enum class OrderStatus {
    PENDING, FILLED, CANCELED, REJECTED
}