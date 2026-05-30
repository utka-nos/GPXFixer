package com.gpxeditor.android.data.imported

import android.content.Context
import com.gpxeditor.shared.domain.imported.ImportedTrack
import com.gpxeditor.shared.domain.imported.ports.ImportedTrackStore
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

class JsonImportedTrackStore(
    context: Context,
) : ImportedTrackStore {
    private val metadataFile = File(context.filesDir, FILE_NAME)

    override suspend fun getAll(): List<ImportedTrack> {
        if (!metadataFile.exists()) return emptyList()

        val json = metadataFile.readText().trim()
        if (json.isEmpty()) return emptyList()

        val array = JSONArray(json)
        return List(array.length()) { index ->
            array.getJSONObject(index).toImportedTrack()
        }
    }

    override suspend fun add(track: ImportedTrack) {
        val tracks = getAll()
            .filterNot { it.id == track.id }
            .toMutableList()
        tracks.add(index = 0, element = track)
        writeAll(tracks)
    }

    override suspend fun remove(id: String) {
        writeAll(getAll().filterNot { it.id == id })
    }

    private fun writeAll(tracks: List<ImportedTrack>) {
        metadataFile.parentFile?.mkdirs()
        val array = JSONArray()
        tracks.forEach { array.put(it.toJson()) }
        metadataFile.writeText(array.toString())
    }

    private fun ImportedTrack.toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("displayName", displayName)
            .put("originalFileName", originalFileName)
            .put("importedAt", importedAt)
            .put("storageKey", storageKey)
            .put("trackCount", trackCount)
            .put("pointCount", pointCount)
    }

    private fun JSONObject.toImportedTrack(): ImportedTrack {
        return ImportedTrack(
            id = getString("id"),
            displayName = getString("displayName"),
            originalFileName = getString("originalFileName"),
            importedAt = getString("importedAt"),
            storageKey = getString("storageKey"),
            trackCount = getInt("trackCount"),
            pointCount = getInt("pointCount"),
        )
    }

    private companion object {
        const val FILE_NAME = "imported_tracks.json"
    }
}
