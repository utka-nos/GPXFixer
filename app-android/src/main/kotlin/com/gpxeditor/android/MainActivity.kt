package com.gpxeditor.android

import android.content.ContentResolver
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.FileProvider
import com.gpxeditor.android.data.imported.AndroidGpxTrackFileStorage
import com.gpxeditor.android.data.imported.AndroidImportClock
import com.gpxeditor.android.data.imported.AndroidImportIdGenerator
import com.gpxeditor.android.data.imported.JsonImportedTrackStore
import com.gpxeditor.android.recording.FileHeartRateSensorSettingsStorage
import com.gpxeditor.android.recording.FileRecordingJournal
import com.gpxeditor.android.recording.FilePowerSensorSettingsStorage
import com.gpxeditor.android.recording.HeartRateSensorController
import com.gpxeditor.android.recording.PowerSensorController
import com.gpxeditor.android.recording.TrackRecordingService
import com.gpxeditor.android.screens.ImportScreen
import com.gpxeditor.shared.data.fit.FitActivityDecoder
import com.gpxeditor.shared.data.ble.HeartRateSensorSettingsStore
import com.gpxeditor.shared.data.ble.KableHeartRateSensor
import com.gpxeditor.shared.data.ble.KablePowerSensor
import com.gpxeditor.shared.data.ble.PowerSensorSettingsStore
import com.gpxeditor.shared.feature.deletetrack.DeleteImportedTrackUseCase
import com.gpxeditor.shared.feature.edittrack.DeleteGpxTrackPointUseCase
import com.gpxeditor.shared.feature.edittrack.MoveGpxTrackPointUseCase
import com.gpxeditor.shared.feature.edittrack.TrimGpxTrackUseCase
import com.gpxeditor.shared.feature.exportfit.ExportFitTrackUseCase
import com.gpxeditor.shared.feature.importfit.ImportFitTrackUseCase
import com.gpxeditor.shared.feature.importgpx.ImportGpxTrackUseCase
import com.gpxeditor.shared.feature.recordtrack.SaveRecordedTrackUseCase
import com.gpxeditor.shared.feature.renametrack.RenameTrackUseCase
import com.gpxeditor.shared.feature.trackdetail.TrackDetailUseCase
import java.io.File
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

class MainActivity : ComponentActivity() {
    private lateinit var openGpxLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var importScreenController: ImportScreenController
    private val sensorScope = MainScope()
    private lateinit var powerSensorController: PowerSensorController
    private lateinit var heartRateSensorController: HeartRateSensorController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val fileStorage = AndroidGpxTrackFileStorage(applicationContext)
        val importedTrackStore = JsonImportedTrackStore(applicationContext)
        val importIdGenerator = AndroidImportIdGenerator()
        val importClock = AndroidImportClock()
        powerSensorController = PowerSensorController(
            scope = sensorScope,
            sensor = KablePowerSensor(sensorScope),
            settings = PowerSensorSettingsStore(FilePowerSensorSettingsStorage(applicationContext)),
        )
        heartRateSensorController = HeartRateSensorController(
            scope = sensorScope,
            sensor = KableHeartRateSensor(sensorScope),
            settings = HeartRateSensorSettingsStore(FileHeartRateSensorSettingsStorage(applicationContext)),
        )
        importScreenController = ImportScreenController(
            importGpxTrackUseCase = ImportGpxTrackUseCase(
                fileStorage = fileStorage,
                trackStore = importedTrackStore,
                idGenerator = importIdGenerator,
                clock = importClock,
            ),
            importFitTrackUseCase = ImportFitTrackUseCase(
                fitFileDecoder = FitActivityDecoder(),
                fileStorage = fileStorage,
                trackStore = importedTrackStore,
                idGenerator = importIdGenerator,
                clock = importClock,
            ),
            exportFitTrackUseCase = ExportFitTrackUseCase(fileStorage),
            trackDetailUseCase = TrackDetailUseCase(fileStorage),
            renameTrackUseCase = RenameTrackUseCase(importedTrackStore),
            trimGpxTrackUseCase = TrimGpxTrackUseCase(),
            deleteImportedTrackUseCase = DeleteImportedTrackUseCase(
                fileStorage = fileStorage,
                trackStore = importedTrackStore,
            ),
            deleteGpxTrackPointUseCase = DeleteGpxTrackPointUseCase(),
            moveGpxTrackPointUseCase = MoveGpxTrackPointUseCase(),
            saveRecordedTrackUseCase = SaveRecordedTrackUseCase(
                fileStorage = fileStorage,
                trackStore = importedTrackStore,
                idGenerator = importIdGenerator,
                clock = importClock,
            ),
            recordingJournal = FileRecordingJournal(applicationContext),
            isRecordingActive = { TrackRecordingService.stats.value != null },
            fileStorage = fileStorage,
            importedTrackStore = importedTrackStore,
            readTextFrom = ::readTextFrom,
            readBytesFrom = ::readBytesFrom,
            displayNameFor = ::displayNameFor,
            exportGpx = ::exportGpx,
            exportFit = ::exportFit,
            runOnUiThread = { action -> runOnUiThread(action) },
        )

        openGpxLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(importScreenController::importTrackFrom)
        }

        setContent {
            ImportScreen(
                state = importScreenController.state,
                onImportClick = {
                    openGpxLauncher.launch(
                        arrayOf(
                            "application/gpx+xml",
                            "application/xml",
                            "text/xml",
                            "application/vnd.ant.fit",
                            "application/octet-stream",
                            "*/*",
                        ),
                    )
                },
                onTrackClick = importScreenController::openTrackDetail,
                onDeleteTrack = importScreenController::deleteTrack,
                onBackFromDetail = importScreenController::closeTrackDetail,
                onRenameTrack = importScreenController::renameTrack,
                onTrimTrack = importScreenController::startTrimmingTrack,
                onEditTrack = importScreenController::startEditingTrack,
                onExportTrackAsGpx = importScreenController::exportTrackAsGpx,
                onExportTrackAsFit = importScreenController::exportTrackAsFit,
                onBackFromTrim = importScreenController::closeTrimTrack,
                onPreviewTrim = importScreenController::trimTrack,
                onSaveTrimmedTrack = importScreenController::saveTrimmedTrack,
                onBackFromEdit = importScreenController::closeEditTrack,
                onDeleteTrackPoint = importScreenController::deleteTrackPoint,
                onMoveTrackPoint = importScreenController::moveTrackPoint,
                onSaveEditedTrack = importScreenController::saveEditedTrack,
                onRecordingClosed = importScreenController::loadImportedTracks,
                onRestoreRecording = importScreenController::restoreRecoveredRecording,
                onDiscardRecording = importScreenController::discardRecoveredRecording,
                powerSensorController = powerSensorController,
                heartRateSensorController = heartRateSensorController,
            )
        }

        importScreenController.loadImportedTracks()
        importScreenController.checkRecordingRecovery()
        handleViewIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleViewIntent(intent)
    }

    override fun onDestroy() {
        sensorScope.cancel()
        super.onDestroy()
    }

    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return

        val uri = intent.data ?: return
        if (AndroidImportFileName.isTelegramProvider(uri.authority)) {
            Toast.makeText(
                this,
                R.string.telegram_import_name_warning,
                Toast.LENGTH_LONG,
            ).show()
        }
        val fileName = AndroidImportFileName.resolveViewIntent(
            title = intent.getStringExtra(Intent.EXTRA_TITLE),
            subject = intent.getStringExtra(Intent.EXTRA_SUBJECT),
            clipLabel = intent.clipData?.description?.label?.toString(),
            uriFileName = displayNameFor(uri),
            mimeType = intent.type,
        )
        importScreenController.importTrackFrom(uri, fileName)
    }

    private fun readTextFrom(uri: Uri): String {
        return contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Could not open selected GPX file")
    }

    private fun readBytesFrom(uri: Uri): ByteArray {
        return contentResolver.openInputStream(uri)
            ?.use { it.readBytes() }
            ?: error("Could not open selected FIT file")
    }

    private fun displayNameFor(uri: Uri): String? {
        val queriedDisplayName = if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else {
                    null
                }
            }
        } else {
            null
        }

        val documentId = runCatching {
            if (DocumentsContract.isDocumentUri(this, uri)) DocumentsContract.getDocumentId(uri) else null
        }.getOrNull()

        return AndroidImportFileName.resolve(
            queriedDisplayName = queriedDisplayName,
            documentId = documentId,
            lastPathSegment = uri.lastPathSegment,
        )
    }

    private fun exportGpx(displayName: String, content: String) {
        val exportDirectory = File(cacheDir, "exports")
        exportDirectory.mkdirs()

        val file = File(exportDirectory, "${safeFileBaseName(displayName)}.gpx")
        file.writeText(content)

        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file,
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, file.name.ifBlank { "track.gpx" })
            clipData = ClipData.newUri(contentResolver, "GPX export", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(sendIntent, "Export GPX"))
    }

    private fun exportFit(displayName: String, content: ByteArray) {
        val exportDirectory = File(cacheDir, "exports")
        exportDirectory.mkdirs()

        val file = File(exportDirectory, "${safeFileBaseName(displayName)}.fit")
        file.writeBytes(content)

        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file,
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.ant.fit"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, file.name.ifBlank { "track.fit" })
            clipData = ClipData.newUri(contentResolver, "FIT export", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(sendIntent, "Export FIT"))
    }

    private fun safeFileBaseName(displayName: String): String {
        return displayName
            .replace(Regex("[^\\p{L}\\p{N} _-]"), "-")
            .trim()
            .ifBlank { "track" }
    }
}
