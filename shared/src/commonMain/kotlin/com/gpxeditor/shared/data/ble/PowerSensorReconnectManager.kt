package com.gpxeditor.shared.data.ble

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

enum class PowerSensorRecordingStatus {
    NOT_CONFIGURED,
    NOT_CONNECTED,
    RECONNECTING,
    CONNECTED,
}

/** Keeps a selected power sensor connected until the owning coroutine is cancelled. */
class PowerSensorReconnectManager(
    private val sensor: PowerSensor,
) {
    suspend fun run(sensorId: String, onStatus: (PowerSensorRecordingStatus) -> Unit) {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            onStatus(
                if (attempt == 0) PowerSensorRecordingStatus.NOT_CONNECTED
                else PowerSensorRecordingStatus.RECONNECTING,
            )
            try {
                sensor.connect(sensorId)
                if (sensor.connectionState.value is PowerSensorConnectionState.Connected) {
                    onStatus(PowerSensorRecordingStatus.CONNECTED)
                    attempt = 0
                    sensor.connectionState.first { state ->
                        state is PowerSensorConnectionState.Disconnected ||
                            state is PowerSensorConnectionState.Failed
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } finally {
                withContext(NonCancellable) {
                    runCatching { sensor.disconnect() }
                }
            }

            attempt += 1
            onStatus(PowerSensorRecordingStatus.RECONNECTING)
            delay(backoffMillis(attempt))
        }
    }

    companion object {
        fun backoffMillis(attempt: Int): Long {
            if (attempt <= 0) return 0L
            val shift = (attempt - 1).coerceAtMost(4)
            return (1_000L shl shift).coerceAtMost(15_000L)
        }
    }
}
