package com.gpxeditor.shared.data.ble

import kotlin.test.Test
import kotlin.test.assertEquals

class PowerSensorReconnectManagerTest {
    @Test
    fun backoffGrowsExponentiallyAndCapsAtFifteenSeconds() {
        assertEquals(0L, PowerSensorReconnectManager.backoffMillis(0))
        assertEquals(1_000L, PowerSensorReconnectManager.backoffMillis(1))
        assertEquals(2_000L, PowerSensorReconnectManager.backoffMillis(2))
        assertEquals(4_000L, PowerSensorReconnectManager.backoffMillis(3))
        assertEquals(8_000L, PowerSensorReconnectManager.backoffMillis(4))
        assertEquals(15_000L, PowerSensorReconnectManager.backoffMillis(5))
        assertEquals(15_000L, PowerSensorReconnectManager.backoffMillis(20))
    }
}
