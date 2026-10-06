package com.zvpn.domain.ads

import org.junit.Assert.assertEquals
import org.junit.Test

class UnityAdsConfigTest {

    @Test
    fun `unity ads game ids and placement ids match dashboard settings`() {
        assertEquals("6191257", UnityAdsConfig.GAME_ID_ANDROID)
        assertEquals("6191256", UnityAdsConfig.GAME_ID_IOS)
        assertEquals("Rewarded_Android", UnityAdsConfig.PLACEMENT_REWARDED_ANDROID)
        assertEquals("Interstitial_Android", UnityAdsConfig.PLACEMENT_INTERSTITIAL_ANDROID)
        assertEquals("Banner_Android", UnityAdsConfig.PLACEMENT_BANNER_ANDROID)
    }
}
