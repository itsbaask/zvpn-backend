package io.github.mr1ve3r.combined.engine.sstp

import io.github.mr1ve3r.combined.engine.EngineError

internal object SstpErrorMapping {
    fun toEngineError(message: ControlMessage): EngineError {
        val detail = detailOf(message)

        return when (message.result) {
            Result.PROCEEDED -> EngineError.Internal(detail)
            Result.ERR_TIMEOUT -> EngineError.TimedOut(message.from.name.lowercase(), detail)
            Result.ERR_AUTHENTICATION_FAILED -> EngineError.AuthenticationFailed(detail)
            Result.ERR_VERIFICATION_FAILED -> when (message.from) {
                Where.MSCHAPV2, Where.CHAP, Where.EAP, Where.PAP -> EngineError.AuthenticationFailed(detail)
                Where.CERT, Where.CERT_PATH -> EngineError.CertificateRejected(null, detail)
                else -> EngineError.PppNegotiationFailed(message.from.name, detail)
            }
            Result.ERR_INVALID_PACKET_SIZE,
            Result.ERR_PARSING_FAILED,
            Result.ERR_UNKNOWN_TYPE,
            -> when (message.from) {
                Where.INCOMING, Where.OUTGOING, Where.ROUTE -> EngineError.Internal(detail)
                else -> EngineError.PppNegotiationFailed(message.from.name, detail)
            }
            else -> EngineError.PppNegotiationFailed(message.from.name, detail)
        }
    }

    private fun detailOf(message: ControlMessage): String {
        val header = "${message.from.name}: ${message.result.name}"
        return message.supplement?.takeIf { it.isNotBlank() }?.let { "$header\n$it" } ?: header
    }
}
