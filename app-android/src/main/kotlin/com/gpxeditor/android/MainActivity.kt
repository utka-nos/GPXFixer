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
import com.gpxeditor.android.data.imported.AndroidGpxTrackFileStorage
import com.gpxeditor.android.data.imported.AndroidImportClock
import com.gpxeditor.android.data.imported.AndroidImportIdGenerator
import com.gpxeditor.android.data.imported.JsonImportedTrackStore
import com.gpxeditor.shared.feature.importgpx.ImportGpxTrackUseCase
import com.gpxeditor.shared.feature.trackdetail.TrackDetailUseCase

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
            importedTrackStore = importedTrackStore,
            readTextFrom = ::readTextFrom,
            displayNameFor = ::displayNameFor,
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
}
