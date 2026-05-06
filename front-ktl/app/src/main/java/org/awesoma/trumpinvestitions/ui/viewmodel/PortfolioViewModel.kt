package org.awesoma.trumpinvestitions.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.awesoma.trumpinvestitions.TrumpApp
import org.awesoma.trumpinvestitions.data.network.ApiError
import org.awesoma.trumpinvestitions.data.network.dto.DepositRequestDto
import org.awesoma.trumpinvestitions.data.repository.PortfolioRepository
import org.awesoma.trumpinvestitions.data.repository.PortfolioState

class PortfolioViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TrumpApp
    private val portfolioRepository get() = PortfolioRepository(app.network.apiService)

    private val _state = MutableStateFlow<PortfolioState?>(null)
    val state: StateFlow<PortfolioState?> = _state

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _depositError = MutableStateFlow<String?>(null)
    val depositError: StateFlow<String?> = _depositError

    init {
        viewModelScope.launch {
            portfolioRepository.getPortfolioFlow().collect { portfolioState ->
                _state.value = portfolioState
                _isLoading.value = false
            }
        }
    }

    fun deposit(amount: Double) {
        viewModelScope.launch {
            try {
                val result = app.network.apiService.deposit(DepositRequestDto(String.format(java.util.Locale.US, "%.2f", amount)))
                _state.value = _state.value?.copy(cashBalance = result.balance.toDoubleOrNull() ?: 0.0)
            } catch (e: Exception) {
                _depositError.value = ApiError.parse(e)
            }
        }
    }

    fun withdraw(amount: Double) {
        viewModelScope.launch {
            try {
                val result = app.network.apiService.withdraw(DepositRequestDto(String.format(java.util.Locale.US, "%.2f", amount)))
                _state.value = _state.value?.copy(cashBalance = result.balance.toDoubleOrNull() ?: 0.0)
            } catch (e: Exception) {
                _depositError.value = ApiError.parse(e)
            }
        }
    }

    fun clearDepositError() { _depositError.value = null }
}
