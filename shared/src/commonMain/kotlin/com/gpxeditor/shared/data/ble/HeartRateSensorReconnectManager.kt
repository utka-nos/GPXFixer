package com.gpxeditor.shared.data.ble

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

enum class HeartRateSensorRecordingStatus {
    NOT_CONFIGURED,
    NOT_CONNECTED,
    RECONNECTING,
    CONNECTED,
}

/** Keeps a selected heart rate sensor connected until the owning coroutine is cancelled. */
class HeartRateSensorReconnectManager(
    private val sensor: HeartRateSensor,
) {
    suspend fun run(sensorId: String, onStatus: (HeartRateSensorRecordingStatus) -> Unit) {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            onStatus(
                if (attempt == 0) HeartRateSensorRecordingStatus.NOT_CONNECTED
                else HeartRateSensorRecordingStatus.RECONNECTING,
            )
            try {
                sensor.connect(sensorId)
                if (sensor.connectionState.value is HeartRateSensorConnectionState.Connected) {
                    onStatus(HeartRateSensorRecordingStatus.CONNECTED)
                    attempt = 0
                    sensor.connectionState.first { state ->
                        state is HeartRateSensorConnectionState.Disconnected ||
                            state is HeartRateSensorConnectionState.Failed
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
            onStatus(HeartRateSensorRecordingStatus.RECONNECTING)
            delay(backoffMillis(attempt))
        }
    }

    companion object {
        fun backoffMillis(attempt: Int): Long = PowerSensorReconnectManager.backoffMillis(attempt)
    }
}
