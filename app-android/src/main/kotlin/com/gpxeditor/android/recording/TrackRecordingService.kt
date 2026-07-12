package com.gpxeditor.android.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.gpxeditor.android.R
import com.gpxeditor.android.data.imported.AndroidGpxTrackFileStorage
import com.gpxeditor.android.data.imported.AndroidImportClock
import com.gpxeditor.android.data.imported.AndroidImportIdGenerator
import com.gpxeditor.android.data.imported.JsonImportedTrackStore
import com.gpxeditor.android.data.location.AndroidLocationSource
import com.gpxeditor.android.screens.formatDistance
import com.gpxeditor.android.screens.formatDuration
import com.gpxeditor.shared.feature.recordtrack.LiveMetricSeries
import com.gpxeditor.shared.feature.recordtrack.LocationSource
import com.gpxeditor.shared.feature.recordtrack.RecordedActivity
import com.gpxeditor.shared.feature.recordtrack.RecordingJournal
import com.gpxeditor.shared.feature.recordtrack.RecordingState
import com.gpxeditor.shared.feature.recordtrack.RecordingStats
import com.gpxeditor.shared.feature.recordtrack.RoutePoint
import com.gpxeditor.shared.feature.recordtrack.SaveRecordedTrackRequest
import com.gpxeditor.shared.feature.recordtrack.SaveRecordedTrackResult
import com.gpxeditor.shared.feature.recordtrack.SaveRecordedTrackUseCase
import com.gpxeditor.shared.feature.recordtrack.TrackRecorder
import com.gpxeditor.shared.feature.trackdetail.TrackChartSample
import com.gpxeditor.shared.data.ble.HeartRateSensorReconnectManager
import com.gpxeditor.shared.data.ble.HeartRateSensorRecordingStatus
import com.gpxeditor.shared.data.ble.HeartRateSensorSettingsStore
import com.gpxeditor.shared.data.ble.KableHeartRateSensor
import com.gpxeditor.shared.data.ble.KablePowerSensor
import com.gpxeditor.shared.data.ble.PowerSensorSettingsStore
import com.gpxeditor.shared.data.ble.PowerSensorReconnectManager
import com.gpxeditor.shared.data.ble.PowerSensorRecordingStatus
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service that owns the [TrackRecorder] and its [LocationSource]
 * so a recording survives the screen turning off and the app going to the
 * background. Controlled with intent actions; publishes live stats and the
 * finished recording through [stats] and [lastRecording].
 */
class TrackRecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recorder: TrackRecorder? = null
    private var locationJob: Job? = null
    private var tickerJob: Job? = null
    private var powerSensor: KablePowerSensor? = null
    private var powerSensorJob: Job? = null
    private var powerSampleJob: Job? = null
    private var heartRateSensor: KableHeartRateSensor? = null
    private var heartRateSensorJob: Job? = null
    private var heartRateSampleJob: Job? = null
    private val livePowerSeries = LiveMetricSeries()

    // Single-threaded so journal lines land in order; also runs the final save.
    private val journalExecutor = Executors.newSingleThreadExecutor()
    private val journalDispatcher = journalExecutor.asCoroutineDispatcher()
    private val journal by lazy { FileRecordingJournal(applicationContext) }
    private val saveRecordedTrackUseCase by lazy {
        SaveRecordedTrackUseCase(
            fileStorage = AndroidGpxTrackFileStorage(applicationContext),
            trackStore = JsonImportedTrackStore(applicationContext),
            idGenerator = AndroidImportIdGenerator(),
            clock = AndroidImportClock(),
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(RecordingPermissions.TAG, "Service onStartCommand action=${intent?.action}")
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_STOP -> stopRecording()
        }
        // Without a journal (#32) a killed process cannot restore the recorder,
        // so a sticky restart would only show a stale notification.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        powerSensorJob?.cancel()
        powerSensorJob = null
        powerSampleJob?.cancel()
        powerSampleJob = null
        heartRateSensorJob?.cancel()
        heartRateSensorJob = null
        heartRateSampleJob?.cancel()
        heartRateSampleJob = null
        scope.cancel()
        journalExecutor.shutdown()
        if (recorder != null) {
            recorder = null
            _stats.value = null
            _routeSegments.value = emptyList()
            _powerChartSamples.value = emptyList()
        }
        super.onDestroy()
    }

    private fun startRecording() {
        if (recorder != null) {
            Log.i(RecordingPermissions.TAG, "startRecording ignored: recording already active")
            return
        }
        if (!RecordingPermissions.allGranted(this)) {
            Log.w(
                RecordingPermissions.TAG,
                "startRecording aborted, missing: ${RecordingPermissions.missing(this)}",
            )
            RecordingPermissions.logStatus(this, "service start")
            stopSelf()
            return
        }

        _lastSaveMessage.value = null
        _routeSegments.value = emptyList()
        livePowerSeries.clear()
        _powerChartSamples.value = emptyList()
        val startedAt = now()
        val startedRecorder = TrackRecorder()
        recorder = startedRecorder
        startedRecorder.start(atEpochMillis = startedAt)
        scope.launch(journalDispatcher) {
            journal.clear()
            journal.append(RecordingJournal.startLine(startedAt))
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(startedRecorder.stats(now())),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                0
            },
        )

        Log.i(RecordingPermissions.TAG, "Recording started in foreground service")
        startLocationUpdates()
        startPowerSensor()
        startHeartRateSensor()
        tickerJob = scope.launch {
            while (isActive) {
                publishStats()
                delay(STATS_UPDATE_INTERVAL_MILLIS)
            }
        }
    }

    private fun pauseRecording() {
        val recorder = recorder ?: return
        if (recorder.state != RecordingState.RECORDING) return

        val pausedAt = now()
        recorder.pause(atEpochMillis = pausedAt)
        appendToJournal { RecordingJournal.pauseLine(pausedAt) }
        stopLocationUpdates()
        publishStats()
    }

    private fun resumeRecording() {
        val recorder = recorder ?: return
        if (recorder.state != RecordingState.PAUSED) return

        val resumedAt = now()
        recorder.resume(atEpochMillis = resumedAt)
        appendToJournal { RecordingJournal.resumeLine(resumedAt) }
        startLocationUpdates()
        publishStats()
    }

    private fun stopRecording() {
        val recorder = recorder ?: return
        this.recorder = null

        val recorded = recorder.stop(atEpochMillis = now())
        _stats.value = null
        _routeSegments.value = emptyList()
        livePowerSeries.clear()
        _powerChartSamples.value = emptyList()
        stopLocationUpdates()
        stopPowerSensor()
        stopHeartRateSensor()
        tickerJob?.cancel()
        tickerJob = null

        scope.launch {
            _lastSaveMessage.value = withContext(journalDispatcher) { saveRecording(recorded) }
            ServiceCompat.stopForeground(this@TrackRecordingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /** Saves the finished recording; the journal is kept on failure so recovery can retry. */
    private suspend fun saveRecording(recorded: RecordedActivity): String {
        return try {
            when (val result = saveRecordedTrackUseCase(SaveRecordedTrackRequest(recorded.document))) {
                is SaveRecordedTrackResult.Success -> {
                    journal.clear()
                    "Saved ${result.importedTrack.displayName}"
                }

                is SaveRecordedTrackResult.Failure -> {
                    journal.clear()
                    result.message
                }
            }
        } catch (throwable: Throwable) {
            "Failed to save recording: ${throwable.message}"
        }
    }

    private fun appendToJournal(line: () -> String) {
        scope.launch(journalDispatcher) {
            journal.append(line())
        }
    }

    private fun startLocationUpdates() {
        if (locationJob != null) return
        locationJob = scope.launch {
            AndroidLocationSource(this@TrackRecordingService).locations().collect { sample ->
                if (recorder?.onLocation(sample) == true) {
                    appendToJournal { RecordingJournal.pointLine(sample) }
                    recorder?.let {
                        _routeSegments.value = it.routeSegments(MAX_DISPLAY_POINTS_PER_SEGMENT)
                    }
                }
            }
        }
    }

    private fun stopLocationUpdates() {
        locationJob?.cancel()
        locationJob = null
    }

    private fun startPowerSensor() {
        if (powerSensorJob != null) return
        if (!BleSensorPermissions.allGranted(this)) {
            _powerSensorStatus.value = PowerSensorRecordingStatus.NOT_CONNECTED
            return
        }
        val selected = PowerSensorSettingsStore(FilePowerSensorSettingsStorage(this)).load()
        if (selected == null) {
            _powerSensorStatus.value = PowerSensorRecordingStatus.NOT_CONFIGURED
            return
        }
        val sensor = KablePowerSensor(scope)
        powerSensor = sensor
        powerSampleJob = scope.launch {
            sensor.samples.collect { sample ->
                val sampledAt = now()
                recorder?.onPower(sample, sampledAt)
                recorder?.let { activeRecorder ->
                    if (activeRecorder.state == RecordingState.RECORDING) {
                        livePowerSeries.add(
                            elapsedSeconds = activeRecorder.stats(sampledAt).elapsedMillis / 1_000,
                            value = sample.watts,
                        )
                        _powerChartSamples.value = livePowerSeries.snapshot()
                    }
                }
                publishStats()
            }
        }
        powerSensorJob = scope.launch {
            PowerSensorReconnectManager(sensor).run(selected.id) { status ->
                _powerSensorStatus.value = status
            }
        }
    }

    private fun stopPowerSensor() {
        powerSensorJob?.cancel()
        powerSensorJob = null
        powerSampleJob?.cancel()
        powerSampleJob = null
        val sensor = powerSensor
        powerSensor = null
        _powerSensorStatus.value = PowerSensorRecordingStatus.NOT_CONNECTED
        if (sensor != null) scope.launch { sensor.disconnect() }
    }

    private fun startHeartRateSensor() {
        if (heartRateSensorJob != null) return
        if (!BleSensorPermissions.allGranted(this)) {
            _heartRateSensorStatus.value = HeartRateSensorRecordingStatus.NOT_CONNECTED
            return
        }
        val selected = HeartRateSensorSettingsStore(FileHeartRateSensorSettingsStorage(this)).load()
        if (selected == null) {
            _heartRateSensorStatus.value = HeartRateSensorRecordingStatus.NOT_CONFIGURED
            return
        }
        val sensor = KableHeartRateSensor(scope)
        heartRateSensor = sensor
        heartRateSampleJob = scope.launch {
            sensor.samples.collect { sample ->
                recorder?.onHeartRate(sample, now())
                publishStats()
            }
        }
        heartRateSensorJob = scope.launch {
            HeartRateSensorReconnectManager(sensor).run(selected.id) { status ->
                _heartRateSensorStatus.value = status
            }
        }
    }

    private fun stopHeartRateSensor() {
        heartRateSensorJob?.cancel()
        heartRateSensorJob = null
        heartRateSampleJob?.cancel()
        heartRateSampleJob = null
        val sensor = heartRateSensor
        heartRateSensor = null
        _heartRateSensorStatus.value = HeartRateSensorRecordingStatus.NOT_CONNECTED
        if (sensor != null) scope.launch { sensor.disconnect() }
    }

    private fun publishStats() {
        val currentStats = recorder?.stats(now()) ?: return
        _stats.value = currentStats
        notificationManager().notify(NOTIFICATION_ID, buildNotification(currentStats))
    }

    private fun buildNotification(stats: RecordingStats): Notification {
        val stateText = if (stats.state == RecordingState.PAUSED) {
            getString(R.string.recording_notification_paused)
        } else {
            getString(R.string.recording_notification_recording)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_recording)
            .setContentTitle(stateText)
            .setContentText(buildNotificationText(stats))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .build()
    }

    private fun buildNotificationText(stats: RecordingStats): String = buildString {
        append(formatDuration(stats.elapsedMillis))
        append(" • ")
        append(formatDistance(stats.distanceMeters))
        stats.currentPowerWatts?.let { append(" • $it W") }
        stats.currentCadenceRpm?.let { append(" • $it rpm") }
        stats.currentHeartRateBpm?.let { append(" • $it bpm") }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.recording_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager {
        return getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    private fun now(): Long = System.currentTimeMillis()

    companion object {
        private val _stats = MutableStateFlow<RecordingStats?>(null)

        /** Live stats of the active recording, or null when nothing is being recorded. */
        val stats: StateFlow<RecordingStats?> = _stats

        private val _routeSegments = MutableStateFlow<List<List<RoutePoint>>>(emptyList())

        /**
         * Route of the active recording as coordinate segments, one per
         * pause/resume span. Updated whenever a location becomes a track point;
         * empty when nothing is being recorded or there is no GPS fix yet.
         */
        val routeSegments: StateFlow<List<List<RoutePoint>>> = _routeSegments

        private val _lastSaveMessage = MutableStateFlow<String?>(null)

        /** Outcome of saving the most recently stopped recording; cleared when a new one starts. */
        val lastSaveMessage: StateFlow<String?> = _lastSaveMessage

        private val _powerChartSamples = MutableStateFlow<List<TrackChartSample>>(emptyList())

        /**
         * Live power-over-active-time series of the active recording, one point
         * per elapsed second; empty when nothing is being recorded or no power
         * sample has arrived yet.
         */
        val powerChartSamples: StateFlow<List<TrackChartSample>> = _powerChartSamples

        private val _powerSensorStatus =
            MutableStateFlow(PowerSensorRecordingStatus.NOT_CONFIGURED)

        val powerSensorStatus: StateFlow<PowerSensorRecordingStatus> = _powerSensorStatus

        private val _heartRateSensorStatus =
            MutableStateFlow(HeartRateSensorRecordingStatus.NOT_CONFIGURED)

        val heartRateSensorStatus: StateFlow<HeartRateSensorRecordingStatus> = _heartRateSensorStatus

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, intent(context, ACTION_START))
        }

        fun pause(context: Context) {
            context.startService(intent(context, ACTION_PAUSE))
        }

        fun resume(context: Context) {
            context.startService(intent(context, ACTION_RESUME))
        }

        fun stop(context: Context) {
            context.startService(intent(context, ACTION_STOP))
        }

        private fun intent(context: Context, action: String): Intent {
            return Intent(context, TrackRecordingService::class.java).setAction(action)
        }

        private const val ACTION_START = "com.gpxeditor.android.recording.START"
        private const val ACTION_PAUSE = "com.gpxeditor.android.recording.PAUSE"
        private const val ACTION_RESUME = "com.gpxeditor.android.recording.RESUME"
        private const val ACTION_STOP = "com.gpxeditor.android.recording.STOP"
        private const val MAX_DISPLAY_POINTS_PER_SEGMENT = 1_000

        private const val CHANNEL_ID = "track_recording"
        private const val NOTIFICATION_ID = 1
        private const val STATS_UPDATE_INTERVAL_MILLIS = 1_000L
    }
}
