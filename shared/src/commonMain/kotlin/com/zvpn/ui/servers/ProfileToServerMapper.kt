package com.zvpn.ui.servers

import com.zvpn.data.map.RegionResolver
import com.zvpn.domain.config.VpnProfile

object ProfileToServerMapper {
    fun map(profile: VpnProfile): PublicServer {
        val cc = profile.countryCode?.uppercase()?.takeIf { it.length == 2 } ?: "US"
        val flagEmoji = countryCodeToEmoji(cc)
        val continent = RegionResolver.resolve(cc)
        val countryName = profile.name.substringBefore(" -").trim().ifBlank { "Global Node" }

        val tier = when {
            profile.name.contains("VIP", true) || profile.name.contains("Plus", true) -> ServerTier.PremiumPlus
            profile.name.contains("PRO", true) || profile.name.contains("Premium", true) -> ServerTier.Premium
            else -> ServerTier.Free
        }

        return PublicServer(
            id = profile.id,
            displayName = profile.name,
            countryCode = cc,
            countryName = countryName,
            flag = flagEmoji,
            protocolLabel = profile.protocol.uppercase(),
            speedMbps = 35.0f,
            latencyMs = profile.lastPingMs,
            tier = tier,
            continent = continent,
            profileUrl = "",
            configUri = profile.rawPayload() ?: "",
            engine = profile.protocol.uppercase()
        )
    }

    private fun countryCodeToEmoji(cc: String): String {
        if (cc.length != 2) return "🌐"
        val firstChar = Character.codePointAt(cc, 0) - 0x41 + 0x1F1E6
        val secondChar = Character.codePointAt(cc, 1) - 0x41 + 0x1F1E6
        return String(intArrayOf(firstChar, secondChar), 0, 2)
    }
}
