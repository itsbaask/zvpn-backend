package com.zvpn.domain.auth

import com.zvpn.domain.ads.UnityAdsS2SManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserAuthAndAdsTest {

    @Test
    fun `user session pro status updates correctly`() {
        val user = UserSession(userId = "user_123", email = "test@example.com", isPro = true)
        UserAuthManager.updateSession(user)
        assertTrue(UserAuthManager.session.value.isProActive)
        assertEquals("user_123", UserAuthManager.session.value.userId)

        UserAuthManager.signOut()
        assertFalse(UserAuthManager.session.value.isProActive)
    }

    @Test
    fun `ads are suppressed for pro users`() {
        assertFalse(UnityAdsS2SManager.shouldShowAds(isPro = true))
        assertTrue(UnityAdsS2SManager.shouldShowAds(isPro = false))
    }

    @Test
    fun `ad reward token validation checks timestamp and user`() {
        val now = System.currentTimeMillis()
        assertTrue(UnityAdsS2SManager.isRewardTokenValid("user_1", now, "valid_token_12345678"))
        assertFalse(UnityAdsS2SManager.isRewardTokenValid("user_1", now - 600000L, "valid_token_12345678"))
        assertFalse(UnityAdsS2SManager.isRewardTokenValid("", now, "valid_token_12345678"))
    }
}
