package com.gpxeditor.shared.core.time

/**
 * Parses an ISO-8601 UTC instant (`yyyy-MM-ddTHH:mm:ss`, optional fractional seconds
 * and trailing `Z`) into Unix epoch seconds. Returns `null` for anything it cannot
 * parse; non-UTC offsets are not supported and treated as UTC.
 */
internal fun isoToUnixSeconds(iso: String): Long? {
    if (iso.length < 19) return null
    return try {
        val year = iso.substring(0, 4).toInt()
        if (iso[4] != '-' || iso[7] != '-') return null
        val month = iso.substring(5, 7).toInt()
        val day = iso.substring(8, 10).toInt()
        if (iso[10] != 'T' && iso[10] != ' ') return null
        val hour = iso.substring(11, 13).toInt()
        val minute = iso.substring(14, 16).toInt()
        val second = iso.substring(17, 19).toInt()
        daysFromCivil(year, month, day) * 86400L + hour * 3600L + minute * 60L + second
    } catch (exception: NumberFormatException) {
        null
    }
}

/** Days since 1970-01-01 for a civil date (Howard Hinnant's algorithm). */
private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
    val y = if (month <= 2) year - 1L else year.toLong()
    val era = (if (y >= 0) y else y - 399) / 400
    val yearOfEra = y - era * 400
    val monthOffset = if (month > 2) month - 3 else month + 9
    val dayOfYear = (153 * monthOffset + 2) / 5 + day - 1
    val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
    return era * 146097 + dayOfEra - 719468
}

/** Formats Unix epoch seconds as an ISO-8601 UTC instant (`yyyy-MM-ddTHH:mm:ssZ`). */
internal fun unixSecondsToIso(unixSeconds: Long): String {
    var days = unixSeconds / SECONDS_PER_DAY
    var secondsOfDay = unixSeconds % SECONDS_PER_DAY
    if (secondsOfDay < 0) {
        secondsOfDay += SECONDS_PER_DAY
        days -= 1
    }

    var z = days + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val dayOfEra = z - era * 146097
    val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / 146096) / 365
    val year = yearOfEra + era * 400
    val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    val mp = (5 * dayOfYear + 2) / 153
    val day = dayOfYear - (153 * mp + 2) / 5 + 1
    val month = if (mp < 10) mp + 3 else mp - 9
    val calendarYear = if (month <= 2) year + 1 else year

    val hour = secondsOfDay / 3600
    val minute = (secondsOfDay % 3600) / 60
    val second = secondsOfDay % 60

    return buildString {
        append(pad(calendarYear, 4))
        append('-')
        append(pad(month, 2))
        append('-')
        append(pad(day, 2))
        append('T')
        append(pad(hour, 2))
        append(':')
        append(pad(minute, 2))
        append(':')
        append(pad(second, 2))
        append('Z')
    }
}

private const val SECONDS_PER_DAY = 86400L

private fun pad(value: Long, width: Int): String {
    val text = value.toString()
    if (text.length >= width) {
        return text
    }
    return "0".repeat(width - text.length) + text
}
