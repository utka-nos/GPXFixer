package com.gpxeditor.shared.data.ble

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface PowerSensor {
    val connectionState: StateFlow<PowerSensorConnectionState>
    val samples: Flow<PowerSample>

    /** Emits nearby devices advertising the Cycling Power Service. */
    fun scan(): Flow<PowerSensorDevice>

    /** Scans for [sensorId] (or the first power sensor when null) and connects to it. */
    suspend fun connect(sensorId: String? = null)

    suspend fun disconnect()
}

data class PowerSensorDevice(
    val id: String,
    val name: String?,
    val rssi: Int,
)

sealed interface PowerSensorConnectionState {
    data object Disconnected : PowerSensorConnectionState
    data object Scanning : PowerSensorConnectionState
    data class Connecting(val sensorName: String?) : PowerSensorConnectionState
    data class Connected(val sensorName: String?) : PowerSensorConnectionState
    data class Failed(val message: String) : PowerSensorConnectionState
}
