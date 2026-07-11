package com.gpxeditor.android.screens

import com.gpxeditor.shared.domain.activity.ActivityDocument
import com.gpxeditor.shared.feature.edittrack.TrimGpxTrackResult
import com.gpxeditor.shared.feature.trackdetail.TrackDetail
import com.gpxeditor.shared.feature.trackdetail.TrackSummary

fun trimPreviewState(
    detail: TrackDetail,
    trimStartIndex: Int,
    trimEndIndex: Int,
    onPreviewTrim: (ActivityDocument, Int, Int) -> TrimGpxTrackResult,
): TrimPreviewState {
    if (detail.summary.pointCount == 0) {
        return TrimPreviewState(
            document = detail.document,
            summary = detail.summary,
            removedPointCount = 0,
            errorMessage = null,
        )
    }

    return when (
        val result = onPreviewTrim(
            detail.document,
            trimStartIndex,
            trimEndIndex,
        )
    ) {
        is TrimGpxTrackResult.Failure -> TrimPreviewState(
            document = null,
            summary = null,
            removedPointCount = 0,
            errorMessage = result.error.message,
        )

        is TrimGpxTrackResult.Success -> TrimPreviewState(
            document = result.document,
            summary = result.summary,
            removedPointCount = result.removedPointCount,
            errorMessage = null,
        )
    }
}

data class TrimPreviewState(
    val document: ActivityDocument?,
    val summary: TrackSummary?,
    val removedPointCount: Int,
    val errorMessage: String?,
)
