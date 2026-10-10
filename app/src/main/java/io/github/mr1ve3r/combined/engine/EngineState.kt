package io.github.mr1ve3r.combined.engine

sealed interface EngineState {
    data object Idle : EngineState

    data class Connecting(val stage: String) : EngineState

    data class Connected(
        val params: TunnelParams,
        val since: Long,
    ) : EngineState

    data class Reconnecting(
        val attempt: Int,
        val cause: EngineError,
    ) : EngineState

    data class Failed(val error: EngineError) : EngineState

    data object Disconnected : EngineState
}
