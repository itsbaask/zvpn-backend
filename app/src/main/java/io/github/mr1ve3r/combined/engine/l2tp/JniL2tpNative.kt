package io.github.mr1ve3r.combined.engine.l2tp

import io.github.evokelektrique.tunnelforge.VpnBridge

class JniL2tpNative : L2tpNative {
    override fun setSocketProtectionEnabled(enabled: Boolean) {
        VpnBridge.nativeSetSocketProtectionEnabled(enabled)
    }

    override fun negotiate(
        server: String,
        username: String,
        password: String,
        presharedKey: String,
        mtu: Int,
        outClientIpv4: IntArray,
        outPrimaryDnsIpv4: IntArray,
        outSecondaryDnsIpv4: IntArray,
    ): Int {
        return VpnBridge.nativeNegotiate(
            server = server,
            user = username,
            password = password,
            psk = presharedKey,
            mtu = mtu,
            clientIpv4 = outClientIpv4,
            primaryDns = outPrimaryDnsIpv4,
            secondaryDns = outSecondaryDnsIpv4,
        )
    }

    override fun startLoop(tunFd: Int): Int {
        return VpnBridge.nativeStartLoop(tunFd)
    }

    override fun stopTunnel() {
        VpnBridge.nativeStopTunnel()
    }
}
