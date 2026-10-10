package io.github.mr1ve3r.combined.engine.sstp

import kotlinx.coroutines.channels.Channel

internal class ControlMailbox {
    private val channel = Channel<ControlMessage>(Channel.BUFFERED)

    internal suspend fun send(from: Where, result: Result, supplement: String? = null) {
        channel.send(ControlMessage(from, result, supplement))
    }

    internal suspend fun send(message: ControlMessage) {
        channel.send(message)
    }

    internal fun tryReport(from: Where, result: Result, supplement: String? = null) {
        channel.trySend(ControlMessage(from, result, supplement))
    }

    internal suspend fun receive(): ControlMessage = channel.receive()

    internal fun close() {
        channel.close()
    }
}

internal data class ControlMessage(
    val from: Where,
    val result: Result,
    val supplement: String? = null,
)

internal enum class Where {
    CERT,
    CERT_PATH,
    SSL,
    PROXY,
    SSTP_DATA,
    SSTP_CONTROL,
    SSTP_REQUEST,
    SSTP_HASH,
    PPP,
    PAP,
    CHAP,
    MSCHAPV2,
    EAP,
    LCP,
    LCP_MRU,
    LCP_AUTH,
    IPCP,
    IPCP_IP,
    IPV6CP,
    IPV6CP_IDENTIFIER,
    IP,
    IPV4,
    IPV6,
    ROUTE,
    INCOMING,
    OUTGOING,
}

internal enum class Result {
    PROCEEDED,
    ERR_TIMEOUT,
    ERR_COUNT_EXHAUSTED,
    ERR_UNKNOWN_TYPE,
    ERR_UNEXPECTED_MESSAGE,
    ERR_PARSING_FAILED,
    ERR_VERIFICATION_FAILED,
    ERR_NEGATIVE_ACKNOWLEDGED,
    ERR_ABORT_REQUESTED,
    ERR_DISCONNECT_REQUESTED,
    ERR_TERMINATE_REQUESTED,
    ERR_PROTOCOL_REJECTED,
    ERR_CODE_REJECTED,
    ERR_AUTHENTICATION_FAILED,
    ERR_ADDRESS_REJECTED,
    ERR_OPTION_REJECTED,
    ERR_INVALID_ADDRESS,
    ERR_INVALID_PACKET_SIZE,
}
