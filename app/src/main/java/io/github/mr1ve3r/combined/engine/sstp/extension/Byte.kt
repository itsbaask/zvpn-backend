package io.github.mr1ve3r.combined.engine.sstp.extension

internal fun Byte.toIntAsUByte(): Int = this.toInt() and 0x000000FF
