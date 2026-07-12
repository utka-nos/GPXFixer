package com.gpxeditor.shared.feature.recordtrack

import com.gpxeditor.shared.feature.trackdetail.TrackChartPresenter
import com.gpxeditor.shared.feature.trackdetail.TrackChartSample
import com.gpxeditor.shared.feature.trackdetail.TrackChartWindow

/**
 * Sliding-window logic for a live metric chart on the recording screen: the
 * chart follows the newest samples showing the last [SPAN_SECONDS], and the
 * user can pan back to review earlier parts of the ride.
 */
object LiveChartWindow {
    /** Fixed time scale of the chart while it follows live samples. */
    const val SPAN_SECONDS = 60L

    /**
     * The fixed-size window that hugs the live edge. During the first minute it
     * stays at 0..[SPAN_SECONDS], leaving room for future samples; afterwards it
     * shows the latest [SPAN_SECONDS]. Null when there is not enough data to
     * draw a chart (fewer than two distinct timestamps).
     */
    fun liveWindow(samples: List<TrackChartSample>): TrackChartWindow? {
        val full = TrackChartPresenter.fullWindow(samples) ?: return null
        val endSeconds = maxOf(SPAN_SECONDS, full.endSeconds)
        return TrackChartWindow(endSeconds - SPAN_SECONDS, endSeconds)
    }

    /** Shifts [window] by [deltaSeconds], clamped to the recorded ride. */
    fun panned(
        samples: List<TrackChartSample>,
        window: TrackChartWindow,
        deltaSeconds: Long,
    ): TrackChartWindow = TrackChartPresenter.panned(samples, window, deltaSeconds)

    /** True when [window] touches the newest sample, so the chart should follow live updates. */
    fun isAtLiveEdge(samples: List<TrackChartSample>, window: TrackChartWindow): Boolean {
        val full = TrackChartPresenter.fullWindow(samples) ?: return true
        return window.endSeconds >= full.endSeconds
    }
}
