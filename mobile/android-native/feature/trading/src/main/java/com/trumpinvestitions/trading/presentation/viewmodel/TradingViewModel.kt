package com.trumpinvestitions.trading.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trumpinvestitions.trading.domain.model.Order
import com.trumpinvestitions.trading.domain.model.Quote
import com.trumpinvestitions.trading.domain.usecase.CreateOrderUseCase
import com.trumpinvestitions.trading.domain.usecase.GetQuotesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TradingViewModel @Inject constructor(
    private val getQuotesUseCase: GetQuotesUseCase,
    private val createOrderUseCase: CreateOrderUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(TradingUiState())
    val uiState: StateFlow<TradingUiState> = _uiState.asStateFlow()

    init {
        loadQuotes()
    }

    private fun loadQuotes() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                getQuotesUseCase().collect { quotes ->
                    _uiState.value = _uiState.value.copy(
                        quotes = quotes,
                        isLoading = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun createOrder(order: Order) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isCreatingOrder = true)
                val result = createOrderUseCase(order)
                
                result.fold(
                    onSuccess = { createdOrder ->
                        _uiState.value = _uiState.value.copy(
                            isCreatingOrder = false,
                            orderCreated = true,
                            error = null
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isCreatingOrder = false,
                            error = error.message
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCreatingOrder = false,
                    error = e.message
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun orderCreatedHandled() {
        _uiState.value = _uiState.value.copy(orderCreated = false)
    }
}

data class TradingUiState(
    val quotes: List<Quote> = emptyList(),
    val isLoading: Boolean = false,
    val isCreatingOrder: Boolean = false,
    val orderCreated: Boolean = false,
    val error: String? = null
)