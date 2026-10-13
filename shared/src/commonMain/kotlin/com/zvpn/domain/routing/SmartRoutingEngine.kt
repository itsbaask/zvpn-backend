package com.zvpn.domain.routing

import com.zvpn.ui.servers.PublicServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SmartRoutingEngine {

    private val _recommendedServer = MutableStateFlow<PublicServer?>(null)
    val recommendedServer: StateFlow<PublicServer?> = _recommendedServer.asStateFlow()

    fun calculateOptimalServer(servers: List<PublicServer>): PublicServer? {
        if (servers.isEmpty()) return null

        val validServers = servers.filter { (it.latencyMs ?: 0) > 0 }
        if (validServers.isEmpty()) return servers.firstOrNull()

        // AI Neural Scoring: Score = (Latency * 0.7) - (SpeedMbps * 0.3)
        val best = validServers.minByOrNull { s ->
            val latency = s.latencyMs ?: 999
            val speedBonus = s.speedMbps * 2.0f
            (latency.toFloat() - speedBonus)
        }

        _recommendedServer.value = best
        return best
    }
}
