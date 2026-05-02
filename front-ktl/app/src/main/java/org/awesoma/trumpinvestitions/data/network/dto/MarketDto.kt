package org.awesoma.trumpinvestitions.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class InstrumentDto(
    val symbol: String,
    val name: String,
    val currency: String = "USD",
    val lotSize: Int = 1,
    val active: Boolean = true
)

@Serializable
data class InstrumentListResponseDto(
    val items: List<InstrumentDto>,
    val total: Int = 0
)

@Serializable
data class CandleDto(
    val timestamp: String,
    val open: String,
    val high: String,
    val low: String,
    val close: String,
    val volume: Long
)

@Serializable
data class CandleListResponseDto(
    val symbol: String,
    val interval: String,
    val items: List<CandleDto>
)

@Serializable
data class OrderBookLevelDto(
    val price: String,
    val quantity: Long
)

@Serializable
data class OrderBookResponseDto(
    val symbol: String,
    val bids: List<OrderBookLevelDto>,
    val asks: List<OrderBookLevelDto>,
    val bestBid: String? = null,
    val bestAsk: String? = null,
    val spread: String? = null,
    val timestamp: String
)
