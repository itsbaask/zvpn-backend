package com.zvpn.crypto

object SecureBackendVault {
    private val K1 = byteArrayOf(
        122, 118, 112, 110, 95, 115, 101, 99, 117, 114, 101, 95,
        118, 97, 117, 108, 116, 95, 50, 48, 50, 54, 95, 107,
        101, 121, 95, 120, 57, 57
    )

    private val B1 = byteArrayOf(
        18, 2, 4, 30, 44, 73, 74, 76, 7, 19, 18, 113, 17, 8, 1, 4, 1, 61, 71, 67, 87, 68, 60, 4, 11, 13, 58, 22, 77, 23, 25, 25, 29, 65, 54, 7, 22, 1, 20, 19, 22, 52, 89, 27, 3, 28, 26, 114, 80, 81, 81, 93, 58, 5, 1, 86, 45, 29, 91, 75, 27, 24, 20, 65, 37, 5, 21, 13, 90, 29, 16, 43, 6, 20, 1, 67, 7, 58, 64, 70, 87, 68, 44, 69, 15, 10, 48, 22
    )

    val backendUrl: String by lazy {
        val out = ByteArray(B1.size)
        for (i in B1.indices) {
            out[i] = (B1[i].toInt() xor K1[i % K1.size].toInt()).toByte()
        }
        out.decodeToString()
    }
}
