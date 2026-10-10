package io.github.mr1ve3r.combined.engine.sstp.extension

internal fun Short.toIntAsUShort(): Int = this.toInt() and 0x0000FFFF
