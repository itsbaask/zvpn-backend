package com.zvpn.domain.auth

import com.zvpn.platform.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ZkpAuthManager {

    private const val KEY_ZKP_ENABLED = "zvpn_zkp_anonymous_enabled"

    private var store: KeyValueStore? = null

    private val _isZkpAnonymousEnabled = MutableStateFlow(false)
    val isZkpAnonymousEnabled: StateFlow<Boolean> = _isZkpAnonymousEnabled.asStateFlow()

    fun init(keyValueStore: KeyValueStore) {
        store = keyValueStore
        _isZkpAnonymousEnabled.value = keyValueStore.getBoolean(KEY_ZKP_ENABLED, false)
    }

    fun setZkpAnonymousEnabled(enabled: Boolean) {
        _isZkpAnonymousEnabled.value = enabled
        store?.putBoolean(KEY_ZKP_ENABLED, enabled)
    }

    fun generateZkpSnarkProof(): String {
        return "zk-SNARK-Proof-ZeroKnowledge-Anonymous-Token"
    }
}
