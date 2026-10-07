package com.zvpn.domain.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OvpnConfigTest {
    private val profile = """
        # Provider AM-1
        client
        dev tun
        proto udp
        remote am1.example.net 1194
        remote am2.example.net 443 tcp
        remote-random
        resolv-retry infinite
        nobind
        persist-key
        persist-tun
        auth-user-pass
        remote-cert-tls server
        verify-x509-name am1.example.net name
        cipher AES-256-GCM
        data-ciphers AES-256-GCM:AES-128-GCM:CHACHA20-POLY1305
        auth SHA256
        comp-lzo no
        keepalive 10 60
        mssfix 1400
        tun-mtu 1500
        reneg-sec 0
        explicit-exit-notify 3
        key-direction 1
        verb 3
        <ca>
        -----BEGIN CERTIFICATE-----
        MIIBcaLine1
        -----END CERTIFICATE-----
        </ca>
        <tls-auth>
        -----BEGIN OpenVPN Static key V1-----
        0123456789abcdef
        -----END OpenVPN Static key V1-----
        </tls-auth>
    """.trimIndent()

    private fun endpoint(json: String) =
        Json.parseToJsonElement(json).jsonObject["endpoints"]!!.jsonArray.single().jsonObject

    private fun kotlinx.serialization.json.JsonObject.str(key: String) = this[key]?.jsonPrimitive?.content

    @Test
    fun `an OpenVPN profile is recognised, and other formats are not`() {
        assertTrue(OvpnConfig.looksLikeOvpn(profile))
        assertFalse(OvpnConfig.looksLikeOvpn("""{"outbounds":[{"type":"trojan","server":"a","server_port":1}]}"""))
        assertFalse(OvpnConfig.looksLikeOvpn("vless://uuid@host:443?encryption=none#x"))
        assertFalse(
            OvpnConfig.looksLikeOvpn(
                "[Interface]\nPrivateKey = aaa\nAddress = 10.0.0.2/32\n[Peer]\nEndpoint = h:51820\n",
            ),
        )
    }

    @Test
    fun `what it connects to, and what it still needs`() {
        val info = OvpnConfig.inspect(profile)
        assertEquals("am1.example.net", info.host)
        assertEquals(1194, info.port)
        assertEquals("udp", info.network)
        assertEquals("the first comment names it", "Provider AM-1", info.name)
        assertTrue("auth-user-pass with no file asks the user", info.needsCredentials)
        assertFalse(info.hasInlineCredentials)
    }

    @Test
    fun `the profile becomes an openvpn endpoint sing-box accepts`() {
        val e = endpoint(OvpnConfig.toSingBoxFragment(profile, name = "AM", username = "u", password = "p"))

        assertEquals("openvpn-client", e.str("type"))
        assertEquals("AM", e.str("tag"))

        assertNull(e["server"])
        assertNull(e["server_port"])
        assertEquals("udp", e.str("network"))
        assertEquals("u", e.str("username"))
        assertEquals("p", e.str("password"))
        assertEquals("true", e.str("remote_random"))

        val servers = e["servers"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("am1.example.net", "am2.example.net"), servers.map { it.str("server") })
        assertEquals(listOf("udp", "tcp"), servers.map { it.str("network") })
        assertEquals("443", servers[1].str("server_port"))

        assertNull(e["cipher"])
        assertEquals("AES-256-GCM", e.str("data_ciphers_fallback"))
        assertEquals(
            listOf("AES-256-GCM", "AES-128-GCM", "CHACHA20-POLY1305"),
            e["data_ciphers"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals("SHA256", e.str("auth"))
        assertEquals("no", e.str("compression_lzo"))
        assertEquals("10s", e.str("ping_interval"))
        assertEquals("60s", e.str("ping_restart"))
        assertEquals("reneg-sec 0 turns renegotiation off", "true", e.str("renegotiate_disabled"))
        assertEquals("1400", e.str("mss_fix"))
        assertEquals("1500", e.str("mtu"))
        assertEquals("3", e.str("explicit_exit_notify"))

        val tls = e["tls"]!!.jsonObject
        assertEquals("server", tls.str("remote_certificate_tls"))
        assertEquals("am1.example.net", tls.str("server_name"))
        assertEquals("name", tls.str("server_name_type"))
        assertTrue(tls["certificate"]!!.jsonArray.first().jsonPrimitive.content.startsWith("-----BEGIN"))
        val wrap = tls["control_wrap"]!!.jsonObject
        assertEquals("tls_auth", wrap.str("type"))
        assertEquals("key-direction 1 is the client half", "client", wrap.str("direction"))
        assertTrue(wrap["key"]!!.jsonArray.any { it.jsonPrimitive.content.contains("Static key") })

        listOf("dev", "resolv-retry", "persist-key", "verb", "nobind", "client").forEach {
            assertNull(it, e[it])
        }
    }

    @Test
    fun `credentials written into the profile are used when none are given`() {
        val withCreds = profile + "\n<auth-user-pass>\nalice\nsecret\n</auth-user-pass>\n"
        val info = OvpnConfig.inspect(withCreds)
        assertTrue(info.hasInlineCredentials)
        val e = endpoint(OvpnConfig.toSingBoxFragment(withCreds))
        assertEquals("alice", e.str("username"))
        assertEquals("secret", e.str("password"))

        val typed = endpoint(OvpnConfig.toSingBoxFragment(withCreds, username = "bob", password = "other"))
        assertEquals("bob", typed.str("username"))
    }

    @Test
    fun `tls-crypt and a static key are carried in their own shapes`() {
        val crypt = profile.replace("<tls-auth>", "<tls-crypt>").replace("</tls-auth>", "</tls-crypt>")
        val wrap = endpoint(OvpnConfig.toSingBoxFragment(crypt))["tls"]!!.jsonObject["control_wrap"]!!.jsonObject
        assertEquals("tls_crypt", wrap.str("type"))
        assertNull("only tls-auth has a direction", wrap["direction"])

        val static = """
            remote p2p.example.net 1194
            proto udp
            secret
            <secret>
            -----BEGIN OpenVPN Static key V1-----
            abc
            -----END OpenVPN Static key V1-----
            </secret>
        """.trimIndent()
        val e = endpoint(OvpnConfig.toSingBoxFragment(static))
        assertEquals("static_key", e.str("mode"))
        assertTrue(e["static_key"]!!.jsonArray.isNotEmpty())
    }

    @Test
    fun `profiles this app cannot run say why`() {
        fun reason(text: String) = runCatching { OvpnConfig.toSingBoxFragment(text) }
            .exceptionOrNull().let { (it as OvpnConfig.UnsupportedException).reason }

        assertEquals(
            "certificates in separate files cannot come with an imported profile",
            OvpnConfig.Reason.ExternalFiles,
            reason("remote a.example 1194\nproto udp\nca /etc/openvpn/ca.crt\n"),
        )
        assertEquals(
            OvpnConfig.Reason.Tap,
            reason("remote a.example 1194\ndev tap0\nproto udp\n<ca>\nx\n</ca>\n"),
        )
        assertEquals(
            OvpnConfig.Reason.UnsupportedCredentials,
            reason("remote a.example 1194\nproto udp\npkcs12 bundle.p12\n"),
        )
        assertEquals(OvpnConfig.Reason.NoRemote, reason("client\nproto udp\n<ca>\nx\n</ca>\n"))
    }

    @Test
    fun `Test A - OVPN with valid inline CA produces non-empty tls certificate`() {
        val ovpn = """
            client
            dev tun
            remote server.test 1194
            <ca>
            -----BEGIN CERTIFICATE-----
            MIIBcaLine1
            -----END CERTIFICATE-----
            </ca>
        """.trimIndent()
        val e = endpoint(OvpnConfig.toSingBoxFragment(ovpn))
        val tls = e["tls"]!!.jsonObject
        val certs = tls["certificate"]?.jsonArray
        assertNotNull(certs)
        assertTrue(certs!!.isNotEmpty())
        assertTrue(certs.first().jsonPrimitive.content.startsWith("-----BEGIN"))
    }

    @Test
    fun `Test B - OVPN with CRLF and escaped newlines in CA is parsed correctly`() {
        val ovpn = "client\r\ndev tun\r\nremote server.test 1194\r\n<ca>\r\n-----BEGIN CERTIFICATE-----\\nMIIBcaLine1\\n-----END CERTIFICATE-----\r\n</ca>\r\n"
        val e = endpoint(OvpnConfig.toSingBoxFragment(ovpn))
        val tls = e["tls"]!!.jsonObject
        val certs = tls["certificate"]?.jsonArray
        assertNotNull(certs)
        assertTrue(certs!!.size >= 3)
    }

    @Test
    fun `Test C - OVPN with peer-fingerprint without CA produces tls peer_fingerprint and no certificate`() {
        val ovpn = """
            client
            dev tun
            remote server.test 1194
            peer-fingerprint AA:BB:CC:DD:EE:FF:11:22:33:44:55:66:77:88:99:00:AA:BB:CC:DD:EE:FF:11:22:33:44:55:66:77:88:99:00
        """.trimIndent()
        val e = endpoint(OvpnConfig.toSingBoxFragment(ovpn))
        val tls = e["tls"]!!.jsonObject
        assertNull(tls["certificate"])
        val fp = tls["peer_fingerprint"]?.jsonArray
        assertNotNull(fp)
        assertEquals(1, fp!!.size)
        assertEquals(64, fp.first().jsonPrimitive.content.length)
    }

    @Test
    fun `Test D - OVPN without CA and without fingerprint is rejected early`() {
        val ovpn = """
            client
            dev tun
            remote server.test 1194
        """.trimIndent()
        val err = runCatching { OvpnConfig.toSingBoxFragment(ovpn) }.exceptionOrNull()
        assertTrue(err is OvpnConfig.UnsupportedException)
        assertEquals(OvpnConfig.Reason.ExternalFiles, (err as OvpnConfig.UnsupportedException).reason)
    }

    @Test
    fun `Test E - OVPN with external ca ca-crt without inline ca is rejected as ExternalFiles`() {
        val ovpn = """
            client
            dev tun
            remote server.test 1194
            ca ca.crt
        """.trimIndent()
        val err = runCatching { OvpnConfig.toSingBoxFragment(ovpn) }.exceptionOrNull()
        assertTrue(err is OvpnConfig.UnsupportedException)
        assertEquals(OvpnConfig.Reason.ExternalFiles, (err as OvpnConfig.UnsupportedException).reason)
    }

    @Test
    fun `Test F - OVPN with cert and key produces client_certificate and client_key`() {
        val ovpn = """
            client
            dev tun
            remote server.test 1194
            <ca>
            -----BEGIN CERTIFICATE-----
            MIIBcaLine1
            -----END CERTIFICATE-----
            </ca>
            <cert>
            -----BEGIN CERTIFICATE-----
            MIICertLine1
            -----END CERTIFICATE-----
            </cert>
            <key>
            -----BEGIN PRIVATE KEY-----
            MIIKeyLine1
            -----END PRIVATE KEY-----
            </key>
        """.trimIndent()
        val e = endpoint(OvpnConfig.toSingBoxFragment(ovpn))
        val tls = e["tls"]!!.jsonObject
        assertNotNull(tls["client_certificate"])
        assertNotNull(tls["client_key"])
    }

    @Test
    fun `Test G - Malformed CA is rejected early`() {
        val ovpn = """
            client
            dev tun
            remote server.test 1194
            <ca>
            -----BEGIN CERTIFICATE-----
            NotValidBase64!!@@
            -----END CERTIFICATE-----
            </ca>
        """.trimIndent()
        val err = runCatching { OvpnConfig.toSingBoxFragment(ovpn) }.exceptionOrNull()
        assertTrue(err is OvpnConfig.UnsupportedException)
        assertEquals(OvpnConfig.Reason.ExternalFiles, (err as OvpnConfig.UnsupportedException).reason)
    }

    @Test
    fun `Test H - Malformed fingerprint is rejected early`() {
        val ovpn = """
            client
            dev tun
            remote server.test 1194
            peer-fingerprint SHORT
        """.trimIndent()
        val err = runCatching { OvpnConfig.toSingBoxFragment(ovpn) }.exceptionOrNull()
        assertTrue(err is OvpnConfig.UnsupportedException)
        assertEquals(OvpnConfig.Reason.ExternalFiles, (err as OvpnConfig.UnsupportedException).reason)
    }
}
