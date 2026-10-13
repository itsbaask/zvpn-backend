package com.zvpn.ui.servers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RandomGroupingTest {

    @Test
    fun testNullOrXxCountryCodeServerGoesToRandomGrouping() {
        val server1 = PublicServer(
            id = "srv_01",
            displayName = "Test Server 1",
            countryCode = "XX",
            countryName = "Unknown",
            flag = "🌐",
            protocolLabel = "VLESS",
            speedMbps = 10f,
            latencyMs = 50,
            tier = ServerTier.Free,
        )

        val server2 = PublicServer(
            id = "srv_02",
            displayName = "Test Server 2",
            countryCode = "US",
            countryName = "United States",
            flag = "🇺🇸",
            protocolLabel = "VLESS",
            speedMbps = 20f,
            latencyMs = 40,
            tier = ServerTier.Free,
        )

        assertTrue(RandomServerGrouping.isRandomServer(server1))
        assertFalse(RandomServerGrouping.isRandomServer(server2))
    }

    @Test
    fun testSystemServerWithoutCountryCodeIsExcludedFromRandomGrouping() {
        val systemServer = PublicServer(
            id = "system_backend",
            displayName = "System Tunnel",
            countryCode = "XX",
            countryName = "Unknown",
            flag = "🌐",
            protocolLabel = "WIREGUARD",
            speedMbps = 100f,
            latencyMs = 20,
            tier = ServerTier.Free,
            isSystem = true,
        )

        assertFalse(RandomServerGrouping.isRandomServer(systemServer))
    }

    @Test
    fun testDeterministicStableNumberingForRandomServers() {
        val servers = (1..5).map { i ->
            PublicServer(
                id = "srv_${10 - i}", // Reverse order id
                displayName = "Raw $i",
                countryCode = "XX",
                countryName = "Unknown",
                flag = "🌐",
                protocolLabel = "VMESS",
                speedMbps = 10f,
                latencyMs = 50,
                tier = ServerTier.Free,
            )
        }

        val randomDisplayNameMap = RandomServerGrouping.computeRandomDisplayNameMap(servers)

        assertEquals("Random 1", randomDisplayNameMap["srv_5"])
        assertEquals("Random 2", randomDisplayNameMap["srv_6"])
        assertEquals("Random 3", randomDisplayNameMap["srv_7"])
        assertEquals("Random 4", randomDisplayNameMap["srv_8"])
        assertEquals("Random 5", randomDisplayNameMap["srv_9"])
    }

    @Test
    fun testChunkingIntoGroupsOf100WhenRandomCountExceeds100() {
        val totalRandom = 250
        val randomBase = (1..totalRandom).map { i ->
            PublicServer(
                id = "rand_$i",
                displayName = "Server $i",
                countryCode = "XX",
                countryName = "Unknown",
                flag = "🌐",
                protocolLabel = "VLESS",
                speedMbps = 10f,
                latencyMs = 50,
                tier = ServerTier.Free,
            )
        }

        val displayNameMap = RandomServerGrouping.computeRandomDisplayNameMap(randomBase)
        val groups = RandomServerGrouping.buildRandomCountryGroups(randomBase, displayNameMap)

        assertEquals(3, groups.size)
        assertEquals("Random 1-100", groups[0].name)
        assertEquals("Random 101-200", groups[1].name)
        assertEquals("Random 201-250", groups[2].name)
        assertEquals(100, groups[0].servers.size)
        assertEquals("Random 1", groups[0].servers[0].displayName)
    }
}
