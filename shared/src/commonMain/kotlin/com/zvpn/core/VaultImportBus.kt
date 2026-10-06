package com.zvpn.core

import com.zvpn.crypto.ZvxMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object VaultImportBus {
    class Pending(val bytes: ByteArray, val metadata: ZvxMetadata)

    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()

    fun request(bytes: ByteArray, metadata: ZvxMetadata) {
        _pending.value = Pending(bytes, metadata)
    }

    fun clear() {
        _pending.value = null
    }
}
