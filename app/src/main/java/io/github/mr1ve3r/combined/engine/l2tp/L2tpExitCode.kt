package io.github.mr1ve3r.combined.engine.l2tp

import io.github.mr1ve3r.combined.engine.EngineError

object L2tpExitCode {
    const val OK: Int = 0
    const val IKE_FAILED: Int = 1
    const val L2TP_FAILED: Int = 2
    const val PPP_FAILED: Int = 3
    const val POLL_ERROR: Int = 4
    const val BAD_ARGS: Int = 10
    const val PROXY_NOT_IMPLEMENTED: Int = 11
    const val STOPPED: Int = 12

    fun toEngineError(code: Int): EngineError? = when (code) {
        OK, STOPPED -> null
        IKE_FAILED -> EngineError.IpsecFailed(IKE_DETAIL)
        L2TP_FAILED -> EngineError.PppNegotiationFailed(phase = "L2TP", detail = L2TP_DETAIL)
        PPP_FAILED -> EngineError.PppNegotiationFailed(phase = "PPP", detail = PPP_DETAIL)
        POLL_ERROR -> EngineError.NetworkUnreachable(POLL_DETAIL)
        BAD_ARGS -> EngineError.Internal(BAD_ARGS_DETAIL)
        PROXY_NOT_IMPLEMENTED -> EngineError.Internal(PROXY_DETAIL)
        else -> EngineError.Internal(unknownDetail(code))
    }

    fun isCleanExit(code: Int): Boolean = code == OK || code == STOPPED

    private const val IKE_DETAIL = "IPsec negotiation failed. Check the PSK and server settings."
    private const val L2TP_DETAIL = "L2TP handshake failed."
    private const val PPP_DETAIL = "PPP negotiation failed."
    private const val POLL_DETAIL = "Tunnel poll I/O error."
    private const val BAD_ARGS_DETAIL = "Invalid tunnel arguments from the app."
    private const val PROXY_DETAIL = "Proxy transport is not implemented yet."

    private fun unknownDetail(code: Int): String = "Tunnel engine exited with code $code"
}
