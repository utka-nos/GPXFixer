package com.gpxeditor.shared.core.geo

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Great-circle distance between two coordinates in meters. */
internal fun haversineMeters(
    fromLatitude: Double,
    fromLongitude: Double,
    toLatitude: Double,
    toLongitude: Double,
): Double {
    val earthRadiusMeters = 6_371_000.0
    val fromLatitudeRadians = fromLatitude.toRadians()
    val toLatitudeRadians = toLatitude.toRadians()
    val latitudeDelta = (toLatitude - fromLatitude).toRadians()
    val longitudeDelta = (toLongitude - fromLongitude).toRadians()

    val a = sin(latitudeDelta / 2.0) * sin(latitudeDelta / 2.0) +
        cos(fromLatitudeRadians) * cos(toLatitudeRadians) *
        sin(longitudeDelta / 2.0) * sin(longitudeDelta / 2.0)
    val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))

    return earthRadiusMeters * c
}

private fun Double.toRadians(): Double = this * PI / 180.0
