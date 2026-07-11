package com.gpxeditor.shared.feature.recordtrack

import kotlinx.coroutines.flow.Flow

/**
 * Platform source of GPS fixes for track recording. Collecting the flow
 * starts location updates; cancelling the collection stops them.
 */
interface LocationSource {
    fun locations(): Flow<LocationSample>
}
