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
    VLESS("vless", "VLESS"),
    VMess("vmess", "VMess"),
    Trojan("trojan", "Trojan"),
    Shadowsocks("shadowsocks", "Shadowsocks"),
    Hysteria2("hysteria2", "Hysteria 2"),
    TUIC("tuic", "TUIC"),
    OpenVPN("openvpn", "OpenVPN"),
    WireGuard("wireguard", "WireGuard"),
    L2TP("l2tp", "L2TP"),
    SSTP("sstp", "SSTP"),
    IKEv2("ikev2", "IKEv2"),
    HTTP("http", "HTTP"),
    SOCKS5("socks5", "SOCKS5"),
    SSH("ssh", "SSH"),
    Tor("tor", "Tor"),
    Psiphon("psiphon", "Psiphon"),
    DNSTunnel("dnstunnel", "DNS Tunnel");

    companion object {
        fun fromId(id: String): ProtocolFilter {
            val clean = id.trim().lowercase()
            return entries.find {
                it.id.equals(clean, true) ||
                it.label.lowercase() == clean ||
                (clean in listOf("ss", "shadowsocks", "shadowsocks2022") && it == Shadowsocks) ||
                (clean in listOf("hy2", "hysteria", "hysteria2") && it == Hysteria2) ||
                (clean in listOf("awg", "amneziawg", "wg", "wireguard") && it == WireGuard) ||
                (clean in listOf("socks", "socks5", "socks4") && it == SOCKS5) ||
                (clean in listOf("http", "https") && it == HTTP) ||
                (clean in listOf("masterdns", "dnstunnel", "dns") && it == DNSTunnel)
            } ?: All
        }
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
