package org.awesoma.trumpinvestitions.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.awesoma.trumpinvestitions.TrumpApp
import org.awesoma.trumpinvestitions.data.repository.AuthRepository
import org.awesoma.trumpinvestitions.data.repository.PortfolioRepository

data class ProfileUiState(
    val username: String = "",
    val cashBalance: Double = 0.0,
    val isLoading: Boolean = true
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TrumpApp
    private val tokenManager = app.tokenManager
    private val portfolioRepository = PortfolioRepository(app.network.apiService)
    private val authRepository = AuthRepository(app.network.authApiService, tokenManager)

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState

    init {
        viewModelScope.launch {
            val username = tokenManager.getUsername()
            _uiState.value = _uiState.value.copy(username = username)
        }
        viewModelScope.launch {
            portfolioRepository.getPortfolioFlow().collect { state ->
                _uiState.value = _uiState.value.copy(
                    cashBalance = state.cashBalance,
                    isLoading = false
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }
}
