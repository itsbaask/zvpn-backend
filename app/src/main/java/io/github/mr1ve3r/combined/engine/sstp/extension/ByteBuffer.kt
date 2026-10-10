package io.github.mr1ve3r.combined.engine.sstp.extension

import java.nio.ByteBuffer

internal fun ByteBuffer.move(diff: Int) {
    position(position() + diff)
}

internal fun ByteBuffer.padZeroByte(size: Int) {
    repeat(size) { put(0) }
}

internal fun ByteBuffer.probeByte(diff: Int): Byte = this.get(this.position() + diff)

internal fun ByteBuffer.probeShort(diff: Int): Short = this.getShort(this.position() + diff)

internal val ByteBuffer.capacityAfterLimit: Int
    get() = this.capacity() - this.limit()

internal fun ByteBuffer.slide() {
    val remaining = this.remaining()

    this.array().also {
        it.copyInto(it, 0, this.position(), this.limit())
    }

    this.position(0)
    this.limit(remaining)
}
