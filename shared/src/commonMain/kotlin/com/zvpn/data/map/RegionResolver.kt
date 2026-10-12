package com.zvpn.data.map

import java.util.Locale

enum class Continent(val label: String, val icon: String) {
    Americas("Americas", "🌎"),
    Europe("Europe", "🇪🇺"),
    AsiaPacific("Asia-Pacific", "🌏"),
    MeAfrica("ME/Africa", "🌍")
}

fun continentOf(cc: String?): Continent {
    return when (RegionResolver.resolve(cc)) {
        "Americas" -> Continent.Americas
        "Europe" -> Continent.Europe
        "Asia-Pacific" -> Continent.AsiaPacific
        "ME/Africa" -> Continent.MeAfrica
        else -> Continent.Americas
    }
}

/**
 * Covers all ISO 3166-1 alpha-2 codes (249) + "UK" (common alias of GB) + "XK" (Kosovo).
 * Uninhabited / remote territories are assigned to the nearest region.
 */
object RegionResolver {
    private const val DEFAULT_REGION = "Americas"

    private val AMERICAS = setOf(
        // North America
        "US", "CA", "MX", "GL", "PM", "BM",
        // Central America
        "GT", "BZ", "HN", "SV", "NI", "CR", "PA",
        // Caribbean
        "CU", "DO", "HT", "JM", "PR", "BS", "BB", "TT", "LC", "VC", "GD", "AG", "KN", "DM",
        "AW", "CW", "SX", "BQ", "GP", "MQ", "KY", "TC", "VG", "VI", "AI", "MS", "BL", "MF",
        // South America
        "BR", "AR", "CL", "CO", "PE", "VE", "EC", "UY", "BO", "PY", "GY", "SR", "GF", "FK", "GS"
    )

    private val EUROPE = setOf(
        "DE", "FR", "GB", "UK", "NL", "IT", "ES", "PL", "SE", "FI", "CH", "AT", "NO", "DK", "BE", "CZ",
        "RO", "UA", "RU", "GR", "IE", "PT", "HU", "BG", "SK", "HR", "RS", "LT", "SI", "LV", "EE", "BA",
        "AL", "MK", "LU", "MT", "IS", "MD", "ME", "CY", "BY", "AD", "SM", "MC", "LI", "VA", "FO", "GI",
        "IM", "JE", "GG", "AX", "SJ", "BV", "XK"
    )

    private val ASIA_PACIFIC = setOf(
        // East Asia
        "CN", "JP", "KR", "KP", "HK", "MO", "TW", "MN",
        // South Asia
        "IN", "PK", "BD", "LK", "NP", "BT", "MV", "AF", "IO",
        // Southeast Asia
        "SG", "ID", "TH", "MY", "PH", "VN", "KH", "MM", "LA", "BN", "TL",
        // Central Asia & Caucasus
        "KZ", "UZ", "KG", "TJ", "TM", "GE", "AM", "AZ",
        // Oceania
        "AU", "NZ", "FJ", "PG", "SB", "VU", "NC", "PF", "GU", "MP", "WS", "AS", "TO", "FM", "KI",
        "MH", "PW", "NR", "TV", "CK", "NU", "TK", "WF", "PN", "NF", "CX", "CC", "HM", "UM",
        // Antarctica
        "AQ"
    )

    private val ME_AFRICA = setOf(
        // Middle East
        "AE", "SA", "QA", "BH", "KW", "OM", "YE", "IQ", "IR", "SY", "JO", "LB", "IL", "PS", "TR",
        // North Africa
        "EG", "LY", "TN", "DZ", "MA", "EH", "SD", "SS",
        // West Africa
        "NG", "GH", "CI", "SN", "ML", "BF", "NE", "MR", "GM", "GN", "GW", "SL", "LR", "TG", "BJ",
        "CV", "SH",
        // Central Africa
        "CM", "TD", "CF", "CG", "CD", "GA", "GQ", "ST", "AO",
        // East Africa
        "KE", "ET", "TZ", "UG", "RW", "BI", "SO", "DJ", "ER",
        // Southern Africa
        "ZA", "NA", "BW", "ZM", "ZW", "MZ", "MW", "LS", "SZ", "MG",
        // Indian Ocean
        "MU", "SC", "KM", "RE", "YT", "TF"
    )

    // Built once: O(1) lookup instead of checking four sets in sequence.
    private val LOOKUP: Map<String, String> = buildMap {
        AMERICAS.forEach { put(it, "Americas") }
        EUROPE.forEach { put(it, "Europe") }
        ASIA_PACIFIC.forEach { put(it, "Asia-Pacific") }
        ME_AFRICA.forEach { put(it, "ME/Africa") }
    }

    fun resolve(cc: String?): String {
        val code = cc?.trim()?.uppercase(Locale.ROOT)
        if (code.isNullOrEmpty()) return DEFAULT_REGION
        return LOOKUP[code] ?: DEFAULT_REGION
    }

    /** Debug helper: ISO country codes that are not mapped (should be empty). */
    fun missingCodes(): List<String> =
        Locale.getISOCountries().filter { it !in LOOKUP }

    /** Debug helper: codes that appear in more than one region set (should be empty). */
    fun duplicateCodes(): List<String> {
        val all = AMERICAS.toList() + EUROPE.toList() + ASIA_PACIFIC.toList() + ME_AFRICA.toList()
        return all.groupingBy { it }.eachCount().filter { it.value > 1 }.keys.toList()
    }
}