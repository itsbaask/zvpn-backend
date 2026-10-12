package com.zvpn.ui.servers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.zvpn.data.map.RegionResolver
import com.zvpn.domain.config.VpnProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

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
        val cc = profile.countryCode?.uppercase()?.takeIf { it.length == 2 }
            ?: countryNameToCode(countryName)
            ?: UNKNOWN_CODE

        val known = cc != UNKNOWN_CODE
        val flagEmoji = extractedFlag ?: if (known) countryCodeToEmoji(cc) else "🌐"
        val continent = RegionResolver.resolve(cc)

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

    // ─────────────────────── Country name → ISO code (hash lookup, no regex) ───────────────────────
    //
    // The previous version ran up to ~250 regexes per profile on the main thread, which froze the UI on
    // big lists. Now: normalize once, then look up word n-grams in a HashMap, longest n-gram first, so
    // "papua new guinea" beats "guinea", "north korea" beats "korea", "us virgin islands" beats "us".

    private val ALIASES: List<Pair<String, String>> = listOf(
        // ── Multi-word / overlapping names ──
        "american samoa" to "AS",
        "british virgin islands|bvi" to "VG",
        "us virgin islands|united states virgin islands" to "VI",
        "united states minor outlying islands" to "UM",
        "united states|usa|us|america" to "US",
        "united kingdom|uk|great britain|britain|england|scotland|wales|northern ireland" to "GB",
        "united arab emirates|uae|dubai|abu dhabi" to "AE",
        "papua new guinea|png" to "PG",
        "equatorial guinea" to "GQ",
        "guinea bissau" to "GW",
        "south sudan" to "SS",
        "north korea|dprk" to "KP",
        "south korea|republic of korea" to "KR",
        "south africa" to "ZA",
        "new zealand" to "NZ",
        "new caledonia" to "NC",
        "french polynesia|tahiti" to "PF",
        "french guiana" to "GF",
        "french southern territories" to "TF",
        "saint kitts and nevis|saint kitts|st kitts" to "KN",
        "saint lucia|st lucia" to "LC",
        "saint vincent and the grenadines|saint vincent|st vincent" to "VC",
        "saint pierre and miquelon" to "PM",
        "saint martin" to "MF",
        "sint maarten" to "SX",
        "saint barthelemy|saint barthélemy" to "BL",
        "saint helena" to "SH",
        "sao tome and principe|são tomé and príncipe|sao tome" to "ST",
        "trinidad and tobago|trinidad" to "TT",
        "antigua and barbuda|antigua" to "AG",
        "turks and caicos" to "TC",
        "cayman islands|cayman" to "KY",
        "cook islands" to "CK",
        "faroe islands|faroe" to "FO",
        "falkland islands|falklands" to "FK",
        "marshall islands" to "MH",
        "solomon islands" to "SB",
        "northern mariana islands" to "MP",
        "norfolk island" to "NF",
        "christmas island" to "CX",
        "cocos|keeling" to "CC",
        "heard island" to "HM",
        "pitcairn" to "PN",
        "bouvet" to "BV",
        "south georgia" to "GS",
        "british indian ocean territory" to "IO",
        "wallis and futuna" to "WF",
        "svalbard|jan mayen" to "SJ",
        "western sahara" to "EH",
        "bosnia and herzegovina|bosnia" to "BA",
        "north macedonia|macedonia" to "MK",
        "czech republic|czechia" to "CZ",
        "dominican republic" to "DO",
        "central african republic" to "CF",
        "democratic republic of the congo|dr congo|drc|congo kinshasa" to "CD",
        "republic of the congo|congo brazzaville" to "CG",
        "ivory coast|cote d'ivoire|côte d'ivoire" to "CI",
        "cape verde|cabo verde" to "CV",
        "east timor|timor leste" to "TL",
        "saudi arabia" to "SA",
        "hong kong" to "HK",
        "macau|macao" to "MO",
        "sri lanka" to "LK",
        "el salvador" to "SV",
        "costa rica" to "CR",
        "puerto rico" to "PR",
        "burkina faso" to "BF",
        "sierra leone" to "SL",
        "isle of man" to "IM",
        "san marino" to "SM",
        "vatican city|vatican|holy see" to "VA",
        "aland islands|åland" to "AX",

        // ── Single-word names (A–Z) ──
        "afghanistan" to "AF",
        "albania" to "AL",
        "algeria" to "DZ",
        "andorra" to "AD",
        "angola" to "AO",
        "anguilla" to "AI",
        "antarctica" to "AQ",
        "argentina" to "AR",
        "armenia" to "AM",
        "aruba" to "AW",
        "australia" to "AU",
        "austria" to "AT",
        "azerbaijan" to "AZ",
        "bahamas" to "BS",
        "bahrain" to "BH",
        "bangladesh" to "BD",
        "barbados" to "BB",
        "belarus" to "BY",
        "belgium" to "BE",
        "belize" to "BZ",
        "benin" to "BJ",
        "bermuda" to "BM",
        "bhutan" to "BT",
        "bolivia" to "BO",
        "bonaire" to "BQ",
        "botswana" to "BW",
        "brazil" to "BR",
        "brunei" to "BN",
        "bulgaria" to "BG",
        "burundi" to "BI",
        "cambodia" to "KH",
        "cameroon" to "CM",
        "canada" to "CA",
        "chad" to "TD",
        "chile" to "CL",
        "china" to "CN",
        "colombia" to "CO",
        "comoros" to "KM",
        "congo" to "CG",
        "croatia" to "HR",
        "cuba" to "CU",
        "curacao|curaçao" to "CW",
        "cyprus" to "CY",
        "denmark" to "DK",
        "djibouti" to "DJ",
        "dominica" to "DM",
        "ecuador" to "EC",
        "egypt" to "EG",
        "eritrea" to "ER",
        "estonia" to "EE",
        "eswatini|swaziland" to "SZ",
        "ethiopia" to "ET",
        "fiji" to "FJ",
        "finland" to "FI",
        "france" to "FR",
        "gabon" to "GA",
        "gambia" to "GM",
        "georgia" to "GE",
        "germany" to "DE",
        "ghana" to "GH",
        "gibraltar" to "GI",
        "greece" to "GR",
        "greenland" to "GL",
        "grenada" to "GD",
        "guadeloupe" to "GP",
        "guam" to "GU",
        "guatemala" to "GT",
        "guernsey" to "GG",
        "guinea" to "GN",
        "guyana" to "GY",
        "haiti" to "HT",
        "honduras" to "HN",
        "hungary" to "HU",
        "iceland" to "IS",
        "india" to "IN",
        "indonesia" to "ID",
        "iran" to "IR",
        "iraq" to "IQ",
        "ireland" to "IE",
        "israel" to "IL",
        "italy" to "IT",
        "jamaica" to "JM",
        "japan" to "JP",
        "jersey" to "JE",
        "jordan" to "JO",
        "kazakhstan" to "KZ",
        "kenya" to "KE",
        "kiribati" to "KI",
        "korea" to "KR",
        "kosovo" to "XK",
        "kuwait" to "KW",
        "kyrgyzstan" to "KG",
        "laos" to "LA",
        "latvia" to "LV",
        "lebanon" to "LB",
        "lesotho" to "LS",
        "liberia" to "LR",
        "libya" to "LY",
        "liechtenstein" to "LI",
        "lithuania" to "LT",
        "luxembourg" to "LU",
        "madagascar" to "MG",
        "malawi" to "MW",
        "malaysia" to "MY",
        "maldives" to "MV",
        "mali" to "ML",
        "malta" to "MT",
        "martinique" to "MQ",
        "mauritania" to "MR",
        "mauritius" to "MU",
        "mayotte" to "YT",
        "mexico" to "MX",
        "micronesia" to "FM",
        "moldova" to "MD",
        "monaco" to "MC",
        "mongolia" to "MN",
        "montenegro" to "ME",
        "montserrat" to "MS",
        "morocco" to "MA",
        "mozambique" to "MZ",
        "myanmar|burma" to "MM",
        "namibia" to "NA",
        "nauru" to "NR",
        "nepal" to "NP",
        "netherlands|holland" to "NL",
        "nicaragua" to "NI",
        "niger" to "NE",
        "nigeria" to "NG",
        "niue" to "NU",
        "norway" to "NO",
        "oman" to "OM",
        "pakistan" to "PK",
        "palau" to "PW",
        "palestine" to "PS",
        "panama" to "PA",
        "paraguay" to "PY",
        "peru" to "PE",
        "philippines" to "PH",
        "poland" to "PL",
        "portugal" to "PT",
        "qatar" to "QA",
        "reunion|réunion" to "RE",
        "romania" to "RO",
        "russia" to "RU",
        "rwanda" to "RW",
        "samoa" to "WS",
        "senegal" to "SN",
        "serbia" to "RS",
        "seychelles" to "SC",
        "singapore" to "SG",
        "slovakia" to "SK",
        "slovenia" to "SI",
        "somalia" to "SO",
        "spain" to "ES",
        "sudan" to "SD",
        "suriname" to "SR",
        "sweden" to "SE",
        "switzerland" to "CH",
        "syria" to "SY",
        "taiwan" to "TW",
        "tajikistan" to "TJ",
        "tanzania" to "TZ",
        "thailand" to "TH",
        "togo" to "TG",
        "tokelau" to "TK",
        "tonga" to "TO",
        "tunisia" to "TN",
        "turkey|türkiye|turkiye" to "TR",
        "turkmenistan" to "TM",
        "tuvalu" to "TV",
        "uganda" to "UG",
        "ukraine" to "UA",
        "uruguay" to "UY",
        "uzbekistan" to "UZ",
        "vanuatu" to "VU",
        "venezuela" to "VE",
        "vietnam|viet nam" to "VN",
        "yemen" to "YE",
        "zambia" to "ZM",
        "zimbabwe" to "ZW",
    )

    /** Lowercase, keep letters/digits, collapse everything else into single spaces. */
    private fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        var lastSpace = true
        for (ch in s) {
            if (ch.isLetterOrDigit()) {
                sb.append(ch.lowercaseChar())
                lastSpace = false
            } else if (!lastSpace) {
                sb.append(' ')
                lastSpace = true
            }
        }
        if (sb.isNotEmpty() && sb[sb.length - 1] == ' ') sb.setLength(sb.length - 1)
        return sb.toString()
    }

    private val ALIAS_MAP: Map<String, String> = buildMap {
        for ((names, code) in ALIASES) {
            for (n in names.split('|')) {
                val key = normalize(n)
                if (key.isNotEmpty() && key !in this) put(key, code)
            }
        }
    }

    private val MAX_ALIAS_WORDS: Int = ALIAS_MAP.keys.maxOf { k -> k.count { it == ' ' } + 1 }

    private fun countryNameToCode(name: String): String? {
        val words = normalize(name).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        for (n in minOf(MAX_ALIAS_WORDS, words.size) downTo 1) {
            for (start in 0..(words.size - n)) {
                val key = if (n == 1) words[start] else words.subList(start, start + n).joinToString(" ")
                ALIAS_MAP[key]?.let { return it }
            }
        }
        return null
    }

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
