package com.zvpn.ui.servers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.zvpn.domain.config.VpnProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

object ProfileToServerMapper {

    private const val UNKNOWN_CODE = "XX"
    private const val DEFAULT_COUNTRY = "Unknown"
    private const val DEFAULT_CONTINENT = "Other"

    // Whole-word matches only: "VIP", "Pro", "Plus" — but not "Proxy" or "Provider".
    private val TIER_PLUS = Regex("""\b(?:vip|plus|premium\+)\b""", RegexOption.IGNORE_CASE)
    private val TIER_PRO = Regex("""\b(?:pro|premium)\b""", RegexOption.IGNORE_CASE)

    fun map(profile: VpnProfile): PublicServer {
        val rawName = profile.name.trim()

        val countryName = profile.countryName ?: DEFAULT_COUNTRY
        val cc = profile.countryCode?.takeIf { it.isNotBlank() } ?: UNKNOWN_CODE
        val flagEmoji = profile.flag ?: "🌐"
        val continent = profile.continent ?: DEFAULT_CONTINENT

        val tier = when {
            TIER_PLUS.containsMatchIn(rawName) -> ServerTier.PremiumPlus
            TIER_PRO.containsMatchIn(rawName) -> ServerTier.Premium
            else -> ServerTier.Free
        }

        return PublicServer(
            id = profile.id,
            displayName = rawName,
            countryCode = cc,
            countryName = countryName,
            flag = flagEmoji,
            protocolLabel = profile.protocol.uppercase(),
            speedMbps = 35.0f,
            latencyMs = profile.lastPingMs?.takeIf { it > 0 },
            tier = tier,
            continent = continent,
            profileUrl = "",
            configUri = profile.rawPayload() ?: "",
            engine = profile.protocol.uppercase(),
            isSystem = profile.isSystemBackend,
        )
    }
}

// ───────────────────────────── Off-main-thread mapping with per-profile cache ─────────────────────────────

private class MapCache {
    var map: Map<String, Pair<VpnProfile, PublicServer>> = emptyMap()
}

/**
 * Maps profiles to [PublicServer] on Dispatchers.Default.
 * A profile that is the same instance as last time is NOT re-mapped, so a ping update on one
 * server only re-maps that one server instead of the whole list.
 */
@Composable
fun rememberPublicServers(profiles: List<VpnProfile>): List<PublicServer> {
    val cache = remember { MapCache() }
    val result by produceState(emptyList<PublicServer>(), profiles) {
        value = withContext(Dispatchers.Default) {
            val old = cache.map
            val next = HashMap<String, Pair<VpnProfile, PublicServer>>(profiles.size * 2)
            val out = ArrayList<PublicServer>(profiles.size)
            profiles.forEachIndexed { i, p ->
                if (i and 127 == 0) ensureActive()
                val hit = old[p.id]?.takeIf { it.first === p }
                val server = hit?.second ?: ProfileToServerMapper.map(p)
                next[p.id] = p to server
                out += server
            }
            cache.map = next
            out
        }
    }
    return result
}
