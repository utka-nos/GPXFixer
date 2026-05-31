package com.gpxeditor.shared.data.activity

import com.gpxeditor.shared.domain.activity.ActivityDocument
import com.gpxeditor.shared.domain.activity.ActivityMetadata
import com.gpxeditor.shared.domain.activity.ActivityPoint
import com.gpxeditor.shared.domain.activity.ActivitySegment
import com.gpxeditor.shared.domain.activity.ActivityTrack
import com.gpxeditor.shared.domain.fit.FitActivityDocument

object ActivityFitMapper {
    fun fromFitActivity(document: FitActivityDocument): ActivityDocument {
        return ActivityDocument(
            metadata = ActivityMetadata(
                name = document.metadata.name,
                sport = document.metadata.sport,
                startTime = document.metadata.startTime,
                source = document.metadata.source,
            ),
            tracks = document.tracks.map { track ->
                ActivityTrack(
                    name = track.name,
                    segments = track.segments.map { segment ->
                        ActivitySegment(
                            points = segment.points.map { point ->
                                ActivityPoint(
                                    time = point.time,
                                    latitude = point.latitude,
                                    longitude = point.longitude,
                                    elevationMeters = point.elevationMeters,
                                    heartRateBpm = point.heartRateBpm,
                                    powerWatts = point.powerWatts,
                                    cadenceRpm = point.cadenceRpm,
                                    distanceMeters = point.distanceMeters,
                                    speedMetersPerSecond = point.speedMetersPerSecond,
                                )
                            },
                        )
                    },
                )
            },
        )
    }
}
