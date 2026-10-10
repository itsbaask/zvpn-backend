package io.github.mr1ve3r.combined.engine.sstp.terminal

import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

internal class IpTerminal(
    private val input: InputStream,
    private val output: OutputStream,
    private val mtu: Int,
    private val onClose: () -> Unit,
) {
    fun writePacket(start: Int, size: Int, buffer: ByteBuffer) {
        output.write(buffer.array(), start, size)
    }

    fun readPacket(buffer: ByteBuffer) {
        buffer.clear()
        buffer.position(input.read(buffer.array(), 0, mtu).coerceAtLeast(0))
        buffer.flip()
    }

    fun close() {
        onClose()
    }

    companion object {
        fun of(fd: ParcelFileDescriptor, mtu: Int): IpTerminal = IpTerminal(
            input = FileInputStream(fd.fileDescriptor),
            output = FileOutputStream(fd.fileDescriptor),
            mtu = mtu,
            onClose = { fd.close() },
        )
    }
}
