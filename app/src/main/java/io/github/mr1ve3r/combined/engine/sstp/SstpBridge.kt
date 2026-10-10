package io.github.mr1ve3r.combined.engine.sstp

import io.github.mr1ve3r.combined.engine.sstp.terminal.IpTerminal
import io.github.mr1ve3r.combined.engine.sstp.unit.DataUnit
import java.nio.ByteBuffer
import kotlinx.coroutines.CoroutineScope

internal class SstpBridge(
    val config: SstpEngineConfig,
    val state: SstpSessionState,
    val mailbox: ControlMailbox,
    val scope: CoroutineScope,
) {
    @Volatile
    var transport: SstpTransport? = null

    @Volatile
    var ipTerminal: IpTerminal? = null

    fun requireTransport(): SstpTransport = transport ?: error("The SSTP transport is not established yet")

    suspend fun send(buffer: ByteBuffer) {
        requireTransport().send(buffer)
    }

    suspend fun send(unit: DataUnit) {
        requireTransport().send(unit.toByteBuffer())
    }
}

internal interface SstpTransport {
    val applicationBufferSize: Int
    val serverCertificate: ByteArray
    suspend fun send(buffer: ByteBuffer)
    fun receive(buffer: ByteBuffer)
}
