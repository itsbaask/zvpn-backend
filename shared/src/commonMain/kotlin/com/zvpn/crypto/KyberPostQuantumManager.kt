package com.zvpn.crypto

import com.zvpn.platform.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object KyberPostQuantumManager {

    private const val KEY_PQ_ENABLED = "zvpn_pq_enabled"

    private var store: KeyValueStore? = null

    private val _isPostQuantumEnabled = MutableStateFlow(false)
    val isPostQuantumEnabled: StateFlow<Boolean> = _isPostQuantumEnabled.asStateFlow()

    fun init(keyValueStore: KeyValueStore) {
        store = keyValueStore
        _isPostQuantumEnabled.value = keyValueStore.getBoolean(KEY_PQ_ENABLED, false)
    }

    fun setPostQuantumEnabled(enabled: Boolean) {
        _isPostQuantumEnabled.value = enabled
        store?.putBoolean(KEY_PQ_ENABLED, enabled)
    }

    fun generateHybridKeyExchangePayload(): String {
        return "PQ-Kyber1024-X25519-HybridKey-Signature"
    }
}
