package com.gpxeditor.shared.data.ble

import kotlin.test.Test
import kotlin.test.assertEquals

class HeartRateSensorReconnectManagerTest {
    @Test
    fun backoffGrowsExponentiallyAndCapsAtFifteenSeconds() {
        assertEquals(0L, HeartRateSensorReconnectManager.backoffMillis(0))
        assertEquals(1_000L, HeartRateSensorReconnectManager.backoffMillis(1))
        assertEquals(2_000L, HeartRateSensorReconnectManager.backoffMillis(2))
        assertEquals(4_000L, HeartRateSensorReconnectManager.backoffMillis(3))
        assertEquals(8_000L, HeartRateSensorReconnectManager.backoffMillis(4))
        assertEquals(15_000L, HeartRateSensorReconnectManager.backoffMillis(5))
        assertEquals(15_000L, HeartRateSensorReconnectManager.backoffMillis(20))
    }
}
