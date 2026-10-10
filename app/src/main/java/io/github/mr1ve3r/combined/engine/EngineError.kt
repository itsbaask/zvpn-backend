package io.github.mr1ve3r.combined.engine

sealed interface EngineError {
    val messageKey: String
    val detail: String?

    data class NetworkUnreachable(override val detail: String?) : EngineError {
        override val messageKey: String get() = "engine.error.network_unreachable"
    }

    data class AuthenticationFailed(override val detail: String?) : EngineError {
        override val messageKey: String get() = "engine.error.authentication_failed"
    }

    data class TlsHandshakeFailed(override val detail: String?) : EngineError {
        override val messageKey: String get() = "engine.error.tls_handshake_failed"
    }

    data class CertificateRejected(
        val fingerprintSha256: String?,
        override val detail: String?,
    ) : EngineError {
        override val messageKey: String get() = "engine.error.certificate_rejected"
    }

    data class CertificateExpired(
        val notAfter: Long,
        override val detail: String?,
    ) : EngineError {
        override val messageKey: String get() = "engine.error.certificate_expired"
    }

    data class HostnameMismatch(
        val expected: String,
        val presented: List<String>,
        override val detail: String?,
    ) : EngineError {
        override val messageKey: String get() = "engine.error.hostname_mismatch"
    }

    data class IpsecFailed(override val detail: String?) : EngineError {
        override val messageKey: String get() = "engine.error.ipsec_failed"
    }

    data class PppNegotiationFailed(
        val phase: String,
        override val detail: String?,
    ) : EngineError {
        override val messageKey: String get() = "engine.error.ppp_negotiation_failed"
    }

    data class TimedOut(
        val stage: String,
        override val detail: String?,
    ) : EngineError {
        override val messageKey: String get() = "engine.error.timed_out"
    }

    data class Internal(override val detail: String?) : EngineError {
        override val messageKey: String get() = "engine.error.internal"
    }
}
