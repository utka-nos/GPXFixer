package com.gpxeditor.shared.data.ble

data class SelectedPowerSensor(val id: String, val name: String?)

interface PowerSensorSettingsStorage {
    fun read(): String?
    fun write(content: String?)
}

/** Persists the selected sensor in the same small JSON-file style as other app storage. */
class PowerSensorSettingsStore(private val storage: PowerSensorSettingsStorage) {
    fun load(): SelectedPowerSensor? {
        val json = storage.read() ?: return null
        val id = JSON_STRING.find(json, "id") ?: return null
        val name = JSON_STRING.find(json, "name")
        return SelectedPowerSensor(id = id, name = name)
    }

    fun save(sensor: SelectedPowerSensor?) {
        storage.write(sensor?.let(::encode))
    }

    private fun encode(sensor: SelectedPowerSensor): String = buildString {
        append("{\"id\":\"")
        append(sensor.id.escapeJson())
        append("\",\"name\":")
        if (sensor.name == null) append("null") else {
            append('"')
            append(sensor.name.escapeJson())
            append('"')
        }
        append('}')
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

    private object JSON_STRING {
        fun find(json: String, key: String): String? {
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
    }
}
