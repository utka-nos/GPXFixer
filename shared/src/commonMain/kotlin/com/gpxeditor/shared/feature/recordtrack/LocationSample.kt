package com.gpxeditor.shared.feature.recordtrack

/** A single GPS fix delivered by a platform location source. */
data class LocationSample(
    val epochMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double? = null,
    val horizontalAccuracyMeters: Double? = null,
    val speedMetersPerSecond: Double? = null,
)
