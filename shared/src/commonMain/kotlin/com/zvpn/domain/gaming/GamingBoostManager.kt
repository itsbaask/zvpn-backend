package com.zvpn.domain.gaming

import com.zvpn.platform.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object GamingBoostManager {

    private const val KEY_GAMING_BOOST_ENABLED = "zvpn_gaming_boost_enabled"
    private const val KEY_OPTIMAL_MTU = "zvpn_gaming_optimal_mtu"

    const val GAMING_OPTIMAL_MTU = 1380
    const val DEFAULT_MTU = 1420

    private var store: KeyValueStore? = null

    private val _isGamingBoostEnabled = MutableStateFlow(false)
    val isGamingBoostEnabled: StateFlow<Boolean> = _isGamingBoostEnabled.asStateFlow()

    private val _activeMtu = MutableStateFlow(DEFAULT_MTU)
    val activeMtu: StateFlow<Int> = _activeMtu.asStateFlow()

    fun init(keyValueStore: KeyValueStore) {
        store = keyValueStore
        val enabled = keyValueStore.getBoolean(KEY_GAMING_BOOST_ENABLED, false)
        _isGamingBoostEnabled.value = enabled
        _activeMtu.value = if (enabled) GAMING_OPTIMAL_MTU else DEFAULT_MTU
    }

    fun setGamingBoostEnabled(enabled: Boolean) {
        _isGamingBoostEnabled.value = enabled
        _activeMtu.value = if (enabled) GAMING_OPTIMAL_MTU else DEFAULT_MTU
        store?.putBoolean(KEY_GAMING_BOOST_ENABLED, enabled)
        store?.putInt(KEY_OPTIMAL_MTU, if (enabled) GAMING_OPTIMAL_MTU else DEFAULT_MTU)
    }

    fun getOptimalGamingUdpConfig(): String {
        return """
            [GamingBoost]
            UDP_Priority=High
            Jitter_Minimizer=Enabled
            Optimal_MTU=$GAMING_OPTIMAL_MTU
            Low_Latency_Direct=Active
        """.trimIndent()
    }
}
