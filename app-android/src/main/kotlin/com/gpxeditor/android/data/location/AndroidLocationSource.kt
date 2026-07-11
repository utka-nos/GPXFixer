package com.gpxeditor.android.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.gpxeditor.shared.feature.recordtrack.LocationSample
import com.gpxeditor.shared.feature.recordtrack.LocationSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * [LocationSource] backed by the fused location provider.
 *
 * Callers must hold ACCESS_FINE_LOCATION before collecting the flow;
 * the permission request flow lives at the screen/service level.
 */
class AndroidLocationSource(context: Context) : LocationSource {
    private val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    @SuppressLint("MissingPermission")
    override fun locations(): Flow<LocationSample> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MILLIS)
            .setMinUpdateIntervalMillis(UPDATE_INTERVAL_MILLIS)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location ->
                    trySend(location.toLocationSample())
                }
            }
        }

        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }

    private fun Location.toLocationSample(): LocationSample {
        return LocationSample(
            epochMillis = time,
            latitude = latitude,
            longitude = longitude,
            elevationMeters = if (hasAltitude()) altitude else null,
            horizontalAccuracyMeters = if (hasAccuracy()) accuracy.toDouble() else null,
            speedMetersPerSecond = if (hasSpeed()) speed.toDouble() else null,
        )
    }

    private companion object {
        const val UPDATE_INTERVAL_MILLIS = 1_000L
    }
}
