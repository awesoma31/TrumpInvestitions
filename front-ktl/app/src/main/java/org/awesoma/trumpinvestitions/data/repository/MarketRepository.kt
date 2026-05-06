package org.awesoma.trumpinvestitions.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.awesoma.trumpinvestitions.data.model.Candle
import org.awesoma.trumpinvestitions.data.model.Stock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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

    suspend fun getCandles(
        symbol: String,
        interval: String = "1h",
        windowSeconds: Long = 24 * 3600L,
        limit: Int = 200
    ): List<Candle> {
        val nowMs  = System.currentTimeMillis()
        val to     = toIso8601(nowMs)
        val from   = toIso8601(nowMs - windowSeconds * 1000L)
        return try {
            apiService.getCandles(
                symbol   = symbol,
                from     = from,
                to       = to,
                interval = interval,
                limit    = limit
            ).items.map { dto ->
                Candle(
                    timestamp = parseIso8601ToEpochSeconds(dto.timestamp),
                    open      = dto.open.toDoubleOrNull()  ?: 0.0,
                    high      = dto.high.toDoubleOrNull()  ?: 0.0,
                    low       = dto.low.toDoubleOrNull()   ?: 0.0,
                    close     = dto.close.toDoubleOrNull() ?: 0.0,
                    volume    = dto.volume
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    companion object {
        private val ISO_FMT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        // Server may return fractional seconds: "2024-01-15T09:00:00.123Z"
        private val ISO_FMT_MS = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        fun toIso8601(epochMs: Long): String = ISO_FMT.format(Date(epochMs))

        fun parseIso8601ToEpochSeconds(iso: String): Long = try {
            val fmt = if (iso.length > 20) ISO_FMT_MS else ISO_FMT
            (fmt.parse(iso)?.time ?: 0L) / 1000L
        } catch (_: Exception) { 0L }
    }

    suspend fun getOrderBook(symbol: String): OrderBookResponseDto? = try {
        apiService.getOrderBook(symbol, depth = 5)
    } catch (_: Exception) { null }
}
