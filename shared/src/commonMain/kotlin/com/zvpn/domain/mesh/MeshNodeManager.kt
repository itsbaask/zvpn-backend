package com.zvpn.domain.mesh

import com.zvpn.domain.ads.PointsManager
import com.zvpn.platform.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MeshNodeManager {

    private const val KEY_MESH_SHARING_ENABLED = "zvpn_mesh_sharing_enabled"
    private const val KEY_RELAYED_BYTES = "zvpn_mesh_relayed_bytes"

    private var store: KeyValueStore? = null

    private val _isMeshSharingEnabled = MutableStateFlow(false)
    val isMeshSharingEnabled: StateFlow<Boolean> = _isMeshSharingEnabled.asStateFlow()

    private val _relayedBytes = MutableStateFlow(0L)
    val relayedBytes: StateFlow<Long> = _relayedBytes.asStateFlow()

    fun init(keyValueStore: KeyValueStore) {
        store = keyValueStore
        val enabled = keyValueStore.getBoolean(KEY_MESH_SHARING_ENABLED, false)
        _isMeshSharingEnabled.value = enabled
        _relayedBytes.value = keyValueStore.getString(KEY_RELAYED_BYTES)?.toLongOrNull() ?: 0L
    }

    fun setMeshSharingEnabled(enabled: Boolean) {
        _isMeshSharingEnabled.value = enabled
        store?.putBoolean(KEY_MESH_SHARING_ENABLED, enabled)
    }

    fun recordRelayedData(bytes: Long) {
        if (!_isMeshSharingEnabled.value || bytes <= 0) return
        val newRelayed = _relayedBytes.value + bytes
        _relayedBytes.value = newRelayed
        store?.putString(KEY_RELAYED_BYTES, newRelayed.toString())

        // Reward +100 Points for every 1 GB relayed
        val gigabytes = bytes / (1024 * 1024 * 1024)
        if (gigabytes >= 1) {
            PointsManager.addPoints((gigabytes * 100).toInt())
        }
    }
}
