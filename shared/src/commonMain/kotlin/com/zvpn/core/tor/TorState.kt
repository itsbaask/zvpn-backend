package com.zvpn.core.tor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface TorServiceState {
    data object Stopped : TorServiceState
    data class Connecting(val progress: Int) : TorServiceState
    data object Connected : TorServiceState
    data class Error(val reason: String) : TorServiceState
}

object TorStateHolder {
    private val _state = MutableStateFlow<TorServiceState>(TorServiceState.Stopped)
    val state: StateFlow<TorServiceState> = _state.asStateFlow()

    fun update(newState: TorServiceState) {
        _state.value = newState
    }
}
