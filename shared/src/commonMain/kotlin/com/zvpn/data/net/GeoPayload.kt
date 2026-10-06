package com.zvpn.data.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object GeoPayload {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(body: String): NetworkInfo {
        val obj = json.parseToJsonElement(body).jsonObject

        fun JsonObject?.text(key: String): String? {
            val v = runCatching { this?.get(key)?.jsonPrimitive?.content }.getOrNull()
            return v?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
        }

        fun str(vararg keys: String): String? = keys.firstNotNullOfOrNull { obj.text(it) }

        require(obj.text("success") != "false") { "provider reported failure" }

        val location = runCatching { obj["location"]?.jsonObject }.getOrNull()
        val connection = runCatching { obj["connection"]?.jsonObject }.getOrNull()
        val timezoneObj = runCatching { obj["timezone"]?.jsonObject }.getOrNull()
        val currencyObj = runCatching { obj["currency"]?.jsonObject }.getOrNull()

        val reported = str("ip", "clientIp", "ip_addr", "query", "ipAddress")
        val reportedIsV6 = reported != null && reported.contains(':')

        val countryValues = listOfNotNull(str("country_name"), str("countryName"), str("country"))

        val asnVal = str("asn", "as_number", "asn_code")
            ?: connection.text("asn")
            ?: connection.text("asn_code")
        val regionVal = str("region", "region_name", "regionName", "state")

        val timezoneVal = str("timezone", "time_zone")
            ?: location.text("timezone")
            ?: timezoneObj.text("id")

        val latVal = str("latitude", "lat") ?: location.text("latitude")
        val lonVal = str("longitude", "lon", "lng") ?: location.text("longitude")
        val postalVal = str("postal", "zip", "postal_code")
        val orgVal = str("org", "organization") ?: connection.text("org") ?: connection.text("isp")
        val continentVal = str("continent", "continent_name", "continentName") ?: location.text("continent")
        val capitalVal = str("capital", "country_capital") ?: location.text("capital")
        val callingVal = str("calling_code", "country_calling_code", "country_phone") ?: location.text("calling_code")

        val currencyVal = currencyObj?.let { c ->
            val code = c.text("code") ?: c.text("currency_code")
            val name = c.text("name")
            if (code != null && name != null) "$name ($code)" else code ?: name
        } ?: str("currency", "currency_code")

        val netTypeVal = str("type", "connection_type", "type_name") ?: connection.text("type")

        val rawInfo = NetworkInfo(
            ipv4 = reported?.takeUnless { reportedIsV6 },
            ipv6 = reported?.takeIf { reportedIsV6 },
            isp = (
                str("isp", "organization", "asn_organization", "asOrganization", "asnOrganization", "asn_org", "org", "as")
                    ?: connection.text("isp")
                    ?: connection.text("org")
                )?.let { withoutAsNumber(it) },
            org = orgVal,
            asn = asnVal,
            city = str("city", "cityName"),
            region = regionVal,
            country = countryValues.firstOrNull { !isIsoCode(it) },
            countryCode = (
                str("country_code", "countryCode", "country_iso")
                    ?: location.text("country_code")
                    ?: countryValues.firstOrNull { isIsoCode(it) }
                )?.uppercase(),
            continent = continentVal,
            countryCapital = capitalVal,
            timezone = timezoneVal,
            lat = latVal,
            lon = lonVal,
            postal = postalVal,
            callingCode = callingVal,
            currency = currencyVal,
            netType = netTypeVal,
        )

        return fillCountryDefaults(rawInfo)
    }

    fun isComplete(info: NetworkInfo): Boolean =
        info.isp != null && info.city != null && info.country != null

    fun complete(winner: NetworkInfo, other: NetworkInfo): NetworkInfo {
        val sameAddress = when {
            winner.ipv4 != null && other.ipv4 != null -> winner.ipv4 == other.ipv4
            winner.ipv6 != null && other.ipv6 != null -> winner.ipv6 == other.ipv6

            else -> winner.countryCode != null && winner.countryCode == other.countryCode
        }
        if (!sameAddress) return fillCountryDefaults(winner)
        val sameCountry = winner.countryCode != null && winner.countryCode == other.countryCode
        val merged = winner.copy(
            isp = winner.isp ?: other.isp,
            org = winner.org ?: other.org,
            asn = winner.asn ?: other.asn,
            city = winner.city ?: other.city.takeIf { sameCountry },
            region = winner.region ?: other.region.takeIf { sameCountry },
            country = winner.country ?: other.country.takeIf { sameCountry },
            continent = winner.continent ?: other.continent,
            countryCapital = winner.countryCapital ?: other.countryCapital,
            timezone = winner.timezone ?: other.timezone,
            lat = winner.lat ?: other.lat,
            lon = winner.lon ?: other.lon,
            postal = winner.postal ?: other.postal,
            callingCode = winner.callingCode ?: other.callingCode,
            currency = winner.currency ?: other.currency,
            netType = winner.netType ?: other.netType,
        )
        return fillCountryDefaults(merged)
    }

    fun fillCountryDefaults(info: NetworkInfo): NetworkInfo {
        val code = info.countryCode?.uppercase() ?: return info
        val defaults = getCountryDefaults(code) ?: return info
        return info.copy(
            continent = info.continent ?: defaults.continent,
            countryCapital = info.countryCapital ?: defaults.capital,
            callingCode = info.callingCode ?: defaults.calling,
            currency = info.currency ?: defaults.currency,
        )
    }

    private data class CountryMeta(val continent: String, val capital: String, val calling: String, val currency: String)

    private fun getCountryDefaults(code: String): CountryMeta? = when (code) {
        "LY" -> CountryMeta("Africa", "Tripoli", "218", "Libyan Dinar (LYD)")
        "EG" -> CountryMeta("Africa", "Cairo", "20", "Egyptian Pound (EGP)")
        "SA" -> CountryMeta("Asia", "Riyadh", "966", "Saudi Riyal (SAR)")
        "AE" -> CountryMeta("Asia", "Abu Dhabi", "971", "UAE Dirham (AED)")
        "US" -> CountryMeta("North America", "Washington, D.C.", "1", "US Dollar (USD)")
        "GB" -> CountryMeta("Europe", "London", "44", "British Pound (GBP)")
        "DE" -> CountryMeta("Europe", "Berlin", "49", "Euro (EUR)")
        "FR" -> CountryMeta("Europe", "Paris", "33", "Euro (EUR)")
        "TR" -> CountryMeta("Asia / Europe", "Ankara", "90", "Turkish Lira (TRY)")
        "CA" -> CountryMeta("North America", "Ottawa", "1", "Canadian Dollar (CAD)")
        "JP" -> CountryMeta("Asia", "Tokyo", "81", "Japanese Yen (JPY)")
        "CN" -> CountryMeta("Asia", "Beijing", "86", "Chinese Yuan (CNY)")
        "RU" -> CountryMeta("Europe / Asia", "Moscow", "7", "Russian Ruble (RUB)")
        "IN" -> CountryMeta("Asia", "New Delhi", "91", "Indian Rupee (INR)")
        "BR" -> CountryMeta("South America", "Brasília", "55", "Brazilian Real (BRL)")
        "AU" -> CountryMeta("Oceania", "Canberra", "61", "Australian Dollar (AUD)")
        "NL" -> CountryMeta("Europe", "Amsterdam", "31", "Euro (EUR)")
        "SG" -> CountryMeta("Asia", "Singapore", "65", "Singapore Dollar (SGD)")
        "SE" -> CountryMeta("Europe", "Stockholm", "46", "Swedish Krona (SEK)")
        "CH" -> CountryMeta("Europe", "Bern", "41", "Swiss Franc (CHF)")
        "IT" -> CountryMeta("Europe", "Rome", "39", "Euro (EUR)")
        "ES" -> CountryMeta("Europe", "Madrid", "34", "Euro (EUR)")
        "KW" -> CountryMeta("Asia", "Kuwait City", "965", "Kuwaiti Dinar (KWD)")
        "QA" -> CountryMeta("Asia", "Doha", "974", "Qatari Riyal (QAR)")
        "BH" -> CountryMeta("Asia", "Manama", "973", "Bahraini Dinar (BHD)")
        "OM" -> CountryMeta("Asia", "Muscat", "968", "Omani Rial (OMR)")
        "JO" -> CountryMeta("Asia", "Amman", "962", "Jordanian Dinar (JOD)")
        "IQ" -> CountryMeta("Asia", "Baghdad", "964", "Iraqi Dinar (IQD)")
        "DZ" -> CountryMeta("Africa", "Algiers", "213", "Algerian Dinar (DZD)")
        "MA" -> CountryMeta("Africa", "Rabat", "212", "Moroccan Dirham (MAD)")
        "TN" -> CountryMeta("Africa", "Tunis", "216", "Tunisian Dinar (TND)")
        "LB" -> CountryMeta("Asia", "Beirut", "961", "Lebanese Pound (LBP)")
        "SY" -> CountryMeta("Asia", "Damascus", "963", "Syrian Pound (SYP)")
        "YE" -> CountryMeta("Asia", "Sana'a", "967", "Yemeni Rial (YER)")
        "SD" -> CountryMeta("Africa", "Khartoum", "249", "Sudanese Pound (SDG)")
        else -> null
    }

    private fun isIsoCode(value: String): Boolean = value.length == 2 && value.all { it.isLetter() }

    private fun withoutAsNumber(value: String): String =
        value.replace(AS_PREFIX, "").trim().ifEmpty { value }

    private val AS_PREFIX = Regex("^AS\\d+\\s+", RegexOption.IGNORE_CASE)
}
