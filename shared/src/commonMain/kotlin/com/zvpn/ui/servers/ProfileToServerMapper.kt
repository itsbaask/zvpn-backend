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

    private val TIER_PLUS = Regex("""\b(?:vip|plus|premium\+)\b""", RegexOption.IGNORE_CASE)
    private val TIER_PRO = Regex("""\b(?:pro|premium)\b""", RegexOption.IGNORE_CASE)

    fun countryNameFromCode(cc: String): String {
        if (cc == "XX" || cc.isBlank()) return "United States"
        val locale = java.util.Locale.Builder().setRegion(cc.uppercase()).build()
        val name = locale.getDisplayCountry(java.util.Locale.ENGLISH)
        return if (name.isNotBlank() && name != cc.uppercase()) name else "United States"
    }

    fun flagFromCode(cc: String): String {
        if (cc == "XX" || cc.isBlank()) return "🇺🇸"
        val upper = cc.uppercase()
        if (upper.length != 2) return "🌐"
        val firstChar = Character.codePointAt(upper, 0) - 0x41 + 0x1F1E6
        val secondChar = Character.codePointAt(upper, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
    }

    fun continentFromCode(cc: String): String {
        val upper = cc.uppercase()
        val europe = setOf("AD", "AL", "AT", "BA", "BE", "BG", "BY", "CH", "CY", "CZ", "DE", "DK", "EE", "ES", "FI", "FR", "GB", "GR", "HR", "HU", "IE", "IS", "IT", "LI", "LT", "LU", "LV", "MC", "MD", "ME", "MK", "MT", "NL", "NO", "PL", "PT", "RO", "RS", "RU", "SE", "SI", "SK", "SM", "TR", "UA", "VA")
        val americas = setOf("AG", "AI", "AR", "AW", "BB", "BL", "BM", "BO", "BQ", "BR", "BS", "BZ", "CA", "CL", "CO", "CR", "CU", "CW", "DM", "DO", "EC", "FK", "GD", "GF", "GL", "GP", "GT", "GY", "HN", "HT", "JM", "KN", "KY", "LC", "MF", "MQ", "MS", "MX", "NI", "PA", "PE", "PM", "PR", "PY", "SR", "SV", "SX", "TC", "TT", "UM", "US", "UY", "VC", "VE", "VG", "VI")
        val asia = setOf("AF", "AM", "AS", "AU", "AZ", "BD", "BN", "BT", "CC", "CK", "CN", "CX", "FJ", "FM", "GE", "GU", "HK", "ID", "IN", "IO", "JP", "KH", "KI", "KP", "KR", "KZ", "KG", "LA", "LK", "MH", "MM", "MN", "MO", "MP", "MV", "MY", "NC", "NF", "NP", "NR", "NU", "NZ", "PF", "PG", "PH", "PK", "PN", "PW", "SB", "SG", "SY", "TH", "TK", "TL", "TJ", "TM", "TW", "UZ", "VN", "VU", "WF", "WS", "AE", "SA", "IL", "QA", "KW", "OM", "BH", "JO", "LB", "IQ", "YE", "PS")
        val africa = setOf("AO", "BF", "BI", "BJ", "BW", "CD", "CF", "CG", "CI", "CM", "CV", "DJ", "DZ", "EG", "EH", "ER", "ET", "GA", "GH", "GM", "GN", "GQ", "GW", "KE", "KM", "LR", "LS", "LY", "MA", "MG", "ML", "MR", "MU", "MW", "MZ", "NA", "NE", "NG", "RE", "RW", "SC", "SD", "SH", "SL", "SN", "SO", "SS", "ST", "SZ", "TD", "TG", "TN", "TZ", "UG", "YT", "ZA", "ZM", "ZW")

        return when (upper) {
            in europe -> "Europe"
            in americas -> "Americas"
            in asia -> "Asia-Pacific"
            in africa -> "ME/Africa"
            else -> "Americas"
        }
    }

    fun resolveCountryCode(hostOrIp: String, fallbackCode: String? = null): String {
        if (!fallbackCode.isNullOrBlank() && fallbackCode != "XX" && fallbackCode != "NONE") {
            return fallbackCode.uppercase()
        }
        val clean = hostOrIp.split(":").firstOrNull()?.trim()?.lowercase() ?: return "US"
        // Domain suffix checks
        return when {
            clean.endsWith(".jp") -> "JP"
            clean.endsWith(".de") -> "DE"
            clean.endsWith(".nl") -> "NL"
            clean.endsWith(".fr") -> "FR"
            clean.endsWith(".uk") -> "GB"
            clean.endsWith(".ca") -> "CA"
            clean.endsWith(".kr") -> "KR"
            clean.endsWith(".hk") -> "HK"
            clean.endsWith(".sg") -> "SG"
            clean.endsWith(".ru") -> "RU"
            clean.endsWith(".pl") -> "PL"
            clean.endsWith(".tr") -> "TR"
            clean.endsWith(".in") -> "IN"
            clean.endsWith(".id") -> "ID"
            clean.endsWith(".ir") -> "IR"
            clean.endsWith(".br") -> "BR"
            clean.endsWith(".se") -> "SE"
            clean.endsWith(".fi") -> "FI"
            else -> "US"
        }
    }

    fun map(profile: VpnProfile): PublicServer {
        val rawName = profile.name.trim()

        val cc = profile.countryCode?.takeIf { it.isNotBlank() && it != "XX" } ?: "US"
        val countryName = profile.countryName?.takeIf { it.isNotBlank() && it != "Unknown" } ?: countryNameFromCode(cc)
        val flagEmoji = profile.flag?.takeIf { it.isNotBlank() && it != "🌐" } ?: flagFromCode(cc)
        val continent = profile.continent?.takeIf { it.isNotBlank() && it != "Other" } ?: continentFromCode(cc)

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
