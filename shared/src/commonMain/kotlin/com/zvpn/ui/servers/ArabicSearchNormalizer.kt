package com.zvpn.ui.servers

/**
 * Normalizes text for robust multi-lingual search (EN + AR diacritics removal, letter unification).
 */
object ArabicSearchNormalizer {
    private val TASHKEEL_REGEX = Regex("""[\u064B-\u0652]""")
    private val ALEF_REGEX = Regex("""[إأآٱ]""")
    private val HEEH_TEH_REGEX = Regex("""ة""")
    private val YEH_REGEX = Regex("""ى""")

    fun normalize(input: String?): String {
        if (input.isNullOrBlank()) return ""
        var res = input.trim().lowercase()
        // Remove Arabic tashkeel / diacritics
        res = TASHKEEL_REGEX.replace(res, "")
        // Unify Alef variations to ا
        res = ALEF_REGEX.replace(res, "ا")
        // Unify Teh Marbuta to ه
        res = HEEH_TEH_REGEX.replace(res, "ه")
        // Unify Alef Maksura to ي
        res = YEH_REGEX.replace(res, "ي")
        return res
    }

    fun rankMatch(query: String, target: String): Int {
        val q = normalize(query)
        val t = normalize(target)
        if (q.isEmpty()) return 2
        return when {
            t == q -> 0       // Exact match
            t.startsWith(q) -> 1 // Prefix match
            t.contains(q) -> 2   // Contains match
            else -> 3            // No match
        }
    }
}
