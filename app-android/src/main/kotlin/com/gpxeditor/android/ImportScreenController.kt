package com.gpxeditor.android

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gpxeditor.android.data.imported.JsonImportedTrackStore
import com.gpxeditor.android.recording.FileRecordingJournal
import com.gpxeditor.shared.data.activity.ActivityDocumentJson
import com.gpxeditor.shared.data.activity.ActivityGpxMapper
import com.gpxeditor.shared.data.gpx.GpxSerializer
import com.gpxeditor.shared.domain.activity.ActivityDocument
import com.gpxeditor.shared.domain.imported.ImportedTrack
import com.gpxeditor.shared.domain.imported.ports.GpxTrackFileStorage
import com.gpxeditor.shared.feature.deletetrack.DeleteImportedTrackUseCase
import com.gpxeditor.shared.feature.edittrack.DeleteGpxTrackPointRequest
import com.gpxeditor.shared.feature.edittrack.DeleteGpxTrackPointUseCase
import com.gpxeditor.shared.feature.edittrack.MoveGpxTrackPointRequest
import com.gpxeditor.shared.feature.edittrack.MoveGpxTrackPointUseCase
import com.gpxeditor.shared.feature.edittrack.TrimGpxTrackRequest
import com.gpxeditor.shared.feature.edittrack.TrimGpxTrackUseCase
import com.gpxeditor.shared.feature.exportfit.ExportFitTrackUseCase
import com.gpxeditor.shared.feature.importfit.ImportFitTrackRequest
import com.gpxeditor.shared.feature.importfit.ImportFitTrackResult
import com.gpxeditor.shared.feature.importfit.ImportFitTrackUseCase
import com.gpxeditor.shared.feature.importgpx.ImportGpxTrackRequest
import com.gpxeditor.shared.feature.importgpx.ImportGpxTrackResult
import com.gpxeditor.shared.feature.importgpx.ImportGpxTrackUseCase
import com.gpxeditor.shared.feature.recordtrack.RecordingJournal
import com.gpxeditor.shared.feature.recordtrack.SaveRecordedTrackRequest
import com.gpxeditor.shared.feature.recordtrack.SaveRecordedTrackResult
import com.gpxeditor.shared.feature.recordtrack.SaveRecordedTrackUseCase
import com.gpxeditor.shared.feature.renametrack.RenameTrackRequest
import com.gpxeditor.shared.feature.renametrack.RenameTrackResult
import com.gpxeditor.shared.feature.renametrack.RenameTrackUseCase
import com.gpxeditor.shared.feature.trackdetail.TrackDetail
import com.gpxeditor.shared.feature.trackdetail.TrackDetailResult
import com.gpxeditor.shared.feature.trackdetail.TrackDetailUseCase
import java.util.concurrent.CountDownLatch
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

class ImportScreenController(
    private val importGpxTrackUseCase: ImportGpxTrackUseCase,
    private val importFitTrackUseCase: ImportFitTrackUseCase,
    private val exportFitTrackUseCase: ExportFitTrackUseCase,
    private val trackDetailUseCase: TrackDetailUseCase,
    private val renameTrackUseCase: RenameTrackUseCase,
    private val trimGpxTrackUseCase: TrimGpxTrackUseCase,
    private val deleteImportedTrackUseCase: DeleteImportedTrackUseCase,
    private val deleteGpxTrackPointUseCase: DeleteGpxTrackPointUseCase,
    private val moveGpxTrackPointUseCase: MoveGpxTrackPointUseCase,
    private val saveRecordedTrackUseCase: SaveRecordedTrackUseCase,
    private val recordingJournal: FileRecordingJournal,
    private val isRecordingActive: () -> Boolean,
    private val fileStorage: GpxTrackFileStorage,
    private val importedTrackStore: JsonImportedTrackStore,
    private val readTextFrom: (Uri) -> String,
    private val readBytesFrom: (Uri) -> ByteArray,
    private val displayNameFor: (Uri) -> String?,
    private val exportGpx: (displayName: String, content: String) -> Unit,
    private val exportFit: (displayName: String, content: ByteArray) -> Unit,
    private val runOnUiThread: (() -> Unit) -> Unit,
) {
    var state by mutableStateOf(ImportScreenState())
        private set

    fun importTrackFrom(uri: Uri) {
        state = state.copy(
            isImporting = true,
            selectedTrackDetail = null,
            trimTrackDetail = null,
            editTrackDetail = null,
            statusMessage = null,
            errorMessage = null,
        )

        Thread {
            runCatching {
                val fileName = displayNameFor(uri) ?: "track"
                val outcome = if (fileName.endsWith(".fit", ignoreCase = true)) {
                    importFit(fileName, readBytesFrom(uri))
                } else {
                    importGpx(fileName, readTextFrom(uri))
                }
                val history = runSuspendBlocking { importedTrackStore.getAll() }

                runOnUiThread {
                    state = when (outcome) {
                        is ImportOutcome.Failure -> state.copy(
                            tracks = history,
                            isImporting = false,
                            statusMessage = null,
                            errorMessage = outcome.message,
                        )

                        is ImportOutcome.Success -> state.copy(
                            tracks = history,
                            isImporting = false,
                            statusMessage = "Imported ${outcome.displayName}",
                            errorMessage = null,
                        )
                    }
                }
            }.onFailure { throwable ->
                runOnUiThread {
                    state = state.copy(
                        isImporting = false,
                        statusMessage = null,
                        errorMessage = throwable.message ?: "Failed to import track file",
                    )
                }
            }
        }.start()
    }

    private fun importGpx(fileName: String, content: String): ImportOutcome {
        val result = runSuspendBlocking {
            importGpxTrackUseCase(
                ImportGpxTrackRequest(
                    originalFileName = fileName,
                    content = content,
                ),
            )
        }
        return when (result) {
            is ImportGpxTrackResult.Failure -> ImportOutcome.Failure(result.error.message)
            is ImportGpxTrackResult.Success -> ImportOutcome.Success(result.importedTrack.displayName)
        }
    }

    private fun importFit(fileName: String, content: ByteArray): ImportOutcome {
        val result = runSuspendBlocking {
            importFitTrackUseCase(
                ImportFitTrackRequest(
                    originalFileName = fileName,
                    content = content,
                ),
            )
        }
        return when (result) {
            is ImportFitTrackResult.Failure -> ImportOutcome.Failure(result.error.message)
            is ImportFitTrackResult.Success -> ImportOutcome.Success(result.importedTrack.displayName)
        }
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
            trimTrackDetail = null,
            editTrackDetail = null,
            isLoadingTrackDetail = false,
        )
    }

    fun renameTrack(newDisplayName: String) {
        val detail = state.selectedTrackDetail ?: return
        state = state.copy(
            isLoadingTrackDetail = true,
            statusMessage = null,
            errorMessage = null,
        )

        Thread {
            runCatching {
                val result = runSuspendBlocking {
                    renameTrackUseCase(
                        RenameTrackRequest(
                            track = detail.importedTrack,
                            newDisplayName = newDisplayName,
                        ),
                    )
                }
                val history = runSuspendBlocking { importedTrackStore.getAll() }

                result to history
            }.onSuccess { (result, history) ->
                runOnUiThread {
                    state = when (result) {
                        is RenameTrackResult.Failure -> state.copy(
                            isLoadingTrackDetail = false,
                            statusMessage = null,
                            errorMessage = result.message,
                        )

                        is RenameTrackResult.Success -> state.copy(
                            tracks = history,
                            isLoadingTrackDetail = false,
                            selectedTrackDetail = detail.copy(importedTrack = result.importedTrack),
                            statusMessage = "Renamed to ${result.importedTrack.displayName}",
                            errorMessage = null,
                        )
                    }
                }
            }.onFailure { throwable ->
                runOnUiThread {
                    state = state.copy(
                        isLoadingTrackDetail = false,
                        statusMessage = null,
                        errorMessage = throwable.message ?: "Failed to rename track",
                    )
                }
            }
        }.start()
    }

    fun startTrimmingTrack() {
        val detail = state.selectedTrackDetail ?: return
        state = state.copy(
            trimTrackDetail = detail,
            statusMessage = null,
            errorMessage = null,
        )
    }

    fun closeTrimTrack() {
        state = state.copy(trimTrackDetail = null)
    }

    fun startEditingTrack() {
        val detail = state.selectedTrackDetail ?: return
        state = state.copy(
            editTrackDetail = detail,
            statusMessage = null,
            errorMessage = null,
        )
    }

    fun closeEditTrack() {
        state = state.copy(editTrackDetail = null)
    }

    fun exportTrackAsGpx() {
        val detail = state.selectedTrackDetail ?: return
        runExport(detail) {
            exportGpx(
                detail.importedTrack.displayName,
                GpxSerializer.serialize(ActivityGpxMapper.toGpxDocument(detail.document)),
            )
        }
    }

    fun exportTrackAsFit() {
        val detail = state.selectedTrackDetail ?: return
        runExport(detail) {
            val bytes = runSuspendBlocking {
                exportFitTrackUseCase.encode(detail.importedTrack, detail.document)
            }
            exportFit(detail.importedTrack.displayName, bytes)
        }
    }

    private fun runExport(detail: TrackDetail, block: () -> Unit) {
        runCatching(block)
            .onSuccess {
                state = state.copy(
                    statusMessage = "Exported ${detail.importedTrack.displayName}",
                    errorMessage = null,
                )
            }
            .onFailure { throwable ->
                state = state.copy(
                    statusMessage = null,
                    errorMessage = throwable.message ?: "Failed to export track file",
                )
            }
    }

    fun saveTrimmedTrack(document: ActivityDocument) {
        val detail = state.trimTrackDetail ?: return
        saveEditedDocument(
            detail = detail,
            document = document,
            closeTrim = true,
            closeEdit = false,
        )
    }

    fun saveEditedTrack(document: ActivityDocument) {
        val detail = state.editTrackDetail ?: return
        saveEditedDocument(
            detail = detail,
            document = document,
            closeTrim = false,
            closeEdit = true,
        )
    }

    fun deleteTrackPoint(
        document: ActivityDocument,
        pointIndex: Int,
    ) = deleteGpxTrackPointUseCase(
        DeleteGpxTrackPointRequest(
            document = document,
            pointIndex = pointIndex,
        ),
    )

    fun moveTrackPoint(
        document: ActivityDocument,
        pointIndex: Int,
        latitude: Double,
        longitude: Double,
    ) = moveGpxTrackPointUseCase(
        MoveGpxTrackPointRequest(
            document = document,
            pointIndex = pointIndex,
            latitude = latitude,
            longitude = longitude,
        ),
    )

    private fun saveEditedDocument(
        detail: TrackDetail,
        document: ActivityDocument,
        closeTrim: Boolean,
        closeEdit: Boolean,
    ) {
        state = state.copy(
            isLoadingTrackDetail = true,
            statusMessage = null,
            errorMessage = null,
        )

        Thread {
            runCatching {
                val updatedTrack = runSuspendBlocking {
                    overwriteTrack(
                        track = detail.importedTrack,
                        document = document,
                    )
                }
                val updatedDetail = detailFor(updatedTrack)
                val history = runSuspendBlocking { importedTrackStore.getAll() }

                updatedTrack to (updatedDetail to history)
            }.onSuccess { (updatedTrack, detailAndHistory) ->
                val (updatedDetail, history) = detailAndHistory
                runOnUiThread {
                    state = state.copy(
                        tracks = history,
                        isLoadingTrackDetail = false,
                        selectedTrackDetail = updatedDetail,
                        trimTrackDetail = if (closeTrim) null else state.trimTrackDetail,
                        editTrackDetail = if (closeEdit) null else state.editTrackDetail,
                        statusMessage = "Saved ${updatedTrack.displayName}",
                        errorMessage = null,
                    )
                }
            }.onFailure { throwable ->
                runOnUiThread {
                    state = state.copy(
                        isLoadingTrackDetail = false,
                        statusMessage = null,
                        errorMessage = throwable.message ?: "Failed to save trimmed track",
                    )
                }
            }
        }.start()
    }

    fun deleteTrack(track: ImportedTrack) {
        state = state.copy(
            statusMessage = null,
            errorMessage = null,
        )

        Thread {
            runCatching {
                runSuspendBlocking { deleteImportedTrackUseCase(track) }
                runSuspendBlocking { importedTrackStore.getAll() }
            }.onSuccess { history ->
                runOnUiThread {
                    state = state.copy(
                        tracks = history,
                        statusMessage = "Deleted ${track.displayName}",
                        errorMessage = null,
                    )
                }
            }.onFailure { throwable ->
                runOnUiThread {
                    state = state.copy(
                        statusMessage = null,
                        errorMessage = throwable.message ?: "Failed to delete track",
                    )
                }
            }
        }.start()
    }

    fun checkRecordingRecovery() {
        Thread {
            runCatching {
                if (!recordingJournal.exists() || isRecordingActive()) {
                    null
                } else {
                    val recovered = RecordingJournal.recover(recordingJournal.read())
                    if (recovered == null || recovered.document.pointCount == 0) {
                        recordingJournal.clear()
                        null
                    } else {
                        recovered
                    }
                }
            }.onSuccess { recovered ->
                if (recovered != null) {
                    runOnUiThread {
                        state = state.copy(recoveredRecording = recovered)
                    }
                }
            }
        }.start()
    }

    fun restoreRecoveredRecording() {
        val recovered = state.recoveredRecording ?: return
        state = state.copy(
            recoveredRecording = null,
            statusMessage = null,
            errorMessage = null,
        )

        Thread {
            runCatching {
                val result = runSuspendBlocking {
                    saveRecordedTrackUseCase(SaveRecordedTrackRequest(recovered.document))
                }
                if (!isRecordingActive()) {
                    recordingJournal.clear()
                }
                val history = runSuspendBlocking { importedTrackStore.getAll() }
                result to history
            }.onSuccess { (result, history) ->
                runOnUiThread {
                    state = when (result) {
                        is SaveRecordedTrackResult.Success -> state.copy(
                            tracks = history,
                            statusMessage = "Restored ${result.importedTrack.displayName}",
                            errorMessage = null,
                        )

                        is SaveRecordedTrackResult.Failure -> state.copy(
                            tracks = history,
                            statusMessage = null,
                            errorMessage = result.message,
                        )
                    }
                }
            }.onFailure { throwable ->
                runOnUiThread {
                    state = state.copy(
                        statusMessage = null,
                        errorMessage = throwable.message ?: "Failed to restore recording",
                    )
                }
            }
        }.start()
    }

    fun discardRecoveredRecording() {
        state = state.copy(recoveredRecording = null)

        Thread {
            runCatching {
                if (!isRecordingActive()) {
                    recordingJournal.clear()
                }
            }
        }.start()
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

    fun trimTrack(
        document: ActivityDocument,
        startPointIndex: Int,
        endPointIndexInclusive: Int,
    ) = trimGpxTrackUseCase(
        TrimGpxTrackRequest(
            document = document,
            startPointIndex = startPointIndex,
            endPointIndexInclusive = endPointIndexInclusive,
        ),
    )

    private suspend fun overwriteTrack(
        track: ImportedTrack,
        document: ActivityDocument,
    ): ImportedTrack {
        val previousContent = fileStorage.read(track.storageKey)
        val updatedTrack = track.copy(
            trackCount = document.tracks.size,
            pointCount = document.pointCount,
        )

        fileStorage.save(track.storageKey, ActivityDocumentJson.serialize(document))
        try {
            importedTrackStore.add(updatedTrack)
        } catch (throwable: Throwable) {
            fileStorage.save(track.storageKey, previousContent)
            throw throwable
        }

        return updatedTrack
    }

    private fun detailFor(track: ImportedTrack): TrackDetail {
        return when (val result = runSuspendBlocking { trackDetailUseCase(track) }) {
            is TrackDetailResult.Failure -> error(result.error.message)
            is TrackDetailResult.Success -> result.detail
        }
    }
}

private sealed interface ImportOutcome {
    data class Success(val displayName: String) : ImportOutcome
    data class Failure(val message: String) : ImportOutcome
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
