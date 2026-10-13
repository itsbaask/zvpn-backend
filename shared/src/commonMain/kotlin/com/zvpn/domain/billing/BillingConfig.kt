package com.zvpn.domain.billing

object BillingConfig {
    // Google Play product IDs — must match exactly what's in Play Console
    const val PRODUCT_PREMIUM_WEEKLY      = "zvpn_premium_weekly"
    const val PRODUCT_PREMIUM_MONTHLY     = "zvpn_premium_monthly"
    const val PRODUCT_PREMIUM_YEARLY      = "zvpn_premium_yearly"
    const val PRODUCT_PREMIUM_PLUS_YEARLY = "zvpn_premium_plus_yearly"

    // Pricing display (shown before real price loads from Play)
    const val PRICE_WEEKLY_DISPLAY       = "$2.99"
    const val PRICE_MONTHLY_DISPLAY      = "$7.99"
    const val PRICE_YEARLY_DISPLAY       = "$39.99"
    const val PRICE_PLUS_YEARLY_DISPLAY  = "$59.99"

    // Per-day cost for UI display
    const val PRICE_WEEKLY_PER_DAY       = "$0.42/day"
    const val PRICE_MONTHLY_PER_DAY      = "$0.26/day"
    const val PRICE_YEARLY_PER_DAY       = "$0.11/day"
    const val PRICE_PLUS_YEARLY_PER_DAY  = "$0.16/day"

    // Ad reward duration
    const val AD_REWARD_MINUTES = 30

    // Free tier limits
    const val FREE_DAILY_AD_REWARDS_MAX = 5   // max free ad rewards per day
    const val FREE_SERVER_TIER = "free"
    const val PREMIUM_SERVER_TIER = "premium"
    const val PREMIUM_PLUS_SERVER_TIER = "premium_plus"
}

enum class UserTier(
    val label: String,
    val canAccessPremium: Boolean,
    val canAccessPremiumPlus: Boolean,
    val hasAds: Boolean,
    val canUseProxyChain: Boolean,
    val canExportConfigs: Boolean,
) {
    Free("Free", canAccessPremium = false, canAccessPremiumPlus = false, hasAds = true, canUseProxyChain = false, canExportConfigs = false),
    Premium("Premium", canAccessPremium = true, canAccessPremiumPlus = false, hasAds = false, canUseProxyChain = false, canExportConfigs = true),
    PremiumPlus("Premium+", canAccessPremium = true, canAccessPremiumPlus = true, hasAds = false, canUseProxyChain = true, canExportConfigs = true),
    AdRewarded("Free (Rewarded)", canAccessPremium = true, canAccessPremiumPlus = false, hasAds = true, canUseProxyChain = false, canExportConfigs = false);

    companion object {
        fun canAccess(userTier: UserTier, serverTier: String): Boolean = when (serverTier.lowercase()) {
            "free" -> true
            "premium", "pro" -> userTier.canAccessPremium
            "premium_plus", "vip" -> userTier.canAccessPremiumPlus
            else -> true
        }
    }
}
