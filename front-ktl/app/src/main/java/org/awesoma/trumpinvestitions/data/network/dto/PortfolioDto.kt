package org.awesoma.trumpinvestitions.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class PositionResponseDto(
    val symbol: String,
    val quantity: Int,
    val avgPrice: String,
    val currentPrice: String,
    val marketValue: String,
    val realizedPnl: String,
    val unrealizedPnl: String,
    val totalPnl: String,
    val currency: String,
    val updatedAt: String
)

@Serializable
data class PositionListResponseDto(
    val items: List<PositionResponseDto>
)

@Serializable
data class PortfolioResponseDto(
    val userId: Long,
    val cashBalance: String,
    val totalMarketValue: String,
    val totalEquity: String,
    val realizedPnl: String,
    val unrealizedPnl: String,
    val totalPnl: String,
    val positions: List<PositionResponseDto>,
    val updatedAt: String
)
