package org.awesoma.trumpinvestitions.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.awesoma.trumpinvestitions.data.model.Order
import org.awesoma.trumpinvestitions.data.model.OrderKind
import org.awesoma.trumpinvestitions.data.model.OrderStatus
import org.awesoma.trumpinvestitions.data.model.OrderType
import org.awesoma.trumpinvestitions.data.model.Position
import org.awesoma.trumpinvestitions.data.network.ApiService
import org.awesoma.trumpinvestitions.data.network.dto.PortfolioResponseDto

data class PortfolioState(
    val cashBalance: Double,
    val totalPnl: Double,
    val positions: List<Position>,
    val orders: List<Order>
)

class PortfolioRepository(private val apiService: ApiService) {

    fun getPortfolioFlow(): Flow<PortfolioState> = flow {
        while (true) {
            try {
                val portfolio = apiService.getPortfolio()
                val orders = apiService.getOrders().items

                val positions = portfolio.positions.map { p ->
                    Position(
                        symbol = p.symbol,
                        name = p.symbol,
                        quantity = p.quantity,
                        avgBuyPrice = p.avgPrice.toDoubleOrNull() ?: 0.0,
                        currentPrice = p.currentPrice.toDoubleOrNull() ?: 0.0
                    )
                }

                val domainOrders = orders.map { o ->
                    Order(
                        id = o.id,
                        symbol = o.symbol,
                        type = if (o.side.uppercase() == "BUY") OrderType.BUY else OrderType.SELL,
                        orderKind = if (o.type.uppercase() == "LIMIT") OrderKind.LIMIT else OrderKind.MARKET,
                        quantity = o.quantity,
                        price = o.avgFillPrice?.toDoubleOrNull() ?: 0.0,
                        status = when (o.status.uppercase()) {
                            "FILLED" -> OrderStatus.FILLED
                            "CANCELLED" -> OrderStatus.CANCELLED
                            "REJECTED" -> OrderStatus.REJECTED
                            else -> OrderStatus.NEW
                        },
                        createdAt = o.createdAt
                    )
                }

                emit(
                    PortfolioState(
                        cashBalance = portfolio.cashBalance.toDoubleOrNull() ?: 0.0,
                        totalPnl = portfolio.totalPnl.toDoubleOrNull() ?: 0.0,
                        positions = positions,
                        orders = domainOrders
                    )
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
            delay(10_000L)
        }
    }
}
