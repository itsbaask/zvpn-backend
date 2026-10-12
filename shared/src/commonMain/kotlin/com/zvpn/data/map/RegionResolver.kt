package com.zvpn.data.map

enum class Continent(val label: String, val icon: String) {
    Americas("Americas", "🌎"),
    Europe("Europe", "🇪🇺"),
    AsiaPacific("Asia-Pacific", "🌏"),
    MeAfrica("ME/Africa", "🌍")
}

fun continentOf(cc: String?): Continent {
    val r = RegionResolver.resolve(cc)
    return when (r) {
        "Americas" -> Continent.Americas
        "Europe" -> Continent.Europe
        "Asia-Pacific" -> Continent.AsiaPacific
        "ME/Africa" -> Continent.MeAfrica
        else -> Continent.Americas
    }
}

object RegionResolver {
    private val AMERICAS = setOf(
        "US", "CA", "MX", "BR", "AR", "CL", "CO", "PE", "VE", "EC", "GT", "CU", "CR", "PA", "UY", "BO", "PY", "HN",
        "SV", "NI", "DO", "HT", "JM", "PR", "BS", "BZ", "GY", "SR", "BB", "TT", "LC", "VC", "GD", "AG", "KN", "DM",
        "AW", "CW", "SX", "GP", "MQ", "KY", "BM", "TC", "VG", "VI", "AI", "MS", "BL", "MF", "PM", "GL", "GF", "FK"
    )

    private val EUROPE = setOf(
        "DE", "FR", "GB", "UK", "NL", "IT", "ES", "PL", "SE", "FI", "CH", "AT", "NO", "DK", "BE", "CZ", "RO", "UA",
        "RU", "GR", "IE", "PT", "HU", "BG", "SK", "HR", "RS", "LT", "SI", "LV", "EE", "BA", "AL", "MK", "LU", "MT",
        "IS", "MD", "ME", "CY", "BY", "AD", "SM", "MC", "LI", "VA", "FO", "GI", "IM", "JE", "GG", "AX"
    )

    private val ASIA_PACIFIC = setOf(
        "JP", "KR", "SG", "HK", "TW", "IN", "AU", "NZ", "ID", "TH", "MY", "PH", "VN", "PK", "BD", "KH", "LK", "MM",
        "NP", "LA", "MN", "BN", "MV", "BT", "FJ", "PG", "SB", "VU", "MO", "KG", "TJ", "TM", "AF", "GE", "AM", "AZ",
        "KZ", "UZ", "TL", "NC", "PF", "GU", "MP", "WS", "TO", "FM", "KI", "MH", "PW", "NR", "TV", "CK"
    )

    private val ME_AFRICA = setOf(
        "AE", "SA", "EG", "TR", "IL", "QA", "BH", "KW", "OM", "ZA", "MA", "DZ", "TN", "NG", "KE", "GH", "ET", "TZ",
        "UG", "CI", "CM", "SN", "ZM", "ZW", "AO", "MZ", "MU", "JO", "LB", "IQ", "IR", "YE", "SY", "PS", "NA", "BW",
        "MG", "RW", "BI", "SD", "SS", "LY", "MR", "ML", "BF", "NE", "TD", "CF", "CG", "CD", "GA", "GQ", "GW", "GM",
        "SL", "LR", "TG", "BJ", "ST", "DJ", "SO", "ER", "SC", "KM", "CV"
    )

    fun resolve(cc: String?): String {
        val code = cc?.uppercase()?.trim() ?: return "Americas"
        return when {
            code in AMERICAS -> "Americas"
            code in EUROPE -> "Europe"
            code in ASIA_PACIFIC -> "Asia-Pacific"
            code in ME_AFRICA -> "ME/Africa"
            else -> "Americas"
        }
    }
}
