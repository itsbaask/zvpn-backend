package io.github.mr1ve3r.combined.engine

sealed interface EngineProfile {
    val server: String
    val username: String
    val password: String
    val mtu: Int
    val customDns: List<String>

    data class L2tp(
        override val server: String,
        override val username: String,
        override val password: String,
        override val mtu: Int,
        override val customDns: List<String>,
        val ipsecEnabled: Boolean,
        val presharedKey: String?,
        val localIdentifier: String?,
        val phase1Proposals: List<String>,
        val phase2Proposals: List<String>,
    ) : EngineProfile {
        companion object {
            const val DEFAULT_MTU: Int = 1400
        }
    }

    data class Sstp(
        override val server: String,
        override val username: String,
        override val password: String,
        override val mtu: Int,
        override val customDns: List<String>,
        val port: Int,
        val trustPolicy: TrustPolicy,
        val trustedCertificateIds: List<String>,
        val pinnedFingerprints: Set<String>,
        val expectedHostname: String?,
        val minTlsVersion: TlsVersion,
        val pppAuthMethods: Set<PppAuthMethod>,
        val proxy: ProxyConfig?,
    ) : EngineProfile {
        companion object {
            const val DEFAULT_MTU: Int = 1400
            const val DEFAULT_PORT: Int = 443
            val DEFAULT_AUTH_METHODS: Set<PppAuthMethod> =
                setOf(PppAuthMethod.MSCHAPV2, PppAuthMethod.CHAP, PppAuthMethod.PAP)
        }
    }
}

data class ProxyConfig(
    val host: String,
    val port: Int,
    val username: String?,
    val password: String?,
)

enum class PppAuthMethod {
    PAP,
    CHAP,
    MSCHAPV2,
    EAP_MSCHAPV2,
}
