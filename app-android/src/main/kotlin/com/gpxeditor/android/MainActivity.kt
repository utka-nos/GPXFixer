package com.gpxeditor.android

import android.content.ContentResolver
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.FileProvider
import com.gpxeditor.android.data.imported.AndroidGpxTrackFileStorage
import com.gpxeditor.android.data.imported.AndroidImportClock
import com.gpxeditor.android.data.imported.AndroidImportIdGenerator
import com.gpxeditor.android.data.imported.JsonImportedTrackStore
import com.gpxeditor.shared.feature.edittrack.TrimGpxTrackUseCase
import com.gpxeditor.shared.feature.importgpx.ImportGpxTrackUseCase
import com.gpxeditor.shared.feature.trackdetail.TrackDetailUseCase
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var openGpxLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var importScreenController: ImportScreenController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val fileStorage = AndroidGpxTrackFileStorage(applicationContext)
        val importedTrackStore = JsonImportedTrackStore(applicationContext)
        importScreenController = ImportScreenController(
            importGpxTrackUseCase = ImportGpxTrackUseCase(
                fileStorage = fileStorage,
                trackStore = importedTrackStore,
                idGenerator = AndroidImportIdGenerator(),
                clock = AndroidImportClock(),
            ),
            trackDetailUseCase = TrackDetailUseCase(fileStorage),
            trimGpxTrackUseCase = TrimGpxTrackUseCase(),
            fileStorage = fileStorage,
            importedTrackStore = importedTrackStore,
            readTextFrom = ::readTextFrom,
            displayNameFor = ::displayNameFor,
            exportGpx = ::exportGpx,
            runOnUiThread = { action -> runOnUiThread(action) },
        )

        openGpxLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(importScreenController::importGpxFrom)
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
                            "application/octet-stream",
                            "*/*",
                        ),
                    )
                },
                onTrackClick = importScreenController::openTrackDetail,
                onBackFromDetail = importScreenController::closeTrackDetail,
                onTrimTrack = importScreenController::startTrimmingTrack,
                onEditTrack = importScreenController::showEditPlaceholder,
                onExportTrack = importScreenController::exportTrack,
                onBackFromTrim = importScreenController::closeTrimTrack,
                onPreviewTrim = importScreenController::trimTrack,
                onSaveTrimmedTrack = importScreenController::saveTrimmedTrack,
            )
        }

        importScreenController.loadImportedTracks()
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
        importScreenController.importGpxFrom(uri)
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

    private fun safeFileBaseName(displayName: String): String {
        return displayName
            .replace(Regex("[^A-Za-z0-9 _-]"), "-")
            .trim()
            .ifBlank { "track" }
    }
}
