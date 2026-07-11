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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.gpxeditor.android.R
import com.gpxeditor.android.data.location.AndroidLocationSource
import com.gpxeditor.android.formatDistance
import com.gpxeditor.android.formatDuration
import com.gpxeditor.shared.feature.recordtrack.LocationSource
import com.gpxeditor.shared.feature.recordtrack.RecordedActivity
import com.gpxeditor.shared.feature.recordtrack.RecordingState
import com.gpxeditor.shared.feature.recordtrack.RecordingStats
import com.gpxeditor.shared.feature.recordtrack.TrackRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
        scope.cancel()
        if (recorder != null) {
            recorder = null
            _stats.value = null
        }
        super.onDestroy()
    }

    private fun startRecording() {
        if (recorder != null) return
        if (!RecordingPermissions.allGranted(this)) {
            stopSelf()
            return
        }

        val startedRecorder = TrackRecorder()
        recorder = startedRecorder
        startedRecorder.start(atEpochMillis = now())

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

        startLocationUpdates()
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

        recorder.pause(atEpochMillis = now())
        stopLocationUpdates()
        publishStats()
    }

    private fun resumeRecording() {
        val recorder = recorder ?: return
        if (recorder.state != RecordingState.PAUSED) return

        recorder.resume(atEpochMillis = now())
        startLocationUpdates()
        publishStats()
    }

    private fun stopRecording() {
        val recorder = recorder ?: return
        this.recorder = null

        _lastRecording.value = recorder.stop(atEpochMillis = now())
        _stats.value = null

        stopLocationUpdates()
        tickerJob?.cancel()
        tickerJob = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startLocationUpdates() {
        if (locationJob != null) return
        locationJob = scope.launch {
            AndroidLocationSource(this@TrackRecordingService).locations().collect { sample ->
                recorder?.onLocation(sample)
            }
        }
    }

    private fun stopLocationUpdates() {
        locationJob?.cancel()
        locationJob = null
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
            .setContentText("${formatDuration(stats.elapsedMillis)} • ${formatDistance(stats.distanceMeters)}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .build()
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

        private val _lastRecording = MutableStateFlow<RecordedActivity?>(null)

        /** Result of the most recently stopped recording; consumed by the save flow. */
        val lastRecording: StateFlow<RecordedActivity?> = _lastRecording

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

        fun consumeLastRecording(): RecordedActivity? {
            return _lastRecording.value.also { _lastRecording.value = null }
        }

        private fun intent(context: Context, action: String): Intent {
            return Intent(context, TrackRecordingService::class.java).setAction(action)
        }

        private const val ACTION_START = "com.gpxeditor.android.recording.START"
        private const val ACTION_PAUSE = "com.gpxeditor.android.recording.PAUSE"
        private const val ACTION_RESUME = "com.gpxeditor.android.recording.RESUME"
        private const val ACTION_STOP = "com.gpxeditor.android.recording.STOP"

        private const val CHANNEL_ID = "track_recording"
        private const val NOTIFICATION_ID = 1
        private const val STATS_UPDATE_INTERVAL_MILLIS = 1_000L
    }
}
