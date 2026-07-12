package com.gpxeditor.shared.data.ble

data class SelectedHeartRateSensor(val id: String, val name: String?)

interface HeartRateSensorSettingsStorage {
    fun read(): String?
    fun write(content: String?)
}

/** Persists the selected sensor in the same small JSON-file style as other app storage. */
class HeartRateSensorSettingsStore(private val storage: HeartRateSensorSettingsStorage) {
    fun load(): SelectedHeartRateSensor? {
        val json = storage.read() ?: return null
        val id = SensorSettingsJson.findString(json, "id") ?: return null
        val name = SensorSettingsJson.findString(json, "name")
        return SelectedHeartRateSensor(id = id, name = name)
    }

    fun save(sensor: SelectedHeartRateSensor?) {
        storage.write(sensor?.let { SensorSettingsJson.encode(it.id, it.name) })
    }
}
