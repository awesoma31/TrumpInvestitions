package org.awesoma.trumpinvestitions.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.awesoma.trumpinvestitions.data.model.PricePoint
import org.awesoma.trumpinvestitions.data.model.Stock
import org.awesoma.trumpinvestitions.data.network.ApiService
import org.awesoma.trumpinvestitions.data.network.dto.OrderBookResponseDto

class MarketRepository(private val apiService: ApiService) {

    private val SYMBOLS = "BTCUSDT,AAPL,ETHUSDT,MSFT,TSLA"

    fun getStocksFlow(): Flow<List<Stock>> = flow {
        val instruments = try {
            apiService.getInstruments(limit = 50).items.associateBy { it.symbol }
        } catch (_: Exception) { emptyMap() }

        while (true) {
            try {
                val quotes = apiService.getQuotes(SYMBOLS).items
                val stocks = quotes.map { q ->
                    val instrument = instruments[q.symbol]
                    val price = q.last.toDoubleOrNull() ?: 0.0
                    val open = q.open?.toDoubleOrNull() ?: price
                    val change = if (open > 0) (price - open) / open * 100 else 0.0
                    Stock(
                        symbol = q.symbol,
                        name = instrument?.name ?: q.symbol,
                        price = price,
                        changePercent = change,
                        highestBid = q.bid.toDoubleOrNull() ?: price,
                        lowestAsk = q.ask.toDoubleOrNull() ?: price
                    )
                }
                emit(stocks)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
            delay(5_000L)
        }
    }

    suspend fun getCandles(symbol: String): List<PricePoint> {
        val to = java.time.Instant.now().toString()
        val from = java.time.Instant.now().minusSeconds(6 * 3600).toString()
        return try {
            apiService.getCandles(symbol = symbol, from = from, to = to, interval = "5m", limit = 50)
                .items.map { candle ->
                    val epoch = try {
                        java.time.Instant.parse(candle.timestamp).epochSecond
                    } catch (_: Exception) { 0L }
                    PricePoint(epoch, candle.close.toDoubleOrNull() ?: 0.0)
                }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun getOrderBook(symbol: String): OrderBookResponseDto? = try {
        apiService.getOrderBook(symbol, depth = 5)
    } catch (_: Exception) { null }
}
