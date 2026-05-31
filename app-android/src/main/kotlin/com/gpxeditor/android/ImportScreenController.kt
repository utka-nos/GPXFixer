package com.gpxeditor.android

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gpxeditor.android.data.imported.JsonImportedTrackStore
import com.gpxeditor.shared.domain.imported.ImportedTrack
import com.gpxeditor.shared.feature.importgpx.ImportGpxTrackRequest
import com.gpxeditor.shared.feature.importgpx.ImportGpxTrackResult
import com.gpxeditor.shared.feature.importgpx.ImportGpxTrackUseCase
import com.gpxeditor.shared.feature.trackdetail.TrackDetailResult
import com.gpxeditor.shared.feature.trackdetail.TrackDetailUseCase
import java.util.concurrent.CountDownLatch
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

class ImportScreenController(
    private val importGpxTrackUseCase: ImportGpxTrackUseCase,
    private val trackDetailUseCase: TrackDetailUseCase,
    private val importedTrackStore: JsonImportedTrackStore,
    private val readTextFrom: (Uri) -> String,
    private val displayNameFor: (Uri) -> String?,
    private val runOnUiThread: (() -> Unit) -> Unit,
) {
    var state by mutableStateOf(ImportScreenState())
        private set

    fun importGpxFrom(uri: Uri) {
        state = state.copy(
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
                    state = when (result) {
                        is ImportGpxTrackResult.Failure -> state.copy(
                            tracks = history,
                            isImporting = false,
                            statusMessage = null,
                            errorMessage = result.error.message,
                        )

                        is ImportGpxTrackResult.Success -> state.copy(
                            tracks = history,
                            isImporting = false,
                            statusMessage = "Imported ${result.importedTrack.displayName}",
                            errorMessage = null,
                        )
                    }
                }
            }.onFailure { throwable ->
                runOnUiThread {
                    state = state.copy(
                        isImporting = false,
                        statusMessage = null,
                        errorMessage = throwable.message ?: "Failed to import GPX file",
                    )
                }
            }
        }.start()
    }

    fun openTrackDetail(track: ImportedTrack) {
        state = state.copy(
            isLoadingTrackDetail = true,
            statusMessage = null,
            errorMessage = null,
        )

        Thread {
            runCatching {
                runSuspendBlocking { trackDetailUseCase(track) }
            }.onSuccess { result ->
                runOnUiThread {
                    state = when (result) {
                        is TrackDetailResult.Failure -> state.copy(
                            isLoadingTrackDetail = false,
                            selectedTrackDetail = null,
                            errorMessage = result.error.message,
                        )

                        is TrackDetailResult.Success -> state.copy(
                            isLoadingTrackDetail = false,
                            selectedTrackDetail = result.detail,
                            errorMessage = null,
                        )
                    }
                }
            }.onFailure { throwable ->
                runOnUiThread {
                    state = state.copy(
                        isLoadingTrackDetail = false,
                        selectedTrackDetail = null,
                        errorMessage = throwable.message ?: "Failed to open imported track",
                    )
                }
            }
        }.start()
    }

    fun closeTrackDetail() {
        state = state.copy(
            selectedTrackDetail = null,
            isLoadingTrackDetail = false,
        )
    }

    fun loadImportedTracks() {
        Thread {
            runCatching {
                runSuspendBlocking { importedTrackStore.getAll() }
            }.onSuccess { tracks ->
                runOnUiThread {
                    state = state.copy(tracks = tracks)
                }
            }.onFailure { throwable ->
                runOnUiThread {
                    state = state.copy(
                        errorMessage = throwable.message ?: "Failed to load imported tracks",
                    )
                }
            }
        }.start()
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
