package com.gpxeditor.shared.data.ble

import com.juul.kable.Bluetooth
import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import com.juul.kable.State
import com.juul.kable.characteristicOf
import com.juul.kable.peripheral
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val HEART_RATE_SERVICE_UUID = 0x180D
private const val HEART_RATE_MEASUREMENT_UUID = 0x2A37

private val heartRateService = Bluetooth.BaseUuid + HEART_RATE_SERVICE_UUID
private val heartRateMeasurement = characteristicOf(
    service = heartRateService.toString(),
    characteristic = (Bluetooth.BaseUuid + HEART_RATE_MEASUREMENT_UUID).toString(),
)

/** Kable-backed Heart Rate Service transport shared by Android and iOS. */
class KableHeartRateSensor(
    private val scope: CoroutineScope,
    private val parser: HeartRateMeasurementParser = HeartRateMeasurementParser(),
) : HeartRateSensor {
    private val scanner = Scanner {
        filters {
            match { services = listOf(heartRateService) }
        }
    }

    private val mutableConnectionState =
        MutableStateFlow<HeartRateSensorConnectionState>(HeartRateSensorConnectionState.Disconnected)
    override val connectionState = mutableConnectionState.asStateFlow()

    private val mutableSamples = MutableSharedFlow<HeartRateSample>(extraBufferCapacity = 16)
    override val samples: Flow<HeartRateSample> = mutableSamples.asSharedFlow()

    private var peripheral: Peripheral? = null
    private var observationJob: Job? = null
    private var stateJob: Job? = null

    override fun scan(): Flow<HeartRateSensorDevice> = scanner.advertisements.map { advertisement ->
        HeartRateSensorDevice(
            id = advertisement.identifier.toString(),
            name = advertisement.name ?: advertisement.peripheralName,
            rssi = advertisement.rssi,
        )
    }

    override suspend fun connect(sensorId: String?) {
        if (peripheral != null) return

        mutableConnectionState.value = HeartRateSensorConnectionState.Scanning
        try {
            val advertisement = scanner.advertisements.first { advertisement ->
                sensorId == null || advertisement.identifier.toString() == sensorId
            }
            val sensorName = advertisement.name ?: advertisement.peripheralName
            mutableConnectionState.value = HeartRateSensorConnectionState.Connecting(sensorName)

            val foundPeripheral = scope.peripheral(advertisement)
            peripheral = foundPeripheral
            observeMeasurements(foundPeripheral)
            foundPeripheral.connect()
            observeConnectionState(foundPeripheral, sensorName)
            mutableConnectionState.value = HeartRateSensorConnectionState.Connected(sensorName)
            println("GPXFixer HeartRateSensor connected: ${sensorName ?: foundPeripheral.identifier}")
        } catch (cancellation: CancellationException) {
            closeFailedConnection()
            throw cancellation
        } catch (throwable: Throwable) {
            closeFailedConnection()
            val message = throwable.message ?: throwable::class.simpleName ?: "BLE connection failed"
            mutableConnectionState.value = HeartRateSensorConnectionState.Failed(message)
            println("GPXFixer HeartRateSensor failed: $message")
        }
    }

    override suspend fun disconnect() {
        val activePeripheral = peripheral
        observationJob?.cancelAndJoin()
        stateJob?.cancelAndJoin()
        observationJob = null
        stateJob = null
        peripheral = null
        activePeripheral?.disconnect()
        mutableConnectionState.value = HeartRateSensorConnectionState.Disconnected
    }

    private fun observeMeasurements(activePeripheral: Peripheral) {
        observationJob = scope.launch {
            activePeripheral.observe(heartRateMeasurement).collect { bytes ->
                try {
                    val sample = parser.parse(bytes)
                    mutableSamples.emit(sample)
                    println("GPXFixer HeartRateSensor: ${sample.bpm} bpm")
                } catch (error: HeartRateParseException) {
                    println("GPXFixer HeartRateSensor ignored invalid measurement: ${error.message}")
                }
            }
        }
    }

    private fun observeConnectionState(activePeripheral: Peripheral, sensorName: String?) {
        stateJob = scope.launch {
            activePeripheral.state.collect { state ->
                mutableConnectionState.value = when (state) {
                    State.Connected -> HeartRateSensorConnectionState.Connected(sensorName)
                    is State.Connecting -> HeartRateSensorConnectionState.Connecting(sensorName)
                    is State.Disconnected -> HeartRateSensorConnectionState.Disconnected
                    State.Disconnecting -> HeartRateSensorConnectionState.Disconnected
                }
            }
        }
    }

    private fun clearConnection() {
        observationJob?.cancel()
        stateJob?.cancel()
        observationJob = null
        stateJob = null
        peripheral = null
    }

    private suspend fun closeFailedConnection() {
        val failedPeripheral = peripheral
        withContext(NonCancellable) {
            runCatching { failedPeripheral?.disconnect() }
        }
        clearConnection()
    }
}
