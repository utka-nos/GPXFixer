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
private class IosHeartRateSensorSettingsStorage : HeartRateSensorSettingsStorage {
    private val file = documentsDirectory().URLByAppendingPathComponent("settings/heart_rate_sensor.json")!!

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
class IosHeartRateSensorFacade {
    private val scope = MainScope()
    private val sensor = KableHeartRateSensor(scope)
    private val settings = HeartRateSensorSettingsStore(IosHeartRateSensorSettingsStorage())
    private var scanJob: Job? = null
    private var sampleJob: Job? = null
    private var connectionJob: Job? = null

    fun selectedSensor(): SelectedHeartRateSensor? = settings.load()

    fun startScan(onDevice: (HeartRateSensorDevice) -> Unit, onError: (String) -> Unit) {
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

    fun select(device: HeartRateSensorDevice, onComplete: (Boolean) -> Unit) {
        stopScan()
        scope.launch {
            sensor.disconnect()
            sensor.connect(device.id)
            val connected = sensor.connectionState.value is HeartRateSensorConnectionState.Connected
            if (connected) settings.save(SelectedHeartRateSensor(device.id, device.name))
            onComplete(connected)
        }
    }

    fun forget() {
        settings.save(null)
        scope.launch { sensor.disconnect() }
    }

    fun connectSaved(
        onSample: (HeartRateSample) -> Unit,
        onStatus: (HeartRateSensorRecordingStatus) -> Unit,
    ) {
        val selected = settings.load()
        if (selected == null) {
            onStatus(HeartRateSensorRecordingStatus.NOT_CONFIGURED)
            return
        }
        sampleJob?.cancel()
        sampleJob = scope.launch { sensor.samples.collect(onSample) }
        connectionJob?.cancel()
        connectionJob = scope.launch {
            HeartRateSensorReconnectManager(sensor).run(selected.id, onStatus)
        }
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        sampleJob?.cancel()
        sampleJob = null
        scope.launch { sensor.disconnect() }
    }

    fun close() {
        scope.cancel()
    }
}
