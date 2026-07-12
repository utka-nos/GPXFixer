package com.gpxeditor.shared.data.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HeartRateSensorSettingsStoreTest {
    @Test
    fun savesAndLoadsSelectedSensor() {
        val storage = MemoryStorage()
        val store = HeartRateSensorSettingsStore(storage)
        val sensor = SelectedHeartRateSensor("AA:BB\\CC", "Chest \"strap\"")

        store.save(sensor)

        assertEquals(sensor, store.load())
        assertEquals("{\"id\":\"AA:BB\\\\CC\",\"name\":\"Chest \\\"strap\\\"\"}", storage.content)
    }

    @Test
    fun forgetRemovesSettings() {
        val storage = MemoryStorage("{\"id\":\"sensor-id\",\"name\":null}")
        val store = HeartRateSensorSettingsStore(storage)
        assertEquals(SelectedHeartRateSensor("sensor-id", null), store.load())

        store.save(null)

        assertNull(store.load())
        assertNull(storage.content)
    }

    @Test
    fun malformedSettingsAreIgnored() {
        assertNull(HeartRateSensorSettingsStore(MemoryStorage("not json")).load())
    }

    private class MemoryStorage(var content: String? = null) : HeartRateSensorSettingsStorage {
        override fun read(): String? = content
        override fun write(content: String?) { this.content = content }
    }
}
