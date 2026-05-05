package org.awesoma.trumpinvestitions.data.model

data class Stock(
    val symbol: String,
    val name: String,
    val price: Double,
    val changePercent: Double,
    val highestBid: Double,
    val lowestAsk: Double
)

data class Order(
    val id: String,
    val symbol: String,
    val type: OrderType,      // BUY or SELL (maps from backend "side")
    val orderKind: OrderKind, // MARKET or LIMIT (maps from backend "type")
    val quantity: Int,
    val price: Double,
    val status: OrderStatus,
    val createdAt: String
)

enum class OrderType { BUY, SELL }

enum class OrderKind { MARKET, LIMIT }

enum class OrderStatus { NEW, FILLED, CANCELLED, REJECTED }

data class Position(
    val symbol: String,
    val name: String,
    val quantity: Int,
    val avgBuyPrice: Double,
    val currentPrice: Double
) {
    val pnl: Double get() = (currentPrice - avgBuyPrice) * quantity
    val pnlPercent: Double get() = if (avgBuyPrice > 0) (currentPrice - avgBuyPrice) / avgBuyPrice * 100 else 0.0
}

data class User(
    val id: String,
    val username: String,
    val balance: Double
)

data class PricePoint(
    val timestamp: Long,
    val price: Double
)
