package com.zvpn.data.config

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CloudflareWorkerLiveFetchTest {

    @Test
    fun `debug import server count on output servers json`() {
        val file = File("output/servers.json")
        if (!file.exists()) {
            println("output/servers.json not found")
            return
        }

        val json = file.readText()
        val store = InMemoryKeyValueStore()
        val repo = ConfigRepository(store)

        val importedCount = repo.importText(json, subscriptionId = "zvpn_system_backend_v2").getOrThrow()
        println("=== DEBUG IMPORT RESULT ===")
        println("Imported $importedCount profiles into ConfigRepository")
        
        val profiles = repo.profiles.value
        println("Total stored profiles: ${profiles.size}")
        val byProto = profiles.groupBy { it.protocol }
        byProto.forEach { (proto, list) ->
            println("  $proto: ${list.size} profiles")
        }
    }
}
