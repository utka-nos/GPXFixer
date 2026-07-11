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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CYCLING_POWER_SERVICE_UUID = 0x1818
private const val CYCLING_POWER_MEASUREMENT_UUID = 0x2A63

private val cyclingPowerService = Bluetooth.BaseUuid + CYCLING_POWER_SERVICE_UUID
private val cyclingPowerMeasurement = characteristicOf(
    service = cyclingPowerService.toString(),
    characteristic = (Bluetooth.BaseUuid + CYCLING_POWER_MEASUREMENT_UUID).toString(),
)

/** Kable-backed Cycling Power Service transport shared by Android and iOS. */
class KablePowerSensor(
    private val scope: CoroutineScope,
    private val parser: CyclingPowerMeasurementParser = CyclingPowerMeasurementParser(),
) : PowerSensor {
    private val scanner = Scanner {
        filters {
            match { services = listOf(cyclingPowerService) }
        }
    }

    private val mutableConnectionState =
        MutableStateFlow<PowerSensorConnectionState>(PowerSensorConnectionState.Disconnected)
    override val connectionState = mutableConnectionState.asStateFlow()

    private val mutableSamples = MutableSharedFlow<PowerSample>(extraBufferCapacity = 16)
    override val samples: Flow<PowerSample> = mutableSamples.asSharedFlow()

    private var peripheral: Peripheral? = null
    private var observationJob: Job? = null
    private var stateJob: Job? = null

    override suspend fun connect() {
        if (peripheral != null) return

        mutableConnectionState.value = PowerSensorConnectionState.Scanning
        try {
            val advertisement = scanner.advertisements.first()
            val sensorName = advertisement.name ?: advertisement.peripheralName
            mutableConnectionState.value = PowerSensorConnectionState.Connecting(sensorName)

            val foundPeripheral = scope.peripheral(advertisement)
            peripheral = foundPeripheral
            parser.reset()
            observeMeasurements(foundPeripheral)
            foundPeripheral.connect()
            observeConnectionState(foundPeripheral, sensorName)
            mutableConnectionState.value = PowerSensorConnectionState.Connected(sensorName)
            println("GPXFixer PowerSensor connected: ${sensorName ?: foundPeripheral.identifier}")
        } catch (cancellation: CancellationException) {
            closeFailedConnection()
            throw cancellation
        } catch (throwable: Throwable) {
            closeFailedConnection()
            val message = throwable.message ?: throwable::class.simpleName ?: "BLE connection failed"
            mutableConnectionState.value = PowerSensorConnectionState.Failed(message)
            println("GPXFixer PowerSensor failed: $message")
        }
    }

    override suspend fun disconnect() {
        val activePeripheral = peripheral
        observationJob?.cancelAndJoin()
        stateJob?.cancelAndJoin()
        observationJob = null
        stateJob = null
        peripheral = null
        parser.reset()
        activePeripheral?.disconnect()
        mutableConnectionState.value = PowerSensorConnectionState.Disconnected
    }

    private fun observeMeasurements(activePeripheral: Peripheral) {
        observationJob = scope.launch {
            activePeripheral.observe(cyclingPowerMeasurement).collect { bytes ->
                try {
                    val sample = parser.parse(bytes)
                    mutableSamples.emit(sample)
                    println(
                        "GPXFixer PowerSensor: ${sample.watts} W" +
                            (sample.cadenceRpm?.let { ", cadence ${it.toInt()} rpm" } ?: ""),
                    )
                } catch (error: CyclingPowerParseException) {
                    println("GPXFixer PowerSensor ignored invalid measurement: ${error.message}")
                }
            }
        }
    }

    private fun observeConnectionState(activePeripheral: Peripheral, sensorName: String?) {
        stateJob = scope.launch {
            activePeripheral.state.collect { state ->
                mutableConnectionState.value = when (state) {
                    State.Connected -> PowerSensorConnectionState.Connected(sensorName)
                    is State.Connecting -> PowerSensorConnectionState.Connecting(sensorName)
                    is State.Disconnected -> PowerSensorConnectionState.Disconnected
                    State.Disconnecting -> PowerSensorConnectionState.Disconnected
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
        parser.reset()
    }

    private suspend fun closeFailedConnection() {
        val failedPeripheral = peripheral
        withContext(NonCancellable) {
            runCatching { failedPeripheral?.disconnect() }
        }
        clearConnection()
    }
}
