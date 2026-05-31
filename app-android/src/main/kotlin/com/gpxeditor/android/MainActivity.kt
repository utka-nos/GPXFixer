package com.gpxeditor.android

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gpxeditor.android.data.imported.AndroidGpxTrackFileStorage
import com.gpxeditor.android.data.imported.AndroidImportClock
import com.gpxeditor.android.data.imported.AndroidImportIdGenerator
import com.gpxeditor.android.data.imported.JsonImportedTrackStore
import com.gpxeditor.shared.domain.imported.ImportedTrack
import com.gpxeditor.shared.feature.importgpx.ImportGpxTrackRequest
import com.gpxeditor.shared.feature.importgpx.ImportGpxTrackResult
import com.gpxeditor.shared.feature.importgpx.ImportGpxTrackUseCase
import com.gpxeditor.shared.feature.trackdetail.GpxCoordinate
import com.gpxeditor.shared.feature.trackdetail.TrackDetail
import com.gpxeditor.shared.feature.trackdetail.TrackDetailResult
import com.gpxeditor.shared.feature.trackdetail.TrackDetailUseCase
import java.util.concurrent.CountDownLatch
import java.util.Locale
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

class MainActivity : ComponentActivity() {
    private lateinit var openGpxLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var importGpxTrackUseCase: ImportGpxTrackUseCase
    private lateinit var trackDetailUseCase: TrackDetailUseCase
    private lateinit var importedTrackStore: JsonImportedTrackStore

    private var screenState by mutableStateOf(ImportScreenState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val fileStorage = AndroidGpxTrackFileStorage(applicationContext)
        importedTrackStore = JsonImportedTrackStore(applicationContext)
        importGpxTrackUseCase = ImportGpxTrackUseCase(
            fileStorage = fileStorage,
            trackStore = importedTrackStore,
            idGenerator = AndroidImportIdGenerator(),
            clock = AndroidImportClock(),
        )
        trackDetailUseCase = TrackDetailUseCase(fileStorage)

        openGpxLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(::importGpxFrom)
        }

        setContent {
            ImportScreen(
                state = screenState,
                onImportClick = {
                    openGpxLauncher.launch(
                        arrayOf(
                            "application/gpx+xml",
                            "application/xml",
                            "text/xml",
                            "application/octet-stream",
                            "*/*",
                        ),
                    )
                },
                onTrackClick = ::openTrackDetail,
                onBackFromDetail = {
                    screenState = screenState.copy(
                        selectedTrackDetail = null,
                        isLoadingTrackDetail = false,
                    )
                },
            )
        }

        loadImportedTracks()
        handleViewIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleViewIntent(intent)
    }

    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return

        val uri = intent.data ?: return
        importGpxFrom(uri)
    }

    private fun importGpxFrom(uri: Uri) {
        screenState = screenState.copy(
            isImporting = true,
            selectedTrackDetail = null,
            statusMessage = null,
            errorMessage = null,
        )

        Thread {
            runCatching {
                val fileName = displayNameFor(uri) ?: "track.gpx"
                val content = readTextFrom(uri)
                val result = runSuspendBlocking {
                    importGpxTrackUseCase(
                        ImportGpxTrackRequest(
                            originalFileName = fileName,
                            content = content,
                        ),
                    )
                }
                val history = runSuspendBlocking { importedTrackStore.getAll() }

                runOnUiThread {
                    screenState = when (result) {
                        is ImportGpxTrackResult.Failure -> screenState.copy(
                            tracks = history,
                            isImporting = false,
                            statusMessage = null,
                            errorMessage = result.error.message,
                        )

                        is ImportGpxTrackResult.Success -> screenState.copy(
                            tracks = history,
                            isImporting = false,
                            statusMessage = "Imported ${result.importedTrack.displayName}",
                            errorMessage = null,
                        )
                    }
                }
            }.onFailure { throwable ->
                runOnUiThread {
                    screenState = screenState.copy(
                        isImporting = false,
                        statusMessage = null,
                        errorMessage = throwable.message ?: "Failed to import GPX file",
                    )
                }
            }
        }.start()
    }

    private fun openTrackDetail(track: ImportedTrack) {
        screenState = screenState.copy(
            isLoadingTrackDetail = true,
            statusMessage = null,
            errorMessage = null,
        )

        Thread {
            runCatching {
                runSuspendBlocking { trackDetailUseCase(track) }
            }.onSuccess { result ->
                runOnUiThread {
                    screenState = when (result) {
                        is TrackDetailResult.Failure -> screenState.copy(
                            isLoadingTrackDetail = false,
                            selectedTrackDetail = null,
                            errorMessage = result.error.message,
                        )

                        is TrackDetailResult.Success -> screenState.copy(
                            isLoadingTrackDetail = false,
                            selectedTrackDetail = result.detail,
                            errorMessage = null,
                        )
                    }
                }
            }.onFailure { throwable ->
                runOnUiThread {
                    screenState = screenState.copy(
                        isLoadingTrackDetail = false,
                        selectedTrackDetail = null,
                        errorMessage = throwable.message ?: "Failed to open imported track",
                    )
                }
            }
        }.start()
    }

    private fun loadImportedTracks() {
        Thread {
            runCatching {
                runSuspendBlocking { importedTrackStore.getAll() }
            }.onSuccess { tracks ->
                runOnUiThread {
                    screenState = screenState.copy(tracks = tracks)
                }
            }.onFailure { throwable ->
                runOnUiThread {
                    screenState = screenState.copy(
                        errorMessage = throwable.message ?: "Failed to load imported tracks",
                    )
                }
            }
        }.start()
    }

    private fun readTextFrom(uri: Uri): String {
        return contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Could not open selected GPX file")
    }

    private fun displayNameFor(uri: Uri): String? {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) return cursor.getString(index)
                }
            }
        }

        return uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
    }
}

private data class ImportScreenState(
    val tracks: List<ImportedTrack> = emptyList(),
    val isImporting: Boolean = false,
    val isLoadingTrackDetail: Boolean = false,
    val selectedTrackDetail: TrackDetail? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
)

@Composable
private fun ImportScreen(
    state: ImportScreenState,
    onImportClick: () -> Unit,
    onTrackClick: (ImportedTrack) -> Unit,
    onBackFromDetail: () -> Unit,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            state.selectedTrackDetail?.let { detail ->
                TrackDetailScreen(
                    detail = detail,
                    onBackClick = onBackFromDetail,
                )
                return@Surface
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "GPXFixer",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "Imported tracks",
                    style = MaterialTheme.typography.titleMedium,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        enabled = !state.isImporting,
                        onClick = onImportClick,
                    ) {
                        Text(if (state.isImporting) "Importing" else "Import GPX")
                    }
                    if (state.isImporting) {
                        CircularProgressIndicator()
                    }
                    if (state.isLoadingTrackDetail) {
                        CircularProgressIndicator()
                    }
                }

                state.statusMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                state.errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (state.tracks.isEmpty()) {
                    EmptyHistory()
                } else {
                    state.tracks.forEach { track ->
                        ImportedTrackRow(
                            track = track,
                            onClick = { onTrackClick(track) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHistory() {
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "No GPX tracks imported yet.",
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun ImportedTrackRow(
    track: ImportedTrack,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = track.displayName,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = track.originalFileName,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "${track.trackCount} tracks / ${track.pointCount} points",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Imported at ${track.importedAt}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TrackDetailScreen(
    detail: TrackDetail,
    onBackClick: () -> Unit,
) {
    val summary = detail.summary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Button(onClick = onBackClick) {
            Text("Back")
        }

        Text(
            text = detail.importedTrack.displayName,
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = detail.importedTrack.originalFileName,
            style = MaterialTheme.typography.bodyMedium,
        )

        DetailSection(title = "Summary") {
            DetailRow("Imported", detail.importedTrack.importedAt)
            DetailRow("Tracks", summary.trackCount.toString())
            DetailRow("Segments", summary.segmentCount.toString())
            DetailRow("Points", summary.pointCount.toString())
            DetailRow("Distance", formatDistance(summary.distanceMeters))
            DetailRow("Elevation gain", formatElevation(summary.totalAscentMeters))
            DetailRow("Elevation loss", formatElevation(summary.totalDescentMeters))
            DetailRow("Elevation range", formatElevationRange(summary.minElevationMeters, summary.maxElevationMeters))
            DetailRow("Time range", formatTimeRange(summary.startTime, summary.endTime))
            DetailRow("Start", formatCoordinate(summary.startCoordinate))
            DetailRow("Finish", formatCoordinate(summary.endCoordinate))
        }

        if (detail.warnings.isNotEmpty()) {
            DetailSection(title = "Warnings") {
                detail.warnings.forEach { warning ->
                    Text(
                        text = warning,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        DetailSection(title = "Segments") {
            if (detail.segments.isEmpty()) {
                Text(
                    text = "No segments found.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                detail.segments.forEach { segment ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Segment ${segment.index}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "${segment.pointCount} points / ${formatDistance(segment.distanceMeters)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = formatTimeRange(segment.startTime, segment.endTime),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            content()
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun formatDistance(meters: Double): String {
    return if (meters >= 1_000.0) {
        String.format(Locale.US, "%.2f km", meters / 1_000.0)
    } else {
        String.format(Locale.US, "%.0f m", meters)
    }
}

private fun formatElevation(meters: Double?): String {
    return meters?.let { String.format(Locale.US, "%.0f m", it) } ?: "No data"
}

private fun formatElevationRange(
    minMeters: Double?,
    maxMeters: Double?,
): String {
    if (minMeters == null || maxMeters == null) return "No data"
    return "${formatElevation(minMeters)} to ${formatElevation(maxMeters)}"
}

private fun formatTimeRange(
    startTime: String?,
    endTime: String?,
): String {
    if (startTime == null && endTime == null) return "No data"
    if (startTime == endTime || endTime == null) return startTime ?: "No data"
    if (startTime == null) return endTime
    return "$startTime to $endTime"
}

private fun formatCoordinate(coordinate: GpxCoordinate?): String {
    return coordinate?.let {
        String.format(Locale.US, "%.5f, %.5f", it.latitude, it.longitude)
    } ?: "No data"
}

private fun <T> runSuspendBlocking(block: suspend () -> T): T {
    val latch = CountDownLatch(1)
    var value: T? = null
    var failure: Throwable? = null

    block.startCoroutine(
        object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<T>) {
                result
                    .onSuccess { value = it }
                    .onFailure { failure = it }
                latch.countDown()
            }
        },
    )

    latch.await()
    failure?.let { throw it }
    @Suppress("UNCHECKED_CAST")
    return value as T
}
