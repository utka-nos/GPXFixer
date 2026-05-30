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
import java.util.concurrent.CountDownLatch
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

class MainActivity : ComponentActivity() {
    private lateinit var openGpxLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var importGpxTrackUseCase: ImportGpxTrackUseCase
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
    val statusMessage: String? = null,
    val errorMessage: String? = null,
)

@Composable
private fun ImportScreen(
    state: ImportScreenState,
    onImportClick: () -> Unit,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
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
                        ImportedTrackRow(track)
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
private fun ImportedTrackRow(track: ImportedTrack) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
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
