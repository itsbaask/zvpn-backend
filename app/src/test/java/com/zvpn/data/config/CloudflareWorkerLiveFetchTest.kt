package com.zvpn.data.config

import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL

class CloudflareWorkerLiveFetchTest {

    @Test
    fun `fetches live servers from Cloudflare Worker endpoint and successfully imports into ConfigRepository`() {
        val endpointUrl = "https://zvpn-backend.xocqoc.workers.dev/servers?limit=20"
        val json = try {
            URL(endpointUrl).readText()
        } catch (e: Exception) {
            println("Live fetch note (offline/CI): ${e.message}")
            return
        }

        assertTrue("Expected non-empty JSON response from Cloudflare Worker", json.isNotBlank())
        assertTrue("JSON should contain servers array", json.contains("\"servers\""))

        val store = InMemoryKeyValueStore()
        val repo = ConfigRepository(store)

        val importedCount = repo.importText(json, subscriptionId = "cloudflare_backend").getOrThrow()
        println("Successfully imported $importedCount profiles from live Cloudflare Worker endpoint!")
        assertTrue("Should import at least 1 profile from live worker endpoint", importedCount > 0)

        val profiles = repo.profiles.value
        assertTrue("Profiles flow should contain imported servers", profiles.isNotEmpty())
        
        profiles.take(5).forEach { profile ->
            println("Imported Profile: ${profile.name} | Protocol: ${profile.protocol} | Address: ${profile.address}:${profile.port}")
            assertTrue("Profile name should not be blank", profile.name.isNotBlank())
            assertTrue("Profile address should not be blank", profile.address.isNotBlank())
        }
    }
}
