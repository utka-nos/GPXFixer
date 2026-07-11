package com.gpxeditor.shared.data.fit

/**
 * FIT `sport` enum codes and their names, shared by [FitActivityDecoder] and
 * [FitEncoder] so a sport survives a decode/encode round-trip. Codes without a
 * known name map to `sport_<code>` and back.
 */
internal object FitSports {
    const val GENERIC = 0L

    private val NAMES = mapOf(
        0 to "generic",
        1 to "running",
        2 to "cycling",
        5 to "swimming",
        11 to "walking",
        12 to "cross_country_skiing",
        13 to "alpine_skiing",
        14 to "snowboarding",
        15 to "rowing",
        17 to "hiking",
    )

    private val CODES = NAMES.entries.associate { (code, name) -> name to code.toLong() }

    fun name(code: Long): String = NAMES[code.toInt()] ?: "sport_$code"

    fun code(name: String?): Long {
        val normalized = name?.trim()?.lowercase() ?: return GENERIC
        CODES[normalized]?.let { return it }
        if (normalized.startsWith("sport_")) {
            normalized.removePrefix("sport_").toLongOrNull()?.let { return it }
        }
        return GENERIC
    }
}
