package io.github.mr1ve3r.combined.engine

enum class FailoverDecision {
    ADVANCE,
    STOP,
}

val EngineError.failoverDecision: FailoverDecision
    get() = when (this) {
        is EngineError.NetworkUnreachable -> FailoverDecision.ADVANCE
        is EngineError.TimedOut -> FailoverDecision.ADVANCE
        is EngineError.IpsecFailed -> FailoverDecision.ADVANCE
        is EngineError.TlsHandshakeFailed -> FailoverDecision.ADVANCE
        is EngineError.PppNegotiationFailed -> FailoverDecision.ADVANCE
        is EngineError.AuthenticationFailed -> FailoverDecision.STOP
        is EngineError.CertificateRejected -> FailoverDecision.STOP
        is EngineError.CertificateExpired -> FailoverDecision.STOP
        is EngineError.HostnameMismatch -> FailoverDecision.STOP
        is EngineError.Internal -> FailoverDecision.STOP
    }
