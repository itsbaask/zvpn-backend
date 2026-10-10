package com.zvpn.ui.servers

import com.zvpn.domain.billing.UserTier

enum class ServerTier(val id: String, val labelResKey: String) {
    Free("free", "server_tier_free"),
    Premium("premium", "server_tier_premium"),
    PremiumPlus("premium_plus", "server_tier_premium_plus");

    companion object {
        fun fromId(id: String): ServerTier = entries.find { it.id.equals(id, true) } ?: Free
    }
}

enum class ProtocolFilter(val id: String, val label: String) {
    All("all", "All"),
    OpenVPN("openvpn", "OpenVPN"),
    VLESS("vless", "VLESS"),
    VMess("vmess", "VMess"),
    Shadowsocks("shadowsocks", "Shadowsocks"),
    WireGuard("wireguard", "WireGuard"),
    Trojan("trojan", "Trojan"),
    Hysteria2("hysteria2", "Hysteria2");

    companion object {
        fun fromId(id: String): ProtocolFilter = entries.find { it.id.equals(id, true) } ?: All
    }
}

enum class SortOption(val id: String, val label: String) {
    Recommended("recommended", "Recommended"),
    Speed("speed", "Fastest"),
    Ping("ping", "Lowest Ping"),
    Name("name", "Name"),
    Favorites("favorites", "Favorites first");

    companion object {
        fun fromId(id: String): SortOption = entries.find { it.id.equals(id, true) } ?: Recommended
    }
}

data class PublicServer(
    val id: String,
    val displayName: String,
    val countryCode: String,
    val countryName: String,
    val flag: String,
    val protocolLabel: String,
    val speedMbps: Float,
    val latencyMs: Int?,
    val tier: ServerTier,
    val continent: String = "Global",
    val profileUrl: String = "",
    val configUri: String = "",
    val engine: String = "OPENVPN",
    val isFavorite: Boolean = false,
    val isPinging: Boolean = false,
)

data class ServersUiState(
    val searchQuery: String = "",
    val selectedTier: ServerTier = ServerTier.Free,
    val selectedProtocol: ProtocolFilter = ProtocolFilter.All,
    val currentSort: SortOption = SortOption.Recommended,
    val favoriteIds: Set<String> = emptySet(),
    val pingingIds: Set<String> = emptySet(),
    val servers: List<PublicServer> = emptyList(),
    val groupedServers: Map<String, Map<String, List<PublicServer>>> = emptyMap(),
    val autoConnectServer: PublicServer? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val hasActiveFilters: Boolean
        get() = searchQuery.isNotBlank() ||
                selectedProtocol != ProtocolFilter.All ||
                currentSort != SortOption.Recommended

    val isEmpty: Boolean
        get() = servers.isEmpty()
}
