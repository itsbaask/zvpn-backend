package io.github.mr1ve3r.combined.engine

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface VpnEngine {
    val state: StateFlow<EngineState>
    val events: SharedFlow<EngineLogEvent>

    suspend fun connect(profile: EngineProfile, protector: SocketProtector): TunnelParams
    fun attachTun(fd: ParcelFileDescriptor)
    suspend fun disconnect()
}

class EngineException(
    val error: EngineError,
    cause: Throwable? = null,
) : Exception(error.detail ?: error.messageKey, cause)
