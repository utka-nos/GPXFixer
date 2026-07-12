package com.gpxeditor.android.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.gpxeditor.shared.domain.activity.ActivityDocument
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

@Composable
fun TrackMapSection(
    document: ActivityDocument,
    onOpenMap: (() -> Unit)? = null,
) {
    val geometry = remember(document) {
        TrackMapGeometry.from(document)
    } ?: return

    DetailSection(title = "Map") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
        ) {
            TrackMapPreview(
                geometry = geometry,
                modifier = Modifier.fillMaxSize(),
            )
            if (onOpenMap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = onOpenMap),
                )
            }
        }
    }
}

@Composable
fun TrackMapFullScreen(
    document: ActivityDocument,
    onBackClick: () -> Unit,
) {
    val geometry = remember(document) {
        TrackMapGeometry.from(document)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Button(onClick = onBackClick) {
            Text("Back")
        }

        if (geometry == null) {
            Text(
                text = "No track geometry",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            TrackMapPreview(
                geometry = geometry,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                isInteractive = true,
            )
        }
    }
}

@Composable
private fun TrackMapPreview(
    geometry: TrackMapGeometry,
    modifier: Modifier = Modifier,
    boundsPadding: Dp = 48.dp,
    isInteractive: Boolean = false,
) {
    val cameraPositionState = rememberCameraPositionState()
    var mapLoaded by remember { mutableStateOf(false) }
    var pointMarkers by remember(geometry) { mutableStateOf(TrackPointMarkers.EMPTY) }

    LaunchedEffect(cameraPositionState, geometry, isInteractive, mapLoaded) {
        if (!isInteractive || !mapLoaded) return@LaunchedEffect

        snapshotFlow { cameraPositionState.isMoving to cameraPositionState.position }
            .collect { (isMoving, position) ->
                if (isMoving) return@collect

                pointMarkers = if (position.zoom < POINT_MARKERS_MIN_ZOOM) {
                    TrackPointMarkers.EMPTY
                } else {
                    val visibleBounds = cameraPositionState.projection
                        ?.visibleRegion
                        ?.latLngBounds
                        ?: return@collect
                    TrackPointMarkers(
                        points = geometry.allPoints
                            .filter(visibleBounds::contains)
                            .subsample(MAX_POINT_MARKERS),
                        radiusMeters = POINT_MARKER_RADIUS_PX *
                            metersPerPixel(position.target.latitude, position.zoom),
                    )
                }
            }
    }

    LaunchedEffect(mapLoaded, geometry) {
        if (!mapLoaded) return@LaunchedEffect

        if (geometry.pointCount == 1) {
            cameraPositionState.move(
                CameraUpdateFactory.newLatLngZoom(geometry.polylines.first().first(), 15f),
            )
        } else {
            cameraPositionState.move(
                CameraUpdateFactory.newLatLngBounds(
                    geometry.bounds,
                    boundsPadding.value.toInt(),
                ),
            )
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(
            compassEnabled = isInteractive,
            indoorLevelPickerEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            rotationGesturesEnabled = isInteractive,
            scrollGesturesEnabled = isInteractive,
            scrollGesturesEnabledDuringRotateOrZoom = isInteractive,
            tiltGesturesEnabled = isInteractive,
            zoomControlsEnabled = isInteractive,
            zoomGesturesEnabled = isInteractive,
        ),
        onMapLoaded = { mapLoaded = true },
    ) {
        geometry.polylines.forEach { points ->
            Polyline(
                points = points,
                color = Color(0xFF1E88E5),
                width = 8f,
            )
        }
        pointMarkers.points.forEach { point ->
            Circle(
                center = point,
                radius = pointMarkers.radiusMeters,
                fillColor = Color(0xFF1565C0),
                strokeColor = Color.White,
                strokeWidth = 2f,
            )
        }
    }
}

// Zoom level at which individual track points become visible. At zoom 16 the
// screen spans a few hundred meters, so a typical 1 point/sec track yields at
// most a few hundred on-screen points — cheap to render and still readable.
private const val POINT_MARKERS_MIN_ZOOM = 16f
private const val MAX_POINT_MARKERS = 500
private const val POINT_MARKER_RADIUS_PX = 4.0

private data class TrackPointMarkers(
    val points: List<LatLng>,
    val radiusMeters: Double,
) {
    companion object {
        val EMPTY = TrackPointMarkers(points = emptyList(), radiusMeters = 0.0)
    }
}

// Web Mercator ground resolution, keeps the circle a constant on-screen size.
private fun metersPerPixel(latitude: Double, zoom: Float): Double =
    156_543.03392 * cos(latitude * PI / 180.0) / 2.0.pow(zoom.toDouble())

private fun <T> List<T>.subsample(maxSize: Int): List<T> {
    if (size <= maxSize) return this
    val step = size.toDouble() / maxSize
    return List(maxSize) { index -> this[(index * step).toInt()] }
}

private data class TrackMapGeometry(
    val polylines: List<List<LatLng>>,
    val bounds: LatLngBounds,
) {
    val allPoints: List<LatLng> = polylines.flatten()
    val pointCount: Int = allPoints.size

    companion object {
        fun from(document: ActivityDocument): TrackMapGeometry? {
            val polylines = document.tracks
                .flatMap { it.segments }
                .map { segment ->
                    segment.points
                        .mapNotNull { point ->
                            val latitude = point.latitude ?: return@mapNotNull null
                            val longitude = point.longitude ?: return@mapNotNull null
                            LatLng(latitude, longitude)
                        }
                        .filter { point ->
                            point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0
                        }
                }
                .filter { it.isNotEmpty() }

            if (polylines.isEmpty()) return null

            val boundsBuilder = LatLngBounds.builder()
            polylines.flatten().forEach(boundsBuilder::include)

            return TrackMapGeometry(
                polylines = polylines,
                bounds = boundsBuilder.build(),
            )
        }
    }
}
