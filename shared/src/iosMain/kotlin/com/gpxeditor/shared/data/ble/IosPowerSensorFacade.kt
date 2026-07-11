package com.gpxeditor.shared.data.ble

import com.gpxeditor.shared.data.imported.documentsDirectory
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.writeToURL

@OptIn(ExperimentalForeignApi::class)
private class IosPowerSensorSettingsStorage : PowerSensorSettingsStorage {
    private val file = documentsDirectory().URLByAppendingPathComponent("settings/power_sensor.json")!!

    override fun read(): String? = NSString.stringWithContentsOfURL(
        url = file,
        encoding = NSUTF8StringEncoding,
        error = null,
    ) as String?

    override fun write(content: String?) {
        if (content == null) {
            file.path?.let { path ->
                if (NSFileManager.defaultManager.fileExistsAtPath(path)) {
                    NSFileManager.defaultManager.removeItemAtURL(file, error = null)
                }
            }
            return
        }
        NSFileManager.defaultManager.createDirectoryAtURL(
            file.URLByDeletingLastPathComponent!!,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        check(
            (content as NSString).writeToURL(
                url = file,
                atomically = true,
                encoding = NSUTF8StringEncoding,
                error = null,
            ),
        )
    }
}

/** Callback-based facade that keeps coroutine APIs from leaking into SwiftUI. */
class IosPowerSensorFacade {
    private val scope = MainScope()
    private val sensor = KablePowerSensor(scope)
    private val settings = PowerSensorSettingsStore(IosPowerSensorSettingsStorage())
    private var scanJob: Job? = null
    private var sampleJob: Job? = null

    fun selectedSensor(): SelectedPowerSensor? = settings.load()

    fun startScan(onDevice: (PowerSensorDevice) -> Unit, onError: (String) -> Unit) {
        stopScan()
        scanJob = scope.launch {
            try {
                sensor.scan().collect(onDevice)
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                onError(throwable.message ?: "Bluetooth scan failed")
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
    }

    fun closeScreen() {
        stopScan()
        scope.launch { sensor.disconnect() }
    }

    fun select(device: PowerSensorDevice, onComplete: (Boolean) -> Unit) {
        stopScan()
        scope.launch {
            sensor.disconnect()
            sensor.connect(device.id)
            val connected = sensor.connectionState.value is PowerSensorConnectionState.Connected
            if (connected) settings.save(SelectedPowerSensor(device.id, device.name))
            onComplete(connected)
        }
    }

    fun forget() {
        settings.save(null)
        scope.launch { sensor.disconnect() }
    }

    fun connectSaved(onSample: (PowerSample) -> Unit) {
        val selected = settings.load() ?: return
        sampleJob?.cancel()
        sampleJob = scope.launch { sensor.samples.collect(onSample) }
        scope.launch { sensor.connect(selected.id) }
    }

    fun disconnect() {
        sampleJob?.cancel()
        sampleJob = null
        scope.launch { sensor.disconnect() }
    }

    fun close() {
        scope.cancel()
    }
}
