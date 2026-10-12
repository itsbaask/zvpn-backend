package com.zvpn.ui.servers

import com.zvpn.data.map.RegionResolver
import com.zvpn.domain.config.VpnProfile

object ProfileToServerMapper {

    private const val UNKNOWN_CODE = "XX"
    private const val GLOBAL = "Global"

    // Everything after the first separator (" - ", " | ", " · ", "[tag]", "(note)") is not part of the country.
    private val COUNTRY_SPLIT = Regex("""\s+[-–—|·•]\s+|\s*[\[(]""")
    private val BRACKET_TAG = Regex("""\s*\[.*?]""")

    // Whole-word matches only: "VIP", "Pro", "Plus" — but not "Proxy" or "Provider".
    private val TIER_PLUS = Regex("""\b(?:vip|plus|premium\+)\b""", RegexOption.IGNORE_CASE)
    private val TIER_PRO = Regex("""\b(?:pro|premium)\b""", RegexOption.IGNORE_CASE)

    fun map(profile: VpnProfile): PublicServer {
        val rawName = profile.name.trim()
        val (extractedFlag, nameWithoutFlag) = splitLeadingFlag(rawName)

        val countryName = nameWithoutFlag.split(COUNTRY_SPLIT, limit = 2).first().trim().ifBlank { GLOBAL }

        // Priority: explicit country code on the profile → code guessed from the name → unknown.
        // (Unknown no longer silently becomes "US".)
        val cc = profile.countryCode?.uppercase()?.takeIf { it.length == 2 }
            ?: countryNameToCode(countryName)
            ?: UNKNOWN_CODE

        val known = cc != UNKNOWN_CODE
        val flagEmoji = extractedFlag ?: if (known) countryCodeToEmoji(cc) else "🌐"
        val continent = if (known) RegionResolver.resolve(cc) else GLOBAL

        val tier = when {
            TIER_PLUS.containsMatchIn(rawName) -> ServerTier.PremiumPlus
            TIER_PRO.containsMatchIn(rawName) -> ServerTier.Premium
            else -> ServerTier.Free
        }

        val cleanDisplayName = nameWithoutFlag.replace(BRACKET_TAG, "").trim().ifBlank { rawName }

        return PublicServer(
            id = profile.id,
            displayName = cleanDisplayName,
            countryCode = cc,
            countryName = countryName,
            flag = flagEmoji,
            protocolLabel = profile.protocol.uppercase(),
            speedMbps = 35.0f,
            // A failed test is stored as a negative number; it must not look like "the fastest server".
            latencyMs = profile.lastPingMs?.takeIf { it > 0 },
            tier = tier,
            continent = continent,
            profileUrl = "",
            configUri = profile.rawPayload() ?: "",
            engine = profile.protocol.uppercase(),
        )
    }

    /**
     * A flag emoji is two regional-indicator code points = 4 UTF-16 chars:
     * D83C DDE6..DDFF, D83C DDE6..DDFF. Returns (flag, restOfName).
     */
    private fun splitLeadingFlag(name: String): Pair<String?, String> {
        if (name.length >= 4 &&
            name[0] == '\uD83C' && name[2] == '\uD83C' &&
            name[1] in '\uDDE6'..'\uDDFF' && name[3] in '\uDDE6'..'\uDDFF'
        ) {
            return name.substring(0, 4) to name.substring(4).trim()
        }
        return null to name
    }

    // Order matters: more specific names first ("hong kong" before "china", "north korea" before "korea").
    private val COUNTRY_ALIASES: List<Pair<Regex, String>> = listOf(
        "united states|usa|us|america" to "US",
        "united kingdom|uk|britain|england" to "GB",
        "united arab emirates|uae|dubai" to "AE",
        "hong kong" to "HK",
        "north korea" to "KP",
        "south korea|korea" to "KR",
        "south africa" to "ZA",
        "new zealand" to "NZ",
        "czech republic|czechia" to "CZ",
        "saudi arabia" to "SA",
        "china" to "CN",
        "japan" to "JP",
        "germany" to "DE",
        "france" to "FR",
        "canada" to "CA",
        "netherlands|holland" to "NL",
        "singapore" to "SG",
        "australia" to "AU",
        "vietnam" to "VN",
        "turkey|türkiye|turkiye" to "TR",
        "switzerland" to "CH",
        "sweden" to "SE",
        "poland" to "PL",
        "brazil" to "BR",
        "india" to "IN",
        "indonesia" to "ID",
        "taiwan" to "TW",
        "russia" to "RU",
        "ukraine" to "UA",
        "italy" to "IT",
        "spain" to "ES",
        "portugal" to "PT",
        "finland" to "FI",
        "norway" to "NO",
        "denmark" to "DK",
        "ireland" to "IE",
        "austria" to "AT",
        "belgium" to "BE",
        "romania" to "RO",
        "bulgaria" to "BG",
        "hungary" to "HU",
        "greece" to "GR",
        "latvia" to "LV",
        "lithuania" to "LT",
        "estonia" to "EE",
        "luxembourg" to "LU",
        "israel" to "IL",
        "iran" to "IR",
        "iraq" to "IQ",
        "egypt" to "EG",
        "mexico" to "MX",
        "argentina" to "AR",
        "chile" to "CL",
        "thailand" to "TH",
        "malaysia" to "MY",
        "philippines" to "PH",
        "pakistan" to "PK",
        "kazakhstan" to "KZ",
    ).map { (names, code) -> Regex("""\b(?:$names)\b""", RegexOption.IGNORE_CASE) to code }

    private fun countryNameToCode(name: String): String? =
        COUNTRY_ALIASES.firstOrNull { (regex, _) -> regex.containsMatchIn(name) }?.second

    /** Common-code friendly (no java.lang.Character): regional indicators are D83C DDE6 + (letter - 'A'). */
    private fun countryCodeToEmoji(cc: String): String {
        if (cc.length != 2 || cc.any { it !in 'A'..'Z' }) return "🌐"
        return buildString {
            cc.forEach { ch ->
                append('\uD83C')
                append((0xDDE6 + (ch - 'A')).toChar())
            }
        }
    }
}