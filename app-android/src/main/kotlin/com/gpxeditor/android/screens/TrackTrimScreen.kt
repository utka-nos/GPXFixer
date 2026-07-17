package com.gpxeditor.android.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gpxeditor.shared.domain.activity.ActivityDocument
import com.gpxeditor.shared.feature.edittrack.TrimGpxTrackResult
import com.gpxeditor.shared.feature.trackdetail.TrackDetail

@Composable
fun TrackTrimScreen(
    detail: TrackDetail,
    isSaving: Boolean,
    statusMessage: String?,
    errorMessage: String?,
    onBackClick: () -> Unit,
    onPreviewTrim: (ActivityDocument, Int, Int) -> TrimGpxTrackResult,
    onSaveClick: (ActivityDocument) -> Unit,
) {
    val context = LocalContext.current
    val originalPointCount = detail.summary.pointCount
    val lastPointIndex = (originalPointCount - 1).coerceAtLeast(0)
    var trimStartIndex by remember(detail.importedTrack.id) { mutableIntStateOf(0) }
    var trimEndIndex by remember(detail.importedTrack.id) { mutableIntStateOf(lastPointIndex) }
    val preview = remember(detail.document, trimStartIndex, trimEndIndex) {
        trimPreviewState(
            detail = detail,
            trimStartIndex = trimStartIndex,
            trimEndIndex = trimEndIndex,
            onPreviewTrim = onPreviewTrim,
        )
    }
    val previewDocument = preview.document ?: detail.document
    val previewSummary = preview.summary ?: detail.summary

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Trim track",
                onBackClick = onBackClick,
                actions = {
                    IconButton(
                        enabled = !isSaving && preview.document != null && preview.removedPointCount > 0,
                        onClick = {
                            preview.document?.let { document ->
                                Toast
                                    .makeText(context, "Saving trimmed track", Toast.LENGTH_SHORT)
                                    .show()
                                onSaveClick(document)
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save trimmed track",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = detail.importedTrack.displayName,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (isSaving) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            statusMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            TrackMapSection(document = previewDocument)
            TrackTrimControlsSection(
                originalPointCount = originalPointCount,
                lastPointIndex = lastPointIndex,
                trimStartIndex = trimStartIndex,
                trimEndIndex = trimEndIndex,
                removedPointCount = preview.removedPointCount,
                previewErrorMessage = preview.errorMessage,
                onTrimStartChange = { trimStartIndex = it },
                onTrimEndChange = { trimEndIndex = it },
                onResetTrim = {
                    trimStartIndex = 0
                    trimEndIndex = lastPointIndex
                },
            )
            SummarySection(
                title = "Preview summary",
                importedAt = null,
                summary = previewSummary,
            )
        }
    }
}
