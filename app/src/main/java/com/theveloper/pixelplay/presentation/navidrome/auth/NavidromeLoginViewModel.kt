package com.theveloper.pixelplay.presentation.navidrome.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.navidrome.NavidromeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import javax.inject.Inject

sealed interface NavidromeLoginState {
    data object Idle : NavidromeLoginState
    data object Loading : NavidromeLoginState
    data class Success(val username: String) : NavidromeLoginState
    data class Error(val message: String) : NavidromeLoginState
}

@HiltViewModel
class NavidromeLoginViewModel @Inject constructor(
    private val repository: NavidromeRepository,
    private val userPreferencesRepository: com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
) : ViewModel() {

    private val _state = MutableStateFlow<NavidromeLoginState>(NavidromeLoginState.Idle)
    val state: StateFlow<NavidromeLoginState> = _state.asStateFlow()

    fun login(serverUrl: String, username: String, password: String) {
        if (_state.value is NavidromeLoginState.Loading) return

        viewModelScope.launch {
            _state.value = NavidromeLoginState.Loading

            val result = repository.login(serverUrl, username, password)
            if (result.isSuccess) suggestCoverAddress()

            _state.value = result.fold(
                onSuccess = { NavidromeLoginState.Success(it) },
                onFailure = { NavidromeLoginState.Error(it.message ?: "Login failed") }
            )
        }
    }

    /** Fills in an empty field only: an address somebody saved themselves stays. */
    private suspend fun suggestCoverAddress() {
        if (userPreferencesRepository.sonosCoverAddressFlow.first().isNotBlank()) return
        com.theveloper.pixelplay.data.sonos.SonosTrackResolver.suggestCoverAddress(repository.serverUrl)
            ?.let { userPreferencesRepository.setSonosCoverAddress(it) }
    }

    fun clearError() {
        if (_state.value is NavidromeLoginState.Error) {
            _state.value = NavidromeLoginState.Idle
        }
    }

    fun reset() {
        _state.value = NavidromeLoginState.Idle
    }
}
