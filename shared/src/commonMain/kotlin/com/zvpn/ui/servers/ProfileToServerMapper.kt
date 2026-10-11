package com.zvpn.ui.servers

import com.zvpn.data.map.RegionResolver
import com.zvpn.domain.config.VpnProfile

object ProfileToServerMapper {
    fun map(profile: VpnProfile): PublicServer {
        val rawName = profile.name.trim()
        val flagMatch = Regex("""^([\uD83C][\uDDE6-\uDDFF]{2})\s*""").find(rawName)
        val extractedFlag = flagMatch?.groupValues?.get(1)
        val nameWithoutFlag = if (extractedFlag != null) rawName.substring(flagMatch.range.last + 1).trim() else rawName

        val countryName = nameWithoutFlag.substringBefore(" -").trim().ifBlank { "Global" }
        
        val cc = profile.countryCode?.uppercase()?.takeIf { it.length == 2 }
            ?: countryNameToCode(countryName)
            ?: "US"

        val flagEmoji = extractedFlag ?: countryCodeToEmoji(cc)
        val continent = RegionResolver.resolve(cc)

        val tier = when {
            profile.name.contains("VIP", true) || profile.name.contains("Plus", true) -> ServerTier.PremiumPlus
            profile.name.contains("PRO", true) || profile.name.contains("Premium", true) -> ServerTier.Premium
            else -> ServerTier.Free
        }

        val cleanDisplayName = profile.name.replace(Regex("""\s*\[.*?]"""), "").trim()

        return PublicServer(
            id = profile.id,
            displayName = cleanDisplayName,
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

    private fun countryNameToCode(name: String): String? {
        val n = name.lowercase()
        return when {
            "united states" in n || "us" in n -> "US"
            "united kingdom" in n || "uk" in n || "britain" in n -> "GB"
            "japan" in n -> "JP"
            "south korea" in n || "korea" in n -> "KR"
            "germany" in n -> "DE"
            "france" in n -> "FR"
            "canada" in n -> "CA"
            "netherlands" in n -> "NL"
            "singapore" in n -> "SG"
            "australia" in n -> "AU"
            "vietnam" in n -> "VN"
            "turkey" in n -> "TR"
            "switzerland" in n -> "CH"
            "sweden" in n -> "SE"
            "poland" in n -> "PL"
            "brazil" in n -> "BR"
            "india" in n -> "IN"
            "indonesia" in n -> "ID"
            "china" in n || "hong kong" in n -> "HK"
            "taiwan" in n -> "TW"
            "russia" in n -> "RU"
            "italy" in n -> "IT"
            "spain" in n -> "ES"
            "finland" in n -> "FI"
            "norway" in n -> "NO"
            "denmark" in n -> "DK"
            else -> null
        }
    }

    private fun countryCodeToEmoji(cc: String): String {
        if (cc.length != 2) return "🌐"
        val firstChar = Character.codePointAt(cc, 0) - 0x41 + 0x1F1E6
        val secondChar = Character.codePointAt(cc, 1) - 0x41 + 0x1F1E6
        return String(intArrayOf(firstChar, secondChar), 0, 2)
    }
}
