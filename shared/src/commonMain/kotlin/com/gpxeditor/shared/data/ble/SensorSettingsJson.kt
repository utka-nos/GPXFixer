package com.gpxeditor.shared.data.ble

/** Encodes and decodes the `{"id":…,"name":…}` JSON shared by the sensor settings stores. */
internal object SensorSettingsJson {
    fun encode(id: String, name: String?): String = buildString {
        append("{\"id\":\"")
        append(id.escapeJson())
        append("\",\"name\":")
        if (name == null) append("null") else {
            append('"')
            append(name.escapeJson())
            append('"')
        }
        append('}')
    }

    fun findString(json: String, key: String): String? {
        val keyStart = json.indexOf("\"$key\"")
        if (keyStart < 0) return null
        var position = json.indexOf(':', keyStart) + 1
        if (position == 0) return null
        while (position < json.length && json[position].isWhitespace()) position++
        if (json.startsWith("null", position) || json.getOrNull(position) != '"') return null
        position++
        val result = StringBuilder()
        while (position < json.length) {
            val character = json[position++]
            if (character == '"') return result.toString()
            if (character != '\\') result.append(character) else {
                val escaped = json.getOrNull(position++) ?: return null
                result.append(
                    when (escaped) {
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        '"' -> '"'
                        '\\' -> '\\'
                        else -> return null
                    },
                )
            }
        }
        return null
    }

    private fun String.escapeJson(): String = buildString {
        for (character in this@escapeJson) {
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
    }
}
