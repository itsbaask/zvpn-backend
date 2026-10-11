package com.zvpn.data.config

import com.zvpn.domain.config.ProfileSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the backend servers.json import path (importServerJson).
 *
 * The backend produces JSON with schema_version + servers[] where each
 * server has an "engine" field ("OPENVPN" or "XRAY") and either
 * "ovpn_content" (inline .ovpn text) or "config_uri" (vless://, vmess://, etc.).
 */
class ServerJsonImportTest {

    /** Minimal but valid inline OpenVPN profile text. */
    private fun ovpnText(host: String = "vpn.example.net", port: Int = 1194) = """
        client
        dev tun
        proto udp
        remote $host $port
        cipher AES-256-GCM
        <ca>
        -----BEGIN CERTIFICATE-----
        MIIBexampleABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqr
        -----END CERTIFICATE-----
        </ca>
    """.trimIndent()

    /** Escape a string for embedding as a JSON string value. */
    private fun String.jsonEscape(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

    private fun serverEntry(
        id: String,
        host: String,
        port: Int = 1194,
        name: String = "Test Server",
        engine: String = "OPENVPN",
        ovpnContent: String = "",
        configUri: String = "",
    ) = """
        {
          "id": "$id",
          "engine": "$engine",
          "protocol": "$engine",
          "name": "$name",
          "host": "$host",
          "port": $port,
          "ovpn_content": "${ovpnContent.jsonEscape()}",
          "config_uri": "${configUri.jsonEscape()}",
          "profile_url": "https://zvpn.net/profiles/$id.ovpn"
        }
    """.trimIndent()

    private fun serversJson(vararg entries: String) = """
        {
          "schema_version": 1,
          "version": "2026-01-01T00:00:00Z-abc123",
          "count": ${entries.size},
          "servers": [ ${entries.joinToString(",")} ]
        }
    """.trimIndent()

    // ─────────────────────────────────────────────
    // Happy-path tests
    // ─────────────────────────────────────────────

    @Test
    fun `servers json with openvpn inline content imports one server`() {
        val json = serversJson(
            serverEntry("s1", "vpn.example.net", ovpnContent = ovpnText("vpn.example.net"))
        )
        val repo = ConfigRepository(InMemoryKeyValueStore())
        assertEquals(1, repo.importText(json).getOrThrow())

        val profile = repo.profiles.value.single()
        assertEquals("OPENVPN", profile.protocol)
        assertEquals("vpn.example.net", profile.address)
        assertEquals(1194, profile.port)
        assertTrue(profile.source is ProfileSource.SingBox)
    }

    @Test
    fun `servers json imports multiple openvpn servers`() {
        val json = serversJson(
            serverEntry("s1", "vpn1.example.net", 1194, ovpnContent = ovpnText("vpn1.example.net", 1194)),
            serverEntry("s2", "vpn2.example.net", 1195, ovpnContent = ovpnText("vpn2.example.net", 1195)),
        )
        val repo = ConfigRepository(InMemoryKeyValueStore())
        assertEquals(2, repo.importText(json).getOrThrow())
        assertEquals(2, repo.profiles.value.size)
        val addresses = repo.profiles.value.map { it.address }.toSet()
        assertTrue("vpn1.example.net" in addresses)
        assertTrue("vpn2.example.net" in addresses)
    }

    @Test
    fun `mixed bad and good entries only imports the valid one`() {
        val json = serversJson(
            // Bad: ovpn_content missing CA → rejected by OvpnConfig.refuseWhatCannotRun
            serverEntry("bad", "bad.example.net",
                ovpnContent = "client\ndev tun\nremote bad.example.net 1194"),
            // Good: complete profile
            serverEntry("good", "good.example.net",
                ovpnContent = ovpnText("good.example.net")),
        )
        val repo = ConfigRepository(InMemoryKeyValueStore())
        assertEquals(1, repo.importText(json).getOrThrow())
        assertEquals("good.example.net", repo.profiles.value.single().address)
    }

    // ─────────────────────────────────────────────
    // Edge-case / guard tests
    // ─────────────────────────────────────────────

    @Test
    fun `servers json with missing ovpn_content does not add any profile`() {
        val json = serversJson(
            serverEntry("empty", "empty.example.net", ovpnContent = "")
        )
        val repo = ConfigRepository(InMemoryKeyValueStore())
        // importServerJson should return null (no valid entries) → falls through → error
        val result = repo.importText(json)
        assertEquals(0, repo.profiles.value.size)
        // Must not throw NullPointerException / ClassCastException
        assertTrue(result.exceptionOrNull() !is NullPointerException)
        assertTrue(result.exceptionOrNull() !is ClassCastException)
    }

    @Test
    fun `ordinary json without schema_version or engine is not mistakenly consumed`() {
        // Plain JSON that has a "servers" array but no schema_version and no "engine" field
        // → importServerJson should return null
        val plainJson = """{"servers":[{"host":"x.com","port":443}]}"""
        val repo = ConfigRepository(InMemoryKeyValueStore())
        val result = repo.importText(plainJson)
        // Must not throw an unexpected crash
        assertTrue(result.exceptionOrNull() !is NullPointerException)
        assertTrue(result.exceptionOrNull() !is ClassCastException)
        assertEquals(0, repo.profiles.value.size)
    }

    @Test
    fun `duplicate server from same subscription is not imported twice`() {
        val ovpn = ovpnText("dup.example.net")
        val json = serversJson(
            serverEntry("dup1", "dup.example.net", ovpnContent = ovpn),
            serverEntry("dup2", "dup.example.net", ovpnContent = ovpn),
        )
        val repo = ConfigRepository(InMemoryKeyValueStore())
        // The second entry produces the same SingBox fragment → counted as duplicate
        val added = repo.importText(json, subscriptionId = "sub1").getOrThrow()
        // At most 1 unique profile
        assertTrue("Expected ≤1 fresh profiles, got $added", added <= 1)
        assertTrue(repo.profiles.value.size <= 1)
    }

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    @Test
    fun `backend xor encrypted b64 payload imports correctly`() {
        val rawJson = """{"version":2,"servers":[{"name":"Encrypted Server","raw_uri":"vless://11111111-2222-3333-4444-555555555555@example.com:443?encryption=none#Encrypted"}]}"""
        val key = java.security.MessageDigest.getInstance("SHA-256").digest("ZedSecure .zvx v2".toByteArray(Charsets.UTF_8))
        val rawBytes = rawJson.toByteArray(Charsets.UTF_8)
        val encBytes = ByteArray(rawBytes.size) { i -> (rawBytes[i].toInt() xor key[i % key.size].toInt()).toByte() }
        val b64 = kotlin.io.encoding.Base64.Default.encode(encBytes)

        val repo = ConfigRepository(InMemoryKeyValueStore())
        val added = repo.importText(b64, subscriptionId = "sub_xor").getOrThrow()
        assertEquals(1, added)
        assertEquals("Encrypted Server", repo.profiles.value.first().name)
    }

    @Test
    fun `test real output servers json file`() {
        val file = java.io.File("../../output/servers.json")
        if (!file.exists()) return
        val text = file.readText()
        val repo = ConfigRepository(InMemoryKeyValueStore())
        val result = repo.importText(text, subscriptionId = "cloud_sub")
        println("REAL_FILE_RESULT: ${result.getOrNull()} EXCEPTION: ${result.exceptionOrNull()}")
        assertTrue("Import must succeed, got $result", result.isSuccess && (result.getOrNull() ?: 0) > 0)
    }
}
