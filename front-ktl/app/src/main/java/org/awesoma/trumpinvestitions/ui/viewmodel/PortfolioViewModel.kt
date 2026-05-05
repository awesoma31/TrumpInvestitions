package org.awesoma.trumpinvestitions.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.awesoma.trumpinvestitions.TrumpApp
import org.awesoma.trumpinvestitions.data.repository.PortfolioRepository
import org.awesoma.trumpinvestitions.data.repository.PortfolioState

class PortfolioViewModel(application: Application) : AndroidViewModel(application) {

    private val portfolioRepository = PortfolioRepository((application as TrumpApp).network.apiService)

    private val _state = MutableStateFlow<PortfolioState?>(null)
    val state: StateFlow<PortfolioState?> = _state

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        viewModelScope.launch {
            portfolioRepository.getPortfolioFlow().collect { portfolioState ->
                _state.value = portfolioState
                _isLoading.value = false
            }
        }
    }
}
