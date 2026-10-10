package io.github.mr1ve3r.combined.engine.l2tp

import androidx.annotation.Keep

@Keep
object L2tpNativeCallbacks {
    @Volatile
    private var active: L2tpEngine? = null

    internal fun install(engine: L2tpEngine) {
        active = engine
    }

    internal fun uninstall(engine: L2tpEngine) {
        if (active === engine) {
            active = null
        }
    }

    @JvmStatic
    fun protect(fd: Int): Boolean? = active?.protectNativeSocket(fd)

    @JvmStatic
    fun tunnelReady(detail: String?) {
        active?.onNativeTunnelReady(detail)
    }

    @JvmStatic
    fun nativeLog(priority: Int, tag: String, message: String) {
        active?.onNativeLog(priority, tag, message)
    }
}
