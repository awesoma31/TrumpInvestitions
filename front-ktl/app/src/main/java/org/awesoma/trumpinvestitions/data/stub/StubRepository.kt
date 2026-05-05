package org.awesoma.trumpinvestitions.data.stub

import org.awesoma.trumpinvestitions.data.model.Order
import org.awesoma.trumpinvestitions.data.model.OrderKind
import org.awesoma.trumpinvestitions.data.model.OrderStatus
import org.awesoma.trumpinvestitions.data.model.OrderType
import org.awesoma.trumpinvestitions.data.model.Position
import org.awesoma.trumpinvestitions.data.model.PricePoint
import org.awesoma.trumpinvestitions.data.model.Stock
import org.awesoma.trumpinvestitions.data.model.User

object StubRepository {

    val stocks = listOf(
        Stock("AAPL", "Apple Inc.", 182.50, 1.24, 182.48, 182.52),
        Stock("GOOGL", "Alphabet Inc.", 175.30, -0.53, 175.28, 175.33),
        Stock("MSFT", "Microsoft Corp.", 415.20, 0.87, 415.18, 415.22),
        Stock("AMZN", "Amazon.com Inc.", 220.10, -1.15, 220.08, 220.13),
        Stock("TSLA", "Tesla Inc.", 248.70, 3.42, 248.67, 248.73),
        Stock("NVDA", "NVIDIA Corp.", 875.40, 2.31, 875.37, 875.43),
        Stock("META", "Meta Platforms", 512.80, -0.78, 512.77, 512.83),
        Stock("JPM", "JPMorgan Chase", 198.60, 0.45, 198.58, 198.62),
    )

    fun getStock(symbol: String): Stock? = stocks.find { it.symbol == symbol }

    fun getPriceHistory(symbol: String): List<PricePoint> = listOf(
        PricePoint(1710000000, 178.0),
        PricePoint(1710003600, 179.5),
        PricePoint(1710007200, 181.0),
        PricePoint(1710010800, 180.2),
        PricePoint(1710014400, 182.5),
        PricePoint(1710018000, 183.1),
        PricePoint(1710021600, 182.8),
    )

    val orders = listOf(
        Order("1", "AAPL", OrderType.BUY, OrderKind.MARKET, 10, 180.00, OrderStatus.FILLED, "2025-04-10 09:30"),
        Order("2", "TSLA", OrderType.BUY, OrderKind.MARKET, 5, 245.00, OrderStatus.FILLED, "2025-04-12 11:00"),
        Order("3", "GOOGL", OrderType.SELL, OrderKind.MARKET, 3, 176.00, OrderStatus.CANCELLED, "2025-04-15 14:20"),
        Order("4", "MSFT", OrderType.BUY, OrderKind.LIMIT, 2, 414.00, OrderStatus.NEW, "2025-04-18 10:05"),
        Order("5", "NVDA", OrderType.BUY, OrderKind.MARKET, 1, 870.00, OrderStatus.NEW, "2025-04-19 08:15"),
    )

    val positions = listOf(
        Position("AAPL", "Apple Inc.", 10, 180.00, 182.50),
        Position("TSLA", "Tesla Inc.", 5, 245.00, 248.70),
        Position("MSFT", "Microsoft Corp.", 2, 414.00, 415.20),
    )

    val currentUser = User("u1", "demo_user", 9_234.50)
}
