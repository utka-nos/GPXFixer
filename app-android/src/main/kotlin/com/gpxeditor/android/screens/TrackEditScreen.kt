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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.gpxeditor.shared.domain.activity.ActivityDocument
import com.gpxeditor.shared.feature.edittrack.DeleteGpxTrackPointResult
import com.gpxeditor.shared.feature.edittrack.MoveGpxTrackPointResult
import com.gpxeditor.shared.feature.trackdetail.TrackDetail
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow

@Composable
fun TrackEditScreen(
    detail: TrackDetail,
    isSaving: Boolean,
    errorMessage: String?,
    onBackClick: () -> Unit,
    onDeletePoint: (ActivityDocument, Int) -> DeleteGpxTrackPointResult,
    onMovePoint: (ActivityDocument, Int, Double, Double) -> MoveGpxTrackPointResult,
    onSaveClick: (ActivityDocument) -> Unit,
) {
    var editedDocument by remember(detail.importedTrack.id) { mutableStateOf(detail.document) }
    var selectedPointIndex by remember(detail.importedTrack.id) { mutableStateOf<Int?>(null) }
    var movingPointIndex by remember(detail.importedTrack.id) { mutableStateOf<Int?>(null) }
    var localErrorMessage by remember(detail.importedTrack.id) { mutableStateOf<String?>(null) }
    var deletedPointSnapshots by remember(detail.importedTrack.id) {
        mutableStateOf<List<DeletedPointSnapshot>>(emptyList())
    }
    val geometry = remember(editedDocument) { EditableTrackMapGeometry.from(editedDocument) }
    val hasChanges = editedDocument != detail.document
    val cameraPositionState = rememberCameraPositionState()

    fun movePointTo(pointIndex: Int, latitude: Double, longitude: Double) {
        when (val result = onMovePoint(editedDocument, pointIndex, latitude, longitude)) {
            is MoveGpxTrackPointResult.Failure -> {
                localErrorMessage = result.error.message
            }
            is MoveGpxTrackPointResult.Success -> {
                editedDocument = result.document
                selectedPointIndex = result.movedPointIndex
                movingPointIndex = result.movedPointIndex
                localErrorMessage = null
                // Undo restores a full pre-delete snapshot, which would
                // silently revert this move too — drop the history instead.
                deletedPointSnapshots = emptyList()
            }
        }
    }

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
                isMovingPoint = movingPointIndex != null,
                onPointClick = { pointIndex ->
                    if (movingPointIndex == null) {
                        selectedPointIndex = pointIndex
                        localErrorMessage = null
                    }
                },
                onMapClick = { position ->
                    val pointIndex = movingPointIndex ?: return@EditableTrackMap
                    movePointTo(pointIndex, position.latitude, position.longitude)
                },
                cameraPositionState = cameraPositionState,
                modifier = Modifier.fillMaxSize(),
            )
        }

        TrackEditTopBar(
            isSaving = isSaving,
            canSave = hasChanges && !isSaving,
            canUndoDelete = deletedPointSnapshots.isNotEmpty() && !isSaving,
            onBackClick = onBackClick,
            onUndoDeleteClick = {
                deletedPointSnapshots.lastOrNull()?.let { snapshot ->
                    editedDocument = snapshot.document
                    selectedPointIndex = snapshot.pointIndex
                    movingPointIndex = null
                    localErrorMessage = null
                    deletedPointSnapshots = deletedPointSnapshots.dropLast(1)
                }
            },
            onSaveClick = { onSaveClick(editedDocument) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp),
        )

        val selectedPoint = selectedPointIndex?.let { index ->
            geometry?.points?.firstOrNull { it.index == index }
        }
        val movingPoint = movingPointIndex?.let { index ->
            geometry?.points?.firstOrNull { it.index == index }
        }
        if (movingPoint != null) {
            MovePointControls(
                onNudge = { latSign, lonSign ->
                    val step = nudgeStepDegrees(cameraPositionState)
                    movePointTo(
                        pointIndex = movingPoint.index,
                        latitude = movingPoint.position.latitude + latSign * step.latitude,
                        longitude = movingPoint.position.longitude + lonSign * step.longitude,
                    )
                },
                onSaveClick = {
                    movingPointIndex = null
                    localErrorMessage = null
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        } else if (selectedPoint != null && geometry != null) {
            val pointPosition = geometry.points.indexOf(selectedPoint)
            SelectedPointMenu(
                point = selectedPoint,
                previousPointIndex = geometry.points.getOrNull(pointPosition - 1)?.index,
                nextPointIndex = geometry.points.getOrNull(pointPosition + 1)?.index,
                onSelectPoint = { pointIndex ->
                    selectedPointIndex = pointIndex
                    localErrorMessage = null
                },
                onDeleteClick = {
                    when (val result = onDeletePoint(editedDocument, selectedPoint.index)) {
                        is DeleteGpxTrackPointResult.Failure -> {
                            localErrorMessage = result.error.message
                        }
                        is DeleteGpxTrackPointResult.Success -> {
                            deletedPointSnapshots = deletedPointSnapshots + DeletedPointSnapshot(
                                document = editedDocument,
                                pointIndex = selectedPoint.index,
                            )
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
    canUndoDelete: Boolean,
    onBackClick: () -> Unit,
    onUndoDeleteClick: () -> Unit,
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
            OutlinedButton(onClick = onUndoDeleteClick, enabled = canUndoDelete) {
                Text("Undo delete")
            }
            Button(onClick = onSaveClick, enabled = canSave) {
                Text(if (isSaving) "Saving" else "Save")
            }
        }
    }
}

@Composable
private fun MovePointControls(
    onNudge: (latSign: Int, lonSign: Int) -> Unit,
    onSaveClick: () -> Unit,
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Tap the map or use the arrows to move the point",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge,
            )
            OutlinedButton(onClick = { onNudge(1, 0) }) {
                Text("↑")
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = { onNudge(0, -1) }) {
                    Text("←")
                }
                OutlinedButton(onClick = { onNudge(-1, 0) }) {
                    Text("↓")
                }
                OutlinedButton(onClick = { onNudge(0, 1) }) {
                    Text("→")
                }
            }
            Button(
                onClick = onSaveClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save position")
            }
        }
    }
}

private data class NudgeStepDegrees(
    val latitude: Double,
    val longitude: Double,
)

// One nudge moves the point by 1% of the visible map span, so the step
// scales with the current zoom level.
private fun nudgeStepDegrees(
    cameraPositionState: CameraPositionState,
    spanFraction: Double = 0.01,
    fallbackDegrees: Double = 0.00005,
): NudgeStepDegrees {
    val bounds = cameraPositionState.projection?.visibleRegion?.latLngBounds
        ?: return NudgeStepDegrees(latitude = fallbackDegrees, longitude = fallbackDegrees)

    val latitudeSpan = bounds.northeast.latitude - bounds.southwest.latitude
    val longitudeSpan = (bounds.northeast.longitude - bounds.southwest.longitude + 360.0) % 360.0

    return NudgeStepDegrees(
        latitude = (latitudeSpan * spanFraction).takeIf { it > 0.0 } ?: fallbackDegrees,
        longitude = (longitudeSpan * spanFraction).takeIf { it > 0.0 } ?: fallbackDegrees,
    )
}

@Composable
private fun SelectedPointMenu(
    point: EditableTrackPoint,
    previousPointIndex: Int?,
    nextPointIndex: Int?,
    onSelectPoint: (Int) -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Point ${point.index + 1}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = { previousPointIndex?.let(onSelectPoint) },
                    enabled = previousPointIndex != null,
                ) {
                    Text("←")
                }
                OutlinedButton(
                    onClick = { nextPointIndex?.let(onSelectPoint) },
                    enabled = nextPointIndex != null,
                ) {
                    Text("→")
                }
            }
            Text(
                text = "Index: ${point.index}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Time: ${point.time ?: "No data"}",
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
    isMovingPoint: Boolean,
    onPointClick: (Int) -> Unit,
    onMapClick: (LatLng) -> Unit,
    cameraPositionState: CameraPositionState = rememberCameraPositionState(),
    modifier: Modifier = Modifier,
) {
    var mapLoaded by remember { mutableStateOf(false) }
    var didSetInitialCamera by remember { mutableStateOf(false) }
    var visiblePointDots by remember(geometry) { mutableStateOf(emptyList<EditableTrackPoint>()) }
    var pointDotRadiusMeters by remember(geometry) { mutableStateOf(0.0) }

    LaunchedEffect(cameraPositionState, geometry, mapLoaded) {
        if (!mapLoaded) return@LaunchedEffect

        snapshotFlow { cameraPositionState.isMoving to cameraPositionState.position.zoom }
            .collect { (isMoving, zoom) ->
                if (isMoving) return@collect
                if (zoom < EDIT_POINT_DOTS_MIN_ZOOM) {
                    visiblePointDots = emptyList()
                    pointDotRadiusMeters = 0.0
                    return@collect
                }

                val visibleBounds = cameraPositionState.projection
                    ?.visibleRegion
                    ?.latLngBounds
                    ?: return@collect
                val candidates = geometry.points
                    .asSequence()
                    .filter { visibleBounds.contains(it.position) }
                    .take(MAX_VISIBLE_EDIT_POINT_DOTS + 1)
                    .toList()
                visiblePointDots = candidates.takeIf {
                    it.size <= MAX_VISIBLE_EDIT_POINT_DOTS
                }.orEmpty()
                pointDotRadiusMeters = EDIT_POINT_DOT_RADIUS_PX * metersPerPixel(
                    latitude = cameraPositionState.position.target.latitude,
                    zoom = zoom,
                )
            }
    }

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
        onMapClick = { position ->
            if (isMovingPoint) {
                onMapClick(position)
            } else {
                cameraPositionState.projection
                    ?.let { projection -> geometry.nearestPoint(position = position, projection = projection) }
                    ?.let { point -> onPointClick(point.index) }
            }
        },
    ) {
        geometry.polylines.forEach { points ->
            Polyline(
                points = points,
                color = Color(0xFF1E88E5),
                width = 8f,
            )
        }

        visiblePointDots.forEach { point ->
            Circle(
                center = point.position,
                radius = pointDotRadiusMeters,
                fillColor = Color(0xFF1565C0),
                strokeColor = Color.White,
                strokeWidth = 2f,
                clickable = !isMovingPoint,
                onClick = { onPointClick(point.index) },
            )
        }

        geometry.points.firstOrNull { it.index == selectedPointIndex }?.let { point ->
            Marker(
                state = MarkerState(position = point.position),
                title = "Point ${point.index + 1}",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE),
                zIndex = 1f,
                onClick = {
                    onPointClick(point.index)
                    true
                },
            )
        }
    }
}

private const val EDIT_POINT_DOTS_MIN_ZOOM = 16f
private const val MAX_VISIBLE_EDIT_POINT_DOTS = 50
private const val EDIT_POINT_DOT_RADIUS_PX = 4.0

private fun metersPerPixel(latitude: Double, zoom: Float): Double =
    156_543.03392 * cos(latitude * PI / 180.0) / 2.0.pow(zoom.toDouble())

private data class EditableTrackMapGeometry(
    val polylines: List<List<LatLng>>,
    val points: List<EditableTrackPoint>,
    val bounds: LatLngBounds,
) {
    companion object {
        fun from(document: ActivityDocument): EditableTrackMapGeometry? {
            var globalPointIndex = 0
            val pointMarkers = mutableListOf<EditableTrackPoint>()
            val polylines = document.tracks
                .flatMap { it.segments }
                .map { segment ->
                    segment.points.mapNotNull { point ->
                        val pointIndex = globalPointIndex
                        globalPointIndex += 1
                        val latitude = point.latitude ?: return@mapNotNull null
                        val longitude = point.longitude ?: return@mapNotNull null
                        val position = LatLng(latitude, longitude)
                        if (!position.isValid()) return@mapNotNull null

                        pointMarkers += EditableTrackPoint(
                            index = pointIndex,
                            position = position,
                            time = point.time,
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

    fun nearestPoint(
        position: LatLng,
        projection: com.google.android.gms.maps.Projection,
        maxDistancePixels: Double = 48.0,
    ): EditableTrackPoint? {
        val tapPoint = projection.toScreenLocation(position)

        return points
            .asSequence()
            .map { point ->
                val screenPoint = projection.toScreenLocation(point.position)
                val distance = hypot(
                    (screenPoint.x - tapPoint.x).toDouble(),
                    (screenPoint.y - tapPoint.y).toDouble(),
                )
                point to distance
            }
            .filter { (_, distance) -> distance <= maxDistancePixels }
            .minByOrNull { (_, distance) -> distance }
            ?.first
    }
}

private data class EditableTrackPoint(
    val index: Int,
    val position: LatLng,
    val time: String?,
)

private data class DeletedPointSnapshot(
    val document: ActivityDocument,
    val pointIndex: Int,
)
