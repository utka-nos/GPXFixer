package com.gpxeditor.android

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.gpxeditor.shared.domain.gpx.GpxDocument

@Composable
fun TrackMapSection(
    document: GpxDocument,
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
    document: GpxDocument,
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
    }
}

private data class TrackMapGeometry(
    val polylines: List<List<LatLng>>,
    val bounds: LatLngBounds,
) {
    val pointCount: Int = polylines.sumOf { it.size }

    companion object {
        fun from(document: GpxDocument): TrackMapGeometry? {
            val polylines = document.tracks
                .flatMap { it.segments }
                .map { segment ->
                    segment.points
                        .map { point -> LatLng(point.latitude, point.longitude) }
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
