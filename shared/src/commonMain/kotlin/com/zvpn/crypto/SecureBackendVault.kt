package com.zvpn.crypto

object SecureBackendVault {
    private val K1 = byteArrayOf(
        122, 118, 112, 110, 95, 115, 101, 99, 117, 114, 101, 95,
        118, 97, 117, 108, 116, 95, 50, 48, 50, 54, 95, 107,
        101, 121, 95, 120, 57, 57
    )

    private val B1 = byteArrayOf(
        18, 2, 4, 30, 44, 73, 74, 76, 15, 4, 21, 49, 91, 3, 20, 15,
        31, 58, 92, 84, 28, 78, 48, 8, 20, 22, 60, 86, 78, 86, 8,
        29, 21, 28, 44, 93, 1, 6, 3, 93, 22, 58, 4, 23, 16, 30, 7
    )

    val backendUrl: String by lazy {
        val out = ByteArray(B1.size)
        for (i in B1.indices) {
            out[i] = (B1[i].toInt() xor K1[i % K1.size].toInt()).toByte()
        }
        out.decodeToString()
    }
}
