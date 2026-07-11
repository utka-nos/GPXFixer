package com.gpxeditor.android.recording

import com.gpxeditor.shared.data.ble.KablePowerSensor
import com.gpxeditor.shared.data.ble.PowerSensorConnectionState
import com.gpxeditor.shared.data.ble.PowerSensorDevice
import com.gpxeditor.shared.data.ble.PowerSensorSettingsStore
import com.gpxeditor.shared.data.ble.SelectedPowerSensor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PowerSensorScreenState(
    val selected: SelectedPowerSensor? = null,
    val devices: List<PowerSensorDevice> = emptyList(),
    val isScanning: Boolean = false,
    val connectionState: PowerSensorConnectionState = PowerSensorConnectionState.Disconnected,
    val errorMessage: String? = null,
)

class PowerSensorController(
    private val scope: CoroutineScope,
    private val sensor: KablePowerSensor,
    private val settings: PowerSensorSettingsStore,
) {
    private val mutableState = MutableStateFlow(PowerSensorScreenState(selected = settings.load()))
    val state: StateFlow<PowerSensorScreenState> = mutableState.asStateFlow()
    private var scanJob: Job? = null

    init {
        scope.launch {
            sensor.connectionState.collect { connectionState ->
                mutableState.value = mutableState.value.copy(connectionState = connectionState)
            }
        }
    }

    fun startScan() {
        if (scanJob != null) return
        mutableState.value = mutableState.value.copy(
            devices = emptyList(),
            isScanning = true,
            errorMessage = null,
        )
        scanJob = scope.launch {
            try {
                sensor.scan().collect { device ->
                    val devices = mutableState.value.devices
                        .filterNot { it.id == device.id }
                        .plus(device)
                        .sortedByDescending(PowerSensorDevice::rssi)
                    mutableState.value = mutableState.value.copy(devices = devices)
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                mutableState.value = mutableState.value.copy(
                    errorMessage = throwable.message ?: "Bluetooth scan failed",
                )
            } finally {
                scanJob = null
                mutableState.value = mutableState.value.copy(isScanning = false)
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        mutableState.value = mutableState.value.copy(isScanning = false)
    }

    fun closeScreen() {
        stopScan()
        scope.launch { sensor.disconnect() }
    }

    fun select(device: PowerSensorDevice) {
        stopScan()
        scope.launch {
            sensor.disconnect()
            sensor.connect(device.id)
            if (sensor.connectionState.value is PowerSensorConnectionState.Connected) {
                val selected = SelectedPowerSensor(device.id, device.name)
                settings.save(selected)
                mutableState.value = mutableState.value.copy(selected = selected, errorMessage = null)
            }
        }
    }

    fun forget() {
        stopScan()
        settings.save(null)
        mutableState.value = mutableState.value.copy(selected = null, devices = emptyList())
        scope.launch { sensor.disconnect() }
    }
}
