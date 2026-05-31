package com.gpxeditor.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.gpxeditor.shared.domain.gpx.GpxDocument
import com.gpxeditor.shared.feature.edittrack.DeleteGpxTrackPointResult
import com.gpxeditor.shared.feature.edittrack.MoveGpxTrackPointResult
import com.gpxeditor.shared.feature.trackdetail.TrackDetail

@Composable
fun TrackEditScreen(
    detail: TrackDetail,
    isSaving: Boolean,
    errorMessage: String?,
    onBackClick: () -> Unit,
    onDeletePoint: (GpxDocument, Int) -> DeleteGpxTrackPointResult,
    onMovePoint: (GpxDocument, Int, Double, Double) -> MoveGpxTrackPointResult,
    onSaveClick: (GpxDocument) -> Unit,
) {
    var editedDocument by remember(detail.importedTrack.id) { mutableStateOf(detail.document) }
    var selectedPointIndex by remember(detail.importedTrack.id) { mutableStateOf<Int?>(null) }
    var movingPointIndex by remember(detail.importedTrack.id) { mutableStateOf<Int?>(null) }
    var localErrorMessage by remember(detail.importedTrack.id) { mutableStateOf<String?>(null) }
    val geometry = remember(editedDocument) { EditableTrackMapGeometry.from(editedDocument) }
    val hasChanges = editedDocument != detail.document

    Box(modifier = Modifier.fillMaxSize()) {
        if (geometry == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "No track geometry",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            EditableTrackMap(
                geometry = geometry,
                selectedPointIndex = selectedPointIndex,
                onPointClick = { pointIndex ->
                    if (movingPointIndex == null) {
                        selectedPointIndex = pointIndex
                        localErrorMessage = null
                    }
                },
                onMapClick = { position ->
                    val pointIndex = movingPointIndex ?: return@EditableTrackMap
                    when (
                        val result = onMovePoint(
                            editedDocument,
                            pointIndex,
                            position.latitude,
                            position.longitude,
                        )
                    ) {
                        is MoveGpxTrackPointResult.Failure -> {
                            localErrorMessage = result.error.message
                        }
                        is MoveGpxTrackPointResult.Success -> {
                            editedDocument = result.document
                            selectedPointIndex = result.movedPointIndex
                            movingPointIndex = null
                            localErrorMessage = null
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        TrackEditTopBar(
            isSaving = isSaving,
            canSave = hasChanges && !isSaving,
            onBackClick = onBackClick,
            onSaveClick = { onSaveClick(editedDocument) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp),
        )

        val selectedPoint = selectedPointIndex?.let { index ->
            geometry?.points?.firstOrNull { it.index == index }
        }
        if (movingPointIndex != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 8.dp,
            ) {
                Text(
                    text = "Choose where to move the selected point",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else if (selectedPoint != null) {
            SelectedPointMenu(
                point = selectedPoint,
                onDeleteClick = {
                    when (val result = onDeletePoint(editedDocument, selectedPoint.index)) {
                        is DeleteGpxTrackPointResult.Failure -> {
                            localErrorMessage = result.error.message
                        }
                        is DeleteGpxTrackPointResult.Success -> {
                            editedDocument = result.document
                            selectedPointIndex = null
                            localErrorMessage = null
                        }
                    }
                },
                onMoveClick = {
                    movingPointIndex = selectedPoint.index
                    localErrorMessage = null
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }

        val visibleErrorMessage = localErrorMessage ?: errorMessage
        if (visibleErrorMessage != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 6.dp,
            ) {
                Text(
                    text = visibleErrorMessage,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun TrackEditTopBar(
    isSaving: Boolean,
    canSave: Boolean,
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onBackClick, enabled = !isSaving) {
                Text("Back")
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = onSaveClick, enabled = canSave) {
                Text(if (isSaving) "Saving" else "Save")
            }
        }
    }
}

@Composable
private fun SelectedPointMenu(
    point: EditableTrackPoint,
    onDeleteClick: () -> Unit,
    onMoveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Point ${point.index + 1}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Index: ${point.index}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onDeleteClick) {
                    Text("Delete")
                }
                OutlinedButton(
                    onClick = onMoveClick,
                ) {
                    Text("Move")
                }
            }
        }
    }
}

@Composable
private fun EditableTrackMap(
    geometry: EditableTrackMapGeometry,
    selectedPointIndex: Int?,
    onPointClick: (Int) -> Unit,
    onMapClick: (LatLng) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cameraPositionState = rememberCameraPositionState()
    var mapLoaded by remember { mutableStateOf(false) }
    var didSetInitialCamera by remember { mutableStateOf(false) }

    LaunchedEffect(mapLoaded) {
        if (!mapLoaded || didSetInitialCamera) return@LaunchedEffect

        if (geometry.points.size == 1) {
            cameraPositionState.move(
                CameraUpdateFactory.newLatLngZoom(geometry.points.single().position, 15f),
            )
        } else {
            cameraPositionState.move(
                CameraUpdateFactory.newLatLngBounds(geometry.bounds, 64),
            )
        }
        didSetInitialCamera = true
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(
            compassEnabled = true,
            indoorLevelPickerEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            rotationGesturesEnabled = true,
            scrollGesturesEnabled = true,
            scrollGesturesEnabledDuringRotateOrZoom = true,
            tiltGesturesEnabled = true,
            zoomControlsEnabled = true,
            zoomGesturesEnabled = true,
        ),
        onMapLoaded = { mapLoaded = true },
        onMapClick = onMapClick,
    ) {
        geometry.polylines.forEach { points ->
            Polyline(
                points = points,
                color = Color(0xFF1E88E5),
                width = 8f,
            )
        }

        geometry.points.forEach { point ->
            val isSelected = point.index == selectedPointIndex
            Marker(
                state = MarkerState(position = point.position),
                title = "Point ${point.index + 1}",
                icon = BitmapDescriptorFactory.defaultMarker(
                    if (isSelected) BitmapDescriptorFactory.HUE_ORANGE else BitmapDescriptorFactory.HUE_AZURE,
                ),
                zIndex = if (isSelected) 1f else 0f,
                onClick = {
                    onPointClick(point.index)
                    true
                },
            )
        }
    }
}

private data class EditableTrackMapGeometry(
    val polylines: List<List<LatLng>>,
    val points: List<EditableTrackPoint>,
    val bounds: LatLngBounds,
) {
    companion object {
        fun from(document: GpxDocument): EditableTrackMapGeometry? {
            var globalPointIndex = 0
            val pointMarkers = mutableListOf<EditableTrackPoint>()
            val polylines = document.tracks
                .flatMap { it.segments }
                .map { segment ->
                    segment.points.mapNotNull { point ->
                        val pointIndex = globalPointIndex
                        globalPointIndex += 1
                        val position = LatLng(point.latitude, point.longitude)
                        if (!position.isValid()) return@mapNotNull null

                        pointMarkers += EditableTrackPoint(
                            index = pointIndex,
                            position = position,
                        )
                        position
                    }
                }
                .filter { it.isNotEmpty() }

            if (polylines.isEmpty() || pointMarkers.isEmpty()) return null

            val boundsBuilder = LatLngBounds.builder()
            pointMarkers.forEach { boundsBuilder.include(it.position) }

            return EditableTrackMapGeometry(
                polylines = polylines,
                points = pointMarkers,
                bounds = boundsBuilder.build(),
            )
        }

        private fun LatLng.isValid(): Boolean {
            return latitude in -90.0..90.0 && longitude in -180.0..180.0
        }
    }
}

private data class EditableTrackPoint(
    val index: Int,
    val position: LatLng,
)
