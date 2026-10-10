package com.zvpn.domain.config

import kotlinx.serialization.Serializable

@Serializable
data class L2tpProfile(
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val presharedKey: String = "",
    val mtu: Int = 1400,
) {
    val isValid: Boolean get() = server.isNotBlank()
}
