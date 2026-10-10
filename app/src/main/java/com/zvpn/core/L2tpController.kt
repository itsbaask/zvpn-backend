package com.zvpn.core

import android.os.ParcelFileDescriptor
import com.zvpn.core.AppLog as Log
import com.zvpn.domain.config.L2tpProfile
import io.github.mr1ve3r.combined.engine.EngineProfile
import io.github.mr1ve3r.combined.engine.EngineState
import io.github.mr1ve3r.combined.engine.SocketProtector
import io.github.mr1ve3r.combined.engine.TunnelParams
import io.github.mr1ve3r.combined.engine.l2tp.JniL2tpNative
import io.github.mr1ve3r.combined.engine.l2tp.L2tpEngine
import kotlinx.coroutines.flow.StateFlow

class L2tpController(
    private val profile: L2tpProfile,
) {
    private val engine = L2tpEngine(native = JniL2tpNative())

    val state: StateFlow<EngineState> get() = engine.state
    val events get() = engine.events

    suspend fun connect(protector: SocketProtector): TunnelParams {
        Log.i(TAG, "Starting L2TP/IPsec session for ${profile.server}")
        val engineProfile = EngineProfile.L2tp(
            server = profile.server,
            username = profile.username,
            password = profile.password,
            mtu = profile.mtu,
            customDns = emptyList(),
            ipsecEnabled = profile.presharedKey.isNotBlank(),
            presharedKey = profile.presharedKey,
            localIdentifier = null,
            phase1Proposals = emptyList(),
            phase2Proposals = emptyList(),
        )
        return engine.connect(engineProfile, protector)
    }

    fun attachTun(fd: ParcelFileDescriptor) {
        engine.attachTun(fd)
    }

    suspend fun stop() {
        Log.i(TAG, "Stopping L2TP/IPsec session")
        runCatching { engine.disconnect() }
    }

    companion object {
        private const val TAG = "L2tpController"
    }
}
