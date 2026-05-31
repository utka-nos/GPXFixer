package com.gpxeditor.shared.data.activity

import com.gpxeditor.shared.domain.activity.ActivityDocument
import com.gpxeditor.shared.domain.activity.ActivityMetadata
import com.gpxeditor.shared.domain.activity.ActivityPoint
import com.gpxeditor.shared.domain.activity.ActivitySegment
import com.gpxeditor.shared.domain.activity.ActivityTrack
import com.gpxeditor.shared.domain.gpx.GpxDocument
import com.gpxeditor.shared.domain.gpx.GpxMetadata
import com.gpxeditor.shared.domain.gpx.GpxTrack
import com.gpxeditor.shared.domain.gpx.GpxTrackPoint
import com.gpxeditor.shared.domain.gpx.GpxTrackSegment

object ActivityGpxMapper {
    fun fromGpxDocument(document: GpxDocument): ActivityDocument {
        return ActivityDocument(
            metadata = ActivityMetadata(
                name = document.metadata?.name,
                description = document.metadata?.description,
                startTime = document.metadata?.time,
                source = document.creator,
            ),
            tracks = document.tracks.map { track ->
                ActivityTrack(
                    name = track.name,
                    description = track.description,
                    segments = track.segments.map { segment ->
                        ActivitySegment(
                            points = segment.points.map { point ->
                                ActivityPoint(
                                    time = point.time,
                                    latitude = point.latitude,
                                    longitude = point.longitude,
                                    elevationMeters = point.elevationMeters,
                                    name = point.name,
                                    description = point.description,
                                )
                            },
                        )
                    },
                )
            },
        )
    }

    fun toGpxDocument(document: ActivityDocument): GpxDocument {
        return GpxDocument(
            version = "1.1",
            creator = document.metadata.source ?: "GPXFixer",
            metadata = GpxMetadata(
                name = document.metadata.name,
                description = document.metadata.description ?: document.metadata.sport,
                time = document.metadata.startTime,
            ),
            tracks = document.tracks.mapNotNull { track ->
                val segments = track.segments.mapNotNull { segment ->
                    val points = segment.points.mapNotNull(::toGpxTrackPoint)
                    if (points.isEmpty()) null else GpxTrackSegment(points)
                }

                if (segments.isEmpty()) {
                    null
                } else {
                    GpxTrack(
                        name = track.name ?: document.metadata.name,
                        description = track.description ?: document.metadata.description ?: document.metadata.sport,
                        segments = segments,
                    )
                }
            },
        )
    }

    private fun toGpxTrackPoint(point: ActivityPoint): GpxTrackPoint? {
        val latitude = point.latitude ?: return null
        val longitude = point.longitude ?: return null

        return GpxTrackPoint(
            latitude = latitude,
            longitude = longitude,
            elevationMeters = point.elevationMeters,
            time = point.time,
            name = point.name,
            description = point.description,
        )
    }
}
