package com.gpxeditor.android.recording

import com.gpxeditor.shared.data.ble.HeartRateSensorConnectionState
import com.gpxeditor.shared.data.ble.HeartRateSensorDevice
import com.gpxeditor.shared.data.ble.HeartRateSensorSettingsStore
import com.gpxeditor.shared.data.ble.KableHeartRateSensor
import com.gpxeditor.shared.data.ble.SelectedHeartRateSensor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HeartRateSensorScreenState(
    val selected: SelectedHeartRateSensor? = null,
    val devices: List<HeartRateSensorDevice> = emptyList(),
    val isScanning: Boolean = false,
    val connectionState: HeartRateSensorConnectionState = HeartRateSensorConnectionState.Disconnected,
    val errorMessage: String? = null,
)

class HeartRateSensorController(
    private val scope: CoroutineScope,
    private val sensor: KableHeartRateSensor,
    private val settings: HeartRateSensorSettingsStore,
) {
    private val mutableState = MutableStateFlow(HeartRateSensorScreenState(selected = settings.load()))
    val state: StateFlow<HeartRateSensorScreenState> = mutableState.asStateFlow()
    private var scanJob: Job? = null
    private var selectionJob: Job? = null

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
                        .sortedByDescending(HeartRateSensorDevice::rssi)
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
        val activeSelection = selectionJob
        selectionJob = null
        activeSelection?.cancel()
        scope.launch {
            activeSelection?.join()
            sensor.disconnect()
        }
    }

    fun select(device: HeartRateSensorDevice) {
        if (selectionJob != null) return
        stopScan()
        selectionJob = scope.launch {
            try {
                sensor.disconnect()
                sensor.connect(device.id)
                if (sensor.connectionState.value is HeartRateSensorConnectionState.Connected) {
                    val selected = SelectedHeartRateSensor(device.id, device.name)
                    settings.save(selected)
                    mutableState.value = mutableState.value.copy(selected = selected, errorMessage = null)
                }
            } finally {
                selectionJob = null
            }
        }
    }

    fun forget() {
        stopScan()
        val activeSelection = selectionJob
        selectionJob = null
        activeSelection?.cancel()
        settings.save(null)
        mutableState.value = mutableState.value.copy(selected = null, devices = emptyList())
        scope.launch {
            activeSelection?.join()
            sensor.disconnect()
        }
    }
}
