package com.zvpn.domain.billing

object BillingConfig {
    // ── Google Play Subscriptions ─────────────────────────────────────────────
    const val PRODUCT_PREMIUM_WEEKLY      = "zvpn_premium_weekly"
    const val PRODUCT_PREMIUM_MONTHLY     = "zvpn_premium_monthly"
    const val PRODUCT_PREMIUM_YEARLY      = "zvpn_premium_yearly"
    const val PRODUCT_PREMIUM_PLUS_YEARLY = "zvpn_premium_plus_yearly"

    // ── Consumable In-App Point Packs ──────────────────────────────────────────
    const val PRODUCT_PACK_500_PTS  = "zvpn_pack_500_pts"   // $1.99 -> +500 Pts
    const val PRODUCT_PACK_1200_PTS = "zvpn_pack_1200_pts"  // $3.99 -> +1200 Pts
    const val PRODUCT_PACK_3000_PTS = "zvpn_pack_3000_pts"  // $7.99 -> +3000 Pts

    // ── Display Pricing ────────────────────────────────────────────────────────
    const val PRICE_WEEKLY_DISPLAY       = "$2.99"
    const val PRICE_MONTHLY_DISPLAY      = "$7.99"
    const val PRICE_YEARLY_DISPLAY       = "$39.99"
    const val PRICE_PLUS_YEARLY_DISPLAY  = "$59.99"

    const val PRICE_WEEKLY_PER_DAY       = "$0.42/day"
    const val PRICE_MONTHLY_PER_DAY      = "$0.26/day"
    const val PRICE_YEARLY_PER_DAY       = "$0.11/day"
    const val PRICE_PLUS_YEARLY_PER_DAY  = "$0.16/day"

    // ── Tier IDs ───────────────────────────────────────────────────────────────
    const val FREE_SERVER_TIER         = "free"
    const val PREMIUM_SERVER_TIER      = "premium"
    const val PREMIUM_PLUS_SERVER_TIER = "premium_plus"

    // ── Points Economy ─────────────────────────────────────────────────────────
    /** Points awarded per rewarded ad watch */
    const val AD_REWARD_POINTS = 20

    /** Points awarded per interstitial ad (auto, no choice) */
    const val INTERSTITIAL_REWARD_POINTS = 5

    /** Welcome gift on first install */
    const val WELCOME_GIFT_POINTS = 100

    /** Unlimited ads allowed per day */
    const val FREE_DAILY_AD_CAP = 999999

    /** Time-based ad reward duration (legacy, still supported) */
    const val AD_REWARD_MINUTES = 30

    // ── Points Cost Per Minute By Server Tier ─────────────────────────────────
    /**
     * Each 60-second tick while connected costs this many points.
     * Paid subscribers are exempt (cost = 0).
     */
    const val COST_FREE_SERVER_PER_MIN         = 1   // free servers: cheapest
    const val COST_PREMIUM_SERVER_PER_MIN      = 4   // premium servers
    const val COST_PREMIUM_PLUS_SERVER_PER_MIN = 10  // premium+ servers: most expensive

    // ── Protocol Cost Multiplier (applied on top of tier cost) ────────────────
    /**
     * nextGen protocols add a multiplier on the base cost.
     * Result is always rounded up to nearest integer.
     *
     * Examples (premium server):
     *   Shadowsocks / OpenVPN  → 4 × 1.0 = 4 pts/min
     *   VLESS / Trojan / WG    → 4 × 1.5 = 6 pts/min
     *   Hysteria2 / TUIC       → 4 × 2.5 = 10 pts/min (capped at COST_PREMIUM_PLUS)
     */
    val PROTOCOL_COST_MULTIPLIER: Map<String, Double> = mapOf(
        // Next-gen (most expensive)
        "hysteria2"   to 2.5,
        "hysteria"    to 2.5,
        "tuic"        to 2.5,
        "juicity"     to 2.0,
        // High-security mid-tier
        "vless"       to 1.5,
        "trojan"      to 1.5,
        "wireguard"   to 1.5,
        "amneziawg"   to 1.5,
        "ikev2"       to 1.2,
        "sstp"        to 1.2,
        "openconnect" to 1.2,
        // Standard
        "vmess"       to 1.0,
        "shadowsocks" to 1.0,
        "openvpn"     to 1.0,
        // Budget / anonymous
        "socks5"      to 0.8,
        "http"        to 0.8,
        "ssh"         to 0.8,
        "tor"         to 0.5,
        "psiphon"     to 0.5,
        "dnstunnel"   to 0.5,
    )

    // ── Premium Feature Point Costs (one-time unlock per session) ─────────────
    const val FEATURE_PROXY_CHAIN_UNLOCK   = 50   // one ProxyChain session
    const val FEATURE_SPLIT_TUNNEL_UNLOCK  = 30   // split-tunnelling session
    const val FEATURE_SPEEDTEST_COST       = 10   // run a speed test
    const val FEATURE_PING_ALL_COST        = 5    // ping all servers
    const val FEATURE_EXPORT_CONFIG_COST   = 20   // export/share config
    const val FEATURE_BROWSER_UNLOCK       = 15   // private browser session
    const val FEATURE_MY_IP_INSPECTOR      = 15   // MY IP & ISP inspector
    const val FEATURE_WORLD_MAP_UNLOCK     = 15   // 3D World Map
    const val FEATURE_AI_CHAT_COST         = 25   // AI Chat query
    const val FEATURE_GAMING_BOOST_COST    = 40   // Gaming Boost 1h
    const val FEATURE_VAULT_UNLOCK         = 30   // Encrypted Vault
    const val FEATURE_TOR_BRIDGES_UNLOCK   = 35   // Tor Bridges
    const val FEATURE_DNS_PROTOCOLS_UNLOCK = 15   // DoH / DoT / DoQ
    const val FEATURE_FRAGMENT_UNLOCK      = 20   // SNI Fragmentation
    const val FEATURE_MUX_UNLOCK           = 20   // QUIC Mux
    const val FEATURE_LIVE_MONITOR_UNLOCK  = 15   // Live Network Monitor
    const val FEATURE_ASSETS_SYNC_COST     = 15   // GeoIP Auto-Sync
    const val FEATURE_SINGBOX_EDITOR_UNLOCK = 25  // Sing-Box JSON Editor
    const val FEATURE_MTU_TEST_COST         = 10  // MTU auto-optimisation test
    const val FEATURE_ADVANCED_PORTS_UNLOCK = 15  // Advanced port & proxy settings
    const val FEATURE_SSH_KEYS_UNLOCK       = 25  // SSH remote tunnel keys
    const val FEATURE_THEME_STYLE_UNLOCK    = 10  // Custom UI theme & connect-button style
    const val FEATURE_AMNEZIA_WG_UNLOCK     = 25  // AmneziaWG obfuscated WireGuard
    const val FEATURE_OPENCONNECT_UNLOCK    = 35  // OpenConnect SSL-VPN session
    const val FEATURE_DNS_TUNNEL_UNLOCK     = 30  // DNS Stealth Tunnel session

    // ── Points Cap (Unlimited) ────────────────────────────────────────────────
    const val MAX_DAILY_EARNED_POINTS = 999999

    fun pointsCostPerMin(tier: String, protocol: String): Int {
        val base = when (tier.lowercase()) {
            PREMIUM_PLUS_SERVER_TIER, "vip"       -> COST_PREMIUM_PLUS_SERVER_PER_MIN
            PREMIUM_SERVER_TIER, "pro"             -> COST_PREMIUM_SERVER_PER_MIN
            else                                   -> COST_FREE_SERVER_PER_MIN
        }
        val multiplier = PROTOCOL_COST_MULTIPLIER[protocol.lowercase()] ?: 1.0
        val cost = (base * multiplier).toInt().coerceAtLeast(1)
        return cost.coerceAtMost(COST_PREMIUM_PLUS_SERVER_PER_MIN)
    }
}

enum class UserTier(
    val label: String,
    val canAccessPremium: Boolean,
    val canAccessPremiumPlus: Boolean,
    val hasAds: Boolean,
    val canUseProxyChain: Boolean,
    val canExportConfigs: Boolean,
    val pointsCostMultiplier: Double,   // 0.0 = exempt (paid subscriber)
) {
    Free(
        label = "Free",
        canAccessPremium = false,
        canAccessPremiumPlus = false,
        hasAds = true,
        canUseProxyChain = false,
        canExportConfigs = false,
        pointsCostMultiplier = 1.0,
    ),
    Premium(
        label = "Premium",
        canAccessPremium = true,
        canAccessPremiumPlus = false,
        hasAds = false,
        canUseProxyChain = false,
        canExportConfigs = true,
        pointsCostMultiplier = 0.0,   // paid — no point deduction
    ),
    PremiumPlus(
        label = "Premium+",
        canAccessPremium = true,
        canAccessPremiumPlus = true,
        hasAds = false,
        canUseProxyChain = true,
        canExportConfigs = true,
        pointsCostMultiplier = 0.0,   // paid — no point deduction
    ),
    AdRewarded(
        label = "Free (Rewarded)",
        canAccessPremium = true,
        canAccessPremiumPlus = false,
        hasAds = true,
        canUseProxyChain = false,
        canExportConfigs = false,
        pointsCostMultiplier = 0.5,   // half cost during ad-reward window
    );

    companion object {
        fun canAccess(userTier: UserTier, serverTier: String): Boolean = when (serverTier.lowercase()) {
            "free"                               -> true
            "premium", "pro"                     -> userTier.canAccessPremium
            "premium_plus", "vip"                -> userTier.canAccessPremiumPlus
            else                                 -> true
        }

        /** Calculate actual points cost per minute for a given user+server combo */
        fun effectiveCostPerMin(userTier: UserTier, serverTier: String, protocol: String): Int {
            if (userTier.pointsCostMultiplier == 0.0) return 0 // paid subscriber exempt
            val base = BillingConfig.pointsCostPerMin(serverTier, protocol)
            return (base * userTier.pointsCostMultiplier).toInt().coerceAtLeast(1)
        }
    }
}
