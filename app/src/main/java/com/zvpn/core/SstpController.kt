package com.zvpn.core

import android.os.ParcelFileDescriptor
import com.zvpn.core.AppLog as Log
import com.zvpn.domain.config.SstpProfile
import io.github.mr1ve3r.combined.engine.EngineProfile
import io.github.mr1ve3r.combined.engine.EngineState
import io.github.mr1ve3r.combined.engine.SocketProtector
import io.github.mr1ve3r.combined.engine.TlsVersion
import io.github.mr1ve3r.combined.engine.TrustPolicy
import io.github.mr1ve3r.combined.engine.TunnelParams
import io.github.mr1ve3r.combined.engine.sstp.SstpEngine
import kotlinx.coroutines.flow.StateFlow

class SstpController(
    private val profile: SstpProfile,
) {
    private val engine = SstpEngine()

    val state: StateFlow<EngineState> get() = engine.state
    val events get() = engine.events

    suspend fun connect(protector: SocketProtector): TunnelParams {
        Log.i(TAG, "Starting SSTP session for ${profile.server}:${profile.port}")
        val engineProfile = EngineProfile.Sstp(
            server = profile.server,
            username = profile.username,
            password = profile.password,
            mtu = profile.mtu,
            customDns = emptyList(),
            port = profile.port,
            trustPolicy = if (profile.verifyCertificate) TrustPolicy.SYSTEM else TrustPolicy.INSECURE,
            trustedCertificateIds = emptyList(),
            pinnedFingerprints = emptySet(),
            expectedHostname = null,
            minTlsVersion = TlsVersion.TLS_1_2,
            pppAuthMethods = EngineProfile.Sstp.DEFAULT_AUTH_METHODS,
            proxy = null,
        )
        return engine.connect(engineProfile, protector)
    }

    fun attachTun(fd: ParcelFileDescriptor) {
        engine.attachTun(fd)
    }

    suspend fun stop() {
        Log.i(TAG, "Stopping SSTP session")
        runCatching { engine.disconnect() }
    }

    companion object {
        private const val TAG = "SstpController"
    }
}
