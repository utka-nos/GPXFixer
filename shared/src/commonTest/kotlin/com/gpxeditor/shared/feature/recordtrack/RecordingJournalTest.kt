package com.gpxeditor.shared.feature.recordtrack

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordingJournalTest {
    @Test
    fun recoversPointsAndMetadataFromJournal() {
        val journal = listOf(
            RecordingJournal.startLine(T0),
            RecordingJournal.pointLine(sample(secondsAfterStart = 1, latitude = 55.000, elevationMeters = 210.5)),
            RecordingJournal.pointLine(sample(secondsAfterStart = 2, latitude = 55.001, speedMetersPerSecond = 7.5)),
        ).joinToString("\n")

        val recovered = RecordingJournal.recover(journal)!!
        val document = recovered.document
        val points = document.tracks.single().segments.single().points

        assertEquals("cycling", document.metadata.sport)
        assertEquals("2026-07-11T10:00:01Z", document.metadata.startTime)
        assertEquals(2, points.size)
        assertEquals(210.5, points[0].elevationMeters)
        assertNull(points[0].speedMetersPerSecond)
        assertEquals(7.5, points[1].speedMetersPerSecond)
        assertEquals(55.001, points[1].latitude)
        assertEquals(RecordingState.STOPPED, recovered.stats.state)
        assertEquals(2_000, recovered.stats.elapsedMillis)
    }

    @Test
    fun recoversPauseResumeAsSegments() {
        val journal = listOf(
            RecordingJournal.startLine(T0),
            RecordingJournal.pointLine(sample(secondsAfterStart = 1, latitude = 55.000)),
            RecordingJournal.pauseLine(T0 + 10_000),
            RecordingJournal.resumeLine(T0 + 30_000),
            RecordingJournal.pointLine(sample(secondsAfterStart = 31, latitude = 55.100)),
        ).joinToString("\n")

        val recovered = RecordingJournal.recover(journal)!!
        val segments = recovered.document.tracks.single().segments

        assertEquals(2, segments.size)
        assertEquals(listOf(1, 1), segments.map { it.points.size })
        // 10 s recording + 1 s after resume; the 20 s pause is excluded.
        assertEquals(11_000, recovered.stats.elapsedMillis)
    }

    @Test
    fun skipsTornTrailingLine() {
        val journal = listOf(
            RecordingJournal.startLine(T0),
            RecordingJournal.pointLine(sample(secondsAfterStart = 1, latitude = 55.000)),
            "POINT ${T0 + 2_000} 55.001 37",
        ).joinToString("\n")

        val recovered = RecordingJournal.recover(journal)!!

        assertEquals(1, recovered.document.pointCount)
    }

    @Test
    fun skipsGarbageLinesAndDuplicateStart() {
        val journal = listOf(
            "",
            "garbage line",
            RecordingJournal.startLine(T0),
            RecordingJournal.startLine(T0 + 500),
            RecordingJournal.pointLine(sample(secondsAfterStart = 1, latitude = 55.000)),
            "POINT not-a-number 55.002 37.0 - - -",
            RecordingJournal.pointLine(sample(secondsAfterStart = 2, latitude = 55.001)),
        ).joinToString("\n")

        val recovered = RecordingJournal.recover(journal)!!

        assertEquals(2, recovered.document.pointCount)
    }

    @Test
    fun ignoresEventsBeforeStart() {
        val journal = listOf(
            RecordingJournal.pointLine(sample(secondsAfterStart = 1, latitude = 55.000)),
            RecordingJournal.pauseLine(T0 + 1_000),
        ).joinToString("\n")

        assertNull(RecordingJournal.recover(journal))
    }

    @Test
    fun returnsNullForEmptyContent() {
        assertNull(RecordingJournal.recover(""))
    }

    @Test
    fun recoversEmptyRecordingAsDocumentWithoutTracks() {
        val recovered = RecordingJournal.recover(RecordingJournal.startLine(T0))!!

        assertTrue(recovered.document.tracks.isEmpty())
        assertEquals(0, recovered.stats.pointCount)
    }

    private fun sample(
        secondsAfterStart: Long,
        latitude: Double,
        elevationMeters: Double? = null,
        speedMetersPerSecond: Double? = null,
    ): LocationSample {
        return LocationSample(
            epochMillis = T0 + secondsAfterStart * 1_000,
            latitude = latitude,
            longitude = 37.0,
            elevationMeters = elevationMeters,
            horizontalAccuracyMeters = 10.0,
            speedMetersPerSecond = speedMetersPerSecond,
        )
    }

    private companion object {
        // 2026-07-11T10:00:00Z
        const val T0 = 1_783_764_000_000L
    }
}
