package com.gpxeditor.android.data.imported

import android.content.Context
import com.gpxeditor.shared.domain.imported.ports.GpxTrackFileStorage
import java.io.File

class AndroidGpxTrackFileStorage(
    context: Context,
) : GpxTrackFileStorage {
    private val rootDirectory = context.filesDir

    override suspend fun save(storageKey: String, content: String) {
        val file = fileFor(storageKey)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    override suspend fun read(storageKey: String): String {
        return fileFor(storageKey).readText()
    }

    override suspend fun delete(storageKey: String) {
        fileFor(storageKey).delete()
    }

    private fun fileFor(storageKey: String): File {
        val file = File(rootDirectory, storageKey)
        val canonicalRoot = rootDirectory.canonicalFile
        val canonicalFile = file.canonicalFile

        require(
            canonicalFile == canonicalRoot ||
                canonicalFile.path.startsWith(canonicalRoot.path + File.separator),
        ) {
            "Storage key points outside app storage"
        }

        return canonicalFile
    }
}
