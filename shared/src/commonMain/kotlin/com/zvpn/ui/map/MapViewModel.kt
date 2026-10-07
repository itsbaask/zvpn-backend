package com.zvpn.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zvpn.data.config.ConfigRepository
import com.zvpn.data.map.WorldMap
import com.zvpn.data.net.NetworkInfo
import com.zvpn.data.net.NetworkInfoRepository
import com.zvpn.domain.config.VpnProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Enhanced MapViewModel for Interactive World-Class Server Map.
 * Provides real-time multi-protocol filtering, ping threshold filters, region filters,
 * and interactive server selection.
 */
class MapViewModel(
    var configRepo: ConfigRepository? = null,
    private val netRepo: NetworkInfoRepository = NetworkInfoRepository(),
) : ViewModel() {

    private val _countries = MutableStateFlow<List<WorldMap.Country>>(emptyList())
    val countries: StateFlow<List<WorldMap.Country>> = _countries.asStateFlow()

    private val _info = MutableStateFlow(NetworkInfo())
    val info: StateFlow<NetworkInfo> = _info.asStateFlow()

    var preferredIpApiUrl: String? = null

    // Profiles Flow
    private val repoProfiles = MutableStateFlow<List<VpnProfile>>(emptyList())

    fun setRepository(repo: ConfigRepository) {
        configRepo = repo
        viewModelScope.launch {
            repo.profiles.collect { list -> repoProfiles.value = list }
        }
    }

    // Search & Filter State
    val searchQuery = MutableStateFlow("")
    val selectedProtocol = MutableStateFlow("All") // "All", "OpenVPN", "VLESS", "VMess", "Shadowsocks", "WireGuard", "Trojan"
    val maxPingFilter = MutableStateFlow<Int?>(null) // null = All, 100, 200, 300
    val selectedRegion = MutableStateFlow("All") // "All", "Americas", "Europe", "Asia-Pacific", "ME/Africa"

    // Currently Selected Server Pin on Map
    val selectedServer = MutableStateFlow<VpnProfile?>(null)

    // Filtered Server Profiles
    val filteredProfiles: StateFlow<List<VpnProfile>> = combine(
        repoProfiles,
        searchQuery,
        selectedProtocol,
        maxPingFilter,
        selectedRegion
    ) { profiles, query, proto, pingLimit, region ->
        profiles.filter { p ->
            val q = query.trim().lowercase()
            val matchQuery = q.isEmpty() ||
                p.name.lowercase().contains(q) ||
                (p.countryCode ?: "").lowercase().contains(q) ||
                p.address.lowercase().contains(q)

            val matchProto = proto == "All" || p.protocol.equals(proto, ignoreCase = true)

            val matchPing = pingLimit == null || (p.lastPingMs != null && p.lastPingMs <= pingLimit)

            val matchRegion = when (region) {
                "Americas" -> isAmericas(p.countryCode)
                "Europe" -> isEurope(p.countryCode)
                "Asia-Pacific" -> isAsiaPacific(p.countryCode)
                "ME/Africa" -> isMeAfrica(p.countryCode)
                else -> true
            }

            matchQuery && matchProto && matchPing && matchRegion
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var job: Job? = null

    init {
        viewModelScope.launch { _countries.value = WorldMap.countries() }
    }

    fun selectServer(profile: VpnProfile?) {
        selectedServer.value = profile
    }

    fun setActiveServer(profile: VpnProfile) {
        configRepo?.setActive(profile.id)
    }

    fun resetFilters() {
        searchQuery.value = ""
        selectedProtocol.value = "All"
        maxPingFilter.value = null
        selectedRegion.value = "All"
    }

    fun refresh() {
        job?.cancel()
        _info.value = _info.value.copy(loading = true, error = null)
        job = viewModelScope.launch(Dispatchers.IO) {
            val resolved = netRepo.fetchInfo(preferredIpApiUrl)
            _info.value = resolved.copy(loading = false)
        }
    }

    private fun isAmericas(cc: String?): Boolean {
        val code = cc?.uppercase() ?: return false
        return code in setOf("US", "CA", "MX", "BR", "AR", "CL", "CO", "PE", "VE", "EC", "GT", "CU", "CR", "PA")
    }

    private fun isEurope(cc: String?): Boolean {
        val code = cc?.uppercase() ?: return false
        return code in setOf("DE", "FR", "GB", "NL", "IT", "ES", "PL", "SE", "FI", "CH", "AT", "NO", "DK", "BE", "CZ", "RO", "UA", "RU", "GR", "IE", "PT", "HU", "BG")
    }

    private fun isAsiaPacific(cc: String?): Boolean {
        val code = cc?.uppercase() ?: return false
        return code in setOf("JP", "KR", "SG", "HK", "TW", "IN", "AU", "NZ", "ID", "TH", "MY", "PH", "VN", "PK", "BD", "KH")
    }

    private fun isMeAfrica(cc: String?): Boolean {
        val code = cc?.uppercase() ?: return false
        return code in setOf("AE", "SA", "EG", "TR", "IL", "QA", "BH", "KW", "OM", "ZA", "MA", "DZ", "TN", "NG", "KE")
    }
}
