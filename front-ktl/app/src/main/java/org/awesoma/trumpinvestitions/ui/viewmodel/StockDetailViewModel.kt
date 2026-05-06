package org.awesoma.trumpinvestitions.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.awesoma.trumpinvestitions.TrumpApp
import org.awesoma.trumpinvestitions.data.model.Candle
import org.awesoma.trumpinvestitions.data.model.Stock
import org.awesoma.trumpinvestitions.data.network.ApiError
import org.awesoma.trumpinvestitions.data.network.dto.OrderBookResponseDto
import org.awesoma.trumpinvestitions.data.repository.MarketRepository

// ── Time frame selector ──────────────────────────────────────────────────────

enum class TimeFrame(
    val label: String,
    val interval: String,
    val windowSeconds: Long,
    val limit: Int
) {
    H1 ("1Ч",  "1m",  1 * 3600L,    60),
    H4 ("4Ч",  "5m",  4 * 3600L,    48),
    D1 ("1Д",  "15m", 24 * 3600L,   96),
    W1 ("1Н",  "1h",  7 * 24 * 3600L, 168),
    M1 ("1М",  "1d",  30 * 24 * 3600L, 30);
}

// ── Order events ─────────────────────────────────────────────────────────────

sealed class OrderEvent {
    data class Success(val message: String) : OrderEvent()
    data class Error(val message: String)   : OrderEvent()
}

// ── ViewModel ────────────────────────────────────────────────────────────────

class StockDetailViewModel(
    application: Application,
    private val symbol: String
) : AndroidViewModel(application) {

    private val app            = application as TrumpApp
    private val marketRepository get() = MarketRepository(app.network.apiService)
    private val apiService       get() = app.network.apiService

    private val _stock          = MutableStateFlow<Stock?>(null)
    val stock: StateFlow<Stock?> = _stock

    private val _candles              = MutableStateFlow<List<Candle>>(emptyList())
    val candles: StateFlow<List<Candle>> = _candles

    private val _selectedTimeFrame              = MutableStateFlow(TimeFrame.H1)
    val selectedTimeFrame: StateFlow<TimeFrame> = _selectedTimeFrame

    private val _orderBook              = MutableStateFlow<OrderBookResponseDto?>(null)
    val orderBook: StateFlow<OrderBookResponseDto?> = _orderBook

    private val _orderEvent              = MutableStateFlow<OrderEvent?>(null)
    val orderEvent: StateFlow<OrderEvent?> = _orderEvent

    private var candlePollingJob: Job? = null
    private var quotePollingJob: Job?  = null

    init {
        loadStaticData()
        startPolling(_selectedTimeFrame.value)
    }

    /** Load once: stock quote + order book */
    private fun loadStaticData() {
        viewModelScope.launch {
            try {
                val quote = apiService.getQuote(symbol)
                val price = quote.last.toDoubleOrNull() ?: 0.0
                val open  = quote.open?.toDoubleOrNull() ?: price
                val change = if (open > 0) (price - open) / open * 100 else 0.0
                _stock.value = Stock(
                    symbol       = quote.symbol,
                    name         = quote.symbol,
                    price        = price,
                    changePercent = change,
                    highestBid   = quote.bid.toDoubleOrNull() ?: price,
                    lowestAsk    = quote.ask.toDoubleOrNull() ?: price
                )
            } catch (_: Exception) {}

            _orderBook.value = marketRepository.getOrderBook(symbol)
        }
    }

    /** Switch time frame and restart candle polling */
    fun selectTimeFrame(tf: TimeFrame) {
        if (_selectedTimeFrame.value == tf) return
        _selectedTimeFrame.value = tf
        _candles.value = emptyList()
        startPolling(tf)
    }

    private fun startPolling(tf: TimeFrame) {
        candlePollingJob?.cancel()
        candlePollingJob = viewModelScope.launch {
            while (true) {
                val fresh = marketRepository.getCandles(
                    symbol        = symbol,
                    interval      = tf.interval,
                    windowSeconds = tf.windowSeconds,
                    limit         = tf.limit
                )
                if (fresh.isNotEmpty()) _candles.value = fresh

                // also refresh live price
                try {
                    val quote = apiService.getQuote(symbol)
                    val price = quote.last.toDoubleOrNull() ?: return@launch
                    val open  = quote.open?.toDoubleOrNull() ?: price
                    val change = if (open > 0) (price - open) / open * 100 else 0.0
                    _stock.value = Stock(
                        symbol        = quote.symbol,
                        name          = quote.symbol,
                        price         = price,
                        changePercent = change,
                        highestBid    = quote.bid.toDoubleOrNull() ?: price,
                        lowestAsk     = quote.ask.toDoubleOrNull() ?: price
                    )
                } catch (_: Exception) {}

                delay(5_000L)
            }
        }
    }

    fun placeOrder(side: String, quantity: Int) {
        viewModelScope.launch {
            try {
                apiService.createOrder(
                    org.awesoma.trumpinvestitions.data.network.dto.CreateOrderRequestDto(
                        symbol   = symbol,
                        side     = side,
                        type     = "MARKET",
                        quantity = quantity
                    )
                )
                val action = if (side == "BUY") "Куплено" else "Продано"
                _orderEvent.value = OrderEvent.Success("$action $quantity акций $symbol")
            } catch (e: Exception) {
                _orderEvent.value = OrderEvent.Error(ApiError.parse(e))
            }
        }
    }

    fun clearOrderEvent() { _orderEvent.value = null }

    companion object {
        fun factory(symbol: String) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                @Suppress("UNCHECKED_CAST")
                return StockDetailViewModel(app, symbol) as T
            }
        }
    }
}
