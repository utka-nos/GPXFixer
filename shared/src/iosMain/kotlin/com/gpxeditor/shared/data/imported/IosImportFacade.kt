package com.gpxeditor.shared.data.imported

import com.gpxeditor.shared.domain.imported.ImportedTrack
import com.gpxeditor.shared.domain.imported.ports.GpxTrackFileStorage
import com.gpxeditor.shared.domain.imported.ports.ImportClock
import com.gpxeditor.shared.domain.imported.ports.ImportIdGenerator
import com.gpxeditor.shared.domain.imported.ports.ImportedTrackStore
import com.gpxeditor.shared.feature.importgpx.ImportGpxTrackRequest
import com.gpxeditor.shared.feature.importgpx.ImportGpxTrackResult
import com.gpxeditor.shared.feature.importgpx.ImportGpxTrackUseCase
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import platform.Foundation.NSArray
import platform.Foundation.NSData
import platform.Foundation.NSDictionary
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSISO8601DateFormatter
import platform.Foundation.NSJSONSerialization
import platform.Foundation.NSMutableArray
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSDate
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.writeToURL
import platform.Foundation.NSJSONWritingPrettyPrinted
import platform.Foundation.NSJSONWritingSortedKeys

@OptIn(ExperimentalForeignApi::class)
class IosImportFacade {
    private val trackStore = IosImportedTrackStore()
    private val importGpxTrackUseCase = ImportGpxTrackUseCase(
        fileStorage = IosGpxTrackFileStorage(),
        trackStore = trackStore,
        idGenerator = IosImportIdGenerator(),
        clock = IosImportClock(),
    )

    fun getImportedTracks(): List<ImportedTrack> {
        return runSuspendBlocking { trackStore.getAll() }
    }

    fun importGpx(
        originalFileName: String,
        content: String,
    ): ImportGpxTrackResult {
        return runSuspendBlocking {
            importGpxTrackUseCase(
                ImportGpxTrackRequest(
                    originalFileName = originalFileName,
                    content = content,
                ),
            )
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosGpxTrackFileStorage : GpxTrackFileStorage {
    private val rootDirectory = documentsDirectory()

    override suspend fun save(storageKey: String, content: String) {
        val url = urlFor(storageKey)
        NSFileManager.defaultManager.createDirectoryAtURL(
            url.URLByDeletingLastPathComponent ?: error("Invalid storage URL"),
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        val didWrite = (content as NSString).writeToURL(
            url = url,
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null,
        )
        check(didWrite) { "Failed to save GPX file" }
    }

    override suspend fun read(storageKey: String): String {
        return NSString.stringWithContentsOfURL(
            url = urlFor(storageKey),
            encoding = NSUTF8StringEncoding,
            error = null,
        ) as String? ?: error("Failed to read GPX file")
    }

    override suspend fun delete(storageKey: String) {
        val url = urlFor(storageKey)
        if (NSFileManager.defaultManager.fileExistsAtPath(url.path ?: return)) {
            NSFileManager.defaultManager.removeItemAtURL(url, error = null)
        }
    }

    private fun urlFor(storageKey: String): NSURL {
        val url = rootDirectory.URLByAppendingPathComponent(storageKey)
            ?: error("Invalid storage key")
        val rootPath = rootDirectory.standardizedURL?.path ?: error("Invalid documents directory")
        val filePath = url.standardizedURL?.path ?: error("Invalid storage URL")

        require(filePath == rootPath || filePath.startsWith("$rootPath/")) {
            "Storage key points outside app storage"
        }

        return url
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosImportedTrackStore : ImportedTrackStore {
    private val metadataUrl = documentsDirectory()
        .URLByAppendingPathComponent(FILE_NAME)
        ?: error("Invalid metadata URL")

    override suspend fun getAll(): List<ImportedTrack> {
        if (!NSFileManager.defaultManager.fileExistsAtPath(metadataUrl.path ?: return emptyList())) {
            return emptyList()
        }

        val data = NSData.dataWithContentsOfURL(metadataUrl) ?: return emptyList()
        if (data.length == 0uL) return emptyList()

        val json = NSJSONSerialization.JSONObjectWithData(
            data = data,
            options = 0u,
            error = null,
        ) as? NSArray ?: return emptyList()

        return List(json.count.toInt()) { index ->
            (json.objectAtIndex(index.toULong()) as NSDictionary).toImportedTrack()
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
        val array = NSMutableArray()
        tracks.forEach { array.addObject(it.toDictionary()) }

        val data = NSJSONSerialization.dataWithJSONObject(
            obj = array,
            options = NSJSONWritingPrettyPrinted or NSJSONWritingSortedKeys,
            error = null,
        ) ?: error("Failed to encode imported tracks")

        NSFileManager.defaultManager.createDirectoryAtURL(
            metadataUrl.URLByDeletingLastPathComponent ?: error("Invalid metadata URL"),
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )

        val didWrite = data.writeToURL(metadataUrl, atomically = true)
        check(didWrite) { "Failed to save imported tracks" }
    }

    private fun ImportedTrack.toDictionary(): NSDictionary {
        return NSMutableDictionary().apply {
            setObject(id, forKey = "id" as NSString)
            setObject(displayName, forKey = "displayName" as NSString)
            setObject(originalFileName, forKey = "originalFileName" as NSString)
            setObject(importedAt, forKey = "importedAt" as NSString)
            setObject(storageKey, forKey = "storageKey" as NSString)
            setObject(NSNumber(int = trackCount), forKey = "trackCount" as NSString)
            setObject(NSNumber(int = pointCount), forKey = "pointCount" as NSString)
        }
    }

    private fun NSDictionary.toImportedTrack(): ImportedTrack {
        return ImportedTrack(
            id = stringValue("id"),
            displayName = stringValue("displayName"),
            originalFileName = stringValue("originalFileName"),
            importedAt = stringValue("importedAt"),
            storageKey = stringValue("storageKey"),
            trackCount = intValue("trackCount"),
            pointCount = intValue("pointCount"),
        )
    }

    private fun NSDictionary.stringValue(key: String): String {
        return objectForKey(key) as? String ?: error("Missing '$key' field")
    }

    private fun NSDictionary.intValue(key: String): Int {
        return (objectForKey(key) as? NSNumber)?.intValue ?: error("Missing '$key' field")
    }

    private companion object {
        const val FILE_NAME = "imported_tracks.json"
    }
}

private class IosImportIdGenerator : ImportIdGenerator {
    override fun nextId(): String = NSUUID().UUIDString()
}

private class IosImportClock : ImportClock {
    private val formatter = NSISO8601DateFormatter()

    override fun nowIsoString(): String {
        return formatter.stringFromDate(NSDate())
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun documentsDirectory(): NSURL {
    return NSFileManager.defaultManager
        .URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
        .first() as? NSURL
        ?: error("Could not resolve documents directory")
}

private fun <T> runSuspendBlocking(block: suspend () -> T): T {
    var value: T? = null
    var failure: Throwable? = null

    block.startCoroutine(
        object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<T>) {
                result
                    .onSuccess { value = it }
                    .onFailure { failure = it }
            }
        },
    )

    failure?.let { throw it }
    @Suppress("UNCHECKED_CAST")
    return value as T
}
