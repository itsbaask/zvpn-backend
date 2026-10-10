package io.github.evokelektrique.tunnelforge

object VpnBridge {
    init {
        System.loadLibrary("tunnel_engine")
    }

    @JvmStatic
    external fun nativeNegotiate(
        server: String,
        user: String,
        password: String,
        psk: String,
        mtu: Int,
        clientIpv4: IntArray,
        primaryDns: IntArray,
        secondaryDns: IntArray,
    ): Int

    @JvmStatic
    external fun nativeStartLoop(tunFd: Int): Int

    @JvmStatic
    external fun nativeStopTunnel()

    @JvmStatic
    external fun nativeSetSocketProtectionEnabled(enabled: Boolean)
}
