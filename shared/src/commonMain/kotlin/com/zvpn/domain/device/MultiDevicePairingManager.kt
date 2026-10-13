package com.zvpn.domain.device

import com.zvpn.platform.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PairedDevice(
    val deviceId: String,
    val deviceName: String,
    val deviceType: String, // Phone, Android TV, Wear OS, Tablet
    val lastActiveMs: Long,
)

object MultiDevicePairingManager {

    private const val KEY_PAIRED_DEVICES = "zvpn_paired_devices_json"

    private var store: KeyValueStore? = null

    private val _devices = MutableStateFlow<List<PairedDevice>>(emptyList())
    val devices: StateFlow<List<PairedDevice>> = _devices.asStateFlow()

    fun init(keyValueStore: KeyValueStore) {
        store = keyValueStore
    }

    fun generateTvPairingQrCode(): String {
        return "zvpn://pair-tv?token=PAIR_TOKEN_SECURE_QR_99"
    }

    fun canConnectDevice(currentCount: Int, maxLimit: Int): Boolean {
        return currentCount < maxLimit
    }
}
