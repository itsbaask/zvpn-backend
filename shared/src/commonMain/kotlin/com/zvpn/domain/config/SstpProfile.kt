package com.zvpn.domain.config

import kotlinx.serialization.Serializable

@Serializable
data class SstpProfile(
    val server: String = "",
    val port: Int = 443,
    val username: String = "",
    val password: String = "",
    val verifyCertificate: Boolean = true,
    val mtu: Int = 1400,
) {
    val isValid: Boolean get() = server.isNotBlank()
}
