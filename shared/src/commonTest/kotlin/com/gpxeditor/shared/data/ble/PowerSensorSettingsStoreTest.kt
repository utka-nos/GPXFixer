package com.gpxeditor.shared.data.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PowerSensorSettingsStoreTest {
    @Test
    fun savesAndLoadsSelectedSensor() {
        val storage = MemoryStorage()
        val store = PowerSensorSettingsStore(storage)
        val sensor = SelectedPowerSensor("AA:BB\\CC", "Left \"pedal\"")

        store.save(sensor)

        assertEquals(sensor, store.load())
        assertEquals("{\"id\":\"AA:BB\\\\CC\",\"name\":\"Left \\\"pedal\\\"\"}", storage.content)
    }

    @Test
    fun forgetRemovesSettings() {
        val storage = MemoryStorage("{\"id\":\"sensor-id\",\"name\":null}")
        val store = PowerSensorSettingsStore(storage)
        assertEquals(SelectedPowerSensor("sensor-id", null), store.load())

        store.save(null)

        assertNull(store.load())
        assertNull(storage.content)
    }

    @Test
    fun malformedSettingsAreIgnored() {
        assertNull(PowerSensorSettingsStore(MemoryStorage("not json")).load())
    }

    private class MemoryStorage(var content: String? = null) : PowerSensorSettingsStorage {
        override fun read(): String? = content
        override fun write(content: String?) { this.content = content }
    }
}
