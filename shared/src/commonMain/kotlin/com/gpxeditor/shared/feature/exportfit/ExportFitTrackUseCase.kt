package com.gpxeditor.shared.feature.exportfit

import com.gpxeditor.shared.data.fit.FitEncoder
import com.gpxeditor.shared.data.fit.FitPatch
import com.gpxeditor.shared.data.fit.FitRawPatcher
import com.gpxeditor.shared.data.fit.FitSourceStore
import com.gpxeditor.shared.domain.activity.ActivityDocument
import com.gpxeditor.shared.domain.imported.ImportedTrack
import com.gpxeditor.shared.domain.imported.ports.ActivityTrackFileStorage
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Produces FIT bytes for an imported track. When the pristine original FIT was kept
 * at import time, it patches that byte stream so unmodelled messages survive the
 * round-trip. Otherwise (e.g. a GPX-origin track exported to FIT) it falls back to
 * encoding the modelled [ActivityDocument] from scratch.
 */
class ExportFitTrackUseCase(
    private val fileStorage: ActivityTrackFileStorage,
) {
    suspend fun encode(track: ImportedTrack, document: ActivityDocument): ByteArray {
        val original = readSource(track.id)
            ?: return FitEncoder.encode(document)
        val patched = FitRawPatcher.patch(original, FitPatch.from(document))
        // Patching a compressed-timestamp stream that lost records yields a valid-CRC
        // file with corrupt timestamps; re-encode from the model in that case.
        return if (patched.timestampChainBroken) FitEncoder.encode(document) else patched.bytes
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun readSource(id: String): ByteArray? {
        return try {
            Base64.decode(fileStorage.read(FitSourceStore.keyFor(id)))
        } catch (throwable: Throwable) {
            null
        }
    }
}
