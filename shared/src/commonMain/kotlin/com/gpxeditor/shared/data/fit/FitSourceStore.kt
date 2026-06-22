package com.gpxeditor.shared.data.fit

/**
 * Storage-key convention for the pristine original FIT bytes kept alongside an
 * imported track. The bytes are stored base64-encoded so they fit the text-only
 * [com.gpxeditor.shared.domain.imported.ports.ActivityTrackFileStorage] port, and
 * are read back at export time to patch instead of re-encode.
 */
object FitSourceStore {
    fun keyFor(id: String): String = "tracks/${id.trim()}.source.fit.b64"
}
