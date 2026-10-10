package com.zvpn.crypto

expect object ZvxCrypto {
    fun seal(request: ZvxSealRequest): ByteArray
    fun peek(bytes: ByteArray): ZvxMetadata
    fun open(bytes: ByteArray, password: String?): String
    fun decryptBackendXor(bytes: ByteArray): String?
}
