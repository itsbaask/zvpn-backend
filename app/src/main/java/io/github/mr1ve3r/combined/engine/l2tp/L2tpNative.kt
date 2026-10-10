package io.github.mr1ve3r.combined.engine.l2tp

interface L2tpNative {
    fun setSocketProtectionEnabled(enabled: Boolean)

    fun negotiate(
        server: String,
        username: String,
        password: String,
        presharedKey: String,
        mtu: Int,
        outClientIpv4: IntArray,
        outPrimaryDnsIpv4: IntArray,
        outSecondaryDnsIpv4: IntArray,
    ): Int

    fun startLoop(tunFd: Int): Int

    fun stopTunnel()
}
