package com.gpxeditor.shared.data.ble

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface HeartRateSensor {
    val connectionState: StateFlow<HeartRateSensorConnectionState>
    val samples: Flow<HeartRateSample>

    /** Emits nearby devices advertising the Heart Rate Service. */
    fun scan(): Flow<HeartRateSensorDevice>

    /** Scans for [sensorId] (or the first heart rate sensor when null) and connects to it. */
    suspend fun connect(sensorId: String? = null)

    suspend fun disconnect()
}

data class HeartRateSensorDevice(
    val id: String,
    val name: String?,
    val rssi: Int,
)

sealed interface HeartRateSensorConnectionState {
    data object Disconnected : HeartRateSensorConnectionState
    data object Scanning : HeartRateSensorConnectionState
    data class Connecting(val sensorName: String?) : HeartRateSensorConnectionState
    data class Connected(val sensorName: String?) : HeartRateSensorConnectionState
    data class Failed(val message: String) : HeartRateSensorConnectionState
}
