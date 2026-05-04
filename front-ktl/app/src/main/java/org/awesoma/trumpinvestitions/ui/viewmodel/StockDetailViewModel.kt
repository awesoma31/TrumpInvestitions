package org.awesoma.trumpinvestitions.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.awesoma.trumpinvestitions.TrumpApp
import org.awesoma.trumpinvestitions.data.model.PricePoint
import org.awesoma.trumpinvestitions.data.model.Stock
import org.awesoma.trumpinvestitions.data.network.ApiError
import org.awesoma.trumpinvestitions.data.network.dto.OrderBookResponseDto
import org.awesoma.trumpinvestitions.data.repository.MarketRepository

sealed class OrderEvent {
    data class Success(val message: String) : OrderEvent()
    data class Error(val message: String) : OrderEvent()
}

class StockDetailViewModel(
    application: Application,
    private val symbol: String
) : AndroidViewModel(application) {

    private val app = application as TrumpApp
    private val marketRepository get() = MarketRepository(app.network.apiService)
    private val apiService get() = app.network.apiService

    private val _stock = MutableStateFlow<Stock?>(null)
    val stock: StateFlow<Stock?> = _stock

    private val _candles = MutableStateFlow<List<PricePoint>>(emptyList())
    val candles: StateFlow<List<PricePoint>> = _candles

    private val _orderBook = MutableStateFlow<OrderBookResponseDto?>(null)
    val orderBook: StateFlow<OrderBookResponseDto?> = _orderBook

    private val _orderEvent = MutableStateFlow<OrderEvent?>(null)
    val orderEvent: StateFlow<OrderEvent?> = _orderEvent

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                val quote = apiService.getQuote(symbol)
                val price = quote.last.toDoubleOrNull() ?: 0.0
                val open = quote.open?.toDoubleOrNull() ?: price
                val change = if (open > 0) (price - open) / open * 100 else 0.0
                _stock.value = Stock(
                    symbol = quote.symbol,
                    name = quote.symbol,
                    price = price,
                    changePercent = change,
                    highestBid = quote.bid.toDoubleOrNull() ?: price,
                    lowestAsk = quote.ask.toDoubleOrNull() ?: price
                )
            } catch (_: Exception) {}

            _candles.value = marketRepository.getCandles(symbol)
            _orderBook.value = marketRepository.getOrderBook(symbol)
        }
    }

    fun placeOrder(side: String, quantity: Int) {
        viewModelScope.launch {
            try {
                val order = apiService.createOrder(
                    org.awesoma.trumpinvestitions.data.network.dto.CreateOrderRequestDto(
                        symbol = symbol,
                        side = side,
                        type = "MARKET",
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

    fun clearOrderEvent() {
        _orderEvent.value = null
    }

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
