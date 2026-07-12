package com.gpxeditor.shared.feature.recordtrack

import com.gpxeditor.shared.data.ble.PowerSample

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrackRecorderTest {
    @Test
    fun recordsSamplesIntoSingleSegmentDocument() {
        val recorder = TrackRecorder()
        recorder.start(atEpochMillis = T0)

        assertTrue(recorder.onLocation(sample(secondsAfterStart = 1, latitude = 55.000)))
        assertTrue(recorder.onLocation(sample(secondsAfterStart = 2, latitude = 55.001)))
        assertTrue(recorder.onLocation(sample(secondsAfterStart = 3, latitude = 55.002)))

        val recorded = recorder.stop(atEpochMillis = T0 + 4_000, name = "Morning ride")
        val document = recorded.document
        val segment = document.tracks.single().segments.single()

        assertEquals(3, segment.points.size)
        assertEquals("Morning ride", document.metadata.name)
        assertEquals("Morning ride", document.tracks.single().name)
        assertEquals("cycling", document.metadata.sport)
        assertEquals("2026-07-11T10:00:01Z", document.metadata.startTime)
        assertEquals("2026-07-11T10:00:01Z", segment.points.first().time)
        assertEquals(55.001, segment.points[1].latitude)
        assertEquals(37.0, segment.points[1].longitude)
        assertEquals(RecordingState.STOPPED, recorder.state)
    }

    @Test
    fun accumulatesHaversineDistance() {
        val recorder = TrackRecorder()
        recorder.start(atEpochMillis = T0)

        // 0.001 degrees of latitude is ~111.2 m on the WGS-84-ish sphere.
        recorder.onLocation(sample(secondsAfterStart = 1, latitude = 55.000))
        recorder.onLocation(sample(secondsAfterStart = 11, latitude = 55.001))

        val stats = recorder.stats(nowEpochMillis = T0 + 11_000)
        assertEquals(111.2, stats.distanceMeters, absoluteTolerance = 0.5)
    }

    @Test
    fun pauseStartsNewSegmentAndSkipsGapDistance() {
        val recorder = TrackRecorder()
        recorder.start(atEpochMillis = T0)
        recorder.onLocation(sample(secondsAfterStart = 1, latitude = 55.000))
        recorder.onLocation(sample(secondsAfterStart = 2, latitude = 55.001))

        recorder.pause(atEpochMillis = T0 + 3_000)
        assertFalse(recorder.onLocation(sample(secondsAfterStart = 4, latitude = 55.100)))
        recorder.resume(atEpochMillis = T0 + 10_000)

        recorder.onLocation(sample(secondsAfterStart = 11, latitude = 55.200))
        recorder.onLocation(sample(secondsAfterStart = 12, latitude = 55.201))

        val recorded = recorder.stop(atEpochMillis = T0 + 13_000)
        val trackSegments = recorded.document.tracks.single().segments

        assertEquals(2, trackSegments.size)
        assertEquals(listOf(2, 2), trackSegments.map { it.points.size })
        // Two 0.001-degree steps; the 55.001 -> 55.200 jump across the pause is not counted.
        assertEquals(222.4, recorded.stats.distanceMeters, absoluteTolerance = 1.0)
    }

    @Test
    fun elapsedTimeExcludesPauses() {
        val recorder = TrackRecorder()
        recorder.start(atEpochMillis = T0)
        recorder.pause(atEpochMillis = T0 + 60_000)
        recorder.resume(atEpochMillis = T0 + 120_000)

        val stats = recorder.stats(nowEpochMillis = T0 + 150_000)
        assertEquals(90_000, stats.elapsedMillis)

        val recorded = recorder.stop(atEpochMillis = T0 + 160_000)
        assertEquals(100_000, recorded.stats.elapsedMillis)
    }

    @Test
    fun rejectsInaccurateSamples() {
        val recorder = TrackRecorder()
        recorder.start(atEpochMillis = T0)

        assertFalse(recorder.onLocation(sample(secondsAfterStart = 1, latitude = 55.000, accuracyMeters = 80.0)))
        assertTrue(recorder.onLocation(sample(secondsAfterStart = 2, latitude = 55.000, accuracyMeters = 10.0)))

        assertEquals(1, recorder.stats(nowEpochMillis = T0 + 2_000).pointCount)
    }

    @Test
    fun rejectsInvalidCoordinates() {
        val recorder = TrackRecorder()
        recorder.start(atEpochMillis = T0)

        assertFalse(recorder.onLocation(sample(secondsAfterStart = 1, latitude = 91.0)))
        assertFalse(recorder.onLocation(LocationSample(epochMillis = T0 + 2_000, latitude = 55.0, longitude = 181.0)))
    }

    @Test
    fun rejectsNonMonotonicTimestamps() {
        val recorder = TrackRecorder()
        recorder.start(atEpochMillis = T0)

        assertTrue(recorder.onLocation(sample(secondsAfterStart = 1, latitude = 55.000)))
        assertFalse(recorder.onLocation(sample(secondsAfterStart = 1, latitude = 55.001)))
        assertFalse(recorder.onLocation(sample(secondsAfterStart = 0, latitude = 55.002)))
    }

    @Test
    fun ignoresSamplesWhenNotRecording() {
        val recorder = TrackRecorder()
        assertFalse(recorder.onLocation(sample(secondsAfterStart = 1, latitude = 55.000)))

        recorder.start(atEpochMillis = T0)
        recorder.stop(atEpochMillis = T0 + 1_000)
        assertFalse(recorder.onLocation(sample(secondsAfterStart = 2, latitude = 55.000)))
    }

    @Test
    fun usesGpsSpeedAndFallsBackToDerivedSpeed() {
        val recorder = TrackRecorder()
        recorder.start(atEpochMillis = T0)

        recorder.onLocation(sample(secondsAfterStart = 1, latitude = 55.000, speedMetersPerSecond = 5.5))
        assertEquals(5.5, recorder.stats(nowEpochMillis = T0 + 1_000).currentSpeedMetersPerSecond)

        // ~111.2 m in 10 s without a GPS speed reading -> ~11.1 m/s derived.
        recorder.onLocation(sample(secondsAfterStart = 11, latitude = 55.001))
        val derived = recorder.stats(nowEpochMillis = T0 + 11_000).currentSpeedMetersPerSecond
        assertEquals(11.1, derived!!, absoluteTolerance = 0.1)
    }

    @Test
    fun keepsElevationAndSpeedInPoints() {
        val recorder = TrackRecorder()
        recorder.start(atEpochMillis = T0)
        recorder.onLocation(
            sample(secondsAfterStart = 1, latitude = 55.000, elevationMeters = 210.5, speedMetersPerSecond = 7.2),
        )

        val point = recorder.stop(atEpochMillis = T0 + 2_000).document.tracks.single().segments.single().points.single()
        assertEquals(210.5, point.elevationMeters)
        assertEquals(7.2, point.speedMetersPerSecond)
        assertNull(point.heartRateBpm)
        assertNull(point.powerWatts)
        assertNull(point.cadenceRpm)
    }

    @Test
    fun mergesLatestPowerIntoRecordedLocationsAndStats() {
        val recorder = TrackRecorder()
        recorder.start(atEpochMillis = T0)
        recorder.onPower(PowerSample(watts = 247, cadenceRpm = 91.6), atEpochMillis = T0 + 900)

        recorder.onLocation(sample(secondsAfterStart = 1, latitude = 55.0))

        val stats = recorder.stats(T0 + 1_100)
        assertEquals(247, stats.currentPowerWatts)
        assertEquals(92, stats.currentCadenceRpm)
        val point = recorder.stop(T0 + 2_000).document.tracks.single().segments.single().points.single()
        assertEquals(247, point.powerWatts)
        assertEquals(92, point.cadenceRpm)
    }

    @Test
    fun doesNotMergeStaleOrFuturePowerSamples() {
        val recorder = TrackRecorder(TrackRecorderConfig(powerSampleTimeoutMillis = 4_000))
        recorder.start(atEpochMillis = T0)
        recorder.onPower(PowerSample(watts = 200, cadenceRpm = 80.0), atEpochMillis = T0 + 1_000)

        recorder.onLocation(sample(secondsAfterStart = 5, latitude = 55.0))
        recorder.onLocation(sample(secondsAfterStart = 6, latitude = 55.001))

        val points = recorder.stop(T0 + 7_000).document.tracks.single().segments.single().points
        assertEquals(200, points[0].powerWatts)
        assertNull(points[1].powerWatts)
        assertNull(points[1].cadenceRpm)
        assertNull(recorder.stats(T0 + 7_000).currentPowerWatts)

        val futureRecorder = TrackRecorder()
        futureRecorder.start(T0)
        futureRecorder.onPower(PowerSample(300, 100.0), T0 + 2_000)
        futureRecorder.onLocation(sample(secondsAfterStart = 1, latitude = 55.0))
        val futurePoint = futureRecorder.stop(T0 + 3_000).document.tracks.single().segments.single().points.single()
        assertNull(futurePoint.powerWatts)
    }

    @Test
    fun leavesPowerGapDuringDisconnectAndResumesWithFreshSample() {
        val recorder = TrackRecorder(TrackRecorderConfig(powerSampleTimeoutMillis = 3_000))
        recorder.start(T0)
        recorder.onPower(PowerSample(210, 82.0), T0 + 500)
        recorder.onLocation(sample(secondsAfterStart = 1, latitude = 55.0))

        // No power notifications while disconnected: the old sample must expire.
        recorder.onLocation(sample(secondsAfterStart = 5, latitude = 55.001))

        // A notification after reconnect is merged normally again.
        recorder.onPower(PowerSample(260, 90.0), T0 + 5_500)
        recorder.onLocation(sample(secondsAfterStart = 6, latitude = 55.002))

        val points = recorder.stop(T0 + 7_000).document.tracks.single().segments.single().points
        assertEquals(listOf(210, null, 260), points.map { it.powerWatts })
        assertEquals(listOf(82, null, 90), points.map { it.cadenceRpm })
    }

    @Test
    fun routeSegmentsGrowWithAcceptedPointsOnly() {
        val recorder = TrackRecorder()
        recorder.start(atEpochMillis = T0)
        assertEquals(emptyList(), recorder.routeSegments())

        recorder.onLocation(sample(secondsAfterStart = 1, latitude = 55.000))
        recorder.onLocation(sample(secondsAfterStart = 2, latitude = 55.001, accuracyMeters = 80.0))
        recorder.onLocation(sample(secondsAfterStart = 3, latitude = 55.002))

        assertEquals(
            listOf(listOf(RoutePoint(55.000, 37.0), RoutePoint(55.002, 37.0))),
            recorder.routeSegments(),
        )
    }

    @Test
    fun routeSegmentsSplitOnPauseResume() {
        val recorder = TrackRecorder()
        recorder.start(atEpochMillis = T0)
        recorder.onLocation(sample(secondsAfterStart = 1, latitude = 55.000))

        recorder.pause(atEpochMillis = T0 + 2_000)
        // The empty segment opened by resume stays hidden until it gets a point.
        recorder.resume(atEpochMillis = T0 + 10_000)
        assertEquals(1, recorder.routeSegments().size)

        recorder.onLocation(sample(secondsAfterStart = 11, latitude = 55.100))

        assertEquals(
            listOf(
                listOf(RoutePoint(55.000, 37.0)),
                listOf(RoutePoint(55.100, 37.0)),
            ),
            recorder.routeSegments(),
        )
    }

    @Test
    fun routeSegmentsRespectPointLimitAndKeepEndpoints() {
        val recorder = TrackRecorder()
        recorder.start(atEpochMillis = T0)
        repeat(2_000) { index ->
            recorder.onLocation(
                sample(secondsAfterStart = (index + 1).toLong(), latitude = 55.0 + index * 0.00001),
            )
        }

        val route = recorder.routeSegments(maxPointsPerSegment = 1_000).single()

        assertEquals(1_000, route.size)
        assertEquals(RoutePoint(55.0, 37.0), route.first())
        assertEquals(RoutePoint(55.0 + 1_999 * 0.00001, 37.0), route.last())
    }

    @Test
    fun emptyRecordingProducesDocumentWithoutTracks() {
        val recorder = TrackRecorder()
        recorder.start(atEpochMillis = T0)

        val recorded = recorder.stop(atEpochMillis = T0 + 5_000)

        assertTrue(recorded.document.tracks.isEmpty())
        assertEquals(0, recorded.document.pointCount)
        assertEquals(0, recorded.stats.pointCount)
        assertNull(recorded.document.metadata.startTime)
    }

    @Test
    fun rejectsInvalidStateTransitions() {
        val recorder = TrackRecorder()

        assertFailsWith<IllegalStateException> { recorder.pause(atEpochMillis = T0) }
        assertFailsWith<IllegalStateException> { recorder.resume(atEpochMillis = T0) }
        assertFailsWith<IllegalStateException> { recorder.stop(atEpochMillis = T0) }

        recorder.start(atEpochMillis = T0)
        assertFailsWith<IllegalStateException> { recorder.start(atEpochMillis = T0 + 1_000) }
        assertFailsWith<IllegalStateException> { recorder.resume(atEpochMillis = T0 + 1_000) }

        recorder.stop(atEpochMillis = T0 + 2_000)
        assertFailsWith<IllegalStateException> { recorder.start(atEpochMillis = T0 + 3_000) }
        assertFailsWith<IllegalStateException> { recorder.stop(atEpochMillis = T0 + 3_000) }
    }

    private fun sample(
        secondsAfterStart: Long,
        latitude: Double,
        longitude: Double = 37.0,
        elevationMeters: Double? = null,
        accuracyMeters: Double? = null,
        speedMetersPerSecond: Double? = null,
    ): LocationSample {
        return LocationSample(
            epochMillis = T0 + secondsAfterStart * 1_000,
            latitude = latitude,
            longitude = longitude,
            elevationMeters = elevationMeters,
            horizontalAccuracyMeters = accuracyMeters,
            speedMetersPerSecond = speedMetersPerSecond,
        )
    }

    private companion object {
        // 2026-07-11T10:00:00Z
        const val T0 = 1_783_764_000_000L
    }
}
