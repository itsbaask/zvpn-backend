package io.github.mr1ve3r.combined.engine.sstp

import io.github.mr1ve3r.combined.engine.EngineProfile
import io.github.mr1ve3r.combined.engine.PppAuthMethod
import io.github.mr1ve3r.combined.engine.ProxyConfig
import io.github.mr1ve3r.combined.engine.TlsVersion
import io.github.mr1ve3r.combined.engine.TrustPolicy

internal class SstpEngineConfig(
    val server: String,
    val port: Int,
    val username: String,
    val password: String,
    val mtu: Int,
    val mru: Int,
    val customDns: List<String>,
    val expectedHostname: String?,
    val minTlsVersion: TlsVersion,
    val authMethods: Set<PppAuthMethod>,
    val proxy: ProxyConfig?,
    val trustPolicy: TrustPolicy,
    val trustedCertificateIds: List<String>,
    val pinnedFingerprints: Set<String>,
    val ipv4Enabled: Boolean = true,
    val ipv6Enabled: Boolean = false,
) {
    val verificationHostname: String get() = expectedHostname ?: server

    fun isEnabled(method: PppAuthMethod): Boolean = method in authMethods

    companion object {
        const val MIN_MRU: Int = 68
        const val MAX_MRU: Int = 2000
        const val DEFAULT_MRU: Int = 1500

        fun of(profile: EngineProfile.Sstp): SstpEngineConfig {
            val mtu = profile.mtu.coerceIn(MIN_MRU, MAX_MRU)

            return SstpEngineConfig(
                server = profile.server,
                port = profile.port,
                username = profile.username,
                password = profile.password,
                mtu = mtu,
                mru = mtu,
                customDns = profile.customDns,
                expectedHostname = profile.expectedHostname?.takeIf { it.isNotBlank() },
                minTlsVersion = profile.minTlsVersion,
                authMethods = profile.pppAuthMethods,
                proxy = profile.proxy,
                trustPolicy = profile.trustPolicy,
                trustedCertificateIds = profile.trustedCertificateIds,
                pinnedFingerprints = profile.pinnedFingerprints,
            )
        }
    }
}
