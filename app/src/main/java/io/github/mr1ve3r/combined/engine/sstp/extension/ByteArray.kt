package io.github.mr1ve3r.combined.engine.sstp.extension

internal fun ByteArray.toHexString(): String {
    val output = StringBuilder(size * 2)
    forEach { byte -> output.append(String.format("%02X", byte.toInt() and 0xFF)) }
    return output.toString()
}

internal fun String.toHexByteArray(): ByteArray {
    require(length % 2 == 0) { "A hex string needs an even number of characters" }

    val arrayLength = length / 2
    val output = ByteArray(arrayLength)

    repeat(arrayLength) {
        val start = it * 2
        output[it] = this.slice(start..start + 1).toInt(16).toByte()
    }

    return output
}
