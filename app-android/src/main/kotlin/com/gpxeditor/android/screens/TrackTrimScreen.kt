package com.gpxeditor.android

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gpxeditor.shared.domain.gpx.GpxDocument
import com.gpxeditor.shared.feature.edittrack.TrimGpxTrackResult
import com.gpxeditor.shared.feature.trackdetail.TrackDetail

@Composable
fun TrackTrimScreen(
    detail: TrackDetail,
    isSaving: Boolean,
    statusMessage: String?,
    errorMessage: String?,
    onBackClick: () -> Unit,
    onPreviewTrim: (GpxDocument, Int, Int) -> TrimGpxTrackResult,
    onSaveClick: (GpxDocument) -> Unit,
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onBackClick) {
                Text("Back")
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
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
                Text(if (isSaving) "Saving" else "Save")
            }
        }

        Text(
            text = "Trim track",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = detail.importedTrack.displayName,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (isSaving) {
            CircularProgressIndicator()
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
