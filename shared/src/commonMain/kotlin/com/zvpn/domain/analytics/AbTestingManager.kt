package com.zvpn.domain.analytics

import com.zvpn.platform.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PaywallVariant {
    VariantA_Standard,
    VariantB_DailyRate,
    VariantC_CountdownTimer,
}

object AbTestingManager {

    private const val KEY_PAYWALL_VARIANT = "zvpn_ab_paywall_variant"

    private var store: KeyValueStore? = null

    private val _activeVariant = MutableStateFlow(PaywallVariant.VariantB_DailyRate)
    val activeVariant: StateFlow<PaywallVariant> = _activeVariant.asStateFlow()

    fun init(keyValueStore: KeyValueStore) {
        store = keyValueStore
        val variantIndex = keyValueStore.getInt(KEY_PAYWALL_VARIANT, 1)
        _activeVariant.value = PaywallVariant.entries.getOrElse(variantIndex) { PaywallVariant.VariantB_DailyRate }
    }
}
