package io.github.mr1ve3r.combined.engine.sstp

import io.github.mr1ve3r.combined.engine.PppAuthMethod
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SstpSessionState(initialMru: Int) {
    private val frameIdMutex = Mutex()
    private var frameId = -1

    @Volatile
    var currentMru: Int = initialMru

    @Volatile
    var currentAuth: PppAuthMethod? = null

    @Volatile
    var hashProtocol: Byte = 0

    @Volatile
    var hlak: ByteArray? = null

    val nonce: ByteArray = ByteArray(NONCE_SIZE)
    val currentIPv4: ByteArray = ByteArray(IPV4_SIZE)
    val currentIPv6: ByteArray = ByteArray(IPV6_IDENTIFIER_SIZE)
    val currentProposedDns: ByteArray = ByteArray(IPV4_SIZE)
    val guid: String = UUID.randomUUID().toString()

    suspend fun allocateNewFrameId(): Byte = frameIdMutex.withLock {
        frameId += 1
        frameId.toByte()
    }

    companion object {
        private const val NONCE_SIZE = 32
        private const val IPV4_SIZE = 4
        private const val IPV6_IDENTIFIER_SIZE = 8
    }
}
