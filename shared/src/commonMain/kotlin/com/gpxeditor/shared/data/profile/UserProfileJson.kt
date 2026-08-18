package com.gpxeditor.shared.data.profile

import com.gpxeditor.shared.domain.profile.HeartRateZones
import com.gpxeditor.shared.domain.profile.PowerZones
import com.gpxeditor.shared.domain.profile.Sex
import com.gpxeditor.shared.domain.profile.UserProfile

/** Encodes and decodes the flat `{"weightKg":…}` JSON used by the profile settings file. */
internal object UserProfileJson {
    fun encode(profile: UserProfile): String = buildString {
        append('{')
        var first = true
        fun appendProperty(name: String, rawValue: String) {
            if (!first) append(',')
            first = false
            append('"')
            append(name)
            append("\":")
            append(rawValue)
        }
        profile.weightKg?.let { appendProperty("weightKg", it.toString()) }
        profile.bikeWeightKg?.let { appendProperty("bikeWeightKg", it.toString()) }
        profile.sex?.let { appendProperty("sex", "\"${it.name}\"") }
        profile.birthYear?.let { appendProperty("birthYear", it.toString()) }
        profile.heartRateZones?.let {
            appendProperty("heartRateZoneUpperBoundsBpm", it.upperBoundsBpm.joinToString(",", "[", "]"))
        }
        profile.ftpWatts?.let { appendProperty("ftpWatts", it.toString()) }
        profile.powerZones?.let {
            appendProperty("powerZoneUpperBoundsWatts", it.upperBoundsWatts.joinToString(",", "[", "]"))
        }
        append('}')
    }

    /** Returns null when the content is not a well-formed profile document. */
    fun decode(json: String): UserProfile? {
        val values = Scanner(json).parseObject() ?: return null
        val heartRateBounds = values.intList("heartRateZoneUpperBoundsBpm")
        val powerBounds = values.intList("powerZoneUpperBoundsWatts")
        if (heartRateBounds != null && heartRateBounds.size != HeartRateZones.BOUNDARY_COUNT) return null
        if (powerBounds != null && powerBounds.size != PowerZones.BOUNDARY_COUNT) return null
        return UserProfile(
            weightKg = values["weightKg"] as? Double,
            bikeWeightKg = values["bikeWeightKg"] as? Double,
            sex = (values["sex"] as? String)?.let { name -> Sex.entries.firstOrNull { it.name == name } },
            birthYear = values.int("birthYear"),
            heartRateZones = heartRateBounds?.let(::HeartRateZones),
            ftpWatts = values.int("ftpWatts"),
            powerZones = powerBounds?.let(::PowerZones),
        )
    }

    private fun Map<String, Any?>.int(key: String): Int? = (this[key] as? Double)?.toInt()

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.intList(key: String): List<Int>? =
        (this[key] as? List<Any?>)?.map { (it as? Double)?.toInt() ?: return null }

    /** Minimal parser for the flat objects this codec produces: strings, numbers, null, number arrays. */
    private class Scanner(private val input: String) {
        private var index = 0

        fun parseObject(): Map<String, Any?>? = try {
            skipWhitespace()
            val values = readObject()
            skipWhitespace()
            if (index == input.length) values else null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IndexOutOfBoundsException) {
            null
        }

        private fun readObject(): Map<String, Any?> {
            expect('{')
            val values = mutableMapOf<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                index++
                return values
            }
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                values[key] = readValue()
                skipWhitespace()
                when (input[index++]) {
                    ',' -> Unit
                    '}' -> return values
                    else -> throw IllegalArgumentException("Expected ',' or '}'")
                }
            }
        }

        private fun readValue(): Any? = when (peek()) {
            '"' -> readString()
            '[' -> readArray()
            'n' -> {
                require(input.startsWith("null", index)) { "Expected null" }
                index += 4
                null
            }
            else -> readNumber()
        }

        private fun readArray(): List<Any?> {
            expect('[')
            val values = mutableListOf<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                index++
                return values
            }
            while (true) {
                skipWhitespace()
                values += readValue()
                skipWhitespace()
                when (input[index++]) {
                    ',' -> Unit
                    ']' -> return values
                    else -> throw IllegalArgumentException("Expected ',' or ']'")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            return buildString {
                while (true) {
                    when (val character = input[index++]) {
                        '"' -> return@buildString
                        '\\' -> append(
                            when (val escaped = input[index++]) {
                                '"', '\\', '/' -> escaped
                                'n' -> '\n'
                                'r' -> '\r'
                                't' -> '\t'
                                else -> throw IllegalArgumentException("Unsupported escape: \\$escaped")
                            },
                        )
                        else -> append(character)
                    }
                }
            }
        }

        private fun readNumber(): Double {
            val start = index
            while (index < input.length && (input[index].isDigit() || input[index] in "-+.eE")) index++
            require(index > start) { "Expected number" }
            return requireNotNull(input.substring(start, index).toDoubleOrNull()) { "Invalid number" }
        }

        private fun expect(character: Char) {
            require(input[index] == character) { "Expected '$character'" }
            index++
        }

        private fun peek(): Char = input[index]

        private fun skipWhitespace() {
            while (index < input.length && input[index].isWhitespace()) index++
        }
    }
}
