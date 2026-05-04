package org.awesoma.trumpinvestitions.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.awesoma.trumpinvestitions.TrumpApp
import org.awesoma.trumpinvestitions.data.network.ApiError
import org.awesoma.trumpinvestitions.data.repository.AuthRepository

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TrumpApp

    // Репозиторий создаётся при каждом вызове чтобы всегда использовать актуальный network
    private fun repo() = AuthRepository(app.network.authApiService, app.tokenManager)

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    fun login(login: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            try {
                repo().login(login, password)
                _uiState.value = AuthUiState(isSuccess = true)
            } catch (e: Exception) {
                _uiState.value = AuthUiState(error = ApiError.parse(e))
            }
        }
    }

    fun register(username: String, email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            try {
                repo().register(username, email, password)
                _uiState.value = AuthUiState(isSuccess = true)
            } catch (e: Exception) {
                _uiState.value = AuthUiState(error = ApiError.parse(e))
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repo().logout()
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
