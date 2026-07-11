package com.gpxeditor.shared.data.recordtrack

import com.gpxeditor.shared.data.imported.IosGpxTrackFileStorage
import com.gpxeditor.shared.data.imported.IosImportClock
import com.gpxeditor.shared.data.imported.IosImportIdGenerator
import com.gpxeditor.shared.data.imported.IosImportedTrackStore
import com.gpxeditor.shared.data.imported.runSuspendBlocking
import com.gpxeditor.shared.domain.activity.ActivityDocument
import com.gpxeditor.shared.feature.recordtrack.SaveRecordedTrackRequest
import com.gpxeditor.shared.feature.recordtrack.SaveRecordedTrackResult
import com.gpxeditor.shared.feature.recordtrack.SaveRecordedTrackUseCase

/** Swift-facing entry point for saving finished recordings on iOS. */
class IosRecordingFacade {
    private val saveRecordedTrackUseCase = SaveRecordedTrackUseCase(
        fileStorage = IosGpxTrackFileStorage(),
        trackStore = IosImportedTrackStore(),
        idGenerator = IosImportIdGenerator(),
        clock = IosImportClock(),
    )

    /**
     * Returns null instead of throwing when storage fails, so the caller can
     * keep the journal for a later recovery: Kotlin exceptions must not cross
     * the framework boundary into Swift.
     */
    fun saveRecordingOrNull(document: ActivityDocument): SaveRecordedTrackResult? {
        return try {
            runSuspendBlocking { saveRecordedTrackUseCase(SaveRecordedTrackRequest(document)) }
        } catch (throwable: Throwable) {
            null
        }
    }
}
