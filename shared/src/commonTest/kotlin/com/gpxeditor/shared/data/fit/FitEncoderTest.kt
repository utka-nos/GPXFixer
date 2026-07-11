package com.gpxeditor.shared.data.fit

import com.gpxeditor.shared.domain.activity.ActivityDocument
import com.gpxeditor.shared.domain.activity.ActivityMetadata
import com.gpxeditor.shared.domain.activity.ActivityPoint
import com.gpxeditor.shared.domain.activity.ActivitySegment
import com.gpxeditor.shared.domain.activity.ActivityTrack
import com.gpxeditor.shared.domain.fit.FitDecodeResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FitEncoderTest {
    @Test
    fun encodedDocumentRoundTripsThroughDecoder() {
        val document = ActivityDocument(
            tracks = listOf(
                ActivityTrack(
                    segments = listOf(
                        ActivitySegment(
                            points = listOf(
                                ActivityPoint(
                                    time = "2020-01-01T00:00:00Z",
                                    latitude = 41.7151,
                                    longitude = 44.8271,
                                    elevationMeters = 512.4,
                                    heartRateBpm = 142,
                                    powerWatts = 210,
                                    cadenceRpm = 88,
                                    distanceMeters = 0.0,
                                    speedMetersPerSecond = 5.5,
                                ),
                                ActivityPoint(
                                    time = "2020-01-01T00:00:01Z",
                                    latitude = 41.7152,
                                    longitude = 44.8272,
                                    elevationMeters = 513.0,
                                    heartRateBpm = 143,
                                    powerWatts = 215,
                                    cadenceRpm = 89,
                                    distanceMeters = 5.5,
                                    speedMetersPerSecond = 5.6,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val bytes = FitEncoder.encode(document)
        val result = FitActivityDecoder().decode(bytes)

        val success = assertIs<FitDecodeResult.Success>(result)
        val points = success.document.tracks.single().segments.single().points
        assertEquals(2, points.size)

        val first = points.first()
        assertEquals("2020-01-01T00:00:00Z", first.time)
        assertEquals(41.7151, first.latitude!!, absoluteTolerance = 1e-4)
        assertEquals(44.8271, first.longitude!!, absoluteTolerance = 1e-4)
        assertEquals(512.4, first.elevationMeters!!, absoluteTolerance = 0.2)
        assertEquals(142, first.heartRateBpm)
        assertEquals(210, first.powerWatts)
        assertEquals(88, first.cadenceRpm)
        assertEquals(5.5, first.speedMetersPerSecond!!, absoluteTolerance = 1e-3)

        val second = points.last()
        assertEquals("2020-01-01T00:00:01Z", second.time)
        assertEquals(5.5, second.distanceMeters!!, absoluteTolerance = 1e-2)
    }

    @Test
    fun missingFieldsStayNullAfterRoundTrip() {
        val document = ActivityDocument(
            tracks = listOf(
                ActivityTrack(
                    segments = listOf(
                        ActivitySegment(
                            points = listOf(
                                ActivityPoint(
                                    time = "2020-01-01T00:00:00Z",
                                    latitude = 10.0,
                                    longitude = 20.0,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val bytes = FitEncoder.encode(document)
        val result = FitActivityDecoder().decode(bytes)

        val success = assertIs<FitDecodeResult.Success>(result)
        val point = success.document.tracks.single().segments.single().points.single()
        assertEquals(10.0, point.latitude!!, absoluteTolerance = 1e-4)
        assertNull(point.heartRateBpm)
        assertNull(point.powerWatts)
        assertNull(point.elevationMeters)
    }

    @Test
    fun writesSessionAggregatesFromRecordedData() {
        val bytes = FitEncoder.encode(rideDocument(), localTimeOffsetSeconds = 0)
        val messages = decodeMessages(bytes)

        val session = messages.single { it.globalMessageNumber == MESSAGE_SESSION }
        assertEquals(2L, session.field(SESSION_SPORT)?.intValue()) // cycling
        assertEquals(START_FIT_TIMESTAMP, session.field(SESSION_START_TIME)?.intValue())
        assertEquals(START_FIT_TIMESTAMP + 20L, session.field(FIELD_TIMESTAMP)?.intValue())
        assertEquals(20_000L, session.field(SESSION_TOTAL_ELAPSED_TIME)?.intValue())
        assertEquals(20_000L, session.field(SESSION_TOTAL_TIMER_TIME)?.intValue())
        assertEquals(13_000L, session.field(SESSION_TOTAL_DISTANCE)?.intValue()) // 130 m in cm
        assertEquals(6_500L, session.field(SESSION_AVG_SPEED)?.intValue()) // 130 m / 20 s in mm/s
        assertEquals(7_000L, session.field(SESSION_MAX_SPEED)?.intValue())
        assertEquals(150L, session.field(SESSION_AVG_HEART_RATE)?.intValue())
        assertEquals(160L, session.field(SESSION_MAX_HEART_RATE)?.intValue())
        assertEquals(90L, session.field(SESSION_AVG_CADENCE)?.intValue())
        assertEquals(100L, session.field(SESSION_MAX_CADENCE)?.intValue())
        assertEquals(250L, session.field(SESSION_AVG_POWER)?.intValue())
        assertEquals(300L, session.field(SESSION_MAX_POWER)?.intValue())
        assertEquals(1L, session.field(SESSION_NUM_LAPS)?.intValue())

        val lap = messages.single { it.globalMessageNumber == MESSAGE_LAP }
        assertEquals(START_FIT_TIMESTAMP, lap.field(LAP_START_TIME)?.intValue())
        assertEquals(20_000L, lap.field(LAP_TOTAL_TIMER_TIME)?.intValue())
        assertEquals(13_000L, lap.field(LAP_TOTAL_DISTANCE)?.intValue())
        assertEquals(250L, lap.field(LAP_AVG_POWER)?.intValue())
        assertEquals(2L, lap.field(LAP_SPORT)?.intValue())
    }

    @Test
    fun writesActivityWithUtcAndLocalTimestamps() {
        val bytes = FitEncoder.encode(rideDocument(), localTimeOffsetSeconds = 3_600)
        val messages = decodeMessages(bytes)

        val activity = messages.single { it.globalMessageNumber == MESSAGE_ACTIVITY }
        assertEquals(START_FIT_TIMESTAMP + 20L, activity.field(FIELD_TIMESTAMP)?.intValue())
        assertEquals(START_FIT_TIMESTAMP + 20L + 3_600L, activity.field(ACTIVITY_LOCAL_TIMESTAMP)?.intValue())
        assertEquals(20_000L, activity.field(ACTIVITY_TOTAL_TIMER_TIME)?.intValue())
        assertEquals(1L, activity.field(ACTIVITY_NUM_SESSIONS)?.intValue())
    }

    @Test
    fun writesTimerEventsAroundEachSegment() {
        val document = ActivityDocument(
            metadata = ActivityMetadata(sport = "cycling"),
            tracks = listOf(
                ActivityTrack(
                    segments = listOf(
                        ActivitySegment(
                            points = listOf(
                                locatedPoint("2020-01-01T00:00:00Z"),
                                locatedPoint("2020-01-01T00:00:10Z"),
                            ),
                        ),
                        // The recorder starts a new segment after a pause.
                        ActivitySegment(
                            points = listOf(
                                locatedPoint("2020-01-01T00:00:30Z"),
                                locatedPoint("2020-01-01T00:00:40Z"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val bytes = FitEncoder.encode(document, localTimeOffsetSeconds = 0)
        val messages = decodeMessages(bytes)

        val events = messages.filter { it.globalMessageNumber == MESSAGE_EVENT }
        assertEquals(
            listOf(
                EVENT_TYPE_START to START_FIT_TIMESTAMP,
                EVENT_TYPE_STOP to START_FIT_TIMESTAMP + 10L,
                EVENT_TYPE_START to START_FIT_TIMESTAMP + 30L,
                EVENT_TYPE_STOP_ALL to START_FIT_TIMESTAMP + 40L,
            ),
            events.map { it.field(EVENT_EVENT_TYPE)?.intValue() to it.field(FIELD_TIMESTAMP)?.intValue() },
        )
        assertTrue(events.all { it.field(EVENT_EVENT)?.intValue() == 0L }) // timer

        // The pause gap counts towards elapsed time but not timer time.
        val session = messages.single { it.globalMessageNumber == MESSAGE_SESSION }
        assertEquals(40_000L, session.field(SESSION_TOTAL_ELAPSED_TIME)?.intValue())
        assertEquals(20_000L, session.field(SESSION_TOTAL_TIMER_TIME)?.intValue())
        // No per-point distance: total falls back to the coordinate distance.
        assertNotNull(session.field(SESSION_TOTAL_DISTANCE)?.intValue())
    }

    @Test
    fun distanceIsRelativeToSegmentStartAfterTrim() {
        // A trimmed recording: the cumulative distance counter starts at 60 m.
        val document = ActivityDocument(
            tracks = listOf(
                ActivityTrack(
                    segments = listOf(
                        ActivitySegment(
                            points = listOf(
                                ActivityPoint(time = "2020-01-01T00:00:00Z", distanceMeters = 60.0),
                                ActivityPoint(time = "2020-01-01T00:00:10Z", distanceMeters = 130.0),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val bytes = FitEncoder.encode(document, localTimeOffsetSeconds = 0)
        val session = decodeMessages(bytes).single { it.globalMessageNumber == MESSAGE_SESSION }

        assertEquals(7_000L, session.field(SESSION_TOTAL_DISTANCE)?.intValue()) // 70 m covered, not 130 m
        assertEquals(7_000L, session.field(SESSION_AVG_SPEED)?.intValue()) // 70 m / 10 s in mm/s
    }

    @Test
    fun distanceCounterResetBetweenSegmentsIsSummedPerSegment() {
        val document = ActivityDocument(
            tracks = listOf(
                ActivityTrack(
                    segments = listOf(
                        ActivitySegment(
                            points = listOf(
                                ActivityPoint(time = "2020-01-01T00:00:00Z", distanceMeters = 0.0),
                                ActivityPoint(time = "2020-01-01T00:00:10Z", distanceMeters = 50.0),
                            ),
                        ),
                        // A second source restarts the cumulative counter from zero.
                        ActivitySegment(
                            points = listOf(
                                ActivityPoint(time = "2020-01-01T00:00:30Z", distanceMeters = 0.0),
                                ActivityPoint(time = "2020-01-01T00:00:40Z", distanceMeters = 30.0),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val bytes = FitEncoder.encode(document, localTimeOffsetSeconds = 0)
        val session = decodeMessages(bytes).single { it.globalMessageNumber == MESSAGE_SESSION }

        assertEquals(8_000L, session.field(SESSION_TOTAL_DISTANCE)?.intValue()) // 50 m + 30 m
    }

    @Test
    fun stopAllFollowsLastTimedSegment() {
        val document = ActivityDocument(
            tracks = listOf(
                ActivityTrack(
                    segments = listOf(
                        ActivitySegment(
                            points = listOf(
                                locatedPoint("2020-01-01T00:00:00Z"),
                                locatedPoint("2020-01-01T00:00:10Z"),
                            ),
                        ),
                        // A trailing segment whose points carry no timestamps.
                        ActivitySegment(
                            points = listOf(ActivityPoint(latitude = 41.0, longitude = 44.0)),
                        ),
                    ),
                ),
            ),
        )

        val bytes = FitEncoder.encode(document, localTimeOffsetSeconds = 0)
        val messages = decodeMessages(bytes)

        val events = messages.filter { it.globalMessageNumber == MESSAGE_EVENT }
        assertEquals(
            listOf(
                EVENT_TYPE_START to START_FIT_TIMESTAMP,
                EVENT_TYPE_STOP_ALL to START_FIT_TIMESTAMP + 10L,
            ),
            events.map { it.field(EVENT_EVENT_TYPE)?.intValue() to it.field(FIELD_TIMESTAMP)?.intValue() },
        )
        assertEquals(1, messages.count { it.globalMessageNumber == MESSAGE_SESSION })
        assertEquals(1, messages.count { it.globalMessageNumber == MESSAGE_ACTIVITY })
    }

    @Test
    fun omitsSessionAggregatesWithoutSensorData() {
        val document = ActivityDocument(
            tracks = listOf(
                ActivityTrack(
                    segments = listOf(
                        ActivitySegment(
                            points = listOf(
                                locatedPoint("2020-01-01T00:00:00Z"),
                                locatedPoint("2020-01-01T00:00:10Z"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val bytes = FitEncoder.encode(document, localTimeOffsetSeconds = 0)
        val session = decodeMessages(bytes).single { it.globalMessageNumber == MESSAGE_SESSION }

        assertNull(session.field(SESSION_AVG_HEART_RATE))
        assertNull(session.field(SESSION_MAX_HEART_RATE))
        assertNull(session.field(SESSION_AVG_CADENCE))
        assertNull(session.field(SESSION_MAX_CADENCE))
        assertNull(session.field(SESSION_AVG_POWER))
        assertNull(session.field(SESSION_MAX_POWER))
        assertNull(session.field(SESSION_MAX_SPEED))
        assertEquals(0L, session.field(SESSION_SPORT)?.intValue()) // generic
    }

    @Test
    fun skipsSummaryMessagesWhenNoPointHasTime() {
        val document = ActivityDocument(
            tracks = listOf(
                ActivityTrack(
                    segments = listOf(
                        ActivitySegment(points = listOf(ActivityPoint(latitude = 10.0, longitude = 20.0))),
                    ),
                ),
            ),
        )

        val bytes = FitEncoder.encode(document, localTimeOffsetSeconds = 0)
        val messages = decodeMessages(bytes)

        assertTrue(messages.none { it.globalMessageNumber == MESSAGE_EVENT })
        assertTrue(messages.none { it.globalMessageNumber == MESSAGE_LAP })
        assertTrue(messages.none { it.globalMessageNumber == MESSAGE_SESSION })
        assertTrue(messages.none { it.globalMessageNumber == MESSAGE_ACTIVITY })
        assertEquals(1, messages.count { it.globalMessageNumber == MESSAGE_RECORD })
    }

    @Test
    fun sportSurvivesRoundTripThroughDecoder() {
        val document = rideDocument().copy(metadata = ActivityMetadata(sport = "running"))

        val bytes = FitEncoder.encode(document, localTimeOffsetSeconds = 0)
        val result = FitActivityDecoder().decode(bytes)

        val success = assertIs<FitDecodeResult.Success>(result)
        assertEquals("running", success.document.metadata.sport)
        assertEquals("2020-01-01T00:00:00Z", success.document.metadata.startTime)
    }

    /** A 20-second cycling ride: three points, full sensor data, 130 m covered. */
    private fun rideDocument(): ActivityDocument {
        return ActivityDocument(
            metadata = ActivityMetadata(sport = "cycling"),
            tracks = listOf(
                ActivityTrack(
                    segments = listOf(
                        ActivitySegment(
                            points = listOf(
                                ActivityPoint(
                                    time = "2020-01-01T00:00:00Z",
                                    latitude = 41.0,
                                    longitude = 44.0,
                                    heartRateBpm = 140,
                                    powerWatts = 200,
                                    cadenceRpm = 80,
                                    distanceMeters = 0.0,
                                    speedMetersPerSecond = 5.0,
                                ),
                                ActivityPoint(
                                    time = "2020-01-01T00:00:10Z",
                                    latitude = 41.001,
                                    longitude = 44.0,
                                    heartRateBpm = 150,
                                    powerWatts = 250,
                                    cadenceRpm = 90,
                                    distanceMeters = 60.0,
                                    speedMetersPerSecond = 6.0,
                                ),
                                ActivityPoint(
                                    time = "2020-01-01T00:00:20Z",
                                    latitude = 41.002,
                                    longitude = 44.0,
                                    heartRateBpm = 160,
                                    powerWatts = 300,
                                    cadenceRpm = 100,
                                    distanceMeters = 130.0,
                                    speedMetersPerSecond = 7.0,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    private fun locatedPoint(time: String): ActivityPoint =
        ActivityPoint(time = time, latitude = 41.0, longitude = 44.0)

    private fun decodeMessages(bytes: ByteArray): List<FitMessage> =
        FitMessageDecoder(bytes, start = 12, end = bytes.size - 2).decode()

    private companion object {
        // 2020-01-01T00:00:00Z in the FIT epoch (Unix 1577836800 - 631065600).
        const val START_FIT_TIMESTAMP = 946771200L

        const val MESSAGE_SESSION = 18
        const val MESSAGE_LAP = 19
        const val MESSAGE_RECORD = 20
        const val MESSAGE_EVENT = 21
        const val MESSAGE_ACTIVITY = 34

        const val FIELD_TIMESTAMP = 253

        const val EVENT_EVENT = 0
        const val EVENT_EVENT_TYPE = 1
        const val EVENT_TYPE_START = 0L
        const val EVENT_TYPE_STOP = 1L
        const val EVENT_TYPE_STOP_ALL = 4L

        const val LAP_START_TIME = 2
        const val LAP_TOTAL_TIMER_TIME = 8
        const val LAP_TOTAL_DISTANCE = 9
        const val LAP_AVG_POWER = 19
        const val LAP_SPORT = 25

        const val SESSION_START_TIME = 2
        const val SESSION_SPORT = 5
        const val SESSION_TOTAL_ELAPSED_TIME = 7
        const val SESSION_TOTAL_TIMER_TIME = 8
        const val SESSION_TOTAL_DISTANCE = 9
        const val SESSION_AVG_SPEED = 14
        const val SESSION_MAX_SPEED = 15
        const val SESSION_AVG_HEART_RATE = 16
        const val SESSION_MAX_HEART_RATE = 17
        const val SESSION_AVG_CADENCE = 18
        const val SESSION_MAX_CADENCE = 19
        const val SESSION_AVG_POWER = 20
        const val SESSION_MAX_POWER = 21
        const val SESSION_NUM_LAPS = 26

        const val ACTIVITY_TOTAL_TIMER_TIME = 0
        const val ACTIVITY_NUM_SESSIONS = 1
        const val ACTIVITY_LOCAL_TIMESTAMP = 5
    }
}
