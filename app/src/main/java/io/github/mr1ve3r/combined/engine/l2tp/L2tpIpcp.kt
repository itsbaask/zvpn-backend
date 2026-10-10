package io.github.mr1ve3r.combined.engine.l2tp

import java.net.Inet4Address
import java.net.InetAddress

internal object L2tpIpcp {
    fun addressOrNull(octets: IntArray?): Inet4Address? {
        if (octets == null || octets.size < 4) return null
        for (index in 0..3) {
            if (octets[index] !in OCTET_RANGE) return null
        }
        if ((0..3).all { octets[it] == 0 }) return null
        val literal = "${octets[0]}.${octets[1]}.${octets[2]}.${octets[3]}"
        return InetAddress.getByName(literal) as? Inet4Address
    }

    fun dnsServers(primary: IntArray?, secondary: IntArray?): List<Inet4Address> =
        listOfNotNull(addressOrNull(primary), addressOrNull(secondary)).distinct()

    private val OCTET_RANGE = 0..255
}
