package io.github.mr1ve3r.combined.engine.sstp.unit

import java.nio.ByteBuffer

internal abstract class DataUnit {
    internal abstract val length: Int
    internal abstract fun write(buffer: ByteBuffer)
    internal abstract fun read(buffer: ByteBuffer)

    internal fun toByteBuffer(): ByteBuffer {
        val buffer = ByteBuffer.allocate(length)
        write(buffer)
        buffer.flip()
        return buffer
    }
}
