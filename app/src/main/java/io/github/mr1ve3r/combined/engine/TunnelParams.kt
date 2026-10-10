package io.github.mr1ve3r.combined.engine

import java.net.InetAddress

data class TunnelParams(
    val localAddress: InetAddress,
    val prefixLength: Int,
    val dnsServers: List<InetAddress>,
    val mtu: Int,
    val searchDomains: List<String> = emptyList(),
    val routes: List<Route> = emptyList(),
    val excludedRoutes: List<Route> = emptyList(),
)

data class Route(
    val address: InetAddress,
    val prefixLength: Int,
)
