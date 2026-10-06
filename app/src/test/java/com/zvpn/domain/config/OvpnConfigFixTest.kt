package com.zvpn.domain.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OvpnConfigFixTest {

    @Test
    fun `openvpn config without direct outbound gets direct added after fix`() {
        val input = """{"endpoints":[{"type":"openvpn-client","tag":"ovpn1","server":"1.2.3.4"}]}"""
        val fixed = OvpnConfig.fixOpenVpnConfig(input)
        assertTrue(fixed.contains("\"type\": \"direct\"") || fixed.contains("\"type\":\"direct\""))
        assertTrue(fixed.contains("\"tag\": \"direct\"") || fixed.contains("\"tag\":\"direct\""))
    }

    @Test
    fun `openvpn config with direct outbound remains unchanged`() {
        val input = """{"endpoints":[{"type":"openvpn-client","tag":"ovpn1"}],"outbounds":[{"type":"direct","tag":"direct"}]}"""
        val fixed = OvpnConfig.fixOpenVpnConfig(input)
        assertEquals(input, fixed)
    }

    @Test
    fun `non openvpn config returns identical to original text`() {
        val vless = """{"outbounds":[{"type":"vless","tag":"proxy","server":"1.2.3.4"}]}"""
        val fixedVless = OvpnConfig.fixOpenVpnConfig(vless)
        assertEquals(vless, fixedVless)

        val wireguard = """{"endpoints":[{"type":"wireguard","tag":"wg1"}]}"""
        val fixedWg = OvpnConfig.fixOpenVpnConfig(wireguard)
        assertEquals(wireguard, fixedWg)

        val vmess = """{"outbounds":[{"type":"vmess","tag":"proxy"}]}"""
        val fixedVmess = OvpnConfig.fixOpenVpnConfig(vmess)
        assertEquals(vmess, fixedVmess)
    }

    @Test
    fun `invalid json returns as is without exception`() {
        val invalid = "not a valid json {{"
        val result = OvpnConfig.fixOpenVpnConfig(invalid)
        assertEquals(invalid, result)
    }
}
