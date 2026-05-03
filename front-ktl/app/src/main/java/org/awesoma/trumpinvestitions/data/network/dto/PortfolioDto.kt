package org.awesoma.trumpinvestitions.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class DepositRequestDto(val amount: String)

@Serializable
data class BalanceResponseDto(
    val userId: Long = 0,
    val balance: String = "0",
    val currency: String = "",
    val updatedAt: String = ""
)

@Serializable
data class PositionResponseDto(
    val symbol: String = "",
    val quantity: Int = 0,
    val avgPrice: String = "0",
    val currentPrice: String = "0",
    val marketValue: String = "0",
    val realizedPnl: String = "0",
    val unrealizedPnl: String = "0",
    val totalPnl: String = "0",
    val currency: String = "",
    val updatedAt: String = ""
)

@Serializable
data class PositionListResponseDto(
    val items: List<PositionResponseDto> = emptyList()
)

@Serializable
data class PortfolioResponseDto(
    val userId: Long = 0,
    val cashBalance: String = "0",
    val totalMarketValue: String = "0",
    val totalEquity: String = "0",
    val realizedPnl: String = "0",
    val unrealizedPnl: String = "0",
    val totalPnl: String = "0",
    val positions: List<PositionResponseDto> = emptyList(),
    val updatedAt: String = ""
)
