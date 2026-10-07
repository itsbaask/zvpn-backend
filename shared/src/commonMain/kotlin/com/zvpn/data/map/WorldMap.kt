package com.zvpn.data.map

import com.zvpn.shared.resources.Res

data class CountryPoint(val code: String, val name: String, val lat: Float, val lon: Float)

enum class Continent(val label: String, val icon: String) {
    Europe("Europe", "🇪🇺"),
    Americas("Americas", "🌎"),
    AsiaPacific("Asia-Pacific", "🌏"),
    MiddleEastAfrica("Middle East & Africa", "🌍"),
    Other("Other Regions", "🌐")
}

fun continentOf(cc: String?): Continent {
    val code = cc?.uppercase() ?: return Continent.Other
    return when {
        code in setOf("US", "CA", "MX", "BR", "AR", "CL", "CO", "PE", "VE", "EC", "GT", "CU", "CR", "PA") -> Continent.Americas
        code in setOf("DE", "FR", "GB", "NL", "IT", "ES", "PL", "SE", "FI", "CH", "AT", "NO", "DK", "BE", "CZ", "RO", "UA", "RU", "GR", "IE", "PT", "HU", "BG") -> Continent.Europe
        code in setOf("JP", "KR", "SG", "HK", "TW", "IN", "AU", "NZ", "ID", "TH", "MY", "PH", "VN", "PK", "BD", "KH") -> Continent.AsiaPacific
        code in setOf("AE", "SA", "EG", "TR", "IL", "QA", "BH", "KW", "OM", "ZA", "MA", "DZ", "TN", "NG", "KE") -> Continent.MiddleEastAfrica
        else -> Continent.Other
    }
}

object WorldMap {
    const val WIDTH: Float = 65535f
    const val HEIGHT: Float = WIDTH / 2f

    class Country(
        val code: String,
        val anchorX: Float,
        val anchorY: Float,
        val rings: List<FloatArray>,
    )

    private var cached: List<Country>? = null

    val WORLD_COUNTRY_POINTS = listOf(
        CountryPoint("AF", "Afghanistan", 34.53f, 69.17f),
        CountryPoint("AL", "Albania", 41.33f, 19.82f),
        CountryPoint("DZ", "Algeria", 36.75f, 3.06f),
        CountryPoint("AS", "American Samoa", -14.28f, -170.7f),
        CountryPoint("AD", "Andorra", 42.51f, 1.52f),
        CountryPoint("AO", "Angola", -8.84f, 13.23f),
        CountryPoint("AI", "Anguilla", 18.22f, -63.06f),
        CountryPoint("AG", "Antigua and Barbuda", 17.12f, -61.85f),
        CountryPoint("AR", "Argentina", -34.6f, -58.38f),
        CountryPoint("AM", "Armenia", 40.18f, 44.51f),
        CountryPoint("AW", "Aruba", 12.52f, -70.03f),
        CountryPoint("AU", "Australia", -35.28f, 149.13f),
        CountryPoint("AT", "Austria", 48.21f, 16.37f),
        CountryPoint("AZ", "Azerbaijan", 40.41f, 49.87f),
        CountryPoint("BS", "Bahamas", 25.05f, -77.35f),
        CountryPoint("BH", "Bahrain", 26.23f, 50.59f),
        CountryPoint("BD", "Bangladesh", 23.81f, 90.41f),
        CountryPoint("BB", "Barbados", 13.1f, -59.61f),
        CountryPoint("BY", "Belarus", 53.9f, 27.57f),
        CountryPoint("BE", "Belgium", 50.85f, 4.35f),
        CountryPoint("BZ", "Belize", 17.25f, -88.77f),
        CountryPoint("BJ", "Benin", 6.5f, 2.63f),
        CountryPoint("BM", "Bermuda", 32.3f, -64.78f),
        CountryPoint("BT", "Bhutan", 27.47f, 89.64f),
        CountryPoint("BO", "Bolivia", -16.5f, -68.15f),
        CountryPoint("BA", "Bosnia and Herzegovina", 43.86f, 18.41f),
        CountryPoint("BW", "Botswana", -24.65f, 25.91f),
        CountryPoint("BR", "Brazil", -15.79f, -47.88f),
        CountryPoint("VG", "British Virgin Islands", 18.43f, -64.62f),
        CountryPoint("BN", "Brunei", 4.89f, 114.94f),
        CountryPoint("BG", "Bulgaria", 42.7f, 23.32f),
        CountryPoint("BF", "Burkina Faso", 12.37f, -1.52f),
        CountryPoint("BI", "Burundi", -3.38f, 29.36f),
        CountryPoint("CV", "Cabo Verde", 14.93f, -23.51f),
        CountryPoint("KH", "Cambodia", 11.56f, 104.92f),
        CountryPoint("CM", "Cameroon", 3.85f, 11.5f),
        CountryPoint("CA", "Canada", 45.42f, -75.7f),
        CountryPoint("KY", "Cayman Islands", 19.29f, -81.37f),
        CountryPoint("CF", "Central African Republic", 4.36f, 18.56f),
        CountryPoint("TD", "Chad", 12.13f, 15.06f),
        CountryPoint("CL", "Chile", -33.45f, -70.67f),
        CountryPoint("CN", "China", 39.9f, 116.4f),
        CountryPoint("CO", "Colombia", 4.71f, -74.07f),
        CountryPoint("KM", "Comoros", -11.7f, 43.26f),
        CountryPoint("CG", "Congo", -4.27f, 15.28f),
        CountryPoint("CK", "Cook Islands", -21.21f, -159.78f),
        CountryPoint("CR", "Costa Rica", 9.93f, -84.08f),
        CountryPoint("CI", "Cote d'Ivoire", 6.82f, -5.28f),
        CountryPoint("HR", "Croatia", 45.81f, 15.98f),
        CountryPoint("CU", "Cuba", 23.11f, -82.37f),
        CountryPoint("CY", "Cyprus", 35.17f, 33.36f),
        CountryPoint("CZ", "Czechia", 50.08f, 14.44f),
        CountryPoint("CD", "DR Congo", -4.44f, 15.27f),
        CountryPoint("DK", "Denmark", 55.68f, 12.57f),
        CountryPoint("DJ", "Djibouti", 11.59f, 43.15f),
        CountryPoint("DM", "Dominica", 15.3f, -61.39f),
        CountryPoint("DO", "Dominican Republic", 18.49f, -69.93f),
        CountryPoint("EC", "Ecuador", -0.18f, -78.47f),
        CountryPoint("EG", "Egypt", 30.04f, 31.24f),
        CountryPoint("SV", "El Salvador", 13.69f, -89.19f),
        CountryPoint("GQ", "Equatorial Guinea", 3.75f, 8.78f),
        CountryPoint("ER", "Eritrea", 15.34f, 38.93f),
        CountryPoint("EE", "Estonia", 59.44f, 24.75f),
        CountryPoint("SZ", "Eswatini", -26.31f, 31.14f),
        CountryPoint("ET", "Ethiopia", 9.03f, 38.74f),
        CountryPoint("FO", "Faroe Islands", 62.01f, -6.77f),
        CountryPoint("FJ", "Fiji", -18.14f, 178.44f),
        CountryPoint("FI", "Finland", 60.17f, 24.94f),
        CountryPoint("FR", "France", 48.86f, 2.35f),
        CountryPoint("PF", "French Polynesia", -17.54f, -149.57f),
        CountryPoint("GA", "Gabon", 0.39f, 9.45f),
        CountryPoint("GM", "Gambia", 13.45f, -16.58f),
        CountryPoint("GE", "Georgia", 41.72f, 44.79f),
        CountryPoint("DE", "Germany", 52.52f, 13.4f),
        CountryPoint("GH", "Ghana", 5.6f, -0.19f),
        CountryPoint("GI", "Gibraltar", 36.14f, -5.35f),
        CountryPoint("GR", "Greece", 37.98f, 23.73f),
        CountryPoint("GL", "Greenland", 64.18f, -51.72f),
        CountryPoint("GD", "Grenada", 12.06f, -61.75f),
        CountryPoint("GP", "Guadeloupe", 16.24f, -61.53f),
        CountryPoint("GU", "Guam", 13.44f, 144.79f),
        CountryPoint("GT", "Guatemala", 14.63f, -90.51f),
        CountryPoint("GG", "Guernsey", 49.45f, -2.54f),
        CountryPoint("GN", "Guinea", 9.64f, -13.58f),
        CountryPoint("GW", "Guinea-Bissau", 11.86f, -15.6f),
        CountryPoint("GY", "Guyana", 6.8f, -58.16f),
        CountryPoint("HT", "Haiti", 18.59f, -72.31f),
        CountryPoint("HN", "Honduras", 14.07f, -87.19f),
        CountryPoint("HK", "Hong Kong", 22.32f, 114.17f),
        CountryPoint("HU", "Hungary", 47.5f, 19.04f),
        CountryPoint("IS", "Iceland", 64.15f, -21.94f),
        CountryPoint("IN", "India", 28.61f, 77.21f),
        CountryPoint("ID", "Indonesia", -6.21f, 106.85f),
        CountryPoint("IR", "Iran", 35.69f, 51.39f),
        CountryPoint("IQ", "Iraq", 33.31f, 44.37f),
        CountryPoint("IE", "Ireland", 53.35f, -6.26f),
        CountryPoint("IM", "Isle of Man", 54.15f, -4.48f),
        CountryPoint("IL", "Israel", 31.77f, 35.22f),
        CountryPoint("IT", "Italy", 41.9f, 12.5f),
        CountryPoint("JM", "Jamaica", 18.0f, -76.79f),
        CountryPoint("JP", "Japan", 35.68f, 139.69f),
        CountryPoint("JE", "Jersey", 49.19f, -2.11f),
        CountryPoint("JO", "Jordan", 31.95f, 35.93f),
        CountryPoint("KZ", "Kazakhstan", 51.17f, 71.43f),
        CountryPoint("KE", "Kenya", -1.29f, 36.82f),
        CountryPoint("KI", "Kiribati", 1.33f, 172.98f),
        CountryPoint("XK", "Kosovo", 42.66f, 21.17f),
        CountryPoint("KW", "Kuwait", 29.38f, 47.99f),
        CountryPoint("KG", "Kyrgyzstan", 42.87f, 74.59f),
        CountryPoint("LA", "Laos", 17.97f, 102.63f),
        CountryPoint("LV", "Latvia", 56.95f, 24.11f),
        CountryPoint("LB", "Lebanon", 33.89f, 35.5f),
        CountryPoint("LS", "Lesotho", -29.31f, 27.48f),
        CountryPoint("LR", "Liberia", 6.31f, -10.8f),
        CountryPoint("LY", "Libya", 32.88f, 13.19f),
        CountryPoint("LI", "Liechtenstein", 47.14f, 9.52f),
        CountryPoint("LT", "Lithuania", 54.69f, 25.28f),
        CountryPoint("LU", "Luxembourg", 49.61f, 6.13f),
        CountryPoint("MO", "Macao", 22.2f, 113.54f),
        CountryPoint("MG", "Madagascar", -18.91f, 47.54f),
        CountryPoint("MW", "Malawi", -13.98f, 33.79f),
        CountryPoint("MY", "Malaysia", 3.14f, 101.69f),
        CountryPoint("MV", "Maldives", 4.18f, 73.51f),
        CountryPoint("ML", "Mali", 12.65f, -8.0f),
        CountryPoint("MT", "Malta", 35.9f, 14.51f),
        CountryPoint("MH", "Marshall Islands", 7.09f, 171.38f),
        CountryPoint("MQ", "Martinique", 14.6f, -61.08f),
        CountryPoint("MR", "Mauritania", 18.09f, -15.98f),
        CountryPoint("MU", "Mauritius", -20.16f, 57.5f),
        CountryPoint("YT", "Mayotte", -12.78f, 45.23f),
        CountryPoint("MX", "Mexico", 19.43f, -99.13f),
        CountryPoint("FM", "Micronesia", 6.92f, 158.16f),
        CountryPoint("MD", "Moldova", 47.01f, 28.86f),
        CountryPoint("MC", "Monaco", 43.73f, 7.42f),
        CountryPoint("MN", "Mongolia", 47.92f, 106.92f),
        CountryPoint("ME", "Montenegro", 42.44f, 19.26f),
        CountryPoint("MS", "Montserrat", 16.72f, -62.21f),
        CountryPoint("MA", "Morocco", 34.02f, -6.83f),
        CountryPoint("MZ", "Mozambique", -25.97f, 32.58f),
        CountryPoint("MM", "Myanmar", 16.8f, 96.15f),
        CountryPoint("NA", "Namibia", -22.56f, 17.08f),
        CountryPoint("NR", "Nauru", -0.53f, 166.92f),
        CountryPoint("NP", "Nepal", 27.72f, 85.32f),
        CountryPoint("NL", "Netherlands", 52.37f, 4.89f),
        CountryPoint("NC", "New Caledonia", -22.28f, 166.46f),
        CountryPoint("NZ", "New Zealand", -41.29f, 174.78f),
        CountryPoint("NI", "Nicaragua", 12.13f, -86.25f),
        CountryPoint("NE", "Niger", 13.51f, 2.11f),
        CountryPoint("NG", "Nigeria", 9.08f, 7.54f),
        CountryPoint("NU", "Niue", -19.05f, -169.92f),
        CountryPoint("MK", "North Macedonia", 41.99f, 21.43f),
        CountryPoint("MP", "Northern Mariana Islands", 15.2f, 145.75f),
        CountryPoint("NO", "Norway", 59.91f, 10.75f),
        CountryPoint("OM", "Oman", 23.61f, 58.59f),
        CountryPoint("PK", "Pakistan", 33.72f, 73.06f),
        CountryPoint("PW", "Palau", 7.5f, 134.62f),
        CountryPoint("PS", "Palestine", 31.95f, 35.23f),
        CountryPoint("PA", "Panama", 8.98f, -79.52f),
        CountryPoint("PG", "Papua New Guinea", -9.44f, 147.18f),
        CountryPoint("PY", "Paraguay", -25.26f, -57.58f),
        CountryPoint("PE", "Peru", -12.05f, -77.04f),
        CountryPoint("PH", "Philippines", 14.6f, 120.98f),
        CountryPoint("PL", "Poland", 52.23f, 21.01f),
        CountryPoint("PT", "Portugal", 38.72f, -9.14f),
        CountryPoint("PR", "Puerto Rico", 18.47f, -66.11f),
        CountryPoint("QA", "Qatar", 25.28f, 51.53f),
        CountryPoint("RE", "Reunion", -20.88f, 55.45f),
        CountryPoint("RO", "Romania", 44.43f, 26.1f),
        CountryPoint("RU", "Russia", 55.75f, 37.62f),
        CountryPoint("RW", "Rwanda", -1.95f, 30.06f),
        CountryPoint("WS", "Samoa", -13.83f, -171.76f),
        CountryPoint("SM", "San Marino", 43.94f, 12.46f),
        CountryPoint("ST", "Sao Tome and Principe", 0.34f, 6.73f),
        CountryPoint("SA", "Saudi Arabia", 24.71f, 46.68f),
        CountryPoint("SN", "Senegal", 14.69f, -17.44f),
        CountryPoint("RS", "Serbia", 44.82f, 20.46f),
        CountryPoint("SC", "Seychelles", -4.62f, 55.45f),
        CountryPoint("SL", "Sierra Leone", 8.48f, -13.23f),
        CountryPoint("SG", "Singapore", 1.35f, 103.82f),
        CountryPoint("SX", "Sint Maarten", 18.03f, -63.05f),
        CountryPoint("SK", "Slovakia", 48.15f, 17.11f),
        CountryPoint("SI", "Slovenia", 46.05f, 14.51f),
        CountryPoint("SB", "Solomon Islands", -9.43f, 159.95f),
        CountryPoint("SO", "Somalia", 2.04f, 45.34f),
        CountryPoint("ZA", "South Africa", -25.75f, 28.19f),
        CountryPoint("KR", "South Korea", 37.57f, 126.98f),
        CountryPoint("SS", "South Sudan", 4.85f, 31.58f),
        CountryPoint("ES", "Spain", 40.42f, -3.7f),
        CountryPoint("LK", "Sri Lanka", 6.93f, 79.86f),
        CountryPoint("SD", "Sudan", 15.55f, 32.53f),
        CountryPoint("SR", "Suriname", 5.85f, -55.2f),
        CountryPoint("SE", "Sweden", 59.33f, 18.07f),
        CountryPoint("CH", "Switzerland", 46.95f, 7.45f),
        CountryPoint("SY", "Syria", 33.51f, 36.29f),
        CountryPoint("TW", "Taiwan", 25.03f, 121.57f),
        CountryPoint("TJ", "Tajikistan", 38.56f, 68.78f),
        CountryPoint("TZ", "Tanzania", -6.16f, 35.75f),
        CountryPoint("TH", "Thailand", 13.75f, 100.5f),
        CountryPoint("TL", "Timor-Leste", -8.56f, 125.57f),
        CountryPoint("TG", "Togo", 6.14f, 1.21f),
        CountryPoint("TO", "Tonga", -21.14f, -175.2f),
        CountryPoint("TT", "Trinidad and Tobago", 10.65f, -61.52f),
        CountryPoint("TN", "Tunisia", 36.8f, 10.18f),
        CountryPoint("TR", "Turkey", 39.93f, 32.86f),
        CountryPoint("TM", "Turkmenistan", 37.95f, 58.38f),
        CountryPoint("TC", "Turks and Caicos", 21.46f, -71.14f),
        CountryPoint("TV", "Tuvalu", -8.52f, 179.22f),
        CountryPoint("UG", "Uganda", 0.32f, 32.58f),
        CountryPoint("UA", "Ukraine", 50.45f, 30.52f),
        CountryPoint("AE", "United Arab Emirates", 24.47f, 54.37f),
        CountryPoint("GB", "United Kingdom", 51.51f, -0.13f),
        CountryPoint("US", "United States", 38.89f, -77.04f),
        CountryPoint("UY", "Uruguay", -34.9f, -56.17f),
        CountryPoint("UZ", "Uzbekistan", 41.3f, 69.24f),
        CountryPoint("VU", "Vanuatu", -17.73f, 168.32f),
        CountryPoint("VE", "Venezuela", 10.49f, -66.88f),
        CountryPoint("VN", "Vietnam", 21.03f, 105.85f),
        CountryPoint("VI", "Virgin Islands", 18.34f, -64.93f),
        CountryPoint("YE", "Yemen", 15.35f, 44.21f),
        CountryPoint("ZM", "Zambia", -15.39f, 28.32f),
        CountryPoint("ZW", "Zimbabwe", -17.83f, 31.05f)
    )

    private val countryPointMap: Map<String, CountryPoint> by lazy {
        WORLD_COUNTRY_POINTS.associateBy { it.code.uppercase() }
    }

    suspend fun countries(): List<Country> {
        cached?.let { return it }
        val parsed = runCatching { parse(Res.readBytes("files/map/world.bin")) }.getOrDefault(emptyList())
        cached = parsed
        return parsed
    }

    fun anchorOf(countries: List<Country>, code: String?): Pair<Float, Float>? {
        val c = code?.trim()?.uppercase()?.takeIf { it.length == 2 } ?: return null
        val hit = countries.firstOrNull { it.code == c }
        if (hit != null) {
            return hit.anchorX to hit.anchorY
        }
        val pt = countryPointMap[c] ?: return null
        return gridOf(pt.lon, pt.lat)
    }

    fun gridOf(lon: Float, lat: Float): Pair<Float, Float> =
        ((lon + 180f) / 360f * WIDTH) to ((90f - lat) / 180f * HEIGHT)

    private const val Y_SCALE = 0.5f

    private fun parse(bytes: ByteArray): List<Country> {
        var o = 0
        fun u8(): Int = bytes[o++].toInt() and 0xFF
        fun u16(): Int {
            val v = (bytes[o].toInt() and 0xFF) or ((bytes[o + 1].toInt() and 0xFF) shl 8)
            o += 2
            return v
        }

        require(bytes.size > 6) { "world map asset truncated" }
        require(
            bytes[0] == 'Z'.code.toByte() && bytes[1] == 'W'.code.toByte() &&
                bytes[2] == 'M'.code.toByte() && bytes[3] == '1'.code.toByte()
        ) { "world map asset has the wrong magic" }
        o = 4

        val count = u16()
        val out = ArrayList<Country>(count)
        repeat(count) {
            val code = "${bytes[o].toInt().toChar()}${bytes[o + 1].toInt().toChar()}"
            o += 2
            val ax = u16().toFloat()

            val ay = u16().toFloat() * Y_SCALE
            val ringCount = u8()
            val rings = ArrayList<FloatArray>(ringCount)
            repeat(ringCount) {
                val pointCount = u16()
                val ring = FloatArray(pointCount * 2)
                for (i in 0 until pointCount) {
                    ring[i * 2] = u16().toFloat()
                    ring[i * 2 + 1] = u16().toFloat() * Y_SCALE
                }
                rings += ring
            }
            out += Country(code, ax, ay, rings)
        }
        return out
    }
}
