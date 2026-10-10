package com.zvpn.domain.config

import kotlinx.serialization.Serializable

@Serializable
data class StructuredServerRegistry(
    val total_servers: Int = 0,
    val auto_select_top10: List<ServerEntry> = emptyList(),
    val by_protocol: Map<String, List<ServerEntry>> = emptyMap(),
    val by_continent_country_and_tier: Map<String, Map<String, TierGroup>> = emptyMap(),
    val all_servers: List<ServerEntry> = emptyList()
) {
    companion object {
        val Empty = StructuredServerRegistry()
    }
}

@Serializable
data class TierGroup(
    val free: List<ServerEntry> = emptyList(),
    val premium: List<ServerEntry> = emptyList(),
    val premium_plus: List<ServerEntry> = emptyList()
)

@Serializable
data class ServerEntry(
    val protocol: String = "",
    val server: String? = null,
    val host: String? = null,
    val server_port: Int? = null,
    val port: Int? = null,
    val raw_uri: String? = null,
    val config_data: String? = null,
    val password: String? = null,
    val display_name: String = "",
    val continent: String = "Global",
    val country: String = "Global",
    val flag: String = "🌐",
    val tier: String = "free",
    val speed_mbps: Double = 15.0,
    val latency_ms: Int = 50,
    val source: String = ""
) {
    val address: String get() = server ?: host ?: ""
    val resolvedPort: Int get() = server_port ?: port ?: 443
}
